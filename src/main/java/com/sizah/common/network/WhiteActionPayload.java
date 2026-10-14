package com.sizah.common.network;

import com.sizah.common.NewMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the white block star's "hold the mouse button" abilities.
 *
 * <p>Three actions, all of them either "the button went down" or "the button came up":
 *
 * <ul>
 *     <li>{@link #FIRE_START} / {@link #FIRE_STOP}: hold **left click** — while it is held, the server keeps
 *         spawning white block pillars around the player and sending them off along the crosshair;</li>
 *     <li>{@link #PULL_START} / {@link #PULL_STOP}: hold **right click** — the creature the crosshair points at
 *         (within 30 blocks) is held on the player's line of sight until the button is released;</li>
 *     <li>{@link #CORRUPT}: sneak + left click, the white block infection (sent once, on the press).
 *         The block actually painted is decided on the **server** from the held weapon, so this payload cannot be
 *         used to paint arbitrary blocks.</li>
 * </ul>
 *
 * <p>Every one of them carries nothing but the action itself: the shooter's position, the aim direction and the
 * target are all recomputed on the server from its own state, so none of it can be forged by the client.
 */
public record WhiteActionPayload(Action action) implements CustomPacketPayload {
	/** The things the client is allowed to ask for. */
	public enum Action {
		FIRE_START,
		FIRE_STOP,
		PULL_START,
		PULL_STOP,
		CORRUPT
	}

	public static final WhiteActionPayload FIRE_START = new WhiteActionPayload(Action.FIRE_START);
	public static final WhiteActionPayload FIRE_STOP = new WhiteActionPayload(Action.FIRE_STOP);
	public static final WhiteActionPayload PULL_START = new WhiteActionPayload(Action.PULL_START);
	public static final WhiteActionPayload PULL_STOP = new WhiteActionPayload(Action.PULL_STOP);
	public static final WhiteActionPayload CORRUPT = new WhiteActionPayload(Action.CORRUPT);

	public static final CustomPacketPayload.Type<WhiteActionPayload> TYPE =
			new CustomPacketPayload.Type<>(NewMod.id("white_action"));

	/** One VAR_INT ordinal: four start/stop pairs and one one-shot. */
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
