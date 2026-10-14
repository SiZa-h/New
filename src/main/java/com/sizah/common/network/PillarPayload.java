package com.sizah.common.network;

import com.sizah.client.input.GestureInput;

import com.sizah.common.NewMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "that skill's click has finished resolving, fire the pillar now".
 *
 * <p>The two click-triggered pillars (left-click short pillar, right-click long pillar) are **double click**
 * abilities, while a single click belongs to the mouse gestures — so the client cannot shout "fire" on a click.
 * It fires this request the moment the second click of a double click lands, which is exactly when the click is
 * known not to be a gesture (see {@code GestureInput}), and no pillar is ever fired on a lone click.
 *
 * <p>That is also why the right-click pillar does not go through Fabric's use events any more: those events
 * fire on **both** sides, so the first click of a double click would spawn a long pillar on the server before
 * the client ever got the chance to see whether the second click turns it into a gesture.
 *
 * <p>This payload only carries **which** skill resolved: the firing point and direction are still read from
 * the player on the server, so neither can be forged by the client (same rule as the original left-click
 * request).
 */
public record PillarPayload(Kind kind) implements CustomPacketPayload {
	/** Which of the two click-triggered pillars to fire. */
	public enum Kind {
		/** Left-click short pillar (behaviour 1). */
		SHORT,
		/** Right-click long pillar (behaviour 2). */
		LONG
	}

	public static final PillarPayload SHORT = new PillarPayload(Kind.SHORT);
	public static final PillarPayload LONG = new PillarPayload(Kind.LONG);

	public static final CustomPacketPayload.Type<PillarPayload> TYPE =
			new CustomPacketPayload.Type<>(NewMod.id("pillar"));

	/** Single-field payload body: the kind travels as one VAR_INT ordinal. */
	public static final StreamCodec<RegistryFriendlyByteBuf, PillarPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT,
					payload -> payload.kind().ordinal(),
					ordinal -> new PillarPayload(Kind.values()[ordinal]));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
