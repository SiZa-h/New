package com.sizah.content.world;

/**
 * Placeholder for **world generation**: ores, plants, features, structures.
 *
 * <p>Worldgen in this version is data-driven, so most of it is JSON that datagen can produce (a
 * {@code FabricDynamicRegistryProvider} or a {@code RegistrySetBuilder} bootstrap in
 * {@code NewModDataGenerator}):
 *
 * <ul>
 *     <li><b>ore / plant</b>: a {@code ConfiguredFeature} + a {@code PlacedFeature}, then a biome modifier
 *         ({@code data/new-mod/neoforge|fabric/biome_modifier/...} style entry) that adds the placed feature to the
 *         biomes you want. The block itself belongs in {@code com.sizah.content.block};</li>
 *     <li><b>structure</b>: a structure type, a structure set (spacing/separation/salt), a template pool and the
 *         NBT pieces, plus a biome tag listing where it may spawn;</li>
 *     <li><b>dimension</b>: a dimension type + dimension + noise settings, all JSON.</li>
 * </ul>
 *
 * <p>Because the ore block is the only Java part, this package mostly exists to hold the bootstrap/tag helpers
 * that the datagen side refers to.
 */
public final class ModWorldGen {
	private ModWorldGen() {
	}

	/** Registers anything worldgen-related that is not pure JSON (feature types, structure types, placement modifiers). */
	public static void register() {
		// TODO: register a custom Feature/StructureType here if a vanilla one does not fit.
	}
}
