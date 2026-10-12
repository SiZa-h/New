package com.sizah.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * "View motion" tracking: the trigger gate for behaviours 1 and 2.
 *
 * <p>Requirement: **it only triggers when the view's horizontal or vertical motion is less than 1 degree,
 * and does not trigger above 1** — that is, "it cannot be released while the view is turning; only when it stops
 * moving can it be released".
 *
 * <p>View motion means the **absolute change in look direction within a single tick** (in degrees):
 * horizontal = `|Δyaw|` (with {@link Mth#wrapDegrees} handling the ±180 wrap-around), vertical = `|Δpitch|`.
 * **Both** directions must be below the threshold for it to count as "still".
 *
 * <p>This class supports both sides at once:
 *
 * <ul>
 *     <li>Server side: sampled once per server tick ({@link #sample(ServerPlayer)}, called from the mod entry point's tick callback);</li>
 *     <li>Client side: {@code GestureInput} feeds its own look direction in every tick ({@link #sampleLocal}).</li>
 * </ul>
 *
 * <p>They live in the same class so that shared code ({@code ModNetworking}) **does not have to reference any client class**.
 */
public final class ViewMotion {
	/** The threshold (degrees) for "the view is not turning": the per-tick change in both directions must be below it. */
	public static final float STILL_LIMIT_DEGREES = 1.0F;

	private record Sample(float yaw, float pitch, boolean still) {
	}

	/** Server side: player UUID → most recent sample. */
	private static final Map<UUID, Sample> SAMPLES = new HashMap<>();

	/** Client side: the local player's most recent sample (a client only ever has one player). */
	private static @Nullable Sample localSample;

	private ViewMotion() {
	}

	/** Sampled once per server tick; the first sample counts as "still". */
	public static void sample(ServerPlayer player) {
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		Sample previous = SAMPLES.get(player.getUUID());
		boolean still = previous == null || isStill(previous, yaw, pitch);

		SAMPLES.put(player.getUUID(), new Sample(yaw, pitch, still));
	}

	/** Server side: whether this player's view motion **this tick** is small enough. */
	public static boolean isStill(ServerPlayer player) {
		Sample sample = SAMPLES.get(player.getUUID());
		return sample == null || sample.still();
	}

	/** Client side: called every tick by {@code GestureInput} to update the local sample. */
	public static void sampleLocal(float yaw, float pitch) {
		Sample previous = localSample;
		boolean still = previous == null || isStill(previous, yaw, pitch);

		localSample = new Sample(yaw, pitch, still);
	}

	/** Client side: whether the local player's view motion **this tick** is small enough. */
	public static boolean isLocalStill() {
		Sample sample = localSample;
		return sample == null || sample.still();
	}

	/** Clear the record when the player quits. */
	public static void clear(UUID playerId) {
		SAMPLES.remove(playerId);
	}

	/** The change in both directions must be below the threshold for this to count as still. */
	private static boolean isStill(Sample previous, float yaw, float pitch) {
		float deltaYaw = Math.abs(Mth.wrapDegrees(yaw - previous.yaw()));
		float deltaPitch = Math.abs(pitch - previous.pitch());
		return deltaYaw < STILL_LIMIT_DEGREES && deltaPitch < STILL_LIMIT_DEGREES;
	}
}
