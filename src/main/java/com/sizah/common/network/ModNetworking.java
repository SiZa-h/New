package com.sizah.common.network;

import com.sizah.common.config.Settings;
import com.sizah.content.effect.Corruption;
import com.sizah.content.item.ModItems;
import com.sizah.content.weapon.PinTargets;
import com.sizah.content.weapon.WhiteStarFire;
import com.sizah.content.weapon.entity.BlackBlockPillarEntity;
import com.sizah.content.weapon.entity.BlankAxisEntity;
import com.sizah.content.weapon.entity.ModEntities;
import com.sizah.content.weapon.PillarKind;
import com.sizah.content.weapon.PillarWeaponItem;
import com.sizah.content.weapon.WeaponCooldowns;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where every ability request comes from, and the server-authoritative implementation of all of them.
 *
 * <ul>
 *     <li><b>Left click</b> (both pillar weapons have it): the client only asks for the short pillar on the
 *         **second click of a double click** ({@link PillarPayload}), and the server spawns a regular pillar in
 *         front of the player;</li>
 *     <li><b>Right click</b> (black block star only): the client asks for the long pillar the same way
 *         ({@link PillarPayload}); the use events below are left with nothing but the **sneak dash**, because
 *         they fire on both sides and would otherwise fire the pillar on a single click;</li>
 *     <li><b>Mouse gestures</b> ({@link AxisStrikePayload}) and <b>sneak left click</b> ({@link CorruptPayload})
 *         each have a payload of their own.</li>
 * </ul>
 *
 * <p><b>Both pillars share one central axis</b>: the **player's view direction axis** (including pitch). The
 * short pillar grows forward from the first point on that axis (1 block in front of the player's eyes); the
 * long pillar spawns one body length further along the same axis and sweeps back, so its front end stops
 * exactly on the short pillar's origin. The two pillars' models are therefore two segments of one straight
 * line and overlap naturally.
 */
public final class ModNetworking {
	/** Cooldown (in ticks) for behavior #1 (left-click short pillar): **3 seconds**. */
	public static final int FIRE_COOLDOWN_TICKS = 60;

	/** Cooldown (in ticks) for the right-click ability: **tracked independently** of the other five, with no effect on them. */
	public static final int LONG_FIRE_COOLDOWN_TICKS = 20;

	/** Cooldown (in ticks) for the four gesture clash pillars: one slot per gesture, likewise independent of each other. */
	public static final int GESTURE_COOLDOWN_TICKS = 10;

	/** Cooldown (in ticks) for the sneak right-click "dash". */
	public static final int SNEAK_COOLDOWN_TICKS = 20;

	/**
	 * Behaviours 1 and 2 are **double click** abilities and no longer look at the view at all: a single click
	 * only opens the gesture window (see {@code GestureInput.GESTURE_WINDOW_TICKS}) so that a swipe can claim it,
	 * and the pillar is requested over {@link PillarPayload} the moment the second click of a double click lands.
	 * The old "the view must stay still" gate (1 degree per tick) is gone.
	 */

	/** Distance (in blocks) **behind** the player at which the dash pillar spawns. */
	public static final double DASH_BACK_OFFSET = 3.0D;

	/** Radius (in blocks) around the player cleared before dashing. */
	public static final int DASH_CLEAR_RADIUS = 3;

	/** Ray length (in blocks) for the sneak left-click "infection"; the spread radius and speed live in {@link Corruption}. */
	public static final double CORRUPT_REACH = 10.0D;

	/** How far (in blocks) the short black block pillar's origin sits ahead of the player's eyes. */
	public static final double SHORT_PILLAR_OFFSET = 1.0D;

	/** Search radius (in blocks) for finding a "blank axis": the axis sits at the impact point, which may be dozens of blocks away from the player. */
	private static final double AXIS_SEARCH_RADIUS = 80.0D;

	private ModNetworking() {
	}

	public static void register() {
		// Payload types must be registered on both the sending and the receiving side, and before the handlers are registered.
		PayloadTypeRegistry.serverboundPlay().register(PillarPayload.TYPE, PillarPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AxisStrikePayload.TYPE, AxisStrikePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SettingsPayload.TYPE, SettingsPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CorruptPayload.TYPE, CorruptPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WhiteActionPayload.TYPE, WhiteActionPayload.STREAM_CODEC);

		// Behaviours 1 and 2: both are **double click** abilities now, and the client only asks for them once the
		// click is known not to be a gesture (see PillarPayload and GestureInput).
		ServerPlayNetworking.registerGlobalReceiver(PillarPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			ItemStack stack = player.getMainHandItem();

			if (payload.kind() == PillarPayload.Kind.SHORT) {
				if (firePillar(player)) {
					WeaponCooldowns.apply(player, stack, WeaponCooldowns.Slot.LEFT, FIRE_COOLDOWN_TICKS);
				}
			} else if (fireLongPillar(player)) {
				WeaponCooldowns.apply(player, stack, WeaponCooldowns.Slot.RIGHT, LONG_FIRE_COOLDOWN_TICKS);
			}
		});

		// Mouse gesture: spawn a clash pillar at one end of the blank axis
		ServerPlayNetworking.registerGlobalReceiver(AxisStrikePayload.TYPE, (payload, context) ->
				strikeAxis(context.player(), payload.endpoint()));

		// Settings screen: the two toggles land straight in the server's global settings
		ServerPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) ->
				Settings.apply(payload.breakBedrock(), payload.explosionBreaksBlocks()));

		// Sneak left click: find a block from the eyes along the view direction and infect the area around it into black blocks
		ServerPlayNetworking.registerGlobalReceiver(CorruptPayload.TYPE, (payload, context) ->
				corrupt(context.player()));

		// The white block star: hold left click to stream white block pillars, hold right click to carry the
		// creature under the crosshair, sneak + left click to infect with white blocks.
		ServerPlayNetworking.registerGlobalReceiver(WhiteActionPayload.TYPE, (payload, context) ->
				handleWhiteAction(context.player(), payload.action()));

		// Right click: on the server side this now only serves **sneak right-click (the dash)**.
		// The ordinary right-click long pillar is a double click ability, and a single right click belongs to the
		// horizontal gestures, so the client resolves which of the two it is and then asks for the pillar over
		// PillarPayload. These events fire on both sides, so letting them spawn the pillar would fire it on the
		// very first click, before the client could possibly know whether a gesture follows.
		UseItemCallback.EVENT.register((player, level, hand) -> handleUseEvent(player, level, hand));
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> handleUseEvent(player, level, hand));

		// On player disconnect: clear cooldowns, stop the white star's held abilities, and settle any infection
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			ServerPlayer player = listener.player;

			WeaponCooldowns.clear(player.getUUID());
			Corruption.onDisconnect(player);
			WhiteStarFire.stop(player);
			PinTargets.forget(player.getUUID());
		});
	}

	/**
	 * The white block star's hold abilities and its infection.
	 *
	 * <p>The weapon is re-checked here rather than trusted: the packets only say "the button went down" or "it came
	 * up", and everything else (what to fire, what to grab, what to paint) is decided from the server's own view of
	 * the player.
	 *
	 * <p><b>Sneak has priority over both held abilities.</b> While sneak is held, the left button is the white
	 * block infection and the right button does nothing, so a hostile client cannot fire pillars through the
	 * infection — and the infection itself is only accepted while the player really is sneaking.
	 */
	private static void handleWhiteAction(ServerPlayer player, WhiteActionPayload.Action action) {
		boolean holdingStar = player.getMainHandItem().getItem() == ModItems.WHITE_BLOCK_STAR;
		boolean sneaking = player.isShiftKeyDown();

		switch (action) {
			case FIRE_START -> {
				if (holdingStar && !sneaking) {
					WhiteStarFire.start(player);
				}
			}
			case FIRE_STOP -> WhiteStarFire.stop(player);
			case PULL_START -> {
				if (holdingStar && !sneaking) {
					PinTargets.grab(player);
				}
			}
			case PULL_STOP -> PinTargets.release(player);
			case CORRUPT -> {
				if (holdingStar && sneaking) {
					corruptTo(player, com.sizah.content.block.ModBlocks.WHITE_BLOCK);
				}
			}
		}
	}

	/**
	 * Whether behaviour 1 (left-click short pillar) may fire right now: the main hand must hold a pillar weapon
	 * (black block star / obsidian block) and its own cooldown slot must be ready.
	 */
	private static boolean canFireShortPillar(ServerPlayer player, ItemStack stack) {
		return stack.getItem() instanceof PillarWeaponItem
				&& WeaponCooldowns.isReady(player, stack, WeaponCooldowns.Slot.LEFT);
	}

	/**
	 * Behaviour 1: spawn a regular pillar in front of the player (left-click short pillar, also used by the
	 * obsidian block).
	 *
	 * <p>The request arrives over {@link PillarPayload} when the **second click** of a double click lands, so by
	 * that time the "was that a gesture?" question is already answered. There is **no view-motion gate** any
	 * more: the player may turn the view while firing.
	 *
	 * @return whether it actually fired
	 */
	private static boolean firePillar(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();

		if (!canFireShortPillar(player, stack)) {
			return false; // the main hand is not a pillar weapon, or this slot is still on cooldown
		}

		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}

		// Full view direction (including pitch): looking up or down both fire at an angle.
		Vec3 direction = player.getViewVector(1.0F).normalize();

		// Origin: the short black block pillar's start (slightly below the eyes, shifted 1 block along the view direction).
		Vec3 start = shortPillarOrigin(player, direction);

		BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(
				ModEntities.typeFor(((PillarWeaponItem) stack.getItem()).kind()), level);
		pillar.setOwner(player);
		applyRotation(pillar, direction);
		pillar.setPos(start.x, start.y, start.z);

		level.addFreshEntity(pillar);
		return true;
	}

	/**
	 * Whether behaviour 2 (right-click long pillar) may fire right now: the main hand must hold the **black block
	 * star** (the obsidian block has no right-click ability) and its own cooldown slot must be ready.
	 */
	private static boolean canFireLongPillar(ServerPlayer player, ItemStack stack) {
		return stack.getItem() instanceof PillarWeaponItem weapon
				&& weapon.kind() == PillarKind.BLACK_BLOCK
				&& WeaponCooldowns.isReady(player, stack, WeaponCooldowns.Slot.RIGHT);
	}

	/**
	 * Behaviour 2: spawn the "long black block pillar".
	 *
	 * <p>Fired from {@link PillarPayload} on the second click of a double click, so this method only has to answer
	 * "is this legal right now" — there is no view-motion gate any more.
	 *
	 * <p>The origin and the direction are **exactly the same** as the short pillar's: central axis = the player's
	 * view direction axis, origin = {@link #shortPillarOrigin}. The only difference is the type: the long pillar
	 * is spawned already at full length (60 blocks, transparent, inert), and the black blocks plus the
	 * functionality are then filled in from the tip toward the base — so its front end stops exactly on the short
	 * pillar's origin and the two models overlap.
	 *
	 * @return whether it actually fired
	 */
	private static boolean fireLongPillar(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();

		if (!canFireLongPillar(player, stack)) {
			return false; // not the black block star, or this slot is still on cooldown
		}

		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}

		Vec3 direction = player.getViewVector(1.0F).normalize();
		Vec3 start = shortPillarOrigin(player, direction);

		BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.LONG_BLACK_BLOCK_PILLAR, level);
		pillar.setOwner(player);
		applyRotation(pillar, direction);
		pillar.setPos(start.x, start.y, start.z);

		level.addFreshEntity(pillar);
		return true;
	}

	/**
	 * The right-click **use events** (item and block). Since the ordinary long pillar moved to
	 * {@link #fireLongPillar}, the only thing left for these events to do is the **sneak right-click dash**;
	 * every other right click is swallowed locally by the client (it returns {@code FAIL}, which also stops the
	 * use packet from being sent) and, if it turns out to be a double click, re-requested through
	 * {@link PillarPayload}.
	 *
	 * <p>Both events fire on **both sides**, so the sneak test and the dash itself are done on the server side
	 * only; the client-side pass just reports that the click was already handled.
	 *
	 * @return {@code PASS} to let vanilla (and every other mod) handle it as usual, {@code SUCCESS} to consume it
	 */
	private static InteractionResult handleUseEvent(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getMainHandItem();

		if (hand != InteractionHand.MAIN_HAND
				|| !(stack.getItem() instanceof PillarWeaponItem weapon)
				|| weapon.kind() != PillarKind.BLACK_BLOCK) {
			return InteractionResult.PASS;      // not the black block star: nothing here is ours
		}

		if (!player.isShiftKeyDown()) {
			// Ordinary right click: the client decides "gesture or double click" on its own and, if it turns out
			// to be the long pillar, asks for it over PillarPayload.
			return level.isClientSide() ? InteractionResult.FAIL : InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.FAIL;      // sneak + right click: the server side below does the work
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		return sneakDash(serverPlayer, stack);
	}

	/**
	 * Mouse gesture: spawn a 20-block clash pillar at the given endpoint of the "blank transparent axis".
	 *
	 * <p>The four gestures and their consequences (see {@link AxisStrikePayload}):
	 *
	 * <ul>
	 *     <li>Right click + swipe <b>right</b> → spawns at the horizontal axis's <b>left</b> end, grows right →
	 *         knocks enemies to the <b>right</b>;</li>
	 *     <li>Right click + swipe <b>left</b> → spawns at the horizontal axis's <b>right</b> end, grows left →
	 *         knocks them to the <b>left</b>;</li>
	 *     <li>Left click + swipe <b>up</b> → spawns at the vertical axis's <b>bottom</b> end, grows up →
	 *         <b>launches</b> enemies;</li>
	 *     <li>Left click + swipe <b>down</b> → spawns at the vertical axis's <b>top</b> end, grows down →
	 *         <b>slams</b> enemies down.</li>
	 * </ul>
	 *
	 * <p>Both the endpoint and the growth direction are taken from the **server-side blank axis entity**; the
	 * client can only specify "which end".
	 */
	private static void strikeAxis(ServerPlayer player, int endpoint) {
		if (endpoint < 0 || endpoint >= AxisStrikePayload.COUNT) {
			return;      // invalid value, discard it outright
		}

		ItemStack stack = player.getMainHandItem();

		if (!(stack.getItem() instanceof PillarWeaponItem weapon) || weapon.kind() != PillarKind.BLACK_BLOCK) {
			return;      // gestures belong to the black block star only
		}

		// The six behaviors each have their own independent cooldown: every gesture occupies its own slot, so none of them conflict
		WeaponCooldowns.Slot slot = switch (endpoint) {
			case AxisStrikePayload.HORIZONTAL_LEFT -> WeaponCooldowns.Slot.HORIZONTAL_LEFT;
			case AxisStrikePayload.HORIZONTAL_RIGHT -> WeaponCooldowns.Slot.HORIZONTAL_RIGHT;
			case AxisStrikePayload.VERTICAL_BOTTOM -> WeaponCooldowns.Slot.VERTICAL_BOTTOM;
			default -> WeaponCooldowns.Slot.VERTICAL_TOP;
		};

		if (!WeaponCooldowns.isReady(player, stack, slot)) {
			return;      // this slot is still on cooldown
		}

		boolean vertical = AxisStrikePayload.isVertical(endpoint);
		EntityType<BlankAxisEntity> axisType = vertical ? ModEntities.VERTICAL_AXIS : ModEntities.HORIZONTAL_AXIS;
		BlankAxisEntity axis = findLatestAxis(player, axisType);

		if (axis == null) {
			return;      // there is no matching blank axis yet (meaning no black hole has been punched out yet): do nothing
		}

		// Positive endpoint = the horizontal axis's "left" end / the vertical axis's "top" end
		boolean positive = endpoint == AxisStrikePayload.HORIZONTAL_LEFT
				|| endpoint == AxisStrikePayload.VERTICAL_TOP;
		Vec3 start = axis.endpoint(positive);
		// Growth direction = from this end toward the other end; a clash pillar's knockback direction is its
		// facing, so "spawn at the left end and it pushes right"
		Vec3 direction = axis.getBeamDirection().scale(positive ? -1.0D : 1.0D);

		EntityType<BlackBlockPillarEntity> pillarType = vertical
				? ModEntities.VERTICAL_AXIS_PILLAR
				: ModEntities.HORIZONTAL_AXIS_PILLAR;

		BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(pillarType, player.level());
		pillar.setOwner(player);
		applyRotation(pillar, direction);
		pillar.setPos(start.x, start.y, start.z);
		player.level().addFreshEntity(pillar);
		WeaponCooldowns.apply(player, stack, slot, GESTURE_COOLDOWN_TICKS);
	}

	/** Find the most recent blank axis of the given type punched out by the player themselves (a larger entity id is newer). */
	private static @Nullable BlankAxisEntity findLatestAxis(
			ServerPlayer player, EntityType<BlankAxisEntity> type
	) {
		BlankAxisEntity latest = null;

		for (BlankAxisEntity axis : player.level().getEntitiesOfClass(
				BlankAxisEntity.class, player.getBoundingBox().inflate(AXIS_SEARCH_RADIUS))) {
			if (axis.getType() != type || !axis.isOwnedBy(player) || axis.isRemoved()) {
				continue;
			}

			if (latest == null || axis.getId() > latest.getId()) {
				latest = axis;
			}
		}

		return latest;
	}

	/**
	 * Sneak right click: spawn a dash pillar **behind the player** that grows along the **view direction**, so it
	 * pushes the player all the way out; before spawning, the blocks around the player are cleared ("destroy the
	 * blocks around the player") so that the dash doesn't hit a wall immediately.
	 *
	 * <p>It uses a different cooldown slot from the ordinary right click (long pillar), so the two don't affect
	 * each other.
	 */
	private static InteractionResult sneakDash(ServerPlayer player, ItemStack stack) {
		if (!WeaponCooldowns.isReady(player, stack, WeaponCooldowns.Slot.SNEAK_RIGHT)) {
			return InteractionResult.SUCCESS;      // on cooldown: swallow this right click
		}

		if (!(player.level() instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}

		Vec3 view = player.getViewVector(1.0F).normalize();
		Vec3 origin = player.position().add(0.0D, player.getEyeHeight() - 0.5D, 0.0D);
		Vec3 start = origin.subtract(view.scale(DASH_BACK_OFFSET));      // behind the player

		clearAround(level, player.blockPosition(), DASH_CLEAR_RADIUS);   // destroy the blocks around the player

		BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.DASH_PILLAR, level);
		pillar.setOwner(player);
		pillar.setBeamDirection(view);                                   // grows along the view direction → pushes the player forward
		pillar.setPos(start.x, start.y, start.z);
		level.addFreshEntity(pillar);

		WeaponCooldowns.apply(player, stack, WeaponCooldowns.Slot.SNEAK_RIGHT, SNEAK_COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}

	/** Clear the blocks inside the sphere centred on {@code center} with radius {@code radius} (normal drops; bedrock depends on the settings toggle). */
	private static void clearAround(ServerLevel level, BlockPos center, int radius) {
		int radiusSqr = radius * radius;

		for (BlockPos pos : BlockPos.betweenClosed(
				center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (pos.distSqr(center) > radiusSqr) {
				continue;
			}

			BlockState state = level.getBlockState(pos);

			if (state.getCollisionShape(level, pos).isEmpty()) {
				continue;      // air, grass, flowers, water: not counted as blocks
			}

			if (state.getDestroySpeed(level, pos) < 0.0F && !Settings.breakBedrock()) {
				continue;      // bedrock and the like: decided by the settings toggle
			}

			level.destroyBlock(pos, true);
		}
	}

	/**
	 * Sneak left click of the **black** block star: find a block from the eyes along the view direction (at most
	 * {@link #CORRUPT_REACH} blocks away) and infect a patch around it into black blocks. The white block star has
	 * its own entry point ({@link #corruptTo}) with the same body but a different block.
	 */
	private static void corrupt(ServerPlayer player) {
		corruptTo(player, com.sizah.content.block.ModBlocks.BLACK_BLOCK);
	}

	/**
	 * Sneak left click: find a block from the eyes along the view direction (at most {@link #CORRUPT_REACH}
	 * blocks away), and use it as the starting point to **infect the surrounding blocks into {@code target}**
	 * within a {@link #CORRUPT_RADIUS}-block sphere; it settles when the player releases sneak
	 * (restore + drop the original drops and the painted blocks, see {@link Corruption}).
	 *
	 * <p>{@code target} is the block the held weapon paints with: black blocks for the black block star, white
	 * blocks for the white one. It is decided here, on the server, from what the player is actually holding.
	 */
	private static void corruptTo(ServerPlayer player, net.minecraft.world.level.block.Block target) {
		ItemStack stack = player.getMainHandItem();

		if (!WeaponCooldowns.isReady(player, stack, WeaponCooldowns.Slot.SNEAK_LEFT)) {
			return;      // on cooldown
		}

		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 eyes = player.getEyePosition();
		Vec3 end = eyes.add(player.getViewVector(1.0F).scale(CORRUPT_REACH));
		BlockHitResult hit = level.clip(
				new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

		if (hit.getType() == HitResult.Type.MISS) {
			return;      // didn't point at a block
		}

		if (Corruption.start(player, hit.getBlockPos(), target)) {
			WeaponCooldowns.apply(player, stack, WeaponCooldowns.Slot.SNEAK_LEFT, SNEAK_COOLDOWN_TICKS);
		}
	}

	/**
	 * The short black block pillar's origin: slightly below the player's eyes, shifted
	 * {@link #SHORT_PILLAR_OFFSET} blocks along the view direction.
	 *
	 * <p>It is also the long pillar's **final endpoint**: the long pillar spawns "one body length further along
	 * the view direction from this point" and sweeps back, so its front end stops exactly here, and the two
	 * pillars share the same central axis (the player's view direction axis).
	 */
	private static Vec3 shortPillarOrigin(Player player, Vec3 view) {
		return player.position()
				.add(0.0D, player.getEyeHeight() - 0.5D, 0.0D)
				.add(view.scale(SHORT_PILLAR_OFFSET));
	}

	/**
	 * Carry the facing through entity rotation: xRot/yRot are synced vanilla entity fields, and the client
	 * renderer places the blocks from them, so no extra custom sync data is needed.
	 */
	private static void applyRotation(BlackBlockPillarEntity pillar, Vec3 direction) {
		pillar.setYRot((float) (Mth.atan2(-direction.x, direction.z) * (180.0D / Math.PI)));
		pillar.setXRot((float) (-Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D)) * (180.0D / Math.PI)));
	}
}
