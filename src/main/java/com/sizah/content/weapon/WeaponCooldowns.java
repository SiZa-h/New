package com.sizah.content.weapon;

import com.sizah.client.NewModClient;
import com.sizah.common.network.ModNetworking;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * **Independent cooldowns** for the weapon skills.
 *
 * <p>The vanilla {@code ItemCooldowns} records by "item (or its cooldown group)" — a single item can
 * only ever have one cooldown. The Black Block Star has eight behaviours, so if the vanilla cooldown
 * were used directly, firing one of them would put all the others on cooldown. Here the bookkeeping
 * is kept separately along the three dimensions **player + item + skill slot**, so that:
 *
 * <ul>
 *     <li>The eight behaviours (left-click short pillar, right-click long pillar, the four gesture
 *         clash pillars, sneak right-click dash, sneak left-click infection) are **independent of one
 *         another**;</li>
 *     <li>The Black Block Star and the Obsidian Cube **each count on their own** (moving the same
 *         weapon to the other hand or swapping the item never crosses the two).</li>
 * </ul>
 *
 * <p>The real gate is on the server side only; when a key is pressed the client plays a purely visual
 * cooldown animation of its own (see {@code NewModClient} and the client branch of
 * {@code ModNetworking.fireLongPillar}).
 */
public final class WeaponCooldowns {
	/**
	 * Skill slots: **every behaviour gets a slot of its own**, and one never affects another.
	 *
	 * <ol>
	 *     <li>{@link #LEFT}: left-click short pillar;</li>
	 *     <li>{@link #RIGHT}: right-click long pillar;</li>
	 *     <li>{@link #HORIZONTAL_LEFT}: right single click + swipe right (clash pillar at the left end of the horizontal axis);</li>
	 *     <li>{@link #HORIZONTAL_RIGHT}: right single click + swipe left (clash pillar at the right end of the horizontal axis);</li>
	 *     <li>{@link #VERTICAL_BOTTOM}: left single click + swipe up (clash pillar at the bottom end of the vertical axis);</li>
	 *     <li>{@link #VERTICAL_TOP}: left single click + swipe down (clash pillar at the top end of the vertical axis);</li>
	 *     <li>{@link #SNEAK_RIGHT}: **sneak + right click** (spawns a dash pillar behind you and shoves the player out);</li>
	 *     <li>{@link #SNEAK_LEFT}: **sneak + left click** (infects the stretch of blocks in front into black blocks).</li>
	 * </ol>
	 */
	public enum Slot {
		LEFT,
		RIGHT,
		HORIZONTAL_LEFT,
		HORIZONTAL_RIGHT,
		VERTICAL_BOTTOM,
		VERTICAL_TOP,
		SNEAK_RIGHT,
		SNEAK_LEFT
	}

	/** Player UUID → (item + slot → the game tick at which it can be used again). */
	private static final Map<UUID, Map<String, Long>> READY_AT = new HashMap<>();

	private WeaponCooldowns() {
	}

	/** Whether this skill of this weapon is already ready for that player. */
	public static boolean isReady(ServerPlayer player, ItemStack stack, Slot slot) {
		Map<String, Long> slots = READY_AT.get(player.getUUID());

		if (slots == null) {
			return true;
		}

		Long readyAt = slots.get(key(stack, slot));
		return readyAt == null || readyAt <= player.level().getGameTime();
	}

	/** Record one use: from now on this skill is unavailable for {@code ticks} ticks. */
	public static void apply(ServerPlayer player, ItemStack stack, Slot slot, int ticks) {
		READY_AT.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
				.put(key(stack, slot), player.level().getGameTime() + ticks);
	}

	/** Clear the record when a player leaves, so a long-running server does not pile them up. */
	public static void clear(UUID playerId) {
		READY_AT.remove(playerId);
	}

	private static String key(ItemStack stack, Slot slot) {
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return itemId + "/" + slot.name().toLowerCase(Locale.ROOT);
	}
}
