package com.sizah.client.settings;

import com.sizah.common.config.Settings;
import com.sizah.content.effect.Explosions;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings screen: two toggles.
 *
 * <ul>
 *     <li><b>Bedrock</b>: ignore / break — decides whether a pillar clears bedrock along with everything else;</li>
 *     <li><b>Explosion</b>: whether it breaks blocks — decides whether the explosions from pillars clashing and from the finale blow up terrain.</li>
 * </ul>
 *
 * <p>Clicking a button flips it, saves immediately and sends a packet to the server (see
 * {@link ClientSettings}).
 * How to open it: the <b>K</b> key by default (rebindable under "Options → Controls", in the New Mod
 * category).
 */
public class ModSettingsScreen extends Screen {
	private final Screen parent;

	public ModSettingsScreen(Screen parent) {
		super(Component.translatable("screen.new-mod.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - 110;
		int y = this.height / 2 - 30;

		this.addRenderableWidget(Button.builder(label("option.new-mod.break_bedrock", ClientSettings.breakBedrock()),
				button -> {
					ClientSettings.setBreakBedrock(!ClientSettings.breakBedrock());
					button.setMessage(label("option.new-mod.break_bedrock", ClientSettings.breakBedrock()));
					ClientSettings.saveAndSend();
				}).bounds(x, y, 220, 20).build());

		this.addRenderableWidget(Button.builder(
				label("option.new-mod.explosion_breaks", ClientSettings.explosionBreaksBlocks()),
				button -> {
					ClientSettings.setExplosionBreaksBlocks(!ClientSettings.explosionBreaksBlocks());
					button.setMessage(label("option.new-mod.explosion_breaks", ClientSettings.explosionBreaksBlocks()));
					ClientSettings.saveAndSend();
				}).bounds(x, y + 26, 220, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
				.bounds(x, y + 64, 220, 20).build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	/** Button text in the form "title: on/off" (the same key with a `.on` / `.off` suffix). */
	private static Component label(String key, boolean value) {
		return Component.translatable(key, Component.translatable(key + (value ? ".on" : ".off")));
	}
}
