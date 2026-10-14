package com.sizah.content.effect;

import com.sizah.content.effect.entity.PillarBarrageEntity;
import com.sizah.content.enchant.ModEffects;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The "marks" that pillar hits and explosions leave on creatures.
 *
 * <p>Implemented with vanilla **scoreboard tags**: they show up in `/tag @e list`, other mods and command blocks
 * can read them too, there is no field of our own to keep in sync, and they survive a save round-trip.
 *
 * <p>Five marks in total, in two groups:
 *
 * <ul>
 *     <li><b>the black combo</b> — {@link #PILLAR_CLASH} (1), {@link #HORIZONTAL_PAIR} (2) and
 *         {@link #VERTICAL_PAIR} (3): the three collisions of the black block star. Collecting all three
 *         ({@link #hasAll}) triggers the finishing move "black block pillar barrage"
 *         (see {@code PillarBarrageEntity}), and the three are consumed when it triggers;</li>
 *     <li><b>the two named marks</b> — {@link #BLACK} (B) and {@link #WHITE} (W): left by any black block pillar
 *         and by any white block pillar respectively. Carrying **both at once** starts the decay effect
 *         (see {@link Decay}) — that is the whole point of the pair, which is why they are not part of the
 *         counting combo above and why triggering the barrage does not eat them.</li>
 * </ul>
 *
 * <p>Call sites should use {@link #applyAndSync} rather than {@link #apply}: the tags are the source of truth,
 * and the sync is what starts the decay and keeps the HUD icons in step.
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

	/** Mark B: left by any black block pillar that hits the creature. */
	public static final int BLACK = 4;

	/** Mark W: left by any white block pillar that hits the creature. */
	public static final int WHITE = 5;

	/** Every mark, so a reset can wipe them all. */
	private static final int[] ALL = {PILLAR_CLASH, HORIZONTAL_PAIR, VERTICAL_PAIR, BLACK, WHITE};

	private Marks() {
	}

	/** Puts mark number {@code mark} on the entity (does nothing when {@code mark <= 0}). */
	public static void apply(Entity entity, int mark) {
		if (mark <= 0) {
			return;
		}

		entity.addTag(tag(mark));
	}

	/**
	 * Puts a mark on the entity, then brings everything that depends on marks back in step: the decay timer
	 * (both B and W present?) and the HUD icons.
	 *
	 * <p>This is the call site to use; {@link #apply} only writes the tag.
	 */
	public static void applyAndSync(Entity entity, int mark) {
		apply(entity, mark);

		if (!(entity instanceof LivingEntity living)) {
			return;
		}

		// The marks decide everything else, so the state is derived from them rather than tracked separately.
		if (has(living, BLACK) && has(living, WHITE)) {
			Decay.start(living);
		} else {
			Decay.stop(living);
		}

		ModEffects.syncMarks(living, has(living, PILLAR_CLASH), has(living, HORIZONTAL_PAIR), has(living, VERTICAL_PAIR), has(living, BLACK), has(living, WHITE));
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
	 * Whether **all three combo marks** are collected (mark 1 + mark 2 + mark 3).
	 *
	 * <p>This is the "entity carries 3 marks" check: each of the three kinds of collision applies one tier, and
	 * collecting them all triggers the finishing move (the black block pillar barrage, see {@code PillarBarrageEntity}).
	 */
	public static boolean hasAll(Entity entity) {
		return has(entity, PILLAR_CLASH) && has(entity, HORIZONTAL_PAIR) && has(entity, VERTICAL_PAIR);
	}

	/**
	 * Consumes the three combo marks when the finishing move triggers, then re-syncs.
	 *
	 * <p>B and W are deliberately left alone: they are not part of the combo, so a barrage must not cancel a
	 * decay that is already running.
	 */
	public static void clearAll(Entity entity) {
		clear(entity, PILLAR_CLASH);
		clear(entity, HORIZONTAL_PAIR);
		clear(entity, VERTICAL_PAIR);

		if (entity instanceof LivingEntity living) {
			ModEffects.syncMarks(living, false, false, false, has(living, BLACK), has(living, WHITE));
		}
	}

	/** Wipes every mark this mod can leave, including B and W (which also stops the decay timer). */
	public static void reset(Entity entity) {
		for (int mark : ALL) {
			clear(entity, mark);
		}

		if (entity instanceof LivingEntity living) {
			Decay.stop(living);
			ModEffects.syncMarks(living, false, false, false, false, false);
		}
	}

	/** Tag name; namespaced so it does not collide with tags players use themselves. */
	public static String tag(int mark) {
		return mark == BLACK ? "new-mod:markB" : mark == WHITE ? "new-mod:markW" : "new-mod:mark" + mark;
	}
}
