package com.sizah.content.weapon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sizah.content.weapon.entity.ModEntities;
import com.sizah.content.weapon.entity.ThornPillarEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The white block star's **30-hit storm**: once a creature has been struck by white pillars thirty times in a
 * row, every further white pillar hit that lands on it also **summons a thorn white block pillar** out of
 * subspace, and those thorns circle it while it keeps taking white pillar damage.
 *
 * <p>Rules, as specified:
 *
 * <ul>
 *     <li>the counter is per creature and counts **white pillar hits** (the flying bolts, the thorns' own
 *         pierces, and the offhand volley all count — they all go through
 *         {@code WhitePillarEntity#damage});</li>
 *     <li>from the 31st hit on, **every** hit summons one more thorn;</li>
 *     <li>the counter goes stale after two seconds without a white pillar hit, which is the same window the
 *         thorns themselves live on (see {@link ThornPillarEntity#IDLE_TIMEOUT_TICKS});</li>
 *     <li>existing thorns are refreshed by every white pillar hit, so the orbit keeps running while the storm
 *         lands, and fades two seconds after it stops.</li>
 * </ul>
 *
 * <p>Why the tracking lives here rather than on the entity: a crash of hits comes from **different pillar
 * entities**, so the count cannot belong to any single one of them.
 */
public final class WhitePillarStrikes {
	/** How many white pillar hits in a row summon thorns. */
	public static final int HITS_TO_SUMMON = 30;

	/** How long (ticks) the counter survives without a hit: 2 seconds, the same as the thorns' idle window. */
	public static final int COUNTER_TIMEOUT_TICKS = 40;

	/** One creature's storm state. */
	private static final class Record {
		private int hits;
		private int staleTicks;
		private final List<ThornPillarEntity> thorns = new ArrayList<>();
	}

	private static final Map<UUID, Record> RECORDS = new HashMap<>();

	private WhitePillarStrikes() {
	}

	/**
	 * Called by every white pillar hit, right after the damage was dealt.
	 *
	 * <p>This is the only entry point: it bumps the counter, refreshes the thorns already circling the creature,
	 * and — once the count is past {@link #HITS_TO_SUMMON} — summons one more thorn for this hit.
	 */
	public static void onWhitePillarHit(ServerLevel level, LivingEntity victim, Entity owner) {
		Record record = RECORDS.computeIfAbsent(victim.getUUID(), id -> new Record());

		record.hits++;
		record.staleTicks = 0;

		// whatever is already circling keeps running while hits keep landing
		refresh(record);

		if (record.hits > HITS_TO_SUMMON) {
			summon(level, victim, owner, record);
		}
	}

	/** Summons one thorn for this hit, from a random point of the subspace ring around the victim. */
	private static void summon(ServerLevel level, LivingEntity victim, Entity owner, Record record) {
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
		double radius = ThornPillarEntity.WANDER_RADIUS * 0.8D;      // inside the wander radius, so it starts hunting
		double x = victim.getX() + Math.cos(angle) * radius;
		double y = victim.getY() + victim.getBbHeight() * 0.5D;
		double z = victim.getZ() + Math.sin(angle) * radius;

		ThornPillarEntity thorn = new ThornPillarEntity(ModEntities.THORN_PILLAR, level);
		thorn.setOwner(owner);
		thorn.setTarget(victim);
		thorn.setPos(x, y, z);
		level.addFreshEntity(thorn);

		record.thorns.add(thorn);
	}

	private static void refresh(Record record) {
		record.thorns.removeIf(ThornPillarEntity::isRemoved);

		for (ThornPillarEntity thorn : record.thorns) {
			thorn.refresh();
		}
	}

	/** Server tick: drop stale counters, dead creatures, and the bookkeeping for thorns that are gone. */
	public static void tick(MinecraftServer server) {
		if (RECORDS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Record>> iterator = RECORDS.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<UUID, Record> entry = iterator.next();
			Record record = entry.getValue();

			record.staleTicks++;
			record.thorns.removeIf(ThornPillarEntity::isRemoved);

			// The victim left or died: nothing left to count.
			LivingEntity victim = find(server, entry.getKey());

			if (victim == null || victim.isRemoved() || !victim.isAlive()) {
				iterator.remove();
				continue;
			}

			// The storm is over: the counter resets with it, so a new storm needs another thirty hits.
			if (record.staleTicks > COUNTER_TIMEOUT_TICKS) {
				iterator.remove();
			}
		}
	}

	/** Finds the victim in any loaded dimension (the storm may span a teleport, though normally it does not). */
	private static LivingEntity find(MinecraftServer server, UUID id) {
		for (var level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);

			if (entity instanceof LivingEntity living) {
				return living;
			}
		}

		return null;
	}

	/** How many hits this creature has taken in the current storm (for tooling and debugging). */
	public static int hitsOf(LivingEntity victim) {
		Record record = RECORDS.get(victim.getUUID());
		return record == null ? 0 : record.hits;
	}

	/** Drops a creature's state (used when it dies, so the map does not grow forever). */
	public static void forget(LivingEntity victim) {
		RECORDS.remove(victim.getUUID());
	}

	/** Wipes everything when the server stops. */
	public static void clear() {
		RECORDS.clear();
	}
}
