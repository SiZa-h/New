package com.sizah.item;

import com.sizah.NewMod;
import com.sizah.block.ModBlocks;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final ResourceKey<Item> BLACK_BLOCK_KEY = ResourceKey.create(
			Registries.ITEM,
			NewMod.id("black_block")
	);
	public static final ResourceKey<Item> BLACK_BLOCK_STAR_KEY = ResourceKey.create(
			Registries.ITEM,
			NewMod.id("black_block_star")
	);
	public static final ResourceKey<Item> OBSIDIAN_CUBE_KEY = ResourceKey.create(
			Registries.ITEM,
			NewMod.id("obsidian_cube")
	);

	/**
	 * The item form of the black block: {@link BlockItem} lets it be placed in the world as
	 * {@link ModBlocks#BLACK_BLOCK}.
	 * {@code useBlockDescriptionPrefix()} means the item name reuses the block name (language key
	 * block.new-mod.black_block).
	 */
	public static final Item BLACK_BLOCK = new BlockItem(
			ModBlocks.BLACK_BLOCK,
			new Item.Properties()
					.useBlockDescriptionPrefix()
					.setId(BLACK_BLOCK_KEY)
	);

	/** Black Block Star: the pillar looks like a black block and deals 6 points of damage that bypasses armor. */
	public static final Item BLACK_BLOCK_STAR = new PillarWeaponItem(
			PillarKind.BLACK_BLOCK,
			new Item.Properties()
					.sword(ModToolMaterials.BLACK_BLOCK_STAR, 3.0F, -2.4F)
					.setId(BLACK_BLOCK_STAR_KEY)
	);

	/** Obsidian Cube: the pillar looks like obsidian and deals 4 points of damage that armor reduces (everything else matches the Black Block Star). */
	public static final Item OBSIDIAN_CUBE = new PillarWeaponItem(
			PillarKind.OBSIDIAN,
			new Item.Properties()
					.sword(ModToolMaterials.OBSIDIAN_CUBE, 2.0F, -2.4F)
					.setId(OBSIDIAN_CUBE_KEY)
	);

	private ModItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, BLACK_BLOCK_KEY, BLACK_BLOCK);
		Registry.register(BuiltInRegistries.ITEM, BLACK_BLOCK_STAR_KEY, BLACK_BLOCK_STAR);
		Registry.register(BuiltInRegistries.ITEM, OBSIDIAN_CUBE_KEY, OBSIDIAN_CUBE);
	}
}
