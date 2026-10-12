package com.sizah.client;

import com.sizah.item.PillarKind;
import com.sizah.item.PillarWeaponItem;
import com.sizah.network.AxisStrikePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Mouse gesture detection: a **double click** (left or right button) followed by a **mouse swipe**.
 *
 * <table>
 *     <caption>The four gestures</caption>
 *     <tr><th>Gesture</th><th>Action</th></tr>
 *     <tr><td>Double right click + swipe left</td><td>Spawn a clash pillar at the <b>left</b> end of the horizontal axis → knock enemies to the <b>right</b></td></tr>
 *     <tr><td>Double right click + swipe right</td><td>Spawn a clash pillar at the <b>right</b> end of the horizontal axis → knock them to the <b>left</b></td></tr>
 *     <tr><td>Double left click + swipe up</td><td>Spawn a clash pillar at the <b>bottom</b> end of the vertical axis → launch enemies <b>into the air</b></td></tr>
 *     <tr><td>Double left click + swipe down</td><td>Spawn a clash pillar at the <b>top</b> end of the vertical axis → <b>slam</b> enemies down</td></tr>
 * </table>
 *
 * <p>Two implementation notes:
 *
 * <ol>
 *     <li><b>A double click does not take over the button itself</b>: the first click fires the
 *         original skill as usual (left click short pillar / right click long pillar), and only a
 *         second click landing inside the {@link #DOUBLE_CLICK_TICKS} window goes "on standby",
 *         waiting for the swipe.</li>
 *     <li><b>The swipe is judged from view rotation</b>: instead of writing a Mixin to read the
 *         private displacement of {@code MouseHandler}, we accumulate the change in the player's
 *         yaw / pitch — a mouse swipe already shows up in game as view rotation anyway — and the yaw
 *         delta is run through {@link Mth#wrapDegrees} to handle the ±180 wrap-around.</li>
 * </ol>
 */
public final class GestureInput {
	/** Double click window (ticks): two clicks only count as a double click if the gap between them does not exceed it. */
	public static final int DOUBLE_CLICK_TICKS = 4;

	/** Standby duration after a double click (ticks): a swipe only takes effect within this time. */
	public static final int SWIPE_WINDOW_TICKS = 2;

	/** Yaw change (in degrees) needed to count as a "left/right swipe". */
	public static final float HORIZONTAL_SWIPE_DEGREES = 5.0F;

	/** Pitch change (in degrees) needed to count as an "up/down swipe". */
	public static final float VERTICAL_SWIPE_DEGREES = 3.0F;

	private static int tick;
	private static int lastRightClick = -1000;
	private static int lastLeftClick = -1000;
	private static int windowEnd = -1;

	/** Whether what is on standby is a "horizontal swipe" (the right-click group) or a "vertical swipe" (the left-click group). */
	private static boolean horizontalArmed;

	private static boolean rightWasDown;
	private static boolean haveRotation;
	private static float lastYaw;
	private static float lastPitch;
	private static float yawSum;
	private static float pitchSum;

	private GestureInput() {
	}

	/**
	 * Called once per client tick: polls the right-click press edge (left clicks are reported by
	 * {@link #onLeftClick()}), accumulates the view rotation, and sends the gesture packet once it
	 * has enough.
	 */
	public static void tick(Minecraft client) {
		tick++;

		Player player = client.player;

		if (player == null) {
			reset();
			return;
		}

		// View rotation delta (computed first, otherwise the tick we go on standby would miss one movement)
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		float deltaYaw = haveRotation ? Mth.wrapDegrees(yaw - lastYaw) : 0.0F;
		float deltaPitch = haveRotation ? pitch - lastPitch : 0.0F;
		lastYaw = yaw;
		lastPitch = pitch;
		haveRotation = true;

		boolean usable = client.gui.screen() == null
				&& player.getMainHandItem().getItem() instanceof PillarWeaponItem weapon
				&& weapon.kind() == PillarKind.BLACK_BLOCK;

		boolean rightDown = usable && client.options.keyUse.isDown();

		if (rightDown && !rightWasDown) {
			registerRightClick();
		}

		rightWasDown = rightDown;

		if (!usable || tick > windowEnd) {
			disarm();
			return;
		}

		yawSum += deltaYaw;
		pitchSum += deltaPitch;

		if (horizontalArmed) {
			// Turning right = yaw increases (yaw 0 faces south, 90 faces west); turning left = yaw decreases
			if (Math.abs(yawSum) >= HORIZONTAL_SWIPE_DEGREES && Math.abs(yawSum) > Math.abs(pitchSum)) {
				send(yawSum > 0.0F ? AxisStrikePayload.HORIZONTAL_RIGHT : AxisStrikePayload.HORIZONTAL_LEFT);
				disarm();
			}

			return;
		}

		// Swiping the mouse up = looking up = pitch decreases → spawn at the bottom end of the vertical axis and launch enemies into the air; swiping down is the opposite
		if (Math.abs(pitchSum) >= VERTICAL_SWIPE_DEGREES && Math.abs(pitchSum) > Math.abs(yawSum)) {
			send(pitchSum < 0.0F ? AxisStrikePayload.VERTICAL_BOTTOM : AxisStrikePayload.VERTICAL_TOP);
			disarm();
		}
	}

	/**
	 * Left-click "press": reported by {@code ClientPreAttackCallback}.
	 *
	 * <p>The left button is still needed to fire the short pillar, so this only keeps score and
	 * intercepts nothing.
	 */
	public static void onLeftClick() {
		if (tick - lastLeftClick <= DOUBLE_CLICK_TICKS) {
			arm(false);
			lastLeftClick = -1000;      // a triple click must not keep triggering
		} else {
			lastLeftClick = tick;
		}
	}

	/** Right-click "press": obtained by this class's tick polling the use key (to avoid referencing a client class from a shared class). */
	private static void registerRightClick() {
		if (tick - lastRightClick <= DOUBLE_CLICK_TICKS) {
			arm(true);
			lastRightClick = -1000;
		} else {
			lastRightClick = tick;
		}
	}

	/** Go on standby: a swipe only counts within the next {@link #SWIPE_WINDOW_TICKS} ticks. */
	private static void arm(boolean horizontal) {
		horizontalArmed = horizontal;
		windowEnd = tick + SWIPE_WINDOW_TICKS;
		yawSum = 0.0F;
		pitchSum = 0.0F;
	}

	private static void disarm() {
		windowEnd = -1;
		yawSum = 0.0F;
		pitchSum = 0.0F;
	}

	private static void send(int endpoint) {
		if (ClientPlayNetworking.canSend(AxisStrikePayload.TYPE)) {
			ClientPlayNetworking.send(new AxisStrikePayload(endpoint));
		}
	}

	private static void reset() {
		disarm();
		haveRotation = false;
		rightWasDown = false;
	}
}
