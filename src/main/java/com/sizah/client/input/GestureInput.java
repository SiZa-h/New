package com.sizah.client.input;

import com.sizah.common.network.AxisStrikePayload;
import com.sizah.common.network.ModNetworking;
import com.sizah.common.network.PillarPayload;
import com.sizah.content.weapon.PillarKind;
import com.sizah.content.weapon.PillarWeaponItem;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * The single decision point for the black block star's press triggers.
 *
 * <p><b>Two click abilities (double click)</b>:
 *
 * <ul>
 *     <li>Double left click → {@link PillarPayload#SHORT} (behaviour 1, left-click short pillar);</li>
 *     <li>Double right click → {@link PillarPayload#LONG} (behaviour 2, right-click long pillar).</li>
 * </ul>
 *
 * <p><b>Four gestures (single click + swipe)</b> — the button decides which axis is watched:
 *
 * <table>
 *     <caption>The four gestures</caption>
 *     <tr><th>Gesture</th><th>Action</th></tr>
 *     <tr><td>Click + swipe <b>left</b></td><td>Spawn a clash pillar at the <b>right</b> end of the horizontal axis → knock enemies to the <b>left</b> (behaviour 3)</td></tr>
 *     <tr><td>Click + swipe <b>right</b></td><td>Spawn a clash pillar at the <b>left</b> end of the horizontal axis → knock them to the <b>right</b> (behaviour 4)</td></tr>
 *     <tr><td>Click + swipe <b>up</b></td><td>Spawn a clash pillar at the <b>bottom</b> end of the vertical axis → launch enemies <b>into the air</b> (behaviour 5)</td></tr>
 *     <tr><td>Click + swipe <b>down</b></td><td>Spawn a clash pillar at the <b>top</b> end of the vertical axis → <b>slam</b> enemies down (behaviour 6)</td></tr>
 * </table>
 *
 * <p>Because a single click is the opener of a gesture <i>and</i> the first click of a double click, the click
 * itself stays silent: it only opens a {@link #GESTURE_WINDOW_TICKS}-tick window. Inside that window
 *
 * <ol>
 *     <li>a swipe past its threshold → the <b>gesture</b> is sent and the click is consumed, so no pillar fires;</li>
 *     <li>a second click of the same button inside {@link #DOUBLE_CLICK_TICKS} → the click was a <b>double
 *         click</b>, so the pillar is requested and the window closes;</li>
 *     <li>neither → the click simply expires and <b>nothing happens</b> (a lone click is not an ability).</li>
 * </ol>
 *
 * <p>Two implementation notes:
 *
 * <ol>
 *     <li><b>Neither button is taken over by vanilla</b>: left click is intercepted by
 *         {@code ClientPreAttackCallback} and right click by the use events, so nothing fires twice — every
 *         packet is sent from this class and nowhere else.</li>
 *     <li><b>The swipe is judged from view rotation</b>: instead of writing a Mixin to read the private
 *         displacement of {@code MouseHandler}, we accumulate the change in the player's yaw / pitch — a mouse
 *         swipe already shows up in game as view rotation anyway — and the yaw delta is run through
 *         {@link Mth#wrapDegrees} to handle the ±180 wrap-around.</li>
 * </ol>
 */
public final class GestureInput {
	/** Double-click window (ticks, counted from the first click): two clicks only count as a double click if the gap between them does not exceed it. */
	public static final int DOUBLE_CLICK_TICKS = 4;

	/**
	 * Gesture window (ticks, counted from the click): the swipe has to start within this time to claim the click.
	 * Keep it short — it is also the delay before a double click's pillar can fire, because the first click
	 * cannot know yet which of the two it is.
	 */
	public static final int GESTURE_WINDOW_TICKS = 4;

	/** Yaw change (in degrees) needed to count as a "left/right swipe". */
	public static final float HORIZONTAL_SWIPE_DEGREES = 5.0F;

	/** Pitch change (in degrees) needed to count as an "up/down swipe". */
	public static final float VERTICAL_SWIPE_DEGREES = 3.0F;

	private static int tick;
	private static int lastLeftClick = -1000;
	private static int lastRightClick = -1000;
	private static int windowEnd = -1;

	/** Whether the click on standby watches the **horizontal** (yaw, right button) or the **vertical** (pitch, left button) swipe. */
	private static boolean horizontalArmed;

	/** The pillar waiting behind the click ({@code null} = no click on standby). */
	private static PillarPayload pressed;

	private static boolean rightWasDown;
	private static boolean haveRotation;
	private static float lastYaw;
	private static float lastPitch;
	private static float yawSum;
	private static float pitchSum;

	private GestureInput() {
	}

	/**
	 * Called once per client tick, in this order: a lone click whose window ran out is dropped, the right-click
	 * press edge is polled (left clicks arrive through {@link #onLeftClick()}), the view rotation is accumulated,
	 * and whichever of "gesture" or "double click pillar" the click turned out to be is resolved and sent.
	 */
	public static void tick(Minecraft client) {
		tick++;

		Player player = client.player;

		if (player == null) {
			reset();
			return;
		}

		if (!usable(player)) {
			// Opening a GUI, switching items or putting the weapon away: drop the standby state, so that a
			// pending pillar can never fire after the interruption.
			disarm();
			rightWasDown = false;
			haveRotation = false;
			return;
		}

		// A click that ran out of time is dropped here, before the polling below: that way a click landing
		// exactly on the deadline counts as the next click instead of firing the old one.
		if (pressed != null && windowEnd > 0 && tick > windowEnd) {
			disarm();
		}

		boolean rightDown = client.options.keyUse.isDown();

		if (rightDown && !rightWasDown) {
			registerClick(true);      // arms its own window, or fires the pillar when it is the second click
		}

		rightWasDown = rightDown;

		// The tick a click arrives on is accumulated too, otherwise its own movement would be lost
		yawSum += rotationDeltaYaw(player);
		pitchSum += rotationDeltaPitch(player);

		if (pressed == null) {
			return;
		}

		int endpoint = horizontalArmed ? horizontalSwipe() : verticalSwipe();

		if (endpoint >= 0) {
			// The gesture claims the click: the deferred pillar is dropped, never fired
			disarm();
			send(new AxisStrikePayload(endpoint));
		}
	}

	/**
	 * Left-click "press": called from {@code ClientPreAttackCallback} (client only, once per real press).
	 *
	 * <p>Nothing is fired here — the click only opens the gesture window (see {@link #registerClick}).
	 *
	 * @return whether vanilla attack handling should be intercepted (true while a click is on standby)
	 */
	public static boolean onLeftClick() {
		return registerClick(false);
	}

	/**
	 * One press of either button.
	 *
	 * <p>Two clicks of the **same** button inside {@link #DOUBLE_CLICK_TICKS} make a double click → that pillar
	 * is requested right away and the gesture window is closed. A click that does not follow another one just
	 * goes on standby, waiting to see whether it is a gesture instead.
	 *
	 * @return whether the click stays on standby (i.e. whether vanilla should be intercepted)
	 */
	private static boolean registerClick(boolean right) {
		int last = right ? lastRightClick : lastLeftClick;

		if (tick - last <= DOUBLE_CLICK_TICKS) {
			disarm();                                   // a double click can never be a gesture
			send(right ? PillarPayload.LONG : PillarPayload.SHORT);
			swingAndAnimateClientCooldown(right);

			if (right) {
				lastRightClick = -1000;                 // a triple click must not fire a second pillar
			} else {
				lastLeftClick = -1000;
			}

			return true;
		}

		if (right) {
			lastRightClick = tick;
		} else {
			lastLeftClick = tick;
		}

		arm(right);
		return true;
	}

	/** Put the click on standby: a swipe counts if it starts within {@link #GESTURE_WINDOW_TICKS} ticks. */
	private static void arm(boolean right) {
		horizontalArmed = right;                        // right button → horizontal swipe, left button → vertical swipe
		pressed = right ? PillarPayload.LONG : PillarPayload.SHORT;
		windowEnd = tick + GESTURE_WINDOW_TICKS;
		yawSum = 0.0F;
		pitchSum = 0.0F;
	}

	private static void disarm() {
		windowEnd = -1;
		pressed = null;
		yawSum = 0.0F;
		pitchSum = 0.0F;
	}

	/** The horizontal swipe threshold: turning right = yaw increases (yaw 0 faces south, 90 faces west), and a swipe wins only if it clearly beats the other axis. */
	private static int horizontalSwipe() {
		if (Math.abs(yawSum) >= HORIZONTAL_SWIPE_DEGREES && Math.abs(yawSum) > Math.abs(pitchSum)) {
			// Swipe right (yaw growing) → grow from the axis's **left** end, which knocks enemies to the right;
			// swipe left → the **right** end, which knocks them to the left. The clash pillar's facing is its
			// knockback direction, so the two are the same setting.
			return yawSum > 0.0F ? AxisStrikePayload.HORIZONTAL_LEFT : AxisStrikePayload.HORIZONTAL_RIGHT;
		}

		return -1;
	}

	/** The vertical swipe threshold: swiping the mouse up = looking up = pitch decreases → spawn at the bottom end and launch enemies; a swipe down is the opposite. */
	private static int verticalSwipe() {
		if (Math.abs(pitchSum) >= VERTICAL_SWIPE_DEGREES && Math.abs(pitchSum) > Math.abs(yawSum)) {
			return pitchSum < 0.0F ? AxisStrikePayload.VERTICAL_BOTTOM : AxisStrikePayload.VERTICAL_TOP;
		}

		return -1;
	}

	/**
	 * The swing and the purely visual cooldown animation of a double click.
	 *
	 * <p>The real gates are the independent server-side cooldowns ({@code WeaponCooldowns}); the swing has to be
	 * replayed here because both the vanilla attack swing and the right-click use packet are intercepted.
	 */
	private static void swingAndAnimateClientCooldown(boolean right) {
		Player player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.getCooldowns().addCooldown(
				player.getMainHandItem(),
				right ? ModNetworking.LONG_FIRE_COOLDOWN_TICKS : ModNetworking.FIRE_COOLDOWN_TICKS);
	}

	/** The main hand must hold the black block star (the obsidian block has no right-click, gesture or sneak ability). */
	private static boolean usable(Player player) {
		return Minecraft.getInstance().gui.screen() == null
				&& player.getMainHandItem().getItem() instanceof PillarWeaponItem weapon
				&& weapon.kind() == PillarKind.BLACK_BLOCK;
	}

	/** Per-tick yaw delta (degrees), with the ±180 wrap-around handled. */
	private static float rotationDeltaYaw(Player player) {
		float yaw = player.getYRot();
		float delta = haveRotation ? Mth.wrapDegrees(yaw - lastYaw) : 0.0F;
		lastYaw = yaw;
		haveRotation = true;
		return delta;
	}

	/** Per-tick pitch delta (degrees); pitch stays within -90…90, so it never wraps. */
	private static float rotationDeltaPitch(Player player) {
		float pitch = player.getXRot();
		float delta = haveRotation ? pitch - lastPitch : 0.0F;
		lastPitch = pitch;
		return delta;
	}

	private static void send(AxisStrikePayload payload) {
		if (ClientPlayNetworking.canSend(AxisStrikePayload.TYPE)) {
			ClientPlayNetworking.send(payload);
		}
	}

	private static void send(PillarPayload payload) {
		if (ClientPlayNetworking.canSend(PillarPayload.TYPE)) {
			ClientPlayNetworking.send(payload);
		}
	}

	private static void reset() {
		disarm();
		haveRotation = false;
		rightWasDown = false;
	}
}
