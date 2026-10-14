package com.sizah.common.network;

import com.sizah.common.config.Settings;

import com.sizah.common.NewMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: sync the two toggles from the settings screen across.
 *
 * <p>The client sends it once when a setting is changed and once when joining a world/server; the server keeps
 * it in memory and it takes effect immediately (see {@code com.sizah.common.config.Settings}).
 */
public record SettingsPayload(boolean breakBedrock, boolean explosionBreaksBlocks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SettingsPayload> TYPE =
			new CustomPacketPayload.Type<>(NewMod.id("settings"));

	/** Two boolean fields. */
	public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, SettingsPayload::breakBedrock,
					ByteBufCodecs.BOOL, SettingsPayload::explosionBreaksBlocks,
					SettingsPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
