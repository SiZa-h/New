package com.sizah.content.food;

/**
 * Placeholder for **food and consumables** (meals, drinks, potion-like single-use items).
 *
 * <pre>{@code
 * public static final Item RUBY_APPLE = SimpleRegistry.item("ruby_apple",
 *         () -> new Item.Properties().food(ModFoodComponents.RUBY_APPLE),   // FoodProperties built once
 *         ...);
 * }</pre>
 *
 * <p>Keep the {@code FoodProperties} in one place (a {@code ModFoodComponents} class next to this one) so that
 * nutrition/saturation can be compared side by side instead of being scattered over the item declarations.
 * Effects (regeneration, resistance, ...) are attached to the food component itself, which is why food usually
 * needs no separate code beyond the properties.
 */
public final class ModFoods {
	private ModFoods() {
	}

	/** Registers every food item. Called from {@code NewMod#onInitialize}. */
	public static void register() {
		// TODO: register the first food item here.
	}
}
