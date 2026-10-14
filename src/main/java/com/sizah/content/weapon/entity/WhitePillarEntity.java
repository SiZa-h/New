package com.sizah.content.weapon.entity;

import com.sizah.common.damage.ModDamageTypes;
import com.sizah.content.effect.Marks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The **white block pillar**: a thin, fast, single-target bolt of white block.
 *
 * <p>It is deliberately a class of its own rather than another {@code PillarSpec} of
 * {@link BlackBlockPillarEntity}: the black pillar is a **growing beam** anchored to a firing point, while this
 * one is a **projectile** — it travels forward at a fixed speed and is gone once it has covered its range. Trying
 * to express both with one set of fields would have meant a flag for every field.
 *
 * <p>Shape and flight (as specified):
 *
 * <ul>
 *     <li>{@link #THICKNESS} = **0.1 blocks** across, {@link #LENGTH} = **5 blocks** long — the renderer scales
 *         its block models down to the same thickness, so what is drawn and what collides agree;</li>
 *     <li>{@link #SPEED_PER_TICK} = 12.8 blocks/tick = **256 blocks/second**;</li>
 *     <li>{@link #MAX_DISTANCE} = **128 blocks**, then it dissipates;</li>
 *     <li>it flies along the direction it was fired in (set by the launcher) and is **not** affected by gravity.</li>
 * </ul>
 *
 * <p>What it does on the way: it is **harmless to the world** — a solid block simply stops it, nothing is ever
 * destroyed — and a living creature it touches takes {@link #TOUCH_DAMAGE} and gets the **white mark W** left on
 * it. The shooter is never touched or marked, because it is skipped outright.
 *
 * <p>Two phases: it flies until it has covered {@link #MAX_DISTANCE}, hits a creature, or touches a solid block;
 * in the last case it **stays where it stopped** for {@link #REST_TICKS} (the model remains visible for a moment,
 * with no collision and no damage any more) and then dissipates. Gravity never applies to it in either phase:
 * the type has {@code setNoGravity(true)} and the velocity is explicitly zeroed, so a spent bolt hangs exactly
 * where it stopped instead of sinking into the ground.
 *
 * <p>The mark is half of the pair that starts the decay effect: a creature carrying both {@code B} (from any
 * black pillar) and {@code W} starts taking damage over time — see {@code content.effect.Decay}.
 */
public class WhitePillarEntity extends Entity {
	/** Thickness (blocks) on both axes: 0.1, as specified. */
	public static final float THICKNESS = 0.1F;

	/** Length (blocks) of the drawn pillar: 5, as specified. */
	public static final double LENGTH = 5.0D;

	/** 12.8 blocks per tick = 256 blocks per second, as specified. */
	public static final double SPEED_PER_TICK = 12.8D;

	/** Range (blocks): past this the pillar dissipates, as specified. */
	public static final double MAX_DISTANCE = 128.0D;

	/**
	 * How long (ticks) the bolt **stays visible where it hit a block**, before it dissipates: 1 second.
	 *
	 * <p>It has already stopped dealing damage and colliding at that point — the model is simply left standing so
	 * the shot can be read, instead of the bolt blinking out of existence the instant it touches a wall.
	 */
	public static final int REST_TICKS = 20;

	/**
	 * Damage when it touches a creature.
	 *
	 * <p>Chosen to mirror the black block star (6.0) on the other side of the pair: 5.0, and it **bypasses
	 * armour** — the white pillar is the magical half of the black/white pair, so it ignores armour while the
	 * black one, being plain crushing, does too. Change this one constant to rebalance the whole weapon.
	 */
	public static final float TOUCH_DAMAGE = 5.0F;

	/** Hard lifetime: the range divided by the speed, plus a little slack so a stalled pillar cannot linger. */
	private static final int HARD_LIFETIME_TICKS = (int) Math.ceil(MAX_DISTANCE / SPEED_PER_TICK) + 10;

	/** Sampling step (blocks) when walking the path of one tick; below a block nothing gets skipped. */
	private static final double PATH_SAMPLE_STEP = 0.25D;

	private static final EntityDataAccessor<Float> DATA_DIRECTION_X =
			SynchedEntityData.defineId(WhitePillarEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_DIRECTION_Y =
			SynchedEntityData.defineId(WhitePillarEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_DIRECTION_Z =
			SynchedEntityData.defineId(WhitePillarEntity.class, EntityDataSerializers.FLOAT);

	private @Nullable Entity owner;

	/** How far it has travelled so far; the range check uses it. */
	private double travelled;

	private int lifeTicks;

	/** Ticks left of "stuck against a block, still drawn"; 0 means it is still flying. */
	private int restTicks;

	/** Creatures already hit, so one pillar damages each of them exactly once. */
	private final Set<UUID> hitTargets = new HashSet<>();

	public WhitePillarEntity(EntityType<? extends WhitePillarEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** The shooter: never damaged, never marked, and the source of the knockback direction. */
	public void setOwner(@Nullable Entity owner) {
		this.owner = owner;
	}

	public @Nullable Entity getOwner() {
		return this.owner;
	}

	/** Flight direction (unit vector); the renderer draws the pillar along it. */
	public void setFlightDirection(Vec3 direction) {
		Vec3 unit = direction.normalize();
		this.entityData.set(DATA_DIRECTION_X, (float) unit.x);
		this.entityData.set(DATA_DIRECTION_Y, (float) unit.y);
		this.entityData.set(DATA_DIRECTION_Z, (float) unit.z);
	}

	public Vec3 getFlightDirection() {
		return new Vec3(
				this.entityData.get(DATA_DIRECTION_X),
				this.entityData.get(DATA_DIRECTION_Y),
				this.entityData.get(DATA_DIRECTION_Z));
	}

	/** How far it has flown (used by nothing but the debug output and the renderer's fading, if ever needed). */
	public double getTravelled() {
		return this.travelled;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_DIRECTION_X, 0.0F);
		entityData.define(DATA_DIRECTION_Y, 0.0F);
		entityData.define(DATA_DIRECTION_Z, 1.0F);
	}

	@Override
	public void tick() {
		super.tick();

		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}

		this.lifeTicks++;

		// Phase 2: stuck against a block. It does not move, does not collide and deals no more damage — it just
		// lingers for REST_TICKS so the player can see where the bolt ended, then dissipates.
		if (this.restTicks > 0) {
			this.restTicks--;
			this.setDeltaMovement(Vec3.ZERO);
			this.syncVelocity = true;

			if (this.restTicks <= 0) {
				this.discard();
			}

			return;
		}

		Vec3 direction = this.getFlightDirection();

		// A pillar with no direction (or one that is spent) simply goes away.
		if (direction.lengthSqr() < 1.0E-6D || this.travelled > MAX_DISTANCE || this.lifeTicks > HARD_LIFETIME_TICKS) {
			this.discard();
			return;
		}

		// Walk the path of this tick in small steps: at 12.8 blocks per tick a single position check would
		// teleport straight through a one block wall, and through a creature as well.
		double step = Math.min(PATH_SAMPLE_STEP, SPEED_PER_TICK);
		int steps = Math.max(1, (int) Math.ceil(SPEED_PER_TICK / step));
		double perStep = SPEED_PER_TICK / steps;

		for (int i = 0; i < steps; i++) {
			Vec3 next = this.position().add(direction.scale(perStep));
			this.travelled += perStep;

			// Solid blocks **stop** the bolt: it never breaks anything, it simply stays where it hit. Air, water
			// and plants are not "solid" by this test, so it still flies through a lake or tall grass.
			if (this.blocked(level, next)) {
				this.rest();
				return;
			}

			if (this.strike(level, next)) {
				this.discard();      // single target: it is spent on the creature it hits
				return;
			}

			this.setPos(next.x, next.y, next.z);
		}
	}

	/** Stops the bolt where it stands and leaves its model there for {@link #REST_TICKS} before dissipating. */
	private void rest() {
		this.restTicks = REST_TICKS;
		this.setDeltaMovement(Vec3.ZERO);
		this.syncVelocity = true;
	}

	/**
	 * Whether a solid block stands where the bolt is about to be.
	 *
	 * <p>The white pillar is harmless to the world by design: it neither destroys nor passes through terrain, so
	 * this only ever answers "should it stop here". (The black pillars are the ones that cut their way through.)
	 */
	private boolean blocked(ServerLevel level, Vec3 next) {
		BlockPos pos = BlockPos.containing(next);
		BlockState state = level.getBlockState(pos);

		return !state.getCollisionShape(level, pos).isEmpty();
	}

	/** Damage + mark the first creature in the way. Returns whether the pillar hit somebody and should stop. */
	private boolean strike(ServerLevel level, Vec3 next) {
		if (this.restTicks > 0) {
			return false;      // already stuck against a block: it neither damages nor blocks anything any more
		}

		AABB box = new AABB(
				next.x - THICKNESS / 2.0D, next.y - THICKNESS / 2.0D, next.z - THICKNESS / 2.0D,
				next.x + THICKNESS / 2.0D, next.y + THICKNESS / 2.0D, next.z + THICKNESS / 2.0D);

		List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, box.inflate(0.15D));

		for (LivingEntity target : hits) {
			if (target == this.owner || !target.isAlive() || !this.hitTargets.add(target.getUUID())) {
				continue;      // the shooter is immune, and each creature is hit once
			}

			DamageSource source = level.damageSources().source(ModDamageTypes.WHITE_BLOCK_STAR, this.owner);
			target.hurtServer(level, source, TOUCH_DAMAGE);

			// The white mark W: half of the pair that starts the decay effect.
			Marks.applyAndSync(target, Marks.WHITE);
			return true;
		}

		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		// noSave(): never written to the save file.
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;      // not attackable; it lives for a fraction of a second anyway
	}
}
