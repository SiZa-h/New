package com.sizah.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sizah.NewMod;
import com.sizah.entity.ModEntities;
import com.sizah.item.PillarKind;
import com.sizah.item.PillarWeaponItem;
import com.sizah.network.CorruptPayload;
import com.sizah.network.FirePillarPayload;
import com.sizah.network.ModNetworking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Client entry point: registers the entity renderers, "left click fires a black block pillar while
 * holding the Black Block Star", the mouse gestures and the settings screen.
 */
public class NewModClient implements ClientModInitializer {
	/** Our own key category (it gets a separate group under "Options → Controls"). */
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(NewMod.id("main"));

	/** The key that opens the settings screen, K by default. */
	private static KeyMapping settingsKey;

	@Override
	public void onInitializeClient() {
		// The eight pillar types share one renderer (the appearance block is decided by the entity type, see ModEntities)
		EntityRenderers.register(ModEntities.SHORT_BLACK_BLOCK_PILLAR, BlackBlockPillarRenderer::new);
		EntityRenderers.register(ModEntities.OBSIDIAN_PILLAR, BlackBlockPillarRenderer::new);
		EntityRenderers.register(ModEntities.LONG_BLACK_BLOCK_PILLAR, BlackBlockPillarRenderer::new);
		EntityRenderers.register(ModEntities.ETERNAL_BLACK_BLOCK_PILLAR, BlackBlockPillarRenderer::new);
		EntityRenderers.register(ModEntities.HORIZONTAL_AXIS_PILLAR, BlackBlockPillarRenderer::new);
		EntityRenderers.register(ModEntities.VERTICAL_AXIS_PILLAR, BlackBlockPillarRenderer::new);
		EntityRenderers.register(ModEntities.BARRAGE_PILLAR, BlackBlockPillarRenderer::new);
		EntityRenderers.register(ModEntities.DASH_PILLAR, BlackBlockPillarRenderer::new);

		// The entities below are logic only and should have no appearance: they all use the same "draw nothing" renderer
		EntityRenderers.register(ModEntities.BLACK_HOLE, InvisibleRenderer::new);
		EntityRenderers.register(ModEntities.HORIZONTAL_AXIS, InvisibleRenderer::new);
		EntityRenderers.register(ModEntities.VERTICAL_AXIS, InvisibleRenderer::new);
		EntityRenderers.register(ModEntities.PILLAR_BARRAGE, InvisibleRenderer::new);
		EntityRenderers.register(ModEntities.MARK_LOCK, InvisibleRenderer::new);

		// Mouse gestures (click + swipe)
		ClientTickEvents.END_CLIENT_TICK.register(GestureInput::tick);

		// Settings screen: read back the last choice, register the key (K by default), and open it when that key is pressed
		ClientSettings.load();
		settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.new-mod.settings", InputConstants.KEY_K, KEY_CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (settingsKey.consumeClick()) {
				if (client.gui.screen() == null && client.player != null) {
					client.gui.setScreen(new ModSettingsScreen(null));
				}
			}
		});

		// Push the settings over on every world/server join (the server side keeps them in memory only, so this is what keeps the two in step)
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientSettings.send());

		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			// clickCount is non-zero only on the press itself; while the left button is held it stays 0, so nothing repeats.
			if (clickCount == 0) {
				return false;
			}

			ItemStack stack = player.getMainHandItem();

			// Neither the Black Block Star nor the Obsidian Cube uses the normal attack key: the left button is the skill key.
			if (!(stack.getItem() instanceof PillarWeaponItem)) {
				return false;
			}

			// A left single click is the opener of a "vertical gesture", so record it first (it does not affect this shot)
			GestureInput.onLeftClick();

			// Sneak + left click (Black Block Star only): infect the blocks in front, do not fire a short pillar
			if (player.isShiftKeyDown()
					&& stack.getItem() instanceof PillarWeaponItem weapon
					&& weapon.kind() == PillarKind.BLACK_BLOCK) {
				if (ClientPlayNetworking.canSend(CorruptPayload.TYPE)) {
					ClientPlayNetworking.send(CorruptPayload.INSTANCE);
					player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				}

				return true;      // intercept the vanilla left click
			}

			// View motion gate (behaviour no. 1): it only opens when both the horizontal and the
			// vertical turn within one tick stay below ViewMotion.STILL_LIMIT_DEGREES degrees;
			// otherwise do nothing and let the vanilla left click run as usual (so attacking/mining still works).
			if (!ModNetworking.viewStill(player)) {
				return false;
			}

			if (!ClientPlayNetworking.canSend(FirePillarPayload.TYPE)) {
				return false; // the server does not have this mod / is not connected yet
			}

			ClientPlayNetworking.send(FirePillarPayload.INSTANCE);

			// Purely visual cooldown animation: the cooldowns of the left and right buttons are decided
			// **independently** on the server side (see WeaponCooldowns), so the client sets no gate
			// here — otherwise the right-click animation would block the left click along with it.
			player.getCooldowns().addCooldown(stack, ModNetworking.FIRE_COOLDOWN_TICKS);
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true); // the vanilla swing was intercepted, so replay it here

			return true; // intercept the vanilla left-click handling
		});
	}
}
