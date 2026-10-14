package com.sizah.common;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import com.sizah.content.block.ModBlocks;
import com.sizah.content.ModCreativeTabs;
import com.sizah.content.armor.ModArmor;
import com.sizah.content.block.ModBlocks;
import com.sizah.content.effect.Corruption;
import com.sizah.content.effect.Decay;
import com.sizah.content.effect.PushConflicts;
import com.sizah.content.enchant.ModEffects;
import com.sizah.content.enchant.ModEnchantments;
import com.sizah.content.food.ModFoods;
import com.sizah.content.item.ModItems;
import com.sizah.content.machine.ModMachines;
import com.sizah.content.mob.ModMobs;
import com.sizah.content.projectile.ModProjectiles;
import com.sizah.content.tool.ModTools;
import com.sizah.content.weapon.OffhandWhiteStar;
import com.sizah.content.weapon.PinTargets;
import com.sizah.content.weapon.WhiteStarFire;
import com.sizah.content.weapon.entity.ModEntities;
import com.sizah.content.world.ModWorldGen;
import com.sizah.common.network.ModNetworking;

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

		// One register() per content type. They are empty until the first piece of that kind of content
		// arrives, and they must all run **before** the creative tab is built below.
		ModTools.register();
		ModArmor.register();
		ModFoods.register();
		ModProjectiles.register();
		ModMobs.register();
		ModMachines.register();
		ModWorldGen.register();
		ModEnchantments.register();

		// Status effects come before anything that can mark a creature, because marking one needs the effects
		// that display the marks (and the decay clock needs its effect holder).
		ModEffects.register();

		// The creative tab lists every item, so it is built last.
		ModCreativeTabs.register();

		// Entity types, then the network channels.
		ModEntities.register();
		ModNetworking.register();

		// The white block star's offhand ability: it reacts to damage events, so it needs no client input at all.
		OffhandWhiteStar.register();

		// Once at the end of every server tick: clear the "same-tick knockback conflict" records, advance the
		// decay clock (both marks at once), and check whether sneaking has ended (if it has, settle the infection)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PushConflicts.clear();
			Decay.tick(server);
			WhiteStarFire.tick(server);
			PinTargets.tick(server);

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Corruption.tick(player);
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
