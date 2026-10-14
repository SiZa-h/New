package com.sizah.content.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.sizah.common.SimpleRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The "one material, ten blocks" helper.
 *
 * <p>Almost every new material needs the same set: a metal block, an ore, a raw block, stairs, a slab, a wall,
 * a fence, a door, a trapdoor, a button, a pressure plate. Writing that by hand is ten property blocks and ten
 * registrations, and every one of them is a chance to forget the ore's {@code requiresCorrectToolForDrops()} or
 * a stair's sound type.
 *
 * <p>So the properties are built here once and shared:
 *
 * <pre>{@code
 * public static final Block RUBY_BLOCK = oreFamilyMetal("ruby_block", MapColor.COLOR_RED);
 * public static final Block RUBY_ORE = oreFamilyOre("ruby_ore", MapColor.STONE, 3.0F, 3.0F);
 * public static final Block DEEPSLATE_RUBY_ORE = oreFamilyOre("deepslate_ruby_ore", MapColor.DEEPSLATE, 4.5F, 3.0F);
 * }</pre>
 *
 * <p>Stairs/slabs/walls are the vanilla classes over the same base block, e.g.
 * {@code new StairBlock(base.defaultBlockState(), properties)} — keep the properties shared by calling
 * {@link #copyOf(BlockBehaviour)} so a later balance change (hardness, tool, sound) only has to be made once.
 *
 * <p>Every block made here still needs the usual data: a language entry, a model and a loot table. Datagen
 * covers all three — see {@code com.sizah.datagen} — and the ore also wants a {@code BlockTags} entry so the
 * right pickaxe tier can harvest it.
 */
public final class OreFamilies {
	/** Every block created through this class, in registration order (handy for datagen and for the creative tab). */
	private static final List<Block> CREATED = new ArrayList<>();

	private OreFamilies() {
	}

	/** Blocks created through {@link OreFamilies} so far. */
	public static List<Block> created() {
		return List.copyOf(CREATED);
	}

	/** A metal block: hard, metallic, needs a stone-tier pickaxe, blast resistant. */
	public static Block metal(String name, MapColor colour) {
		return create(name, properties -> properties
				.mapColor(colour)
				.instrument(NoteBlockInstrument.IRON_XYLOPHONE)
				.requiresCorrectToolForDrops()
				.strength(5.0F, 6.0F)
				.sound(SoundType.METAL));
	}

	/**
	 * An ore block.
	 *
	 * @param hardness        mining time; stone ores are 3.0, deepslate variants 4.5
	 * @param blastResistance 3.0 is the vanilla default
	 */
	public static Block ore(String name, MapColor colour, float hardness, float blastResistance) {
		return create(name, properties -> properties
				.mapColor(colour)
				.instrument(NoteBlockInstrument.BASEDRUM)
				.requiresCorrectToolForDrops()
				.strength(hardness, blastResistance));
	}

	/** The block of raw material dropped by an ore: stone-like, no tool requirement. */
	public static Block rawBlock(String name, MapColor colour) {
		return create(name, properties -> properties
				.mapColor(colour)
				.requiresCorrectToolForDrops()
				.strength(5.0F, 6.0F));
	}

	/** A decorative stone-like block: no tool requirement, so it drops bare-handed. */
	public static Block decorative(String name, MapColor colour) {
		return create(name, properties -> properties
				.mapColor(colour)
				.strength(1.5F, 6.0F)
				.sound(SoundType.STONE));
	}

	/** A plant-like block: no collision, instant break, and entities walk straight through it. */
	public static Block plant(String name) {
		return create(name, properties -> properties
				.mapColor(MapColor.PLANT)
				.noCollision()
				.instabreak()
				.sound(SoundType.GRASS)
				.pushReaction(PushReaction.IGNORE_ENTITY));
	}

	/** Builds properties from an existing block, so variants (stairs, slabs, walls) stay in step with their base. */
	public static BlockBehaviour.Properties copyOf(BlockBehaviour base) {
		return BlockBehaviour.Properties.ofFullCopy(base);
	}

	/** Registers a block and remembers it, keeping the {@link Block.Properties} chain in one readable place. */
	private static Block create(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
		Block block = SimpleRegistry.block(name, properties.apply(BlockBehaviour.Properties.of()));
		CREATED.add(block);
		return block;
	}

	/** The {@link Item} form of a block from this class (one line, and the name is shared with the block). */
	public static Item itemOf(String name, Block block) {
		return SimpleRegistry.blockItem(name, block);
	}
}
