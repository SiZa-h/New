package com.sizah.datagen;

import com.sizah.content.block.OreFamilies;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Entry point of the data generators: {@code ./gradlew runDatagen}.
 *
 * <p>Everything written by datagen lands in {@code src/main/generated} and is picked up by the build as if it
 * lived in {@code src/main/resources} — which is the point: the JSON for models, block states, drops, recipes,
 * tags and language files stops being something to hand-write and starts being something to derive.
 *
 * <p><b>The loop while adding content</b>:
 *
 * <ol>
 *     <li>write the block/item class and register it (one or two lines, see {@code SimpleRegistry});</li>
 *     <li>draw the PNG ({@code textures/block/<name>.png} or {@code textures/item/<name>.png});</li>
 *     <li>add the one generator line if the default does not fit (see {@link ModModelProvider});</li>
 *     <li>run {@code ./gradlew runDatagen} and let it write the JSON;</li>
 *     <li>{@code ./gradlew runClient} and look at it in game.</li>
 * </ol>
 *
 * <p><b>The model provider is client-side.</b> Models need the client data generator, which is why
 * {@code build.gradle} configures Fabric Loom's data generation with {@code client = true}. Everything else
 * (loot, recipes, tags) is server-side data and would run without it.
 *
 * <p><b>Do not edit generated files.</b> They are overwritten on the next run; change the provider instead. The
 * files under {@code src/main/resources} stay hand-written, which is why the existing weapon models are not
 * generated here.
 */
public final class NewModDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		FabricDataGenerator.Pack pack = generator.createPack();

		// Assets: block states, block models, item models, and both language files.
		pack.addProvider(ModModelProvider::new);
		pack.addProvider(ModLanguageProvider::english);
		pack.addProvider(ModLanguageProvider::chinese);

		// Data: drops, recipes and tags.
		pack.addProvider(ModBlockLootProvider::new);
		pack.addProvider(ModRecipeProvider::new);
		pack.addProvider(ModTagProvider::items);
		pack.addProvider(ModTagProvider::blocks);

		// The placeholder content types have no generated data yet; touching their registry here means a broken
		// line in one of them fails datagen instead of the game.
		OreFamilies.created();
	}
}
