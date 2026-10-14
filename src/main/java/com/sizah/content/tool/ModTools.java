package com.sizah.content.tool;

/**
 * Placeholder for this mod's **tools** (pickaxe / axe / shovel / hoe / shears / hammer ...).
 *
 * <p>The material for the existing weapons lives in {@link ModToolMaterials}; a tool is then one line each:
 *
 * <pre>{@code
 * public static final Item RUBY_PICKAXE = SimpleRegistry.item("ruby_pickaxe",
 *         properties -> new PickaxeItem(ModToolMaterials.RUBY, 1.0F, -2.8F, properties),
 *         () -> new Item.Properties());
 * }</pre>
 *
 * <p>{@code ModToolMaterials} already shows the two things worth deciding per material: the "incorrect for
 * drops" tag (which blocks the tool cannot harvest) and the repair tag.
 */
public final class ModTools {
	private ModTools() {
	}

	/** Registers every tool. Called from {@code NewMod#onInitialize} (before the creative tab is built). */
	public static void register() {
		// TODO: register the first tool here; see the class javadoc for the one-line pattern.
	}
}
