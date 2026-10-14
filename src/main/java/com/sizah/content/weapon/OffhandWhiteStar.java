package com.sizah.content.weapon;

import com.sizah.content.item.ModItems;
import com.sizah.content.weapon.entity.ModEntities;
import com.sizah.content.weapon.entity.WhitePillarEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The white block star **in the offhand**: every time its holder lands a hit, a small volley of white block
 * pillars strikes the victim.
 *
 * <p>As specified: *in the offhand*, each **effective attack** (anything that deals damage) spawns 2–5 white
 * pillars that hit the entity that took the damage. "Effective" is taken literally, so it covers ordinary melee
 * hits, sweeping edge, ranged hits, and the mod's own skills — the damage event does not care where the hit came
 * from, only that the holder caused it.
 *
 * <p>Implementation notes:
 *
 * <ul>
 *     <li>The volley is fired **from around the holder towards the victim**, at most
 *         {@link #SPAWN_DISTANCE} blocks behind the victim, so the bolts visibly converge on it;</li>
 *     <li>each bolt is an ordinary {@link WhitePillarEntity}, so it damages, marks with W and cuts through blocks
 *         exactly like the ones fired by hand;</li>
 *     <li>the count is drawn from 2..5 — with a fixed random source per hit, so two hits in the same tick do not
 *         produce the same count.</li>
 * </ul>
 */
public final class OffhandWhiteStar {
	/** How far behind the victim the volley is produced (blocks). */
	public static final double SPAWN_DISTANCE = 3.0D;

	/** Fewest pillars per hit. */
	public static final int MIN_PILLARS = 2;

	/** Most pillars per hit. */
	public static final int MAX_PILLARS = 5;

	/** How far off the direct line a bolt may be launched, so the volley does not look like one thick bolt (radians). */
	private static final double SPREAD = 0.28D;

	/**
	 * Minimum ticks between two volleys from the same holder.
	 *
	 * <p>With "any damage counts", a volley can itself cause damage (its own pillars hit for damage), and that
	 * would fire another volley: two ticks of breathing room turn that chain into a burst instead of a runaway.
	 */
	public static final int VOLLEY_COOLDOWN_TICKS = 2;

	/** Player UUID → the tick at which that holder may volley again. */
	private static final java.util.Map<java.util.UUID, Long> NEXT_VOLLEY = new java.util.HashMap<>();

	private OffhandWhiteStar() {
	}

	/** Registered once from {@code NewMod}: fires the volley whenever the white star's holder deals damage. */
	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (blocked || damageTaken <= 0.0F) {
				return;
			}

			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}

			// Whoever caused the damage has to be a player holding the white block star in the offhand.
			Entity attacker = source.getEntity();

			if (!(attacker instanceof ServerPlayer player)) {
				return;
			}

			if (player.getOffhandItem().getItem() != ModItems.WHITE_BLOCK_STAR) {
				return;
			}

			if (entity == player) {
				return;      // self-inflicted damage (an explosion of one's own) does not fire a volley at oneself
			}

			// Rate limit: see VOLLEY_COOLDOWN_TICKS.
			long now = level.getGameTime();
			Long next = NEXT_VOLLEY.get(player.getUUID());

			if (next != null && now < next) {
				return;
			}

			NEXT_VOLLEY.put(player.getUUID(), now + VOLLEY_COOLDOWN_TICKS);
			fire(level, player, entity);
		});
	}

	/** The volley itself: 2..5 bolts, produced between the holder and the victim and aimed at the victim. */
	private static void fire(ServerLevel level, ServerPlayer player, Entity victim) {
		int count = MIN_PILLARS + player.getRandom().nextInt(MAX_PILLARS - MIN_PILLARS + 1);

		Vec3 center = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
		Vec3 fromHolder = center.subtract(player.getEyePosition());

		if (fromHolder.lengthSqr() < 1.0E-6D) {
			fromHolder = new Vec3(1.0D, 0.0D, 0.0D);
		}

		Vec3 approach = fromHolder.normalize();

		for (int i = 0; i < count; i++) {
			// Start a little way back along the line the holder is on, then fly through the victim: the bolts pass
			// through the target instead of stopping short of it.
			Vec3 offset = approach.add(randomJitter(player)).normalize().scale(SPAWN_DISTANCE);
			Vec3 from = center.add(offset);

			WhitePillarEntity pillar = new WhitePillarEntity(ModEntities.WHITE_PILLAR, level);
			pillar.setOwner(player);
			pillar.setFlightDirection(center.subtract(from));
			pillar.setPos(from.x, from.y, from.z);
			level.addFreshEntity(pillar);
		}
	}

	/** A little randomness on the approach direction, so a volley reads as several bolts rather than one thick one. */
	private static Vec3 randomJitter(Player player) {
		return new Vec3(
				player.getRandom().nextGaussian() * SPREAD,
				player.getRandom().nextGaussian() * SPREAD,
				player.getRandom().nextGaussian() * SPREAD);
	}
}
