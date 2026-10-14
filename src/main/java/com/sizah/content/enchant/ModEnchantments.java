package com.sizah.content.enchant;

/**
 * Placeholder for **enchantments, status effects and potions**.
 *
 * <p>All three are data-driven in this version, which changes what "adding one" means:
 *
 * <ul>
 *     <li><b>enchantment</b>: a data-driven registry object — the JSON (supported items, cost curve, effects,
 *         exclusive set) is the content, and a custom effect type is only needed when the vanilla effect
 *         components cannot express it. Add the JSON through datagen so the enchantment and its language entry
 *         stay in step;</li>
 *     <li><b>status effect</b>: {@code MobEffect} is still a Java object (register it like a block) and usually
 *         needs an {@code MobEffectInstance} tick hook for anything beyond attribute modifiers;</li>
 *     <li><b>potion</b>: a {@code Potion} registry object that only points at a list of {@code MobEffectInstance}s;
 *         the brewing recipe is a {@code PotionBrewing} entry, and {@code FabricBrewingProvider} in
 *         {@code com.sizah.datagen} writes the brewing data for you.</li>
 * </ul>
 *
 * <p>The mod's existing "throw a status effect at entities" style helpers live in
 * {@code com.sizah.content.effect} — keep behaviour there and keep the <i>definitions</i> here, so that the two
 * do not drift apart.
 */
public final class ModEnchantments {
	private ModEnchantments() {
	}

	/** Registers custom enchantment effect types, status effects and potions. Called from {@code NewMod#onInitialize}. */
	public static void register() {
		// TODO: register the first status effect / potion here.
	}
}
