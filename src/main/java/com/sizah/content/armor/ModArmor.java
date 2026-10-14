package com.sizah.content.armor;

/**
 * Placeholder for **armour and accessories** (helmets, chestplates, leggings, boots, plus trinket-style items).
 *
 * <p>Armour in 26.3 is data-driven: an item declares
 * {@code properties.humanoidArmor(ArmorMaterial, ArmorType)} (or {@code .animalArmor(...)}), and the material
 * itself is a registry object built from a list of layers. Example shape:
 *
 * <pre>{@code
 * public static final Item RUBY_HELMET = SimpleRegistry.item("ruby_helmet",
 *         properties -> new Item(properties.humanoidArmor(ModArmorMaterials.RUBY, ArmorType.HELMET)),
 *         () -> new Item.Properties().durability(ArmorType.HELMET.getDurability(15)));   // 15 = material multiplier
 * }</pre>
 *
 * <p>Assets: armour layers use their own texture folder ({@code textures/entity/equipment/...}) plus two item
 * textures (inventory icon and the worn layer), so check the vanilla {@code ArmorMaterial} of a similar tier
 * when adding the first set.
 */
public final class ModArmor {
	private ModArmor() {
	}

	/** Registers every armour piece and accessory. Called from {@code NewMod#onInitialize}. */
	public static void register() {
		// TODO: register the first armour material and piece here.
	}
}
