package com.sizah.effect;

import net.minecraft.world.entity.Entity;

/**
 * The "marks" an explosion puts on creatures within range.
 *
 * <p>Implemented with vanilla **scoreboard tags**: they show up in `/tag @e list`, other mods and command blocks
 * can read them too, there is no field of our own to keep in sync, and they survive a save round-trip.
 *
 * <p>There are three tiers at present, used to chain the black block star combo; **collecting all three**
 * ({@link #hasAll}) triggers the finishing move "black block pillar barrage" (see {@code PillarBarrageEntity}),
 * and the three tiers are consumed when it triggers.
 *
 * <ul>
 *     <li>{@link #PILLAR_CLASH}: a long black block pillar collides with a short one (the hit that spawns the black hole);</li>
 *     <li>{@link #HORIZONTAL_PAIR}: two horizontally head-on pillars collide;</li>
 *     <li>{@link #VERTICAL_PAIR}: two vertically head-on pillars collide.</li>
 * </ul>
 */
public final class Marks {
	/** No mark. */
	public static final int NONE = 0;

	/** Mark 1: the explosion from a long pillar colliding with a short one. */
	public static final int PILLAR_CLASH = 1;

	/** Mark 2: the explosion from two horizontally head-on pillars colliding. */
	public static final int HORIZONTAL_PAIR = 2;

	/** Mark 3: the explosion from two vertically head-on pillars colliding. */
	public static final int VERTICAL_PAIR = 3;

	private Marks() {
	}

	/** Puts mark number {@code mark} on the entity (does nothing when {@code mark <= 0}). */
	public static void apply(Entity entity, int mark) {
		if (mark <= 0) {
			return;
		}

		entity.addTag(tag(mark));
	}

	/** Whether the entity carries mark number {@code mark}. */
	public static boolean has(Entity entity, int mark) {
		return mark > 0 && entity.entityTags().contains(tag(mark));
	}

	/** Removes a given mark (used when a combo consumes marks). */
	public static void clear(Entity entity, int mark) {
		if (mark <= 0) {
			return;
		}

		entity.removeTag(tag(mark));
	}

	/**
	 * Whether **all three marks** are collected (mark 1 + mark 2 + mark 3).
	 *
	 * <p>This is the "entity carries 3 marks" check: each of the three kinds of collision applies one tier, and
	 * collecting them all triggers the finishing move (the black block pillar barrage, see {@code PillarBarrageEntity}).
	 */
	public static boolean hasAll(Entity entity) {
		return has(entity, PILLAR_CLASH) && has(entity, HORIZONTAL_PAIR) && has(entity, VERTICAL_PAIR);
	}

	/** Wipes all three marks: they are consumed when the finishing move triggers, so the whole combo must be rebuilt. */
	public static void clearAll(Entity entity) {
		clear(entity, PILLAR_CLASH);
		clear(entity, HORIZONTAL_PAIR);
		clear(entity, VERTICAL_PAIR);
	}

	/** Tag name; namespaced so it does not collide with tags players use themselves. */
	public static String tag(int mark) {
		return "new-mod:mark" + mark;
	}
}
