package com.sizah.datagen;

import com.sizah.content.block.ModBlocks;
import com.sizah.content.block.OreFamilies;
import com.sizah.content.item.ModItems;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Block states, block models and item models.
 *
 * <p>Most of the work is done by the two generator objects the vanilla data generator hands over, and the golden
 * rule is: **for a block, call one method and you get both the block state and the matching item model**.
 *
 * <ul>
 *     <li>{@code createTrivialCube(block)} — the full-cube case: 6 faces, one texture
 *         ({@code textures/block/<name>.png}), plus the inventory model. This covers metal blocks, ores and
 *         decorative blocks;</li>
 *     <li>{@code createTrivialBlock(block, TexturedModel.X)} — same, but with a model template of your choice
 *         ({@code CUBE_COLUMN}, {@code CUBE_BOTTOM_TOP}, {@code CUBE_TOP}, {@code ORIENTABLE}, ...) for pillars,
 *         furnaces and the like;</li>
 *     <li>{@code createCrossBlock(block, PlantType.NORMAL)} — flowers and saplings;</li>
 *     <li>{@code createDoor}/{@code createTrapdoor}/{@code createFence}/{@code createStairs}... — the shaped
 *         blocks, so their many states stay correct;</li>
 *     <li>for an item that is not a block, {@code generateFlatItem(item, ModelTemplates.FLAT_ITEM)} uses
 *         {@code textures/item/<name>.png}; {@code FLAT_HANDHELD_ITEM} is the held-tool variant.</li>
 * </ul>
 *
 * <p><b>What you still have to draw:</b> the PNG files. Datagen writes JSON only — put
 * {@code textures/block/<name>.png} or {@code textures/item/<name>.png} next to the other textures and the
 * generated models will find them. Note also that a model the modder wrote by hand (this mod's weapons are
 * 3D {@code models/item/*.json} files) should simply not be generated here: leave the hand-written JSON in
 * {@code src/main/resources} and it stays as it is.
 */
public final class ModModelProvider extends FabricModelProvider {
	public ModModelProvider(FabricPackOutput output) {
		super(output);
	}

	@Override
	public void generateBlockStateModels(BlockModelGenerators generator) {
		// The mod's own plain cubes: one call produces the block state *and* the inventory model, and the texture
		// is looked up as textures/block/<id>.png.
		// add("ruby_block", ModBlocks.RUBY_BLOCK);
		generator.createTrivialCube(ModBlocks.BLACK_BLOCK);
		generator.createTrivialCube(ModBlocks.WHITE_BLOCK);

		// Everything created through OreFamilies: cubes stand in for every shape until the real one is added.
		for (Block block : OreFamilies.created()) {
			generator.createTrivialCube(block);
		}

		// TODO: shaped blocks (stairs/slabs/walls/doors) and plants go here, with the matching generator call.
	}

	@Override
	public void generateItemModels(ItemModelGenerators generator) {
		// A plain item only needs its texture to exist:
		//
		//   generator.generateFlatItem(ModItems.RUBY, ModelTemplates.FLAT_ITEM);
		//
		// and a held tool:
		//
		//   generator.generateFlatItem(ModTools.RUBY_PICKAXE, ModelTemplates.FLAT_HANDHELD_ITEM);

		// Items whose model is written by hand (the two stars and the obsidian cube are 3D models under
		// models/item/) still need their **item definition**: `assets/<ns>/items/<id>.json`, the file that says
		// "this item is drawn with this model". Skipping them entirely leaves the item with no definition at all,
		// and the game then shows the black-and-purple missing model — which is exactly what happened to the white
		// block star before this line existed.
		generator.declareCustomModelItem(ModItems.BLACK_BLOCK_STAR);
		generator.declareCustomModelItem(ModItems.WHITE_BLOCK_STAR);
		generator.declareCustomModelItem(ModItems.OBSIDIAN_CUBE);
	}
}
