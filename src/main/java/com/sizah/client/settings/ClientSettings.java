package com.sizah.client.settings;

import com.sizah.content.effect.Explosions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sizah.common.config.Settings;
import com.sizah.common.network.SettingsPayload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The client-side copy of the settings.
 *
 * <p>It takes care of two things:
 *
 * <ul>
 *     <li><b>Remember</b>: write them into {@code config/new-mod.json} so the next launch is the same;</li>
 *     <li><b>Sync</b>: send a {@link SettingsPayload} to the server after a change (and on every world/server join).</li>
 * </ul>
 *
 * <p>The server side is the authority: it decides "whether a pillar can break bedrock" and "whether
 * explosions break blocks" from the toggles it receives.
 */
public final class ClientSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("new-mod.json");

	private static boolean breakBedrock = Settings.DEFAULT_BREAK_BEDROCK;
	private static boolean explosionBreaksBlocks = Settings.DEFAULT_EXPLOSION_BREAKS_BLOCKS;

	private ClientSettings() {
	}

	public static boolean breakBedrock() {
		return breakBedrock;
	}

	public static boolean explosionBreaksBlocks() {
		return explosionBreaksBlocks;
	}

	public static void setBreakBedrock(boolean value) {
		breakBedrock = value;
	}

	public static void setExplosionBreaksBlocks(boolean value) {
		explosionBreaksBlocks = value;
	}

	/** Read back the last choice; if the file is missing or corrupt, use the defaults (a bad config should not keep you out of the game). */
	public static void load() {
		if (!Files.isRegularFile(FILE)) {
			return;
		}

		try {
			Config config = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Config.class);

			if (config != null) {
				breakBedrock = config.breakBedrock;
				explosionBreaksBlocks = config.explosionBreaksBlocks;
			}
		} catch (Exception ignored) {
			// carry on with the defaults
		}
	}

	/** Write the current choices back to the config file. */
	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE,
					GSON.toJson(new Config(breakBedrock, explosionBreaksBlocks)), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// failing to write does not affect this session, it just will not be remembered next launch
		}
	}

	/** After a change: save to disk first, then sync to the server. */
	public static void saveAndSend() {
		save();
		send();
	}

	/** Send the current choices to the server (also sent once when joining a world/server). */
	public static void send() {
		if (ClientPlayNetworking.canSend(SettingsPayload.TYPE)) {
			ClientPlayNetworking.send(new SettingsPayload(breakBedrock, explosionBreaksBlocks));
		}
	}

	/** Config file shape: a plain field class, so any version of Gson can read and write it. */
	private static final class Config {
		private boolean breakBedrock = Settings.DEFAULT_BREAK_BEDROCK;
		private boolean explosionBreaksBlocks = Settings.DEFAULT_EXPLOSION_BREAKS_BLOCKS;

		private Config(boolean breakBedrock, boolean explosionBreaksBlocks) {
			this.breakBedrock = breakBedrock;
			this.explosionBreaksBlocks = explosionBreaksBlocks;
		}
	}
}
