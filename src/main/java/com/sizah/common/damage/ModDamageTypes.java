package com.sizah.common.damage;

import com.sizah.common.NewMod;

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

	/**
	 * The white block star's crushing damage.
	 *
	 * <p>The weapon itself is a skeleton for now (see {@code ModItems.WHITE_BLOCK_STAR}): there is nothing that
	 * deals this damage yet. The type and its data file exist so that the death message is already localised and
	 * the ability work only has to reference it. Like the obsidian cube it is **not** in the bypasses_armor tag,
	 * so armour reduces it — which is also how the white star differs from its black twin by default.
	 */
	public static final ResourceKey<DamageType> WHITE_BLOCK_STAR = ResourceKey.create(
			Registries.DAMAGE_TYPE,
			NewMod.id("white_block_star")
	);

	/**
	 * The decay damage: dealt to a creature that carries **both** the black mark B and the white mark W
	 * (see {@code content.effect.Decay}). Like the other two it is not in the bypasses_armor tag.
	 */
	public static final ResourceKey<DamageType> DECAY = ResourceKey.create(
			Registries.DAMAGE_TYPE,
			NewMod.id("decay")
	);

	private ModDamageTypes() {
	}
}
