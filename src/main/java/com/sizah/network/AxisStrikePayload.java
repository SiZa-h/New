package com.sizah.network;

import com.sizah.NewMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: ask the server to spawn a clash pillar at whichever end of the blank axis the mouse
 * gesture hit.
 *
 * <p>The four values correspond to the four directions of "click + swipe":
 *
 * <ul>
 *     <li>{@link #HORIZONTAL_LEFT}: horizontal axis's <b>left</b> end → grows right, knocks enemies to the
 *         <b>right</b> (swipe right);</li>
 *     <li>{@link #HORIZONTAL_RIGHT}: horizontal axis's <b>right</b> end → grows left, knocks them to the
 *         <b>left</b> (swipe left);</li>
 *     <li>{@link #VERTICAL_BOTTOM}: vertical axis's <b>bottom</b> end → grows up, <b>launches</b> enemies
 *         (swipe up);</li>
 *     <li>{@link #VERTICAL_TOP}: vertical axis's <b>top</b> end → grows down, <b>slams</b> enemies down
 *         (swipe down).</li>
 * </ul>
 *
 * <p>Both the endpoint position and the growth direction are read from the **blank axis entity** on the
 * server, and the client only says "which end", so coordinates cannot be forged.
 */
public record AxisStrikePayload(int endpoint) implements CustomPacketPayload {
	/** Horizontal axis's left end (positive endpoint): grows to the right. */
	public static final int HORIZONTAL_LEFT = 0;

	/** Horizontal axis's right end (negative endpoint): grows to the left. */
	public static final int HORIZONTAL_RIGHT = 1;

	/** Vertical axis's bottom end (negative endpoint): grows upward (launch). */
	public static final int VERTICAL_BOTTOM = 2;

	/** Vertical axis's top end (positive endpoint): grows downward (slam down). */
	public static final int VERTICAL_TOP = 3;

	/** Total number of gestures, used for validity checks. */
	public static final int COUNT = 4;

	public static final CustomPacketPayload.Type<AxisStrikePayload> TYPE =
			new CustomPacketPayload.Type<>(NewMod.id("axis_strike"));

	/** Single-field payload body: one VAR_INT. */
	public static final StreamCodec<RegistryFriendlyByteBuf, AxisStrikePayload> STREAM_CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, AxisStrikePayload::endpoint, AxisStrikePayload::new);

	/** Vertical gesture (the group belonging to left click). */
	public static boolean isVertical(int endpoint) {
		return endpoint == VERTICAL_BOTTOM || endpoint == VERTICAL_TOP;
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
