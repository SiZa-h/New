package com.sizah.client;

import com.sizah.client.input.GestureInput;
import com.sizah.client.input.WhiteStarInput;
import com.sizah.client.render.BlackBlockPillarRenderer;
import com.sizah.client.render.InvisibleRenderer;
import com.sizah.client.render.WhitePillarRenderer;
import com.sizah.client.settings.ClientSettings;
import com.sizah.client.settings.ModSettingsScreen;
import com.sizah.common.config.Settings;
import com.sizah.content.item.ModItems;
import com.sizah.content.weapon.WeaponCooldowns;

import com.mojang.blaze3d.platform.InputConstants;
import com.sizah.common.NewMod;
import com.sizah.content.weapon.entity.ModEntities;
import com.sizah.content.weapon.PillarKind;
import com.sizah.content.weapon.PillarWeaponItem;
import com.sizah.common.network.CorruptPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

		// The white block star's bolt: the same drawing trick, but it flies instead of growing, and it is drawn
		// as white blocks (see WhitePillarRenderer).
		EntityRenderers.register(ModEntities.WHITE_PILLAR, WhitePillarRenderer::new);

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

		// The white block star's two held abilities: hold left = a stream of white pillars, hold right = carry the
		// creature under the crosshair. It has a tick handler of its own because both last as long as the button is
		// down, which is not something a click callback can report.
		ClientTickEvents.END_CLIENT_TICK.register(WhiteStarInput::tick);

		// Leaving the world must not leave the server thinking a button is still down.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WhiteStarInput.releaseAll());

		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			// clickCount is non-zero only on the press itself; while the left button is held it stays 0, so nothing repeats.
			if (clickCount == 0) {
				return false;
			}

			ItemStack stack = player.getMainHandItem();

			// The white block star: the left button is its held fire (started and stopped by WhiteStarInput), and
			// sneak + left is its white block infection. Vanilla attacking is intercepted either way.
			if (stack.getItem() == ModItems.WHITE_BLOCK_STAR) {
				if (player.isShiftKeyDown()) {
					WhiteStarInput.sendCorrupt();
					player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				}

				return true;
			}

			// Neither the Black Block Star nor the Obsidian Cube uses the normal attack key: the left button is the skill key.
			if (!(stack.getItem() instanceof PillarWeaponItem weapon)) {
				return false;
			}

			// Sneak + left click (Black Block Star only): infect the blocks in front, do not fire a short pillar
			if (player.isShiftKeyDown() && weapon.kind() == PillarKind.BLACK_BLOCK) {
				if (ClientPlayNetworking.canSend(CorruptPayload.TYPE)) {
					ClientPlayNetworking.send(CorruptPayload.INSTANCE);
					player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				}

				return true;      // intercept the vanilla left click
			}

			// Everything else is handled by GestureInput, which decides whether this click opens a vertical
			// gesture or joins the previous click to make the double click that fires the short pillar. The click
			// is only *recorded* here and the pillar request goes out later, from GestureInput.
			//
			// There is no view-motion gate any more, and a lone click fires nothing at all.
			return GestureInput.onLeftClick();
		});

		// Mining is suppressed while the white block star is held with the left button down: that button is its
		// fire, so it must not also chew through the wall in front of the player.
		//
		// This is needed on top of the attack callback above because vanilla only routes the **press** through
		// startAttack(); for as long as the button stays down it goes straight to the block-breaking loop instead.
		// The event fires on both sides, so the client never starts mining and the server refuses it if it ever
		// arrives from elsewhere.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().getItem() == ModItems.WHITE_BLOCK_STAR) {
				return InteractionResult.FAIL;      // no mining, no swinging at blocks: this button fires pillars
			}

			return InteractionResult.PASS;
		});
	}
}
