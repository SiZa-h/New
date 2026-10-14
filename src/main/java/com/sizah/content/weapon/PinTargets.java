package com.sizah.content.weapon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The white block star's "hold right click": the creature the crosshair points at is **held on the player's line
 * of sight**.
 *
 * <p>As specified: pick the creature the crosshair points at within
 * {@link #GRAB_RANGE} (30) blocks, then keep it on the view line until the button is released, the target dies,
 * the player looks away for too long or either of them leaves.
 *
 * <p>Implementation notes:
 *
 * <ul>
 *     <li>The target is **grabbed once**, when the button goes down: after that it stays the one being held even
 *         if the crosshair sweeps across something else, which is what makes it usable as a "carry this thing
 *         around" tool;</li>
 *     <li>It is held at the distance it was grabbed at, at the height of the line of sight — so it neither snaps
 *         against the player's face nor trails behind;</li>
 *     <li>Its velocity is zeroed every tick and its fall distance cleared, so releasing it does not drop it from
 *         the sky, and the server syncs the position (a plain {@code teleportTo} already does).</li>
 * </ul>
 */
public final class PinTargets {
	/** How far the crosshair reaches when grabbing, in blocks: 30, as specified. */
	public static final double GRAB_RANGE = 30.0D;

	/** One player's grip: what is held, how far away it was grabbed, and where on the target to aim. */
	private static final class Grip {
		private final UUID target;
		private final double distance;
		/** Which point of the target's box the line of sight goes through (its eye height, roughly). */
		private final double heightOffset;

		private Grip(UUID target, double distance, double heightOffset) {
			this.target = target;
			this.distance = distance;
			this.heightOffset = heightOffset;
		}
	}

	private static final Map<UUID, Grip> GRIPS = new HashMap<>();

	private PinTargets() {
	}

	/**
	 * The button went down: grab whatever the crosshair is pointing at.
	 *
	 * @return the grabbed creature, or {@code null} when there was nothing to grab
	 */
	public static LivingEntity grab(ServerPlayer player) {
		LivingEntity target = findTarget(player);

		if (target == null) {
			return null;
		}

		Vec3 eyes = player.getEyePosition();
		Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
		double distance = Math.max(1.5D, eyes.distanceTo(center));

		GRIPS.put(player.getUUID(), new Grip(target.getUUID(), distance, target.getBbHeight() * 0.5D));
		return target;
	}

	/** The button came up: let go. */
	public static void release(ServerPlayer player) {
		GRIPS.remove(player.getUUID());
	}

	/** Whether this player is currently holding something (used by tooling). */
	public static boolean isHolding(ServerPlayer player) {
		return GRIPS.containsKey(player.getUUID());
	}

	/** Drops a grip without needing the player object (disconnect, death, dimension change). */
	public static void forget(UUID playerId) {
		GRIPS.remove(playerId);
	}

	/** Server tick: keep every held creature on its holder's line of sight. */
	public static void tick(MinecraftServer server) {
		if (GRIPS.isEmpty()) {
			return;
		}

		var iterator = GRIPS.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<UUID, Grip> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player == null || player.isRemoved() || !player.isAlive()) {
				iterator.remove();
				continue;
			}

			// The weapon has to stay in the main hand, exactly like the fire stream — and sneaking releases the
			// grip as well, because that button belongs to the white block infection while sneak is held.
			if (player.getMainHandItem().getItem() != com.sizah.content.item.ModItems.WHITE_BLOCK_STAR
					|| player.isShiftKeyDown()) {
				iterator.remove();
				continue;
			}

			Entity held = find(server, entry.getValue().target);

			if (!(held instanceof LivingEntity living) || living.isRemoved() || !living.isAlive()) {
				iterator.remove();
				continue;
			}

			if (!(living.level() instanceof ServerLevel level)) {
				continue;
			}

			Vec3 view = player.getViewVector(1.0F).normalize();
			Vec3 point = player.getEyePosition().add(view.scale(entry.getValue().distance));
			Vec3 feet = point.subtract(0.0D, entry.getValue().heightOffset, 0.0D);

			living.teleportTo(feet.x, feet.y, feet.z);
			living.setDeltaMovement(Vec3.ZERO);
			living.syncVelocity = true;
			living.fallDistance = 0.0F;

			// A pinned creature faces where its holder faces: it is being carried, not walking.
			living.setYRot(player.getYRot());
		}
	}

	/** The nearest living creature the crosshair points at, within {@link #GRAB_RANGE}. */
	private static LivingEntity findTarget(ServerPlayer player) {
		Vec3 eyes = player.getEyePosition();
		Vec3 view = player.getViewVector(1.0F).normalize();
		Vec3 end = eyes.add(view.scale(GRAB_RANGE));
		AABB search = new AABB(eyes, end).inflate(1.0D);

		Predicate<Entity> filter = entity ->
				entity instanceof LivingEntity living && living.isAlive() && living != player && !living.isSpectator();

		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (Entity entity : player.level().getEntities(player, search, filter)) {
			Vec3 hit = clip(view, eyes, entity.getBoundingBox().inflate(0.3D));

			if (hit == null) {
				continue;
			}

			double distance = eyes.distanceToSqr(hit);

			if (distance < bestDistance) {
				bestDistance = distance;
				best = (LivingEntity) entity;
			}
		}

		return best;
	}

	/**
	 * Where the ray enters the box, or {@code null} if it misses: the usual slab test, done here rather than
	 * borrowed from a projectile class that only exists for projectiles.
	 */
	private static Vec3 clip(Vec3 direction, Vec3 origin, AABB box) {
		double[] from = {origin.x, origin.y, origin.z};
		double[] dir = {direction.x, direction.y, direction.z};
		double[] min = {box.minX, box.minY, box.minZ};
		double[] max = {box.maxX, box.maxY, box.maxZ};

		double tMin = 0.0D;
		double tMax = GRAB_RANGE;

		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(dir[axis]) < 1.0E-8D) {
				if (from[axis] < min[axis] || from[axis] > max[axis]) {
					return null;
				}

				continue;
			}

			double inverse = 1.0D / dir[axis];
			double t1 = (min[axis] - from[axis]) * inverse;
			double t2 = (max[axis] - from[axis]) * inverse;

			if (t1 > t2) {
				double swap = t1;
				t1 = t2;
				t2 = swap;
			}

			tMin = Math.max(tMin, t1);
			tMax = Math.min(tMax, t2);

			if (tMin > tMax) {
				return null;
			}
		}

		return origin.add(direction.scale(tMin));
	}

	private static Entity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);

			if (entity != null) {
				return entity;
			}
		}

		return null;
	}

	/** Wipes the state when the server stops. */
	public static void clear() {
		GRIPS.clear();
	}
}
