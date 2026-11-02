package com.sizah.client.input;

import com.sizah.common.network.BlackSneakPayload;
import com.sizah.content.weapon.PillarKind;
import com.sizah.content.weapon.PillarWeaponItem;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The black block star's **sneak** inputs, which share the right button and are told apart by timing.
 *
 * <table>
 *     <caption>Inputs (all while sneaking and holding the black block star)</caption>
 *     <tr><th>Input</th><th>Action</th></tr>
 *     <tr><td>Sneak + single right click</td><td>Black block infection ({@link BlackSneakPayload#INFECT})</td></tr>
 *     <tr><td>Sneak + double right click</td><td>Dash pillar ({@link BlackSneakPayload#DASH})</td></tr>
 * </table>
 *
 * <p>Why the wait: a single click has to be told apart from the first half of a double click, so the infection is
 * only sent once the {@link #DOUBLE_CLICK_TICKS}-tick window has passed without a second press. The dash is sent
 * immediately on the second press, so it never feels delayed.
 *
 * <p>While sneaking, {@link GestureInput} stays out of the way (its weapon check requires not sneaking), so the
 * long pillar and the gestures cannot also trigger. The server re-checks the weapon and the sneak state.
 */
public final class BlackStarSneakInput {
	/** Double-click window (ticks): two presses within it are a dash, otherwise the first one infects. */
	public static final int DOUBLE_CLICK_TICKS = 4;

	private static int tick;
	private static int lastPress = -1000;
	private static int pendingInfectUntil = -1;
	private static boolean rightWasDown;

	private BlackStarSneakInput() {
	}

	/** Client tick: poll the right button while sneaking with the black block star, and resolve the two actions. */
	public static void tick(Minecraft client) {
		tick++;

		Player player = client.player;

		if (!active(client, player)) {
			reset();
			return;
		}

		boolean down = client.options.keyUse.isDown();

		if (down && !rightWasDown) {
			if (tick - lastPress <= DOUBLE_CLICK_TICKS) {
				// second press inside the window: this is the dash, and the infection is cancelled
				lastPress = -1000;
				pendingInfectUntil = -1;
				send(BlackSneakPayload.DASH);
			} else {
				// first press: wait out the window to see whether a dash follows
				lastPress = tick;
				pendingInfectUntil = tick + DOUBLE_CLICK_TICKS;
			}
		}

		rightWasDown = down;

		// the window passed with no second press: a plain sneak + right click, so infect
		if (pendingInfectUntil >= 0 && tick > pendingInfectUntil) {
			pendingInfectUntil = -1;
			send(BlackSneakPayload.INFECT);
		}
	}

	/** Called on disconnect: drop any half-finished double click. */
	public static void releaseAll() {
		reset();
	}

	private static boolean active(Minecraft client, Player player) {
		return player != null
				&& client.gui.screen() == null
				&& player.isShiftKeyDown()
				&& player.getMainHandItem().getItem() instanceof PillarWeaponItem weapon
				&& weapon.kind() == PillarKind.BLACK_BLOCK;
	}

	private static void reset() {
		rightWasDown = false;
		lastPress = -1000;
		pendingInfectUntil = -1;
	}

	private static void send(BlackSneakPayload payload) {
		if (ClientPlayNetworking.canSend(BlackSneakPayload.TYPE)) {
			ClientPlayNetworking.send(payload);
		}
	}
}
