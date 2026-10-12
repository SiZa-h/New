package com.sizah;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import com.sizah.block.ModBlocks;
import com.sizah.effect.Corruption;
import com.sizah.effect.PushConflicts;
import com.sizah.effect.ViewMotion;
import com.sizah.entity.ModEntities;
import com.sizah.item.ModItemGroups;
import com.sizah.item.ModItems;
import com.sizah.network.ModNetworking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewMod implements ModInitializer {
	public static final String MOD_ID = "new-mod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

		// Blocks are registered before items: the black block's item form (BlockItem) needs an
		// already-constructed Block instance.
		ModBlocks.register();
		ModItems.register();
		ModItemGroups.register();
		ModEntities.register();
		ModNetworking.register();

		// Once at the end of every server tick: clear the "same-tick knockback conflict" records, sample
		// player facing (the view motion gate), and check whether sneaking has ended (if it has, settle the
		// infection)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PushConflicts.clear();

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ViewMotion.sample(player);
				Corruption.tick(player);
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
