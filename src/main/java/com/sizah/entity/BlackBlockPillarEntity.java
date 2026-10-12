package com.sizah.entity;

import com.sizah.config.Settings;
import com.sizah.effect.Explosions;
import com.sizah.effect.Marks;
import com.sizah.effect.PushConflicts;
import com.sizah.item.PillarKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Pillar entity (black block star / obsidian block / the spare long and eternal variants all share this one class).
 *
 * <p>It is like an "arrow that does not fall": it flies straight out along the view direction from the moment of firing, **unaffected by gravity and not a projectile**
 * (it does not extend {@code Projectile}/{@code AbstractArrow}, has no hit detection and cannot be picked up).
 *
 * <p>Geometrically it is a segment "base → front" described by two distances (both measured from the firing point, along the facing direction).
 * There is only one goal: **bring the total length up to the maximum length for the type (30 blocks for the regular types)**.
 *
 * <ul>
 *     <li><b>Extension</b>: the front advances at 30 blocks/second and the base is pinned to the firing point;</li>
 *     <li><b>The front hits a solid block</b>: the front braces and stops, and the **base retreats** to make up the length instead (reverse extension),
 *         so the owner is pushed back by the "remaining distance";</li>
 *     <li><b>Both ends are braced against solid blocks</b>: inside a sphere of 0.75 blocks radius at the midpoint of each end face, the **softest** block
 *         is destroyed instantly (if both ends have the same hardness the tip side is broken; **behaviour 1 skips indestructible blocks such as bedrock, whereas the other behaviours break bedrock too**),
 *         so that the length can keep filling in;</li>
 *     <li><b>Dissipation</b>: once the length is full, the base retracts forward at 30 blocks/second and the whole pillar dissipates at the front
 *         (the "eternal" type does not dissipate and simply stays there once full).</li>
 * </ul>
 *
 * <p>The differences (appearance block, touch damage, whether armour is ignored, maximum length, whether it dissipates) are carried by the entity type,
 * see {@link ModEntities.PillarSpec} — hence **no additional synced fields are needed**.</p>
 */
public class BlackBlockPillarEntity extends Entity {
	/** Regular extension / dissipation / knockback speed (short black block pillar, obsidian pillar, eternal black block pillar): 30 blocks/second ÷ 20 ticks. */
	public static final double SPEED_PER_TICK = 1.5D;

	/** Nominal extension duration (ticks): with nothing in the way the regular types take exactly 1 second to grow to 30 blocks. */
	public static final int GROWTH_TICKS = 20;

	/** Nominal dissipation duration (ticks) = 1 second. */
	public static final int DISSIPATE_TICKS = 20;

	/** Fallback lifetime: whatever happens, it dissipates once this many ticks have passed (the "eternal" type is exempt). */
	private static final int HARD_LIFETIME_TICKS = GROWTH_TICKS + DISSIPATE_TICKS + 40;

	/** When both ends are braced against blocks and neither can be broken (e.g. bedrock at both ends), how many ticks of stalling at most before extension ends. */
	private static final int MAX_STALL_TICKS = 10;

	/**
	 * Knockback speed (blocks/tick) = the extension speed of this type (see {@link #getSpeed()}).
	 *
	 * <p>The velocity is reset once per tick (20 Hz, the ceiling of the game loop), but a single displacement equals the extension speed,
	 * so the target is "pushed along" by the pillar rather than launched away in one go.
	 */

	/** Sampling step for path destruction: only below 1 block does it avoid skipping blocks while advancing fast. */
	private static final double PATH_SAMPLE_STEP = 0.25D;

	/** Collision detection distance between two pillars (blocks): the pillar cross-section is 1×1, so a centre-line distance within 1 block counts as touching. */
	private static final double COLLISION_DISTANCE = 1.0D;

	/**
	 * Power / radius (blocks) of the collision explosion: damage, marks and the black hole capture list all follow it.
	 *
	 * <p>Requirement "enlarge the explosion and mark range": 4 → **8** (power and radius grow together).
	 */
	private static final double EXPLOSION_RADIUS = 8.0D;

	/** Velocity multiplier at the moment a creature **leaves the pillar** (requirement: ×0.01, practically a standstill). */
	private static final double RELEASE_VELOCITY_SCALE = 0.01D;

	/** Per-tick velocity compensation that cancels vanilla gravity (gravitational acceleration is 0.08 blocks/tick²). */
	private static final double GRAVITY_COMPENSATION = 0.08D;

	/** Margin left when hitting a wall, so that the block face is not treated as usable space. */
	private static final double BLOCK_MARGIN = 0.05D;

	/**
	 * Endpoint destruction range (blocks): a sphere centred on the **midpoint of the two end faces** of the pillar with this value as its radius.
	 * Every solid block inside the sphere is taken into the comparison (the centre is the pillar's front point / back point; the pillar cross-section is 1×1 and centred on the axis,
	 * so the end face midpoint is that point on the axis).
	 *
	 * <p>0.75 blocks exactly covers "the block flush against the endpoint" as well as "the block diagonally adjacent by one step in the cross-section direction"
	 * (for a diagonal neighbour the block cube is about 0.707 blocks from the sphere centre), without reaching the layer beyond.
	 */
	private static final double BREAK_RADIUS = 0.75D;

	/** Fraction of the velocity kept when the recoil ends (0 = stop immediately, 1 = no deceleration at all). */
	private static final double RECOIL_END_VELOCITY_SCALE = 0.1D;

	/** Distance from the front to the firing point (synced to the client, needed for rendering). */
	private static final EntityDataAccessor<Float> DATA_FRONT = SynchedEntityData.defineId(
			BlackBlockPillarEntity.class,
			EntityDataSerializers.FLOAT
	);

	/** Distance from the base to the firing point (synced to the client, needed for rendering). */
	private static final EntityDataAccessor<Float> DATA_BASE = SynchedEntityData.defineId(
			BlackBlockPillarEntity.class,
			EntityDataSerializers.FLOAT
	);

	/**
	 * Distance from the fill frontier to the firing point (synced to the client, needed for rendering).
	 *
	 * <p>Only used by the types where {@link #fillsFromTip()} holds: the entity spawns at full length, but **rendering and functionality** are
	 * filled in little by little from the tip towards the base, and this value is "how far it has been filled".
	 */
	private static final EntityDataAccessor<Float> DATA_FILL = SynchedEntityData.defineId(
			BlackBlockPillarEntity.class,
			EntityDataSerializers.FLOAT
	);

	/** Local lifetime in ticks. */
	private int lifeTicks;

	/** Whether it is still in the extension phase; once the length is full (or it stalls completely) it switches to dissipation. */
	private boolean growing = true;

	/** Whether the front has already braced against a solid block. */
	private boolean frontBlocked;

	/** Whether a fill-type pillar has already filled all the way through once (once filled it switches to dissipation). */
	private boolean sweepFinished;

	/** Whether the collision has already been settled: guarantees one collision explodes only once and spawns only one black hole. */
	private boolean collided;

	/** Targets that were still inside the pillar last tick: used to scale the velocity by ×0.01 when they "leave the pillar". */
	private final Set<Entity> touchedTargets = new HashSet<>();

	/** Targets touched this tick ({@code pushTargets} clears it first and then fills it, every tick). */
	private final Set<Entity> touchedThisTick = new HashSet<>();

	/** How many ticks in a row neither end could advance. */
	private int stallTicks;

	/** Targets this pillar has already settled damage against (each pillar damages the same target only once). */
	private final Set<UUID> damagedTargets = new HashSet<>();

	/** Owner: used for recoil, for excluding himself and for damage attribution (server side only). */
	private @Nullable Entity owner;

	/** Whether the recoil is currently in progress. */
	private boolean ownerRecoilActive;

	/** Cumulative distance the recoil has already pushed out (blocks). */
	private double ownerRecoilPushed;

	/** Total distance this recoil needs to push (blocks) = the length the pillar still has to make up. */
	private double ownerRecoilDistance;

	public BlackBlockPillarEntity(EntityType<? extends BlackBlockPillarEntity> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public void setOwner(@Nullable Entity owner) {
		this.owner = owner;
	}

	@Override
	public void tick() {
		super.tick();
		this.lifeTicks++;

		// Geometry advancement, recoil, touch settlement and the life cycle all happen only on the server side; the client just reads the synced values and renders.
		if (this.level() instanceof ServerLevel serverLevel) {
			this.updateGeometry(serverLevel);
			this.checkPillarCollision(serverLevel);
			this.updateOwnerRecoil();
			this.pushTargets(serverLevel);
			this.updateDetachedTargets();

			if (this.dissipates()
					&& (this.lifeTicks >= HARD_LIFETIME_TICKS
							|| (!this.growing && this.getEffectiveLength() <= 1.0E-6D))) {
				this.discard();
			}
		}
	}

	// ------------------------------------------------------------------
	// Type differences (carried by the entity type)
	// ------------------------------------------------------------------

	private ModEntities.PillarSpec spec() {
		return ModEntities.specOf(this.getType());
	}

	/** Pillar kind: appearance block, touch damage, whether armour is ignored. */
	public PillarKind getKind() {
		return this.spec().kind();
	}

	/** Maximum length for this type (blocks): 30 for the regular types, 60 for the long black block pillar. */
	public double getMaxLength() {
		return this.spec().maxLength();
	}

	/** Advance speed for this type (blocks/tick). */
	public double getSpeed() {
		return this.spec().speedPerTick();
	}

	/** Knockback multiplier: 0.5 for the barrage pillar ("barrage knockback effect halved", "knockback distance halved"), 1.0 for the other types. */
	public double knockbackScale() {
		return this.spec().knockbackScale();
	}

	/** Touch damage multiplier: 0.5 for the barrage pillar ("barrage damage reduced"), 1.0 for the other types. */
	public double damageScale() {
		return this.spec().damageScale();
	}

	/** Whether it dissipates once full: {@code false} for the "eternal" type. */
	public boolean dissipates() {
		return this.spec().dissipates();
	}

	/** Whether it instantly destroys the blocks along its path ({@code true} for the long black block pillar). */
	public boolean destroysPath() {
		return this.spec().destroysPath();
	}

	/**
	 * Whether it can also break **indestructible blocks** (bedrock and the like, {@code getDestroySpeed < 0}).
	 *
	 * <p>**Both conditions must hold at the same time**: the type itself must allow it ({@code PillarSpec.breaksUnbreakable}; only behaviour 1 is
	 * {@code false}) and "break bedrock" must be on in the settings screen ({@link Settings#breakBedrock()}).
	 */
	public boolean breaksUnbreakable() {
		return Settings.breakBedrock() && this.spec().breaksUnbreakable();
	}

	/** Whether the owner is also knocked back by the pillar ({@code true} for the long black block pillar; damage never lands on the owner). */
	public boolean knocksBackOwner() {
		return this.spec().knocksBackOwner();
	}

	/**
	 * Collision group: {@code -1} = takes no part in collisions; {@code 0} = long black block pillar (hits any other pillar → black hole + mark 1);
	 * {@code 2} = horizontal pair; {@code 3} = vertical pair (only the same group with opposite directions collide with each other).
	 */
	public int collisionGroup() {
		return this.spec().collisionGroup();
	}

	/** Mark number applied to the creatures in range after a collision explosion (see {@link Marks}). */
	public int collisionMark() {
		return this.spec().collisionMark();
	}

	/** Whether this is a type that "spawns at full length and is then filled from the tip to the base" ({@code true} for the long black block pillar). */
	public boolean fillsFromTip() {
		return this.spec().fillsFromTip();
	}

	/** Distance from the fill frontier to the firing point (blocks): the fill direction is tip → base. */
	public double getFillDistance() {
		return this.entityData.get(DATA_FILL);
	}

	/**
	 * **Effective** base distance: for a fill-type pillar only the segment that has been filled renders and has functionality,
	 * so rendering, hit detection, collisions and path destruction all read this value (for an ordinary pillar it simply equals {@link #getBaseDistance()}).
	 */
	public double getEffectBaseDistance() {
		if (!this.fillsFromTip()) {
			return this.getBaseDistance();
		}

		return Math.max(this.getBaseDistance(), this.getFillDistance());
	}

	/**
	 * **Effective length**: the distance from the front to the "effective base".
	 *
	 * <p>A fill-type pillar only counts the segment already filled in, so this is also the criterion for "when it should disappear"
	 * (when a collision stops the fill halfway, the front retreating to the fill frontier means dissipation is complete).
	 */
	public double getEffectiveLength() {
		return this.getTipDistance() - this.getEffectBaseDistance();
	}

	/**
	 * Knockback direction.
	 *
	 * <p>For a regular pillar it is its extension direction (pushing the target towards the **tip**); the long black block pillar "advances from the tip towards the base",
	 * so it pushes the target towards the **base** — that is, **towards the player**.
	 */
	private Vec3 knockbackDirection() {
		return this.fillsFromTip() ? this.getBeamDirection().scale(-1.0D) : this.getBeamDirection();
	}

	/**
	 * Stop extending: called by either side on the other when they collide.
	 *
	 * <p>For a **fill-type** pillar (the long black block pillar) the "fill" is stopped along with it — it has no extension phase to begin with,
	 * only the fill frontier advancing; once stopped it switches to dissipation "from the tip towards the base".
	 */
	public void stopGrowing() {
		this.growing = false;
		this.sweepFinished = true;
	}

	// ------------------------------------------------------------------
	// Geometry: front / base
	// ------------------------------------------------------------------

	/**
	 * Advance the pillar geometry.
	 *
	 * <p>There is only one goal: **bring the total length up to {@link #getMaxLength()}**. The length is achieved along two routes — the front advancing,
	 * or the base retreating; when both ends are braced against blocks, the softer of the two is destroyed to make room.
	 */
	private void updateGeometry(ServerLevel level) {
		double front = this.getTipDistance();
		double base = this.getBaseDistance();

		// ── Fill type (long black block pillar): spawns at full length but with no functionality; the black blocks and the functionality are then filled in from the tip towards the base ──
		if (this.fillsFromTip() && !this.sweepFinished) {
			if (this.lifeTicks <= 1) {
				// First tick: instantly becomes an entity that is "60 blocks long, transparent and non-functional" (the effective segment is empty, so nothing is visible and nothing can be hit)
				this.setGeometry(this.getMaxLength(), 0.0D);
				this.entityData.set(DATA_FILL, (float) this.getMaxLength());
				return;
			}

			double fill = this.getFillDistance();
			double nextFill = Math.max(0.0D, fill - this.getSpeed());

			// Only the small segment newly filled in is destroyed (rendering, hit detection and collisions all read getEffectBaseDistance)
			if (this.destroysPath()) {
				this.destroyPathBlocks(level, nextFill, fill);
			}

			this.entityData.set(DATA_FILL, (float) nextFill);

			if (nextFill <= 1.0E-6D) {
				this.sweepFinished = true;
				this.growing = false;      // filled → switches to dissipation
			}

			return;
		}

		if (!this.growing) {
			if (!this.dissipates()) {
				return;      // "eternal" type: stays still once full
			}

			double floor = this.getEffectBaseDistance();

			if (this.fillsFromTip()) {
				// Fill type (long black block pillar): dissipation goes **from the tip to the base** — the front retreats back to "how far it had filled"
				double length = Math.max(0.0D, (front - floor) - this.getSpeed());
				this.setGeometry(floor + length, base);
				return;
			}

			// Regular types: the base retracts forward at the speed of this type (retracting towards the tip)
			double length = Math.max(0.0D, (front - base) - this.getSpeed());
			this.setGeometry(front, front - length);
			return;
		}

		double need = Math.min(this.getSpeed(), this.getMaxLength() - (front - base));

		if (need <= 1.0E-6D) {
			this.growing = false;      // already full, switches to dissipation (or stays still)
			return;
		}

		// ① Front first: if nothing blocks it, extend forward
		if (!this.frontBlocked) {
			// Types that destroy blocks along the way (long black block pillar): clear the blocks on this stretch of the path first, so the front is not stopped by ordinary blocks
			if (this.destroysPath()) {
				this.destroyPathBlocks(level, front, front + need);
			}

			double limit = this.limitForward(level, front, front + need);
			double gained = limit - front;
			front = limit;

			if (gained < need - 1.0E-6D) {
				this.frontBlocked = true;
			}

			need -= Math.min(gained, need);
		}

		// ② The remaining length is made up by the base retreating (i.e. "reverse extension")
		if (need > 1.0E-6D) {
			double limit = this.limitBackward(level, base, base - need);
			need -= Math.min(base - limit, need);
			base = limit;
		}

		// ③ Neither end can advance: destroy the softer side (the tip side if they are equal); bedrock and the like are never destroyed
		if (need > 1.0E-6D) {
			if (this.breakBlockedEnd(level)) {
				this.stallTicks = 0;
			} else if (++this.stallTicks >= MAX_STALL_TICKS) {
				this.growing = false;  // nothing can be pushed any more, end the extension
			}
		} else {
			this.stallTicks = 0;
		}

		this.setGeometry(front, base);
	}

	private void setGeometry(double front, double base) {
		this.entityData.set(DATA_FRONT, (float) front);
		this.entityData.set(DATA_BASE, (float) base);
	}

	/**
	 * Path destruction: **instantly destroy** the solid blocks on the stretch the front is about to sweep (without dropping items).
	 *
	 * <p>The axis is sampled at a step of {@link #PATH_SAMPLE_STEP} blocks and the block containing each sample point is taken, so the destroyed volume is exactly
	 * the volume swept by the pillar's 1×1 cross-section; air, grass, flowers and water do not count as blocks;
	 * **indestructible blocks (bedrock and the like) are skipped only when {@link #breaksUnbreakable()} is false** (behaviour 1);
	 * destroyed blocks **drop items normally**.
	 */
	private void destroyPathBlocks(ServerLevel level, double from, double to) {
		Vec3 direction = this.getBeamDirection();
		Vec3 origin = this.position();
		double distance = Math.abs(to - from);
		int steps = Math.max(1, (int) Math.ceil(distance / PATH_SAMPLE_STEP));

		for (int i = 0; i <= steps; i++) {
			double at = from + (to - from) * i / steps;
			BlockPos pos = BlockPos.containing(origin.add(direction.scale(at)));
			BlockState state = level.getBlockState(pos);

			if (state.getCollisionShape(level, pos).isEmpty()) {
				continue;      // air, grass, flowers, water
			}

			if (!this.breaksUnbreakable() && state.getDestroySpeed(level, pos) < 0.0F) {
				continue;      // only behaviour 1 is stopped by bedrock; the other behaviours clear bedrock too
			}

			level.destroyBlock(pos, true);   // drops items normally
		}
	}

	/** The front advances from {@code from} to {@code to}; returns the distance actually reachable (it stops in front of a block if it hits one). */
	private double limitForward(ServerLevel level, double from, double to) {
		Vec3 direction = this.getBeamDirection();
		Vec3 start = this.position().add(direction.scale(from));
		Vec3 end = this.position().add(direction.scale(to));

		BlockHitResult hit = level.clip(
				new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

		if (hit.getType() == HitResult.Type.MISS) {
			return to;
		}

		double travelled = hit.getLocation().subtract(start).dot(direction);
		return Mth.clamp(from + travelled - BLOCK_MARGIN, from, to);
	}

	/** The base retreats from {@code from} to {@code to} ({@code to < from}); returns the distance actually reachable. */
	private double limitBackward(ServerLevel level, double from, double to) {
		Vec3 direction = this.getBeamDirection();
		Vec3 start = this.position().add(direction.scale(from));
		Vec3 end = this.position().add(direction.scale(to));

		BlockHitResult hit = level.clip(
				new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

		if (hit.getType() == HitResult.Type.MISS) {
			return to;
		}

		double travelled = hit.getLocation().subtract(start).dot(direction);   // negative
		return Mth.clamp(from + travelled + BLOCK_MARGIN, to, from);
	}

	/**
	 * Handling for when both ends are braced against solid blocks: take the **softest** block inside a sphere of 0.75 blocks radius at the **midpoint of each end face**,
	 * then destroy the softer of the two; when the hardness is equal the **tip side** is destroyed; only behaviour 1 cannot touch indestructible blocks.
	 *
	 * @return whether a block was really destroyed (a successful destruction means the length can advance again next tick)
	 */
	private boolean breakBlockedEnd(ServerLevel level) {
		Vec3 direction = this.getBeamDirection();
		Vec3 origin = this.position();

		// The pillar cross-section is 1×1 and centred on the axis, so the midpoints of the two end faces are the front point and the back point on the axis.
		Vec3 frontCenter = origin.add(direction.scale(this.getTipDistance()));
		Vec3 baseCenter = origin.add(direction.scale(this.getBaseDistance()));

		BlockPos frontPos = this.softestBlockNear(level, frontCenter);
		BlockPos rearPos = this.softestBlockNear(level, baseCenter);

		// The sphere at the front is already empty (e.g. the block was mined by another player): just lift the blockage
		if (frontPos == null && this.frontBlocked) {
			this.frontBlocked = false;
			return false;
		}

		if (frontPos == null && rearPos == null) {
			return false;
		}

		if (frontPos == null) {
			return this.breakSide(level, rearPos, false);
		}

		if (rearPos == null) {
			return this.breakSide(level, frontPos, true);
		}

		float frontHardness = level.getBlockState(frontPos).getDestroySpeed(level, frontPos);
		float rearHardness = level.getBlockState(rearPos).getDestroySpeed(level, rearPos);
		boolean frontBreakable = this.breaksUnbreakable() || frontHardness >= 0.0F;
		boolean rearBreakable = this.breaksUnbreakable() || rearHardness >= 0.0F;

		if (!frontBreakable && !rearBreakable) {
			return false;      // both ends are bedrock or the like: nothing can be destroyed, it can only stall
		}

		if (!rearBreakable) {
			return this.breakSide(level, frontPos, true);
		}

		if (!frontBreakable) {
			return this.breakSide(level, rearPos, false);
		}

		// Destroy the softer one first; if the hardness is equal, destroy the tip side
		return frontHardness <= rearHardness
				? this.breakSide(level, frontPos, true)
				: this.breakSide(level, rearPos, false);
	}

	/**
	 * Inside the sphere centred on {@code center} with a radius of {@link #BREAK_RADIUS} blocks, find the **softest** solid block.
	 *
	 * <p>"Softest" = lowest mining hardness; when the hardness is equal the one closer to the sphere centre is taken, and if that is equal too the traversal order decides,
	 * so the result is stable and reproducible. Whether a block is inside the sphere is judged by the distance from the **whole block cube** to the centre.
	 *
	 * @return the softest solid block inside the sphere; {@code null} when there is no solid block inside it
	 */
	private @Nullable BlockPos softestBlockNear(ServerLevel level, Vec3 center) {
		double radiusSqr = BREAK_RADIUS * BREAK_RADIUS;
		BlockPos min = BlockPos.containing(
				center.x - BREAK_RADIUS, center.y - BREAK_RADIUS, center.z - BREAK_RADIUS);
		BlockPos max = BlockPos.containing(
				center.x + BREAK_RADIUS, center.y + BREAK_RADIUS, center.z + BREAK_RADIUS);

		BlockPos best = null;
		float bestHardness = Float.MAX_VALUE;
		double bestDistanceSqr = Double.MAX_VALUE;

		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			BlockState state = level.getBlockState(pos);

			if (state.getCollisionShape(level, pos).isEmpty()) {
				continue;      // air, grass, flowers, water: not counted as a "solid block"
			}

			double distanceSqr = new AABB(pos).distanceToSqr(center);

			if (distanceSqr > radiusSqr) {
				continue;      // the whole block is outside the sphere
			}

			float hardness = state.getDestroySpeed(level, pos);

			if (best == null
					|| hardness < bestHardness
					|| (hardness == bestHardness && distanceSqr < bestDistanceSqr)) {
				best = pos.immutable();      // betweenClosed reuses the same mutable object, so it must be pinned down
				bestHardness = hardness;
				bestDistanceSqr = distanceSqr;
			}
		}

		return best;
	}

	/**
	 * Instantly destroy a block, **dropping items normally**.
	 *
	 * @param frontSide whether the block destroyed is on the tip side (if the front is broken, the front blockage must be lifted)
	 */
	private boolean breakSide(ServerLevel level, BlockPos pos, boolean frontSide) {
		if (!level.destroyBlock(pos, true)) {
			return false;
		}

		if (frontSide) {
			this.frontBlocked = false;   // the front can advance again
		}

		return true;
	}

	// ------------------------------------------------------------------
	// Pillar-versus-pillar collision → explosion (the long pillar's hit also delivers a black hole + two blank axes)
	// ------------------------------------------------------------------

	/**
	 * Detection of a collision with another pillar.
	 *
	 * <p>A collision = the closest distance between two "base → front" segments is less than {@link #COLLISION_DISTANCE}.
	 * Who can collide with whom is decided by {@link ModEntities.PillarSpec#collisionGroup()} (see {@link #canCollideWith}):
	 *
	 * <ul>
	 *     <li><b>Group 0 (long black block pillar)</b>: hits any other pillar → explosion + **black hole** + two blank transparent axes + mark 1;</li>
	 *     <li><b>Groups 2 / 3 (horizontal / vertical clash pillars)</b>: collide only with pillars of the **same group and opposite direction** → **explosion only** + mark 2 / 3.</li>
	 * </ul>
	 *
	 * <p>{@link #collided} ensures the same collision is settled only once: the two pillars detect each other in the same tick, and whichever runs first
	 * marks the other along with itself, so the other immediately skips it — hence **only one black hole is spawned**.
	 */
	private void checkPillarCollision(ServerLevel level) {
		if (this.collisionGroup() < 0 || this.collided || this.isRemoved()) {
			return;
		}

		Vec3 from = this.getBackPoint();
		Vec3 to = this.getFrontPoint();

		if (from.distanceTo(to) < 1.0E-6D) {
			return;
		}

		AABB search = new AABB(from, to).inflate(COLLISION_DISTANCE + 1.0D);

		for (BlackBlockPillarEntity other : level.getEntitiesOfClass(BlackBlockPillarEntity.class, search)) {
			if (other == this || other.isRemoved() || other.collided || !this.canCollideWith(other)) {
				continue;
			}

			Vec3 impact = this.findCollisionPoint(from, to, other.getBackPoint(), other.getFrontPoint());

			if (impact != null) {
				this.collideWith(level, other, impact);
				return;
			}
		}
	}

	/** Who can collide with whom: the long pillar (group 0) hits a pillar of any other group; clash pillars collide with each other only in the same group and with opposite directions. */
	private boolean canCollideWith(BlackBlockPillarEntity other) {
		int mine = this.collisionGroup();
		int theirs = other.collisionGroup();

		if (theirs == ModEntities.GROUP_IMMUNE) {
			return false;      // barrage pillars and the like: they take no part in collisions at all, so that barrages do not interfere with each other
		}

		if (mine == ModEntities.GROUP_LONG) {
			return theirs != ModEntities.GROUP_LONG;
		}

		return mine == theirs && this.getBeamDirection().dot(other.getBeamDirection()) < 0.0D;
	}

	/**
	 * Find the "collision point" of two segments: sample this pillar at a step of 1 block, and for each point find its closest point on the other segment,
	 * then take the closest pair; if the distance is still greater than {@link #COLLISION_DISTANCE}, return {@code null}.
	 *
	 * <p>The sampling approach is easier to follow than an exact segment–segment closest-point algorithm and is accurate enough: a sample point is off by at most half a block,
	 * while the detection threshold is 1 block, so no collision is missed.
	 */
	private @Nullable Vec3 findCollisionPoint(Vec3 from, Vec3 to, Vec3 otherFrom, Vec3 otherTo) {
		double length = from.distanceTo(to);
		int steps = Math.max(1, (int) Math.ceil(length));
		Vec3 best = null;
		double bestDistance = COLLISION_DISTANCE;

		for (int i = 0; i <= steps; i++) {
			Vec3 point = from.lerp(to, (double) i / steps);
			Vec3 closest = closestPointOnSegment(otherFrom, otherTo, point);
			double distance = point.distanceTo(closest);

			if (distance <= bestDistance) {
				bestDistance = distance;
				best = point.add(closest).scale(0.5D);      // the collision point is the midpoint of the two closest points
			}
		}

		return best;
	}

	/** Collision settlement: both stop extending → explode and mark the creatures in range → the long pillar's hit additionally spawns a black hole and two blank axes. */
	private void collideWith(ServerLevel level, BlackBlockPillarEntity other, Vec3 impact) {
		this.collided = true;
		other.collided = true;
		this.stopGrowing();
		other.stopGrowing();

		// Explosion: a real explosion, with power matching the detection radius; whether it destroys blocks is decided by the settings screen (turned off, it only hurts creatures).
		// The Explosions wrapper exists to satisfy "explosions produced by black block entities impart no velocity" — after the blast everyone's original velocity is restored.
		Explosions.explodeWithoutKnockback(level, this, impact,
				(float) EXPLOSION_RADIUS, Settings.explosionInteraction());

		// Affected range: a sphere centred on the collision point with radius EXPLOSION_RADIUS.
		// "The player who fired the behaviour" takes no part in the settlement that follows: not pulled by the black hole, not marked by the explosion, not knocked back by the barrage.
		List<LivingEntity> victims = new ArrayList<>(level.getEntitiesOfClass(
				LivingEntity.class,
				new AABB(impact, impact).inflate(EXPLOSION_RADIUS)));
		victims.removeIf(victim -> victim == this.owner || victim == other.owner);

		// Black hole: only the explosion of **behaviours 1 + 2** (left-click short pillar × right-click long pillar, i.e. group 0) spawns one;
		// it lasts 1 second and pulls the affected creatures back to the collision point (locking them in place); clash pillars and the explosion that closes out a barrage spawn none.
		if (this.collisionGroup() == ModEntities.GROUP_LONG
				|| other.collisionGroup() == ModEntities.GROUP_LONG) {
			BlackHoleEntity blackHole = new BlackHoleEntity(ModEntities.BLACK_HOLE, level);
			blackHole.setPos(impact.x, impact.y, impact.z);
			blackHole.capture(victims);
			level.addFreshEntity(blackHole);
		}

		// Mark: the long pillar's hit is always 1; a clash pillar uses the 2 / 3 of its own group
		int mark = this.collisionGroup() == 0 || other.collisionGroup() == 0
				? Marks.PILLAR_CLASH
				: Math.max(this.collisionMark(), other.collisionMark());

		for (LivingEntity victim : victims) {
			Marks.apply(victim, mark);

			if (mark == Marks.PILLAR_CLASH) {
				// Mark 1: spawn two blank axes on it and "clear all directional velocity for 5 seconds"
				this.applyMarkOne(level, victim);
			}

			this.tryTriggerBarrage(level, victim);
		}
	}

	/**
	 * Collecting all **three marks** (mark 1 + 2 + 3) triggers the finishing move: the **black block pillar barrage**.
	 *
	 * <p>On triggering, the three marks are **consumed**, so doing it again means building the whole combo again, and this also avoids
	 * the chain firing of "as long as it still carries the three marks, every explosion triggers it once more".
	 */
	private void tryTriggerBarrage(ServerLevel level, LivingEntity victim) {
		if (!Marks.hasAll(victim)) {
			return;
		}

		Marks.clearAll(victim);

		PillarBarrageEntity barrage = new PillarBarrageEntity(ModEntities.PILLAR_BARRAGE, level);
		barrage.setTarget(victim);
		barrage.setOwner(this.owner);
		barrage.setPos(victim.getX(), victim.getY(), victim.getZ());
		level.addFreshEntity(barrage);
	}

	/**
	 * The product of mark 1: spawn two blank transparent axes **on the marked creature** and **lock it in place for 5 seconds**
	 * ({@link MarkLockEntity}: zeroes the velocity every tick + holds the position).
	 *
	 * <ul>
	 *     <li>Horizontal axis: perpendicular to the player's view, lying flat, with the facing taken from the player's **left hand** (so its positive endpoint is the "left end");</li>
	 *     <li>Vertical axis: perpendicular to the player's view, standing up, with the facing taken from above the player's **head** (so its positive endpoint is the "upper end").</li>
	 * </ul>
	 *
	 * <p>Both axes are {@link BlankAxisEntity#LENGTH} blocks long and have no functionality of their own; they only provide endpoints for mouse gestures.
	 * The axes are centred on the creature's **body centre**, so the two ends are 10 blocks above/below or left/right of it.
	 */
	private void applyMarkOne(ServerLevel level, LivingEntity victim) {
		Vec3 center = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
		Vec3 view = this.owner != null ? this.owner.getViewVector(1.0F).normalize() : this.getBeamDirection();
		Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 horizontal = up.cross(view).normalize();      // the player's left-hand direction (horizontal)

		if (horizontal.lengthSqr() > 1.0E-6D) {
			Vec3 vertical = view.cross(horizontal).normalize();   // "up" perpendicular to the view (vertical)

			this.spawnAxis(ModEntities.HORIZONTAL_AXIS, level, center, horizontal, victim);
			this.spawnAxis(ModEntities.VERTICAL_AXIS, level, center, vertical, victim);
		}

		MarkLockEntity lock = new MarkLockEntity(ModEntities.MARK_LOCK, level);
		lock.setTarget(victim);
		lock.setPos(victim.getX(), victim.getY(), victim.getZ());
		level.addFreshEntity(lock);
	}

	/** Spawn one blank axis: the facing is carried by the vanilla entity's yRot/xRot, and it is made to follow {@code target} as it moves. */
	private void spawnAxis(
			EntityType<BlankAxisEntity> type, ServerLevel level, Vec3 center, Vec3 direction, Entity target
	) {
		BlankAxisEntity axis = new BlankAxisEntity(type, level);
		axis.setOwner(this.owner);
		axis.setTarget(target);
		axis.setYRot((float) (Mth.atan2(-direction.x, direction.z) * (180.0D / Math.PI)));
		axis.setXRot((float) (-Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D)) * (180.0D / Math.PI)));
		axis.setPos(center.x, center.y, center.z);
		level.addFreshEntity(axis);
	}

	// ------------------------------------------------------------------
	// Player recoil
	// ------------------------------------------------------------------

	/**
	 * Player recoil: it starts the moment **the player touches the pillar**, pushing him in the opposite direction by the "remaining distance" — that is, how much length the pillar still lacks.
	 *
	 * <p>The push speed is exactly the same as the base's retreat speed ({@link #SPEED_PER_TICK}), so the player and the base move together.
	 * When the recoil ends the velocity is multiplied by 0.1 (practically stopped, but without an abrupt zero); during it gravity compensation is applied and the fall distance
	 * is cleared every tick, and it **deals no damage**.
	 */
	private void updateOwnerRecoil() {
		Entity player = this.owner;

		if (player == null || !player.isAlive()) {
			this.ownerRecoilActive = false;
			return;
		}

		if (!this.ownerRecoilActive) {
			// Only during reverse extension (the front braced, the base retreating) does the base sweep towards the player; the player starts recoiling as soon as he touches the pillar.
			if (!this.growing || !this.frontBlocked || !this.isTouching(player)) {
				return;
			}

			this.ownerRecoilActive = true;
			this.ownerRecoilPushed = 0.0D;
			this.ownerRecoilDistance = Math.max(0.0D, this.getMaxLength() - this.getLength());
		}

		// Once the remaining distance has been pushed (or the extension phase has ended), stop and decay the velocity proportionally.
		if (this.ownerRecoilPushed >= this.ownerRecoilDistance || !this.growing) {
			this.endOwnerRecoil(player);
			return;
		}

		this.pushOwnerBackward(player);
		this.ownerRecoilPushed += this.getSpeed();
	}

	/** Push the player backward at the same speed as the base retreats, with gravity compensation. */
	private void pushOwnerBackward(Entity player) {
		Vec3 direction = this.getBeamDirection();

		if (direction.lengthSqr() < 1.0E-6D) {
			return;
		}

		double speed = this.getSpeed();

		player.setDeltaMovement(
				-direction.x * speed,
				-direction.y * speed + GRAVITY_COMPENSATION,
				-direction.z * speed);
		player.syncVelocity = true;      // equivalent to vanilla markHurt(): makes the server sync the velocity to the client
		player.resetFallDistance();      // "deals no damage": recoil produces no fall damage
	}

	/** End of the recoil: the velocity decays proportionally (×0.1 by default), so the player practically stops without an abrupt zero. */
	private void endOwnerRecoil(Entity player) {
		this.ownerRecoilActive = false;
		player.setDeltaMovement(player.getDeltaMovement().scale(RECOIL_END_VELOCITY_SCALE));
		player.syncVelocity = true;
		player.resetFallDistance();
	}

	/** Whether the entity is currently touching the pillar: project the body centre onto the segment and see whether it falls within the bounding box inflated by 0.5 blocks. */
	private boolean isTouching(Entity entity) {
		Vec3 direction = this.getBeamDirection();

		if (direction.lengthSqr() < 1.0E-6D) {
			return false;
		}

		Vec3 origin = this.position();
		Vec3 basePos = origin.add(direction.scale(this.getEffectBaseDistance()));
		Vec3 tipPos = origin.add(direction.scale(this.getTipDistance()));
		Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
		Vec3 closest = closestPointOnSegment(basePos, tipPos, center);

		return entity.getBoundingBox().inflate(0.5D).contains(closest);
	}

	// ------------------------------------------------------------------
	// Touch settlement
	// ------------------------------------------------------------------

	/**
	 * Touch settlement: deal damage to and knock back the creatures touched by the segment the pillar currently covers (base → front).
	 *
	 * <p>Two details:
	 * <ul>
	 *     <li>Damage uses the return value of {@code hurtServer} to decide whether it really took effect (the target may be in invulnerability frames);
	 *         on failure nothing is recorded and it is retried next tick, guaranteeing each pillar hits at most once;</li>
	 *     <li>Knockback resets the velocity every tick and the single displacement equals the extension speed, so the target advances in step with the pillar
	 *         until it has travelled the maximum length (the front endpoint) along the direction.</li>
	 * </ul>
	 */
	private void pushTargets(ServerLevel level) {
		this.touchedThisTick.clear();      // cleared first: whatever was not touched this tick counts as "detached" (see updateDetachedTargets)

		double base = this.getEffectBaseDistance();
		double tip = this.getTipDistance();

		if (tip - base <= 0.0D) {
			return;
		}

		Vec3 direction = this.getBeamDirection();

		if (direction.lengthSqr() < 1.0E-6D) {
			return;
		}

		Vec3 origin = this.position();
		Vec3 basePos = origin.add(direction.scale(base));
		Vec3 tipPos = origin.add(direction.scale(tip));

		// First a coarse filter with the pillar's bounding box, then a precise "point-to-segment" filter, so a slanted pillar does not hit nearby creatures through its AABB.
		AABB search = new AABB(basePos, tipPos).inflate(1.0D);
		PillarKind kind = this.getKind();
		DamageSource source = level.damageSources().source(kind.damageType(), this, this.owner);

		double knockback = this.getSpeed() * this.knockbackScale();   // the barrage pillar's knockback is halved
		boolean knockOwner = this.knocksBackOwner();

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search)) {
			boolean isOwner = target == this.owner;

			// Regular types never touch the owner; the long black block pillar pushes him along as well (but never damages him).
			if (isOwner && !knockOwner) {
				continue;
			}

			if (!target.isAlive()) {
				continue;
			}

			Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
			Vec3 closest = closestPointOnSegment(basePos, tipPos, center);

			if (!target.getBoundingBox().inflate(0.5D).contains(closest)) {
				continue;
			}

			this.touchedThisTick.add(target);      // records "it is inside the pillar this tick", used to wrap up when it detaches

			// Damage: the amount and whether armour is ignored are both decided by the weapon kind; each pillar settles damage against the same target only once;
			// the owner takes no damage (he can only be knocked back).
			if (!isOwner
					&& !this.damagedTargets.contains(target.getUUID())
					&& target.hurtServer(level, source, kind.touchDamage() * (float) this.damageScale())) {
				this.damagedTargets.add(target.getUUID());
			}

			// Being knocked back by **two different black block entities** at the same time while hurt → the velocity is zeroed
			// (two forces in different directions bracing against each other should pin the target in place, rather than stacking into a higher speed).
			if (target.hurtTime > 0 && PushConflicts.isConflicting(level, target, this.getType())) {
				target.setDeltaMovement(Vec3.ZERO);
				target.syncVelocity = true;
				continue;
			}

			// Knockback direction: by type by default (regular towards the tip / the long pillar towards the base); with an "attracted target" it becomes a push towards it
			// (the barrage uses this to make sure the creature it hits ends up near the owner).
			Vec3 push = this.knockbackDirection();

			// Knockback: pushed once per tick, with a single displacement = type speed × multiplier; the distance cap **also** shrinks by the multiplier,
			// so "barrage knockback halved" halves both the push and the distance.
			double travelled = target.position().subtract(origin).dot(push);

			if (travelled < this.getMaxLength() * this.knockbackScale()) {
				target.setDeltaMovement(
						push.x * knockback,
						push.y * knockback + GRAVITY_COMPENSATION,
						push.z * knockback);
				// equivalent to vanilla markHurt(): makes the server sync the velocity to the client, otherwise the knockback is visible only on the server.
				target.syncVelocity = true;
			}
		}
	}

	/**
	 * The wrap-up for "leaving the black block pillar": creatures that were still inside the pillar last tick and are no longer inside this tick get **velocity ×0.01**.
	 *
	 * <p>So a creature pushed along by the pillar comes to a near standstill the moment it leaves the pillar, instead of flying out at full speed.
	 * The owner himself does not count — he has his own recoil wrap-up (×0.1, see {@link #endOwnerRecoil}).
	 */
	private void updateDetachedTargets() {
		for (Entity previous : this.touchedTargets) {
			if (this.touchedThisTick.contains(previous) || previous.isRemoved() || previous == this.owner) {
				continue;
			}

			previous.setDeltaMovement(previous.getDeltaMovement().scale(RELEASE_VELOCITY_SCALE));
			previous.syncVelocity = true;
		}

		this.touchedTargets.clear();
		this.touchedTargets.addAll(this.touchedThisTick);
	}

	private static Vec3 closestPointOnSegment(Vec3 from, Vec3 to, Vec3 point) {		Vec3 segment = to.subtract(from);
		double lengthSqr = segment.lengthSqr();

		if (lengthSqr < 1.0E-8D) {
			return from;
		}

		double t = Mth.clamp(point.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
		return from.add(segment.scale(t));
	}

	// ------------------------------------------------------------------
	// External reads / saving / syncing / being hit
	// ------------------------------------------------------------------

	/** Distance from the front (tip) to the firing point (blocks): fixed on the block face after hitting a wall. */
	public double getTipDistance() {
		return this.entityData.get(DATA_FRONT);
	}

	/** Distance from the base to the firing point (blocks): negative during reverse extension (retreating behind the firing point). */
	public double getBaseDistance() {
		return this.entityData.get(DATA_BASE);
	}

	/**
	 * Carry the facing with the entity rotation: yRot/xRot are vanilla synced fields, and the client renderer places the blocks according to them.
	 *
	 * <p>Spawned pillars all go through here (barrage pillars, weapon firing and clash pillars all use it).
	 */
	public void setBeamDirection(Vec3 direction) {
		this.setYRot((float) (Mth.atan2(-direction.x, direction.z) * (180.0D / Math.PI)));
		this.setXRot((float) (-Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D)) * (180.0D / Math.PI)));
	}

	/** World coordinates of the front point (tip). */
	public Vec3 getFrontPoint() {
		return this.position().add(this.getBeamDirection().scale(this.getTipDistance()));
	}

	/** World coordinates of the back point (tail): for a fill-type pillar it takes "how far it has filled", i.e. the end that actually has functionality at the moment. */
	public Vec3 getBackPoint() {
		return this.position().add(this.getBeamDirection().scale(this.getEffectBaseDistance()));
	}

	/** Total length of the pillar (blocks). */
	public double getLength() {
		return this.getTipDistance() - this.getBaseDistance();
	}

	/** Whether the front has already braced against a solid block (for debugging/documentation reference). */
	public boolean isFrontBlocked() {
		return this.frontBlocked;
	}

	/** Whether it is still in the extension phase. */
	public boolean isGrowing() {
		return this.growing;
	}

	/**
	 * Pillar facing.
	 *
	 * <p>Derived directly from the entity rotation (written with {@code setYRot}/{@code setXRot} when firing),
	 * and the rotation itself is a vanilla synced field, so the client gets the facing without any extra custom synced data.
	 */
	public Vec3 getBeamDirection() {
		return this.getViewVector(1.0F);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		// The front/base distances depend on the world (whether a wall was hit, whether blocks were destroyed) and the client cannot derive them itself, so they must be synced.
		// Differences such as appearance/damage are carried by the entity type (see ModEntities) and need no synced fields.
		entityData.define(DATA_FRONT, 0.0F);
		entityData.define(DATA_BASE, 0.0F);
		entityData.define(DATA_FILL, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		// EntityType.Builder#noSave(): nothing is written to the save file, so no fields need to be read or written.
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false; // cannot be destroyed by attacks
	}
}
