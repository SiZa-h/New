package com.sizah.datagen;

import java.util.concurrent.CompletableFuture;

import com.sizah.common.NewMod;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Crafting recipes.
 *
 * <p>Recipes are the part of adding content that is most often forgotten, and a missing recipe fails silently:
 * the item simply cannot be made. Generating them keeps the recipe next to the block or item it belongs to.
 *
 * <p>In this version {@code RecipeProvider} is a small object that is handed the registries through
 * {@link BootstrapContext}s, and it writes through its own protected {@code output} field. That is why the shape
 * below looks different from older tutorials: {@link #createRecipeProvider} only builds the object, and
 * {@link Recipes#buildRecipes()} describes the recipes.
 *
 * <p>The four shapes worth copying into {@link Recipes#buildRecipes()}:
 *
 * <pre>{@code
 * // 3x3 block <-> 9 items
 * RecipeProvider.nineBlockStorageRecipes(this.output, RecipeCategory.MISC, ModItems.RUBY,
 *         RecipeCategory.BUILDING_BLOCKS, ModBlocks.RUBY_BLOCK);
 *
 * // a shaped recipe of our own
 * ShapedRecipeBuilder.shaped(this.lookup(Registries.ITEM), RecipeCategory.TOOLS, ModTools.RUBY_PICKAXE)
 *         .define('R', ModItems.RUBY)
 *         .define('S', Items.STICK)
 *         .pattern("RRR").pattern(" S ").pattern(" S ")
 *         .unlockedBy("has_ruby", this.has(ModItems.RUBY))
 *         .save(this.output, key("ruby_pickaxe"));
 *
 * // a shapeless recipe
 * ShapelessRecipeBuilder.shapeless(this.lookup(Registries.ITEM), RecipeCategory.MISC, ModItems.RUBY, 9)
 *         .requires(ModBlocks.RUBY_BLOCK)
 *         .unlockedBy("has_ruby_block", this.has(ModBlocks.RUBY_BLOCK))
 *         .save(this.output, key("ruby_from_block"));
 *
 * // smelting / blasting an ore into an ingot
 * this.oreSmelting(List.of(ModBlocks.RUBY_ORE), RecipeCategory.MISC, ModItems.RUBY, 0.7F, 200, "ruby");
 * this.oreBlasting(List.of(ModBlocks.RUBY_ORE), RecipeCategory.MISC, ModItems.RUBY, 0.7F, 100, "ruby");
 * }</pre>
 */
public final class ModRecipeProvider extends FabricRecipeProvider {
	public ModRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected RecipeProvider createRecipeProvider(
			HolderLookup.Provider registries,
			BootstrapContext<Recipe<?>> recipeOutput,
			BootstrapContext<Advancement> advancementOutput
	) {
		return new Recipes(recipeOutput, advancementOutput);
	}

	/** A recipe id in this mod's namespace: {@code new-mod:<path>}. */
	static ResourceKey<Recipe<?>> key(String path) {
		return ResourceKey.create(Registries.RECIPE, NewMod.id(path));
	}

	/**
	 * The actual recipe list.
	 *
	 * <p>It is a named class instead of an anonymous one so that the recipes can be read (and edited) on their
	 * own, and so that {@code this.output}, {@code this.lookup(...)} and {@code this.has(...)} — all of which
	 * need the registries this provider was created with — are available while the recipes are described.
	 */
	private static final class Recipes extends RecipeProvider {
		private Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
			super(recipes, advancements);
		}

		@Override
		public void buildRecipes() {
			// TODO: the first real recipe goes here (see the class javadoc for the four shapes).
		}
	}
}
