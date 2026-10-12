package com.sizah.network;

import com.sizah.NewMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: request firing a black block pillar.
 *
 * <p>The payload body is empty: the server reads the direction from the player itself, and the client is only
 * responsible for saying "I pressed left click", so the firing direction cannot be forged by the client.
 */
public record FirePillarPayload() implements CustomPacketPayload {
	public static final FirePillarPayload INSTANCE = new FirePillarPayload();

	public static final CustomPacketPayload.Type<FirePillarPayload> TYPE =
			new CustomPacketPayload.Type<>(NewMod.id("fire_pillar"));

	/** A payload body with no fields only needs {@link StreamCodec#unit}; there is no codec to hand-write. */
	public static final StreamCodec<RegistryFriendlyByteBuf, FirePillarPayload> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
