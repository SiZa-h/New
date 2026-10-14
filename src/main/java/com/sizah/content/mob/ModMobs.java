package com.sizah.content.mob;

/**
 * Placeholder for **mobs** (passive, hostile, boss) plus their spawn eggs, spawn rules and loot.
 *
 * <p>A mob needs more than a class, so the checklist for the first one is:
 *
 * <ol>
 *     <li>the entity class (extend the closest vanilla base — {@code PathfinderMob}, {@code Monster},
 *         {@code Animal} — and give it a {@code createAttributes()} plus a {@code MobType}/{@code EntityType});</li>
 *     <li>the entity type: {@code EntityType.Builder.of(MyMob::new, MobCategory.CREATURE).sized(w, h)} registered
 *         under {@code new-mod:<name>}, exactly like {@code ModEntities} does for the pillars;</li>
 *     <li>a renderer in {@code com.sizah.client.render} registered in {@code NewModClient};</li>
 *     <li>a spawn egg item (see {@code SpawnEggItem}) and, if it should appear naturally, a biome modifier or a
 *         {@code SpawnPlacements} registration;</li>
 *     <li>loot: a {@code FabricEntityLootSubProvider} in {@code com.sizah.datagen} — that is where
 *         {@code data/new-mod/loot_table/entities/<name>.json} comes from;</li>
 *     <li>a language entry in {@code ModLanguageProvider} ({@code entity.new-mod.<name>}).</li>
 * </ol>
 */
public final class ModMobs {
	private ModMobs() {
	}

	/** Registers every mob entity type and spawn egg. Called from {@code NewMod#onInitialize}. */
	public static void register() {
		// TODO: register the first mob entity type (and its spawn egg) here.
	}
}
