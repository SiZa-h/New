package com.sizah.item;

import com.sizah.NewMod;

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

	private ModToolMaterials() {
	}

	private static TagKey<Item> createRepairTag(Identifier id) {
		return TagKey.create(Registries.ITEM, id);
	}
}
