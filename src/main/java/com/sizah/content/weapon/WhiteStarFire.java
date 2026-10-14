package com.sizah.content.weapon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sizah.content.item.ModItems;
import com.sizah.content.weapon.entity.ModEntities;
import com.sizah.content.weapon.entity.WhitePillarEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The white block star's "hold left click": a stream of white block pillars.
 *
 * <p>As specified: while the button is held, bolts are produced **around the player** and then sent off
 * **towards the crosshair**, so they converge on whatever the player is aiming at instead of running parallel to
 * each other. Each bolt then flies at {@link WhitePillarEntity#SPEED_PER_TICK} (256 blocks/s) and dissipates once
 * it has covered {@link WhitePillarEntity#MAX_DISTANCE} (128 blocks).
 *
 * <p>The **server** owns the state and the rate of fire: the client only says "the button is down"
 * ({@code WhiteActionPayload.FIRE_START}) and "it is up" ({@code FIRE_STOP}). That way a modified client cannot
 * turn this into a machine gun, and a weapon swap or a disconnect stops the stream by itself.
 */
public final class WhiteStarFire {
	/** One bolt every 2 ticks: 10 per second, which with a 5 block bolt reads as a continuous stream. */
	public static final int INTERVAL_TICKS = 2;

	/** Radius (blocks) of the ring the bolts are produced on, around the player. */
	public static final double SPAWN_RADIUS = 1.2D;

	/** Height (blocks) above the player's feet at which that ring is drawn. */
	public static final double SPAWN_HEIGHT = 1.0D;

	/** How far along the crosshair the bolts aim (blocks): they all converge on this point. */
	public static final double AIM_DISTANCE = 48.0D;

	/** Players currently holding the button, with the number of ticks until their next bolt. */
	private static final Map<UUID, Integer> FIRING = new HashMap<>();

	private WhiteStarFire() {
	}

	/** The button went down (or is still down). */
	public static void start(ServerPlayer player) {
		FIRING.putIfAbsent(player.getUUID(), 0);
	}

	/** The button came up. */
	public static void stop(ServerPlayer player) {
		FIRING.remove(player.getUUID());
	}

	/** Whether this player's stream is running (used by tooling and by the disconnect cleanup). */
	public static boolean isFiring(ServerPlayer player) {
		return FIRING.containsKey(player.getUUID());
	}

	/** Server tick: one bolt per {@link #INTERVAL_TICKS} for every player holding the button. */
	public static void tick(MinecraftServer server) {
		if (FIRING.isEmpty()) {
			return;
		}

		var iterator = FIRING.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player == null || player.isRemoved()) {
				iterator.remove();
				continue;
			}

			// The weapon has to stay in the main hand: putting it away ends the stream, so a player cannot keep
			// firing while holding something else. Sneaking ends it too — that button belongs to the white block
			// infection while sneak is held (the client sends FIRE_STOP as well, this is the authority).
			if (player.getMainHandItem().getItem() != ModItems.WHITE_BLOCK_STAR || player.isShiftKeyDown()) {
				iterator.remove();
				continue;
			}

			int due = entry.getValue();

			if (due > 0) {
				entry.setValue(due - 1);
				continue;
			}

			entry.setValue(INTERVAL_TICKS - 1);
			fire(player);
		}
	}

	/** One bolt: produced on the ring around the player, aimed at the crosshair. */
	private static void fire(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 view = player.getViewVector(1.0F).normalize();
		Vec3 aim = player.getEyePosition().add(view.scale(AIM_DISTANCE));

		// A ring around the player's middle, so the bolts visibly come "from around him" rather than from one point.
		float angle = player.getRandom().nextFloat() * Mth.TWO_PI;
		Vec3 side = new Vec3(-view.z, 0.0D, view.x).normalize();
		Vec3 around = side.scale(Math.cos(angle) * SPAWN_RADIUS)
				.add(0.0D, SPAWN_HEIGHT + Math.sin(angle) * SPAWN_RADIUS * 0.5D, 0.0D)
				.add(view.scale(SPAWN_RADIUS));

		Vec3 spawn = player.position().add(around);

		WhitePillarEntity pillar = new WhitePillarEntity(ModEntities.WHITE_PILLAR, level);
		pillar.setOwner(player);
		pillar.setFlightDirection(aim.subtract(spawn));      // converge on the crosshair
		pillar.setPos(spawn.x, spawn.y, spawn.z);
		level.addFreshEntity(pillar);
	}

	/** Wipes the state when the server stops, so a reload never keeps stale entries. */
	public static void clear() {
		FIRING.clear();
	}
}
