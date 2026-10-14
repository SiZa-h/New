package com.sizah.common;

import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The two one-liners every piece of content needs, so that "add a block" and "add an item" stay one call each.
 *
 * <p>Both helpers do the three things that are easy to forget and impossible to fix later (the registry name is
 * baked into every save file):
 *
 * <ol>
 *     <li>build the {@link ResourceKey} from the mod id,</li>
 *     <li>register the object in the vanilla registry,</li>
 *     <li>hand back the object so the caller can keep it in a {@code public static final} field.</li>
 * </ol>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * public static final Block RUBY_BLOCK = SimpleRegistry.block("ruby_block",
 *         BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops());
 *
 * public static final Block RUBY_ORE = SimpleRegistry.block("ruby_ore", oreProperties());
 *
 * // the item form of a block, named after the block itself:
 * public static final Item RUBY_BLOCK_ITEM = SimpleRegistry.blockItem("ruby_block", ModBlocks.RUBY_BLOCK);
 *
 * // a plain item:
 * public static final Item RUBY = SimpleRegistry.item("ruby", new Item.Properties());
 * }</pre>
 *
 * <p><b>Registration order matters</b>: a {@code BlockItem} needs its {@link Block} to exist first, so always
 * register blocks before items (see {@code NewMod#onInitialize}).
 *
 * <p><b>Language keys and assets are not created here.</b> The name, the model and the drop come from datagen
 * (see {@code com.sizah.datagen}) — add a line to {@code ModLanguageProvider} and the model provider and the
 * JSON appears under {@code src/main/generated} on the next {@code ./gradlew runDatagen}.
 */
public final class SimpleRegistry {
	private SimpleRegistry() {
	}

	/** Registers a block under {@code new-mod:<name>}. */
	public static Block block(String name, Block.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, NewMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
	}

	/**
	 * Registers a block with a custom class (stairs, slabs, directional blocks, block entities...).
	 *
	 * <pre>{@code
	 * public static final Block RUBY_LAMP = SimpleRegistry.block("ruby_lamp", RubyLampBlock::new,
	 *         BlockBehaviour.Properties.of().strength(1.0F).lightLevel(state -> 15));
	 * }</pre>
	 */
	public static <T extends Block> T block(String name, java.util.function.Function<Block.Properties, T> factory, Block.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, NewMod.id(name));
		T block = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	/**
	 * Registers a plain item under {@code new-mod:<name>} using a lazy property factory.
	 *
	 * <p>The factory form exists because item properties sometimes need to reference another object that is
	 * still being constructed (a tool material, a food component, an equipment slot); the supplier defers that
	 * read until the item is actually built.
	 */
	public static Item item(String name, Supplier<Item.Properties> properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NewMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.get().setId(key)));
	}

	/** Registers a plain item with ready-made properties. */
	public static Item item(String name, Item.Properties properties) {
		return item(name, () -> properties);
	}

	/** Registers an item of a custom class (weapons, tools, food, spawn eggs...). */
	public static <T extends Item> T item(String name, java.util.function.Function<Item.Properties, T> factory, Supplier<Item.Properties> properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NewMod.id(name));
		T item = factory.apply(properties.get().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/**
	 * Registers the {@link BlockItem} form of a block: the item is named after the block and reuses the block's
	 * language key, so one datagen line covers both names.
	 */
	public static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NewMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties()
				.useBlockDescriptionPrefix()
				.setId(key)));
	}

	/** Registers the {@link BlockItem} form of a block with extra item properties (a creative tab, rarity, ...). */
	public static Item blockItem(String name, Block block, java.util.function.UnaryOperator<Item.Properties> customiser) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NewMod.id(name));
		Item.Properties properties = customiser.apply(new Item.Properties().useBlockDescriptionPrefix()).setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties));
	}
}
