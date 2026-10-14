package com.sizah.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The **decay** effect: the status a creature gets when it carries both the black mark B and the white mark W.
 *
 * <p>This class is only the effect itself — the name, the icon colour and the particles (the base implementation
 * draws the usual "entity effect" puff in {@code color}). The <b>numbers</b> live in {@link Decay}, which owns the
 * clock and does the damage on the server tick.
 *
 * <p>That split is deliberate: the effect has to be a registry object, while "add to the remaining time instead of
 * resetting it" and "remember how long it has been running" are the two things a vanilla {@code MobEffectInstance}
 * cannot express. Damage per bite is {@code 2 × elapsed seconds}, every {@link Decay#INTERVAL_TICKS} ticks.
 */
public final class DecayEffect extends MobEffect {
	/** Icon colour: a violet grey, so it reads as neither the black nor the white mark. */
	private static final int COLOR = 0x8E7CC3;

	public DecayEffect() {
		super(MobEffectCategory.HARMFUL, COLOR);
	}
}
