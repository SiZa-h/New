package com.sizah.common.network;

import com.sizah.common.NewMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: what the black block star does while **sneaking**.
 *
 * <p>Both sneak abilities live on the right button, so the client has to tell them apart by **timing**:
 *
 * <ul>
 *     <li>{@link #INFECT}: sneak + **single** right click — the black block infection. The client sends it once the
 *         double click window has passed without a second press;</li>
 *     <li>{@link #DASH}: sneak + **double** right click — the dash pillar. The client sends it on the second press.</li>
 * </ul>
 *
 * <p>The payload carries only which of the two it is: whether the player is sneaking, what he holds, and the
 * direction all come from the server's own state (see {@code ModNetworking}), so this cannot be used to fire a
 * dash without sneaking.
 */
public record BlackSneakPayload(Action action) implements CustomPacketPayload {
	/** The two sneak abilities of the black block star. */
	public enum Action {
		/** Sneak + single right click: infect a patch of blocks into black blocks. */
		INFECT,
		/** Sneak + double right click: the dash pillar. */
		DASH
	}

	public static final BlackSneakPayload INFECT = new BlackSneakPayload(Action.INFECT);
	public static final BlackSneakPayload DASH = new BlackSneakPayload(Action.DASH);

	public static final CustomPacketPayload.Type<BlackSneakPayload> TYPE =
			new CustomPacketPayload.Type<>(NewMod.id("black_sneak"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BlackSneakPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT,
					payload -> payload.action().ordinal(),
					ordinal -> new BlackSneakPayload(Action.values()[ordinal]));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
