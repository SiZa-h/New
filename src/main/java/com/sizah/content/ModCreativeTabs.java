package com.sizah.content;

import com.sizah.content.item.ModItems;

import com.sizah.common.NewMod;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTabs {
	/** This mod's own creative tab "NEW"; the tab title lives under the language key itemGroup.new-mod.new. */
	public static final ResourceKey<CreativeModeTab> NEW_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			NewMod.id("new")
	);

	private ModCreativeTabs() {
	}

	public static void register() {
		CreativeModeTab newTab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.new-mod.new"))
				.icon(() -> new ItemStack(ModItems.BLACK_BLOCK_STAR))
				.displayItems((parameters, output) -> {
					output.accept(ModItems.BLACK_BLOCK);
					output.accept(ModItems.WHITE_BLOCK);
					output.accept(ModItems.BLACK_BLOCK_STAR);
					output.accept(ModItems.WHITE_BLOCK_STAR);
					output.accept(ModItems.OBSIDIAN_CUBE);
				})
				.build();

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, NEW_TAB, newTab);
	}
}
