package com.sizah.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * This mod's own tags.
 *
 * <p>Tags are how content is grouped without hard-coding lists: "everything a ruby pickaxe can mine",
 * "everything that repairs the black block star", "every ore that spawns in the overworld". They are plain
 * data, so {@code com.sizah.datagen.ModTagProvider} writes them — a tag never has to be hand-typed in JSON.
 *
 * <p>Two rules that matter in practice:
 *
 * <ol>
 *     <li><b>Never write into a vanilla tag</b> ({@code minecraft:mineable/pickaxe} and friends) from datagen:
 *         the generated file would replace the vanilla one instead of extending it. Use
 *         {@code data/<ns>/tags/...} under the {@code minecraft} namespace as a hand-written file if you really
 *         have to add to a vanilla tag, or better, define your own tag and reference it;</li>
 *     <li><b>The repair tag must exist even if it is empty</b>, otherwise an item whose material names it will
 *         fail to load. {@code new-mod:black_block} and {@code new-mod:obsidian} are the two the existing
 *         weapons already use, so they are kept here.</li>
 * </ol>
 */
public final class ModTags {
	private ModTags() {
	}

	/** Item tags. */
	public static final class Items {
		/** Everything that repairs the black block star (used by {@code ModToolMaterials.BLACK_BLOCK_STAR}). */
		public static final TagKey<Item> BLACK_BLOCK = item("black_block");

		/** Everything that repairs the obsidian cube (used by {@code ModToolMaterials.OBSIDIAN_CUBE}). */
		public static final TagKey<Item> OBSIDIAN = item("obsidian");

		/** Everything that repairs the white block star (used by {@code ModToolMaterials.WHITE_BLOCK_STAR}). */
		public static final TagKey<Item> WHITE_BLOCK = item("white_block");

		private Items() {
		}

		private static TagKey<Item> item(String path) {
			return TagKey.create(Registries.ITEM, NewMod.id(path));
		}
	}

	/** Block tags. */
	public static final class Blocks {
		/** Ores that should be mined with a pickaxe of at least the given tier; add as the materials arrive. */
		public static final TagKey<Block> ORES = block("ores");

		private Blocks() {
		}

		private static TagKey<Block> block(String path) {
			return TagKey.create(Registries.BLOCK, NewMod.id(path));
		}
	}

	/** Convenience for a tag created on the fly (for example by a datagen provider). */
	public static TagKey<Item> itemTag(String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NewMod.MOD_ID, path));
	}

	/** Convenience for a block tag created on the fly. */
	public static TagKey<Block> blockTag(String path) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(NewMod.MOD_ID, path));
	}
}
