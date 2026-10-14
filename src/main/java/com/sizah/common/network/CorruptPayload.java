package com.sizah.common.network;

import com.sizah.common.NewMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: request the "sneak left click" infection action (turn a patch of blocks in front into
 * black blocks).
 *
 * <p>The payload body is empty: the ray's origin, its direction and the block it hits are all recomputed on
 * the server from the player's current state, and the client is only responsible for saying "I pressed this".
 */
public record CorruptPayload() implements CustomPacketPayload {
	public static final CorruptPayload INSTANCE = new CorruptPayload();

	public static final CustomPacketPayload.Type<CorruptPayload> TYPE =
			new CustomPacketPayload.Type<>(NewMod.id("corrupt"));

	/** A payload body with no fields only needs {@link StreamCodec#unit}. */
	public static final StreamCodec<RegistryFriendlyByteBuf, CorruptPayload> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
