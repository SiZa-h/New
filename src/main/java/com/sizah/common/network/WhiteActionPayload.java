package com.sizah.common.network;

import com.sizah.common.NewMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the white block star's one-shot / hold switches.
 *
 * <ul>
 *     <li>{@link #FIRE_START} / {@link #FIRE_STOP}: **hold left click** — the server streams white block pillars
 *         from a ring behind the player towards the crosshair while the button is down;</li>
 *     <li>{@link #PULL_TOGGLE}: **double click right** — toggle the grip: grab the creature under the crosshair
 *         (≤30 blocks), or let go if it is already held;</li>
 *     <li>{@link #CORRUPT}: **sneak + right click** — the white block infection.</li>
 * </ul>
 *
 * <p>Each payload carries nothing but the action: the shooter's position, the aim and the target are all worked
 * out on the server from its own state, and the server re-checks the weapon and the sneak state before acting.
 */
public record WhiteActionPayload(Action action) implements CustomPacketPayload {
	/** The things the client is allowed to ask for. */
	public enum Action {
		FIRE_START,
		FIRE_STOP,
		PULL_TOGGLE,
		CORRUPT
	}

	public static final WhiteActionPayload FIRE_START = new WhiteActionPayload(Action.FIRE_START);
	public static final WhiteActionPayload FIRE_STOP = new WhiteActionPayload(Action.FIRE_STOP);
	public static final WhiteActionPayload PULL_TOGGLE = new WhiteActionPayload(Action.PULL_TOGGLE);
	public static final WhiteActionPayload CORRUPT = new WhiteActionPayload(Action.CORRUPT);

	public static final CustomPacketPayload.Type<WhiteActionPayload> TYPE =
			new CustomPacketPayload.Type<>(NewMod.id("white_action"));

	/** One VAR_INT ordinal. */
	public static final StreamCodec<RegistryFriendlyByteBuf, WhiteActionPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT,
					payload -> payload.action().ordinal(),
					ordinal -> new WhiteActionPayload(Action.values()[ordinal]));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
