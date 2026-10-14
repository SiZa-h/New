package com.sizah.datagen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.sizah.content.block.ModBlocks;
import com.sizah.content.block.OreFamilies;
import com.sizah.content.item.ModItems;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Block drops.
 *
 * <p>{@link #generate()} is only needed for the blocks whose drop is **not** "itself": ores that drop a raw
 * item, slabs that drop two when double, silk-touch-only blocks, and anything with a random count.
 * Every plain block is covered by {@link #dropSelf()} in the loops below, so a new decorative block needs no
 * loot code at all.
 *
 * <p>The helpers worth knowing (all inherited from {@code BlockLootSubProvider}):
 *
 * <ul>
 *     <li>{@code createOreDrop(block, drop)} — ore with the usual fortune handling;</li>
 *     <li>{@code createSlabItemTable(slab)} — double slab drops two;</li>
 *     <li>{@code createSingleItemTableWithSilkTouch(block, drop)} — silk touch keeps the block itself;</li>
 *     <li>{@code createShearsOnlyDrop(item)} — leaves, vines and the like;</li>
 *     <li>{@code createSingleItemTable(drop, count)} — a fixed or random count.</li>
 * </ul>
 */
public final class ModBlockLootProvider extends FabricBlockLootSubProvider {
	public ModBlockLootProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public void generate() {
		// The hand-written blocks of this mod: every one of them simply drops itself.
		this.dropSelf(ModBlocks.BLACK_BLOCK);
		this.dropSelf(ModBlocks.WHITE_BLOCK);

		// Everything created through OreFamilies (metal blocks, ores, raw blocks, decorative blocks).
		for (Block block : OreFamilies.created()) {
			this.dropSelf(block);
		}

		// An ore that drops a raw item instead of itself looks like this:
		//
		//   this.add(ModBlocks.RUBY_ORE, block -> this.createOreDrop(block, ModItems.RUBY_RAW));
		//
		// and a slab:
		//
		//   this.add(ModBlocks.RUBY_SLAB, this::createSlabItemTable);
		//
		// TODO: add the non-dropSelf cases here as soon as the first ore/slab arrives.
	}

	/** Kept for readability: every entry of {@link ModItems} that is a block item is dropped by its block. */
	@SuppressWarnings("unused")
	private static List<Item> blockItems() {
		return List.of(ModItems.BLACK_BLOCK, ModItems.WHITE_BLOCK);
	}
}
