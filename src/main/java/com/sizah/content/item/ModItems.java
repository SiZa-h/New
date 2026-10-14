package com.sizah.content.item;

import com.sizah.content.tool.ModToolMaterials;
import com.sizah.content.weapon.PillarWeaponItem;
import com.sizah.content.weapon.PillarKind;

import com.sizah.common.NewMod;
import com.sizah.content.block.ModBlocks;

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
	public static final ResourceKey<Item> WHITE_BLOCK_KEY = ResourceKey.create(
			Registries.ITEM,
			NewMod.id("white_block")
	);
	public static final ResourceKey<Item> BLACK_BLOCK_STAR_KEY = ResourceKey.create(
			Registries.ITEM,
			NewMod.id("black_block_star")
	);
	public static final ResourceKey<Item> OBSIDIAN_CUBE_KEY = ResourceKey.create(
			Registries.ITEM,
			NewMod.id("obsidian_cube")
	);
	public static final ResourceKey<Item> WHITE_BLOCK_STAR_KEY = ResourceKey.create(
			Registries.ITEM,
			NewMod.id("white_block_star")
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

	/** The item form of the white block, the exact opposite of the black block (see {@link ModBlocks#WHITE_BLOCK}). */
	public static final Item WHITE_BLOCK = new BlockItem(
			ModBlocks.WHITE_BLOCK,
			new Item.Properties()
					.useBlockDescriptionPrefix()
					.setId(WHITE_BLOCK_KEY)
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

	/**
	 * White Block Star: the black block star's mirror.
	 *
	 * <p><b>A plain sword for now — its abilities are deliberately not implemented yet.</b> The black block star
	 * carries its behaviour through {@link PillarKind}, and every pillar ability needs its own **entity type**
	 * (the type is what decides which block a pillar is drawn with), so whatever the white star ends up doing is a
	 * set of white block pillar types plus the trigger that fires them.
	 *
	 * <p>What is already in place, so the design can start from a working weapon:
	 *
	 * <ul>
	 *     <li>the item, its 3D model and its two item-atlas textures
	 *         ({@code assets/new-mod/models/item/white_block_star.json}, written by {@code tools/gen-assets.js});</li>
	 *     <li>{@link ModToolMaterials#WHITE_BLOCK_STAR}: the black block star's numbers, repaired with the white
	 *         block;</li>
	 *     <li>an entry in the mod's creative tab and in both language files;</li>
	 *     <li>{@code ModDamageTypes.WHITE_BLOCK_STAR} and its data file, so the death message already exists.</li>
	 * </ul>
	 *
	 * <p>When the design is settled, the shape of the change is:
	 *
	 * <ol>
	 *     <li>a {@code PillarKind.WHITE_BLOCK} variant (appearance = the white block, its own damage type);</li>
	 *     <li>one white entity type per behaviour, mirroring the black ones in {@code ModEntities};</li>
	 *     <li>this field becomes a {@link PillarWeaponItem} of that kind, and the trigger (click / gesture
	 *         handling) learns which of the two stars it is holding.</li>
	 * </ol>
	 */
	public static final Item WHITE_BLOCK_STAR = new Item(
			new Item.Properties()
					.sword(ModToolMaterials.WHITE_BLOCK_STAR, 3.0F, -2.4F)
					.setId(WHITE_BLOCK_STAR_KEY)
	);

	private ModItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, BLACK_BLOCK_KEY, BLACK_BLOCK);
		Registry.register(BuiltInRegistries.ITEM, WHITE_BLOCK_KEY, WHITE_BLOCK);
		Registry.register(BuiltInRegistries.ITEM, BLACK_BLOCK_STAR_KEY, BLACK_BLOCK_STAR);
		Registry.register(BuiltInRegistries.ITEM, OBSIDIAN_CUBE_KEY, OBSIDIAN_CUBE);
		Registry.register(BuiltInRegistries.ITEM, WHITE_BLOCK_STAR_KEY, WHITE_BLOCK_STAR);
	}
}
