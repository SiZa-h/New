package com.sizah.client.input;

import com.sizah.common.network.WhiteActionPayload;
import com.sizah.content.item.ModItems;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The white block star's **hold** inputs, which the black block star does not have.
 *
 * <p>Both of its abilities last as long as a mouse button is down, so this class watches the two buttons directly
 * instead of waiting for click events, and tells the server exactly twice: "it went down" and "it came up". The
 * server owns the rate of fire and the grip (see {@code WhiteStarFire} and {@code PinTargets}); nothing about the
 * aiming or the timing is decided here.
 *
 * <table>
 *     <caption>Inputs</caption>
 *     <tr><th>Input</th><th>What happens</th></tr>
 *     <tr><td>Hold left</td><td>A stream of white block pillars from around the player towards the crosshair</td></tr>
 *     <tr><td>Hold right</td><td>The creature under the crosshair (≤30 blocks) is carried on the view line</td></tr>
 *     <tr><td>Sneak + left</td><td>White block infection (handled by the attack callback, see {@code NewModClient})</td></tr>
 * </table>
 *
 * <p>Releasing, swapping the weapon away, opening a screen or losing the world all send the matching stop, so the
 * server can never be left holding a button that is no longer down.
 */
public final class WhiteStarInput {
	private static boolean leftWasDown;
	private static boolean rightWasDown;

	private WhiteStarInput() {
	}

	/** Client tick: poll both buttons and keep the server's idea of them in step. */
	public static void tick(Minecraft client) {
		Player player = client.player;

		if (player == null || client.gui.screen() != null) {
			releaseAll();
			return;
		}

		boolean holdingStar = player.getMainHandItem().getItem() == ModItems.WHITE_BLOCK_STAR;

		// Both held abilities are off while sneaking: sneak + left click is the white block **infection** (sent by
		// the attack callback in NewModClient), so the fire must not also run — otherwise that same button press
		// would shoot a pillar *and* start the infection. Sneaking with the right button held just lets go.
		boolean sneaking = player.isShiftKeyDown();

		// The weapon has to be in the main hand: putting it away releases whatever is being held.
		boolean leftDown = holdingStar && !sneaking && client.options.keyAttack.isDown();
		boolean rightDown = holdingStar && !sneaking && client.options.keyUse.isDown();

		if (leftDown != leftWasDown) {
			send(leftDown ? WhiteActionPayload.FIRE_START : WhiteActionPayload.FIRE_STOP);
			leftWasDown = leftDown;
		}

		if (rightDown != rightWasDown) {
			send(rightDown ? WhiteActionPayload.PULL_START : WhiteActionPayload.PULL_STOP);
			rightWasDown = rightDown;
		}
	}

	/** Called when the weapon is put away or the world is left: make sure nothing stays "held" on the server. */
	public static void releaseAll() {
		if (leftWasDown) {
			send(WhiteActionPayload.FIRE_STOP);
			leftWasDown = false;
		}

		if (rightWasDown) {
			send(WhiteActionPayload.PULL_STOP);
			rightWasDown = false;
		}
	}

	/** Sends the sneak + left click infection; the server decides which block is painted from the held weapon. */
	public static void sendCorrupt() {
		send(WhiteActionPayload.CORRUPT);
	}

	private static void send(WhiteActionPayload payload) {
		if (ClientPlayNetworking.canSend(WhiteActionPayload.TYPE)) {
			ClientPlayNetworking.send(payload);
		}
	}
}
