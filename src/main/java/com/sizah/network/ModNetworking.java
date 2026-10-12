package com.sizah.network;

import com.sizah.config.Settings;
import com.sizah.effect.Corruption;
import com.sizah.effect.ViewMotion;
import com.sizah.entity.BlackBlockPillarEntity;
import com.sizah.entity.BlankAxisEntity;
import com.sizah.entity.ModEntities;
import com.sizah.item.PillarKind;
import com.sizah.item.PillarWeaponItem;
import com.sizah.item.WeaponCooldowns;

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
 * Two firing paths:
 *
 * <ul>
 *     <li><b>Left click</b> (both weapons have it): client key press → request payload → the server spawns a
 *         regular pillar in front of the player;</li>
 *     <li><b>Right click</b> (black block star only): Fabric's UseItem / UseBlock events **also fire on the
 *         server side**, so they are handled on the server directly and need no extra network payload — a
 *         "long black block pillar" is spawned 60 blocks along the facing direction, and it sweeps back toward
 *         the player at 60 blocks/second.</li>
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
	 * The threshold for the **view motion** gate lives in {@link ViewMotion#STILL_LIMIT_DEGREES} (1 degree):
	 * within a single tick both the horizontal (yaw) and the vertical (pitch) change must be below it before
	 * an ability is allowed to fire.
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
		PayloadTypeRegistry.serverboundPlay().register(FirePillarPayload.TYPE, FirePillarPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AxisStrikePayload.TYPE, AxisStrikePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SettingsPayload.TYPE, SettingsPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CorruptPayload.TYPE, CorruptPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(FirePillarPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			if (firePillar(player)) {
				WeaponCooldowns.apply(player, player.getMainHandItem(), WeaponCooldowns.Slot.LEFT, FIRE_COOLDOWN_TICKS);
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

		// Right click: UseItemCallback for air/items, UseBlockCallback for blocks; the return value of either
		// cancels vanilla handling, so "right click anywhere" fires, and nothing is spawned twice.
		UseItemCallback.EVENT.register((player, level, hand) -> fireLongPillar(player, level, hand));
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> fireLongPillar(player, level, hand));

		// On player disconnect: clear cooldowns and view sampling records, and settle any infection that has not been settled yet
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			ServerPlayer player = listener.player;

			WeaponCooldowns.clear(player.getUUID());
			ViewMotion.clear(player.getUUID());
			Corruption.onDisconnect(player);
		});
	}

	/**
	 * Left click: server-authoritative validation, then spawn a regular pillar (behavior #1).
	 *
	 * <p>Two gates: **view motion** (it only fires while the view is not being turned, see {@link #viewStill})
	 * and the cooldown. If either is unmet, it does nothing at all and **does not consume the cooldown** either.
	 *
	 * @return whether it actually fired
	 */
	private static boolean firePillar(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();

		if (!(stack.getItem() instanceof PillarWeaponItem weapon)) {
			return false; // the main hand must hold a pillar weapon (black block star / obsidian block)
		}

		if (!viewStill(player)) {
			return false; // view motion gate failed: the view is being turned, so don't fire (and don't consume the cooldown)
		}

		if (!WeaponCooldowns.isReady(player, stack, WeaponCooldowns.Slot.LEFT)) {
			return false; // the left-click ability is on cooldown, ignore this request
		}

		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}

		// Full view direction (including pitch): looking up or down both fire at an angle.
		Vec3 direction = player.getViewVector(1.0F).normalize();

		// Origin: the short black block pillar's start (slightly below the eyes, shifted 1 block along the view direction).
		Vec3 start = shortPillarOrigin(player, direction);

		BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.typeFor(weapon.kind()), level);
		pillar.setOwner(player);
		applyRotation(pillar, direction);
		pillar.setPos(start.x, start.y, start.z);

		level.addFreshEntity(pillar);
		return true;
	}

	/**
	 * Right click: spawn the "long black block pillar".
	 *
	 * <p>The reference axis prefers the **actual axis of the most recent left-click short pillar** (origin +
	 * direction), so the long pillar spawns at "the origin plus another {@link ModEntities#LONG_LENGTH} blocks"
	 * and sweeps back toward the origin, **its front end ending up exactly on the short pillar's origin**;
	 * the two pillars' models therefore overlap as completely as possible. Only when there is no short pillar
	 * to reference does it fall back to "computed from the current view".
	 *
	 * <p>The event fires once on each side, and here it is **handled on the server side only** (the client-side
	 * pass returns PASS so that the client still sends the use packet to the server), so a single click spawns
	 * only one pillar.
	 */
	private static InteractionResult fireLongPillar(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide()) {
			// Client side: only play a purely visual cooldown animation (the real gates are on the server, and tracked independently of left click)
			if (hand == InteractionHand.MAIN_HAND
					&& player.getMainHandItem().getItem() instanceof PillarWeaponItem weapon
					&& weapon.kind() == PillarKind.BLACK_BLOCK) {
				// If the view motion gate fails, don't fire (and don't let vanilla send the use packet out); sneaking is a different ability and is let through as usual
				if (!player.isShiftKeyDown() && !viewStill(player)) {
					return InteractionResult.SUCCESS;
				}

				player.getCooldowns().addCooldown(player.getMainHandItem(), LONG_FIRE_COOLDOWN_TICKS);
			}

			return InteractionResult.PASS;         // let it through, so vanilla sends the use packet to the server
		}

		if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		ItemStack stack = serverPlayer.getMainHandItem();

		// Only the "black block star" has the right-click ability; the obsidian block does not.
		if (!(stack.getItem() instanceof PillarWeaponItem weapon) || weapon.kind() != PillarKind.BLACK_BLOCK) {
			return InteractionResult.PASS;
		}

		// Sneak right click = another ability: spawn a dash pillar behind the player and push the player out
		// along the view direction (independent cooldown). It is not subject to the view check, so sneak must be tested first.
		if (serverPlayer.isShiftKeyDown()) {
			return sneakDash(serverPlayer, stack);
		}

		// View motion gate: if the view is being turned, don't fire (and don't consume the cooldown)
		if (!viewStill(serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		if (!WeaponCooldowns.isReady(serverPlayer, stack, WeaponCooldowns.Slot.RIGHT)) {
			return InteractionResult.SUCCESS;      // right-click ability on cooldown: swallow this right click, don't spawn
		}

		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}

		// The spawning logic is **exactly the same** as for the short pillar: central axis = the player's view
		// direction axis, origin = the short pillar's origin. The only difference is the type: the long pillar is
		// spawned already at full length (60 blocks, transparent, inert), and black blocks plus functionality are
		// then filled in from the tip toward the base.
		Vec3 direction = serverPlayer.getViewVector(1.0F).normalize();
		Vec3 start = shortPillarOrigin(serverPlayer, direction);

		BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.LONG_BLACK_BLOCK_PILLAR, serverLevel);
		pillar.setOwner(serverPlayer);
		applyRotation(pillar, direction);
		pillar.setPos(start.x, start.y, start.z);

		serverLevel.addFreshEntity(pillar);
		WeaponCooldowns.apply(serverPlayer, stack, WeaponCooldowns.Slot.RIGHT, LONG_FIRE_COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
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
	 * Sneak left click: find a block from the eyes along the view direction (at most {@link #CORRUPT_REACH}
	 * blocks away), and use it as the starting point to **infect the blocks into black blocks** within a
	 * {@link #CORRUPT_RADIUS}-block sphere around it; it settles when the player releases sneak
	 * (restore + drop the original drops and the black blocks, see {@link Corruption}).
	 */
	private static void corrupt(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();

		if (!(stack.getItem() instanceof PillarWeaponItem weapon) || weapon.kind() != PillarKind.BLACK_BLOCK) {
			return;      // only the black block star can be used this way
		}

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

		if (Corruption.start(player, hit.getBlockPos())) {
			WeaponCooldowns.apply(player, stack, WeaponCooldowns.Slot.SNEAK_LEFT, SNEAK_COOLDOWN_TICKS);
		}
	}

	/**
	 * The **view motion** gate: behaviors #1 and #2 (left-click short pillar, right-click long pillar) can only
	 * fire while the view is **not being turned** — both the horizontal and the vertical per-tick motion must be
	 * below {@link ViewMotion#STILL_LIMIT_DEGREES} degrees.
	 *
	 * <p>Checked on both sides: if the client is not satisfied it sends no packet / cancels the event, and if the
	 * server is not satisfied it does nothing (and **does not consume the cooldown** either).
	 */
	public static boolean viewStill(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			return ViewMotion.isStill(serverPlayer);
		}

		return ViewMotion.isLocalStill();      // client branch: use the local samples
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
