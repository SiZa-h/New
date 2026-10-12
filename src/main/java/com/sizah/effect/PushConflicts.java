package com.sizah.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Detects "knocked back by two kinds of black block entity in the same tick".
 *
 * <p>Requirement: when a **creature is damaged** and receives knockback from two kinds of black block entity at
 * the same time, its **velocity is zeroed** — two forces pulling in different directions cancel each other out,
 * so the result should be being pinned in place, not the two adding up into a faster velocity.
 *
 * <p>Implementation: keyed by "target UUID", it records the type of the last entity to push it **this tick**;
 * switching to the other type within the same tick counts as a conflict. The map is cleared once per server tick,
 * so nothing accumulates.
 */
public final class PushConflicts {
	private record Push(long tick, EntityType<?> type) {
	}

	private static final Map<UUID, Push> LAST_PUSH = new HashMap<>();

	/**
	 * Registers one knockback and answers "is this a conflict?".
	 *
	 * @return {@code true} means **another** kind of black block entity has already pushed it this tick
	 *         (the caller should zero the velocity)
	 */
	public static boolean isConflicting(ServerLevel level, Entity target, EntityType<?> type) {
		long tick = level.getGameTime();
		Push previous = LAST_PUSH.get(target.getUUID());
		boolean conflicting = previous != null && previous.tick() == tick && previous.type() != type;

		LAST_PUSH.put(target.getUUID(), new Push(tick, type));
		return conflicting;
	}

	/** Cleared once per server tick (registered at the mod entry point). */
	public static void clear() {
		LAST_PUSH.clear();
	}
}
