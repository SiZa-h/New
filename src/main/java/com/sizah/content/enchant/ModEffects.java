package com.sizah.content.enchant;

import com.sizah.common.NewMod;
import com.sizah.content.effect.Decay;
import com.sizah.content.effect.DecayEffect;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * This mod's status effects.
 *
 * <p>Five of them are **display only**: one icon per mark, so a player can see at a glance which marks a creature
 * carries without opening the scoreboard. Their duration is {@link MobEffectInstance#INFINITE_DURATION} and they
 * are added and removed from the **tags** (see {@code content.effect.Marks}), which stay the single source of
 * truth — an icon can therefore never disagree with the mark it stands for.
 *
 * <p>{@link #DECAY} is the real one; its numbers live in {@code content.effect.Decay}.
 */
public final class ModEffects {
	/** Mark 1..3 of the black combo, and the two named marks B and W. */
	public static final Holder<MobEffect> MARK_PILLAR_CLASH = register("mark_pillar_clash", MobEffectCategory.HARMFUL, 0x1C1C20);
	public static final Holder<MobEffect> MARK_HORIZONTAL_PAIR = register("mark_horizontal_pair", MobEffectCategory.HARMFUL, 0x2E2E38);
	public static final Holder<MobEffect> MARK_VERTICAL_PAIR = register("mark_vertical_pair", MobEffectCategory.HARMFUL, 0x44445A);
	public static final Holder<MobEffect> MARK_BLACK = register("mark_black", MobEffectCategory.HARMFUL, 0x0A0A0F);
	public static final Holder<MobEffect> MARK_WHITE = register("mark_white", MobEffectCategory.HARMFUL, 0xEDEDEB);

	/** The decay effect: both marks at once, then damage over time (see {@code content.effect.Decay}). */
	public static final Holder<MobEffect> DECAY = registerDecay();

	private ModEffects() {
	}

	/** Registers the effects. Called from {@code NewMod#onInitialize}, before anything can mark a creature. */
	public static void register() {
		// The static initialisers above do the registration; reading the fields here is what forces them to run.
		if (DECAY == null || MARK_BLACK == null) {
			throw new IllegalStateException("mob effects failed to register");
		}
	}

	/**
	 * Keeps the mark icons in step with the tags: every mark the creature carries gets its icon, every icon whose
	 * tag is gone is removed. The icons never expire on their own.
	 */
	public static void syncMarks(LivingEntity living, boolean mark1, boolean mark2, boolean mark3, boolean black, boolean white) {
		set(living, MARK_PILLAR_CLASH, mark1);
		set(living, MARK_HORIZONTAL_PAIR, mark2);
		set(living, MARK_VERTICAL_PAIR, mark3);
		set(living, MARK_BLACK, black);
		set(living, MARK_WHITE, white);
	}

	private static void set(LivingEntity living, Holder<MobEffect> effect, boolean wanted) {
		MobEffectInstance current = living.getEffect(effect);

		if (wanted && current == null) {
			living.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		} else if (!wanted && current != null) {
			living.removeEffect(effect);
		}
	}

	private static Holder<MobEffect> register(String path, MobEffectCategory category, int color) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, NewMod.id(path), new MobEffect(category, color) {
		});
	}

	/** The decay effect has a class of its own (its own particles); it also hands its holder to the clock. */
	private static Holder<MobEffect> registerDecay() {
		Holder<MobEffect> holder = Registry.registerForHolder(
				BuiltInRegistries.MOB_EFFECT, NewMod.id("decay"), new DecayEffect());
		Decay.effect = holder;

		return holder;
	}
}
