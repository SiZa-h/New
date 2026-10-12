package com.sizah.damage;

import com.sizah.NewMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * This mod's damage types.
 *
 * <p>Damage types are a **datapack registry** ({@code data/<ns>/damage_type/*.json}), so the code side only needs
 * a {@link ResourceKey} to reference one; properties such as "bypasses armor" are decided by **tags**
 * (see {@code data/minecraft/tags/damage_type/bypasses_armor.json}).
 */
public final class ModDamageTypes {
	/** The black block star's crushing damage: it carries the bypasses_armor tag, so it bypasses armor and resistance. */
	public static final ResourceKey<DamageType> BLACK_BLOCK_PILLAR = ResourceKey.create(
			Registries.DAMAGE_TYPE,
			NewMod.id("black_block_pillar")
	);

	/** The obsidian cube's crushing damage: it is **not** in the bypasses_armor tag, so armor reduces it as usual. */
	public static final ResourceKey<DamageType> OBSIDIAN_CUBE = ResourceKey.create(
			Registries.DAMAGE_TYPE,
			NewMod.id("obsidian_cube")
	);

	private ModDamageTypes() {
	}
}
