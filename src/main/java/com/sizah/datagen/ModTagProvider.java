package com.sizah.datagen;

import java.util.concurrent.CompletableFuture;

import com.sizah.common.ModTags;
import com.sizah.content.block.ModBlocks;
import com.sizah.content.item.ModItems;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * This mod's tags (see {@link ModTags}).
 *
 * <p>Tags are generated rather than hand-written so that a tag can never point at content that does not exist
 * any more — a hand-written tag fails silently at runtime, a generated one fails the build.
 *
 * <p>Adding to a **vanilla** tag is allowed and is the normal way to make a custom pickaxe-able block behave:
 * the file is merged on load rather than replaced, because this mod never sets the "replace" flag. (Hand-written
 * files must still not *replace* a vanilla tag by accident — that is what {@code "replace": true} would do.)
 */
public final class ModTagProvider {
	private ModTagProvider() {
	}

	/** Item tags: the repair materials the two weapons already reference. */
	public static FabricTagsProvider<Item> items(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		return new FabricTagsProvider<>(output, Registries.ITEM, registries) {
			@Override
			protected void addTags(HolderLookup.Provider registries) {
				// The black block star is repaired with the black block.
				this.tag(ModTags.Items.BLACK_BLOCK).add(ModItems.BLACK_BLOCK.builtInRegistryHolder().key());
				// The obsidian cube is repaired with vanilla obsidian.
				this.tag(ModTags.Items.OBSIDIAN).add(Items.OBSIDIAN.builtInRegistryHolder().key());
				// The white block star is repaired with the white block.
				this.tag(ModTags.Items.WHITE_BLOCK).add(ModItems.WHITE_BLOCK.builtInRegistryHolder().key());

				// TODO: more item tags (a "gems" tag, a "repairs the ruby tools" tag, ...).
			}
		};
	}

	/** Block tags: mining tiers, "needs a tool" groups, and the ore group used by worldgen. */
	public static FabricTagsProvider<Block> blocks(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		return new FabricTagsProvider<>(output, Registries.BLOCK, registries) {
			@Override
			protected void addTags(HolderLookup.Provider registries) {
				// The mod's own ore group; ores join it as they arrive.
				this.tag(ModTags.Blocks.ORES);

				// A vanilla tag, so the black block can actually be mined with a pickaxe.
				this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.BLACK_BLOCK.builtInRegistryHolder().key());

				// TODO: more block tags (needs_stone_tool, needs_iron_tool, ...).
			}
		};
	}
}
