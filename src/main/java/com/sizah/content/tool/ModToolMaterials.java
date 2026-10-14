package com.sizah.content.tool;

import com.sizah.common.NewMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

public final class ModToolMaterials {
	/** Black Block Star: durability 1561, attack bonus 3.0, repair material is the black block tag. */
	public static final ToolMaterial BLACK_BLOCK_STAR = new ToolMaterial(
			BlockTags.INCORRECT_FOR_IRON_TOOL, // incorrectBlocksForDrops
			1561,                              // durability
			8.0F,                              // mining speed
			3.0F,                              // attack damage bonus
			10,                                // enchantment value
			createRepairTag(NewMod.id("black_block"))
	);

	/** Obsidian Cube: same durability and enchantment, but a **lower attack bonus** (2.0), and the repair material is obsidian. */
	public static final ToolMaterial OBSIDIAN_CUBE = new ToolMaterial(
			BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
			1561,
			8.0F,
			2.0F,                              // attack damage bonus: lower than the Black Block Star
			10,
			createRepairTag(NewMod.id("obsidian"))
	);

	/**
	 * White Block Star: the black block star's mirror, so every number matches (durability 1561, mining speed 8.0,
	 * attack bonus 3.0, enchantment value 10) and only the repair material differs — the white block.
	 *
	 * <p>The repair tag has to exist even while its only entry is the white block; {@code ModTagProvider} writes it.
	 */
	public static final ToolMaterial WHITE_BLOCK_STAR = new ToolMaterial(
			BlockTags.INCORRECT_FOR_IRON_TOOL,
			1561,
			8.0F,
			3.0F,
			10,
			createRepairTag(NewMod.id("white_block"))
	);

	private ModToolMaterials() {
	}

	private static TagKey<Item> createRepairTag(Identifier id) {
		return TagKey.create(Registries.ITEM, id);
	}
}
