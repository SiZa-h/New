package com.sizah.block;

import com.sizah.NewMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final ResourceKey<Block> BLACK_BLOCK_KEY = ResourceKey.create(
			Registries.BLOCK,
			NewMod.id("black_block")
	);

	/**
	 * Black block: a placeable full block.
	 * Hardness 1.5, blast resistance 6.0, same sound as stone; it does not require the correct tool,
	 * so breaking it bare-handed still drops itself
	 * (the drop is defined by data/new-mod/loot_table/blocks/black_block.json).
	 */
	public static final Block BLACK_BLOCK = new Block(
			BlockBehaviour.Properties.of()
					.setId(BLACK_BLOCK_KEY)
					.mapColor(MapColor.COLOR_BLACK)
					.strength(1.5F, 6.0F)
					.sound(SoundType.STONE)
	);

	private ModBlocks() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, BLACK_BLOCK_KEY, BLACK_BLOCK);
	}
}
