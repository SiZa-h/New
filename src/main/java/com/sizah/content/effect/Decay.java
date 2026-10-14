package com.sizah.content.effect;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.sizah.common.damage.ModDamageTypes;
import com.sizah.content.enchant.ModEffects;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The **decay** clock: a creature carrying both the black mark B and the white mark W takes growing damage.
 *
 * <p>Specification, implemented literally:
 *
 * <ul>
 *     <li>being marked with **both** B and W starts it (see {@link Marks#applyAndSync}); losing either one stops it;</li>
 *     <li>it bites every **0.5 s** for {@code 2 × elapsed seconds} damage — at 0.5 s that is 1 damage, at 1 s 2,
 *         at 1.5 s 3, and it keeps growing for as long as the marks stay on;</li>
 *     <li>re-applying **adds to the remaining time** instead of resetting it: with 0.23 s left, another 3 s makes
 *         it 3.23 s, and the elapsed counter (and therefore the damage) keeps climbing.</li>
 * </ul>
 *
 * <p>Why a clock of our own instead of a plain {@code MobEffectInstance}: vanilla can only *set* a duration, and
 * both "add to the remaining time" and "remember how long it has been running" are exactly the two things it
 * cannot do. The registered effect ({@link DecayEffect}) is therefore only the icon and the particles, and this
 * class owns the numbers — it is ticked from {@code NewMod}'s server tick, and it follows the marks, so clearing
 * the marks, dying or leaving the world always stops the damage too.
 */
public final class Decay {
	/** One bite every 0.5 s. */
	public static final int INTERVAL_TICKS = 10;

	/** Damage per second of elapsed time: 0.5 s → 1 damage means 2.0. */
	public static final float DAMAGE_PER_SECOND = 2.0F;

	/** How much time one application adds. */
	public static final int APPLICATION_TICKS = 60;

	/** The registered decay effect; assigned by {@code ModEffects} when it registers it. */
	public static Holder<MobEffect> effect;

	private static final Map<UUID, State> STATES = new HashMap<>();

	private Decay() {
	}

	/** One creature's decay: how much is left, and how long it has been running (which is what the damage uses). */
	private static final class State {
		private int remaining;
		private int elapsed;
		private int sinceLastBite;
	}

	/** Both marks at once: start the decay, or add another {@link #APPLICATION_TICKS} to the time already left. */
	public static void start(LivingEntity living) {
		if (!(living.level() instanceof ServerLevel)) {
			return;
		}

		State state = STATES.get(living.getUUID());

		if (state == null) {
			state = new State();
			STATES.put(living.getUUID(), state);
		}

		// "adding to the remaining time" also applies to the very first application, so the clock starts at 3 s
		state.remaining += APPLICATION_TICKS;
		state.sinceLastBite = 0;

		if (effect != null) {
			living.addEffect(new MobEffectInstance(effect, state.remaining, 0, false, true));
		}
	}

	/** Losing a mark (or dying, or leaving) ends it. */
	public static void stop(LivingEntity living) {
		if (STATES.remove(living.getUUID()) != null && effect != null) {
			living.removeEffect(effect);
		}
	}

	/** Whether this creature is currently decaying (used by the debug output and by tests). */
	public static boolean isDecaying(LivingEntity living) {
		return STATES.containsKey(living.getUUID());
	}

	/** Server tick: bite every {@link #INTERVAL_TICKS}, keep the icon's duration in step, and expire at zero. */
	public static void tick(net.minecraft.server.MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, State>> iterator = STATES.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<UUID, State> entry = iterator.next();
			LivingEntity living = find(server, entry.getKey());
			State state = entry.getValue();

			// gone, dead or left the server: drop the clock
			if (living == null || living.isRemoved() || !living.isAlive()) {
				if (living != null && effect != null) {
					living.removeEffect(effect);
				}

				iterator.remove();
				continue;
			}

			// the marks are the source of truth: losing either one stops the decay
			if (!Marks.has(living, Marks.BLACK) || !Marks.has(living, Marks.WHITE)) {
				if (effect != null) {
					living.removeEffect(effect);
				}

				iterator.remove();
				continue;
			}

			if (!(living.level() instanceof ServerLevel level)) {
				continue;
			}

			state.remaining--;
			state.elapsed++;
			state.sinceLastBite++;

			if (state.sinceLastBite >= INTERVAL_TICKS) {
				state.sinceLastBite = 0;
				hurt(level, living, state.elapsed / 20.0D);
			}

			if (state.remaining <= 0) {
				if (effect != null) {
					living.removeEffect(effect);
				}

				iterator.remove();
			} else if (effect != null && living.getEffect(effect) == null) {
				// the icon expired before the clock did (another source refreshed it, for instance): put it back
				living.addEffect(new MobEffectInstance(effect, state.remaining, 0, false, true));
			}
		}
	}

	/** {@code damage = 2 × elapsed seconds}, through this mod's own damage type so the death message is localised. */
	private static void hurt(ServerLevel level, LivingEntity victim, double elapsedSeconds) {
		float damage = (float) (elapsedSeconds * DAMAGE_PER_SECOND);

		if (damage <= 0.0F) {
			return;
		}

		victim.hurtServer(level, level.damageSources().source(ModDamageTypes.DECAY, null), damage);
		// decay eats away, it does not throw anyone around
		victim.setDeltaMovement(Vec3.ZERO);
		victim.syncVelocity = true;
	}

	/** Every player's current decay remaining/elapsed, for the debug log. */
	public static String describe(ServerPlayer player) {
		State state = STATES.get(player.getUUID());

		if (state == null) {
			return "none";
		}

		return String.format("%.2fs left, %.2fs elapsed, %.1f damage per bite",
				state.remaining / 20.0D, state.elapsed / 20.0D, (state.elapsed / 20.0D) * DAMAGE_PER_SECOND);
	}

	/** Wipes the clock when the server stops, so a reload never keeps stale entries. */
	public static void clear() {
		STATES.clear();
	}

	private static LivingEntity find(net.minecraft.server.MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			net.minecraft.world.entity.Entity entity = level.getEntity(id);

			if (entity instanceof LivingEntity living) {
				return living;
			}
		}

		return null;
	}
}
