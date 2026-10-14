package com.sizah.content.machine;

/**
 * Placeholder for **multiblock structures and machines** (a block plus a block entity plus ticking behaviour).
 *
 * <p>The block-entity pattern that keeps ticking cheap:
 *
 * <ol>
 *     <li>the {@code BlockEntity} class holds the state (inventory, progress) and exposes
 *         {@code getUpdateTag}/{@code getUpdatePacket} only for what the client must see;</li>
 *     <li>the block overrides {@code entityBlock()} (or {@code newBlockEntity(...)}) and {@code getTicker(...)},
 *         and returns the ticker only for the server side unless the client genuinely needs to tick;</li>
 *     <li>inventory: implement {@code Container}/{@code WorldlyContainer} and register the
 *         {@code ItemStorage.SIDED} capability if other mods should be able to pipe into it;</li>
 *     <li>recipes: a custom {@code RecipeType}/{@code RecipeSerializer} plus datagen recipes — a machine recipe
 *         provider belongs next to {@code ModRecipeProvider} in {@code com.sizah.datagen};</li>
 *     <li>a multiblock is normally validated from the controller block entity by pattern-matching the
 *         surrounding positions, and cached until a neighbour changes.</li>
 * </ol>
 */
public final class ModMachines {
	private ModMachines() {
	}

	/** Registers machine blocks and their block-entity types. Called from {@code NewMod#onInitialize}. */
	public static void register() {
		// TODO: register the first machine block + block entity type here.
	}
}
