package com.sizah.client.input;

import com.sizah.common.network.WhiteActionPayload;
import com.sizah.content.item.ModItems;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The white block star's inputs.
 *
 * <table>
 *     <caption>Inputs</caption>
 *     <tr><th>Input</th><th>What happens</th></tr>
 *     <tr><td>Hold left</td><td>A stream of white block pillars from a ring **behind** the player, aimed at the crosshair</td></tr>
 *     <tr><td>Double click right</td><td>Toggle the grip: the creature under the crosshair (≤30 blocks) is carried on the view line, or let go if it is already held</td></tr>
 *     <tr><td>Sneak + right click</td><td>The white block infection (single press, no double click needed)</td></tr>
 * </table>
 *
 * <p>Two different mechanisms are needed, so both live here:
 *
 * <ul>
 *     <li>the **left button is a hold** ability, so it is sampled every client tick and only the two edges are sent
 *         ({@link WhiteActionPayload#FIRE_START} / {@link WhiteActionPayload#FIRE_STOP});</li>
 *     <li>the **right button is a double click** (and a single press while sneaking), so it is measured with a
 *         {@link #DOUBLE_CLICK_TICKS}-tick window: the second press inside it toggles the grip, a press while
 *         sneaking sends the infection immediately.</li>
 * </ul>
 *
 * <p>Sneaking disables the hold-fire (that button belongs to nothing while sneaking), and the server re-checks
 * every one of these rules — see {@code ModNetworking.handleWhiteAction}.
 */
public final class WhiteStarInput {
	/** Double-click window (ticks) for the right button. */
	public static final int DOUBLE_CLICK_TICKS = 4;

	private static int tick;
	private static int lastRightPress = -1000;
	private static boolean leftWasDown;
	private static boolean rightWasDown;

	private WhiteStarInput() {
	}

	/** Client tick: sample the left button, detect right-button presses and double clicks, and send what changed. */
	public static void tick(Minecraft client) {
		tick++;

		Player player = client.player;

		if (player == null || client.gui.screen() != null) {
			resetHolds();
			lastRightPress = -1000;
			rightWasDown = false;
			return;
		}

		boolean holdingStar = player.getMainHandItem().getItem() == ModItems.WHITE_BLOCK_STAR;
		boolean sneaking = player.isShiftKeyDown();

		// ---- left button: a hold ability, edges only ------------------------------------------------
		boolean leftDown = holdingStar && !sneaking && client.options.keyAttack.isDown();

		if (leftDown != leftWasDown) {
			send(leftDown ? WhiteActionPayload.FIRE_START : WhiteActionPayload.FIRE_STOP);
			leftWasDown = leftDown;
		}

		// ---- right button: double click (grip toggle) / single press while sneaking (infection) -----
		boolean rightDown = holdingStar && client.options.keyUse.isDown();

		if (rightDown && !rightWasDown) {
			if (sneaking) {
				// sneak + right click = the infection, right away: there is no double click to wait for.
				send(WhiteActionPayload.CORRUPT);
				lastRightPress = -1000;
			} else if (tick - lastRightPress <= DOUBLE_CLICK_TICKS) {
				// second press inside the window: toggle the grip, and do not arm another double click
				send(WhiteActionPayload.PULL_TOGGLE);
				lastRightPress = -1000;
			} else {
				lastRightPress = tick;      // first press: wait to see whether a second one follows
			}
		}

		rightWasDown = rightDown;
	}

	/** Called when the weapon is put away or the world is left: make sure nothing stays "held" on the server. */
	public static void releaseAll() {
		resetHolds();
		lastRightPress = -1000;
		rightWasDown = false;
	}

	private static void resetHolds() {
		if (leftWasDown) {
			send(WhiteActionPayload.FIRE_STOP);
			leftWasDown = false;
		}
	}

	private static void send(WhiteActionPayload payload) {
		if (ClientPlayNetworking.canSend(WhiteActionPayload.TYPE)) {
			ClientPlayNetworking.send(payload);
		}
	}
}
