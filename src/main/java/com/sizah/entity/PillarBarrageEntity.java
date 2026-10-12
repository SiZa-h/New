package com.sizah.entity;

import com.sizah.config.Settings;
import com.sizah.effect.Explosions;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Black block pillar barrage: triggered when the target **has all three marks** (mark 1 + 2 + 3) (see
 * {@code Marks#hasAll}).
 *
 * <p>It has two phases:
 *
 * <ol>
 *     <li><b>Barrage</b> (the first {@code DURATION_TICKS - FINALE_TICKS} ticks): it follows the target and
 *         every {@link #INTERVAL_TICKS} ticks fires one {@code barrage_pillar} from **all directions**;
 *         the directions cycle in groups of three and satisfy the angle constraints
 *         1↔2 ≥ {@link #MIN_ANGLE_1_2} and 1↔3 ≥ {@link #MIN_ANGLE_1_3}.</li>
 *     <li><b>Finale</b> (the last {@link #FINALE_TICKS} ticks): **holds the target in mid-air** →
 *         **simultaneously** spawns a ring of equally spaced pillars around it ({@link #FINALE_PILLARS}
 *         pillars, Fibonacci sphere distribution, all at the same distance so they **hit at the same
 *         moment**) → closes with one big explosion of {@link #FINALE_EXPLOSION_POWER}.</li>
 * </ol>
 *
 * <p>The "piercing" comes from "spawn distance &lt; pillar length": the pillar always spawns at a fixed
 * distance from the target, facing the target, and its length ({@code BARRAGE_LENGTH} = 20) is greater than
 * that distance, so it passes through the target and shoots out the other side.
 */
public class PillarBarrageEntity extends Entity {
	/** Duration (ticks): **40 seconds** (requirement "barrage time doubled"). */
	public static final int DURATION_TICKS = 800;

	/** Interval between two pillars (ticks): one every 0.15 seconds (about 133 pillars) — requirement "count doubled". */
	public static final int INTERVAL_TICKS = 3;

	/** How many ticks before the end the finale phase starts (1 second). */
	public static final int FINALE_TICKS = 20;

	/** Number of pillars in the finale volley: a whole ring spawned around the target at once (requirement "count doubled" → 28). */
	public static final int FINALE_PILLARS = 28;

	/** Spawn distance of the finale volley (blocks): every pillar is at the same distance, so they arrive together and hit together. */
	public static final double FINALE_DISTANCE = 12.0D;

	/** How many blocks above its original spot the target is raised to be "held in mid-air". */
	public static final double HOLD_HEIGHT = 2.5D;

	/** Power of the finale explosion (TNT is 4). */
	public static final float FINALE_EXPLOSION_POWER = 6.0F;

	/** Distance between the spawn point and the target (blocks): it must be smaller than the pillar length to pierce through. */
	public static final double SPAWN_DISTANCE = 8.0D;

	/** Minimum angle between the 1st and the 2nd pillar (degrees). */
	public static final double MIN_ANGLE_1_2 = 60.0D;

	/** Minimum angle between the 1st and the 3rd pillar (degrees). */
	public static final double MIN_ANGLE_1_3 = 100.0D;

	/** The 2nd and the 3rd pillar are also kept at least this far apart (purely to keep the two from overlapping). */
	public static final double MIN_ANGLE_2_3 = 60.0D;

	/** Upper bound on attempts when picking a random direction; past that the requirement is relaxed. */
	private static final int MAX_ATTEMPTS = 64;

	private @Nullable Entity target;

	/**
	 * The player who triggered this barrage (the owner). Every barrage pillar sets it as its
	 * **knockback anchor**, so a creature that gets hit is pushed all the way towards the owner —
	 * requirement "try to keep the creature's final landing spot near the player".
	 */
	private @Nullable Entity owner;

	/** The position where the finale phase presses the target down (raised into mid-air). */
	private @Nullable Vec3 holdPoint;

	/**
	 * **Barrage trigger point**: the target's position at the moment the barrage spawned.
	 *
	 * <p>The main phase lets the target be knocked all over the place by the barrage (requirement "it may
	 * fly around during this period"), but the finale **presses it back above this point**, so that "in the
	 * end the creature returns to near the barrage trigger point".
	 */
	private @Nullable Vec3 triggerPoint;

	private int lifeTicks;
	private int step;
	private Vec3 baseDirection = Vec3.ZERO;
	private Vec3 previousDirection = Vec3.ZERO;

	public PillarBarrageEntity(EntityType<? extends PillarBarrageEntity> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Sets the target to be hit; the barrage ends by itself when the target disappears or dies. */
	public void setTarget(Entity target) {
		this.target = target;
	}

	/** Sets the owner: the barrage pillars push the target towards them (so the landing spot ends up as close to the player as possible). */
	public void setOwner(Entity owner) {
		this.owner = owner;
	}

	@Override
	public void tick() {
		super.tick();
		this.lifeTicks++;

		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;      // only the server side spawns pillars
		}

		Entity victim = this.target;

		if (victim == null || victim.isRemoved() || !victim.isAlive() || victim.level() != serverLevel
				|| this.lifeTicks > DURATION_TICKS) {
			this.discard();
			return;
		}

		// Remember the barrage trigger point (the finale sends the target back here)
		if (this.triggerPoint == null) {
			this.triggerPoint = victim.position();
		}

		// Follow the target: while a black hole holds it in place it barely moves, and when it is pushed away the barrage chases it
		this.setPos(victim.getX(), victim.getY(), victim.getZ());

		// ── Finale: hold it in mid-air → spawn the ring around it at once → attack at once → explosion to close ──
		if (this.lifeTicks >= DURATION_TICKS - FINALE_TICKS) {
			this.tickFinale(serverLevel, victim);
			return;
		}

		if ((this.lifeTicks - 1) % INTERVAL_TICKS == 0) {
			this.fire(serverLevel, victim);
		}
	}

	/** Finale phase: raise it into mid-air and hold it there, spawn a ring around it at once (equal distance → simultaneous hits), then finish it off with one explosion. */
	private void tickFinale(ServerLevel level, Entity victim) {
		if (this.holdPoint == null) {
			// Finale: press the target back above the **barrage trigger point** (requirement "the creature ends up near the barrage trigger point")
			Vec3 anchor = this.triggerPoint != null ? this.triggerPoint : victim.position();
			this.holdPoint = anchor.add(0.0D, HOLD_HEIGHT, 0.0D);
			this.fireVolley(level, victim);
		}

		this.holdVictim(victim);

		if (this.lifeTicks >= DURATION_TICKS) {
			Vec3 center = this.holdPoint;
			Explosions.explodeWithoutKnockback(level, this, center,
					FINALE_EXPLOSION_POWER, Settings.explosionInteraction());
			this.discard();
		}
	}

	/** Barrage phase: spawns one pillar at a fixed distance outside the target, facing the target (so it pierces through). */
	private void fire(ServerLevel level, Entity victim) {
		Vec3 direction = this.nextDirection();
		Vec3 center = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
		this.spawnPillar(level, center.add(direction.scale(SPAWN_DISTANCE)), direction);
	}

	/** Finale volley: with the hold point as the centre, spawns pillars in a whole ring of **equally spaced** directions at once (same distance → simultaneous hits). */
	private void fireVolley(ServerLevel level, Entity victim) {
		Vec3 center = this.holdPoint.add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);

		for (int i = 0; i < FINALE_PILLARS; i++) {
			Vec3 direction = fibonacciDirection(i, FINALE_PILLARS);
			this.spawnPillar(level, center.add(direction.scale(FINALE_DISTANCE)), direction);
		}
	}

	/**
	 * Spawns one piercing pillar facing the centre; {@code outward} is the direction "from the centre to the
	 * spawn point".
	 *
	 * <p>Every pillar **no longer** pushes the target towards the owner (requirement: the main phase may let
	 * it fly around); only the owner is set, so they are neither hit nor pushed by the barrage themselves.
	 */
	private void spawnPillar(ServerLevel level, Vec3 spawn, Vec3 outward) {
		BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.BARRAGE_PILLAR, level);
		pillar.setBeamDirection(outward.scale(-1.0D));      // face the centre → pierce through from the spawn point
		pillar.setOwner(this.owner);                        // the owner is immune (neither the damage nor the knockback lands on them)
		pillar.setPos(spawn.x, spawn.y, spawn.z);
		level.addFreshEntity(pillar);
	}

	/** Presses the target into mid-air every tick: zero the velocity + teleport directly (players must go through the ServerPlayer overload). */
	private void holdVictim(Entity victim) {
		victim.setDeltaMovement(Vec3.ZERO);
		victim.syncVelocity = true;          // so the client sees it held in place too
		victim.fallDistance = 0.0D;          // do not let the lock cause fall damage

		if (victim instanceof ServerPlayer serverPlayer) {
			serverPlayer.teleportTo(this.holdPoint.x, this.holdPoint.y, this.holdPoint.z);
		} else {
			victim.teleportTo(this.holdPoint.x, this.holdPoint.y, this.holdPoint.z);
		}
	}

	/** Picks the direction of the next pillar: cycles in groups of three and satisfies the angle constraints. */
	private Vec3 nextDirection() {
		Vec3 direction;

		if (this.step == 0) {
			direction = this.randomUnitVector();
			this.baseDirection = direction;
		} else if (this.step == 1) {
			direction = this.randomWithMinimumAngle(this.baseDirection, MIN_ANGLE_1_2, null);
		} else {
			direction = this.randomWithMinimumAngle(this.baseDirection, MIN_ANGLE_1_3, this.previousDirection);
		}

		this.previousDirection = direction;
		this.step = (this.step + 1) % 3;
		return direction;
	}

	/**
	 * Picks a random direction that is at least {@code minDegrees} away from {@code from}; when
	 * {@code alsoSpread} is given it must also be {@link #MIN_ANGLE_2_3} away from it. Uses rejection
	 * sampling, and relaxes the requirement if several attempts still fail (better to overlap than not to
	 * fire at all).
	 */
	private Vec3 randomWithMinimumAngle(Vec3 from, double minDegrees, @Nullable Vec3 alsoSpread) {
		double minDot = Math.cos(Math.toRadians(minDegrees));
		double spreadDot = alsoSpread == null ? 1.0D : Math.cos(Math.toRadians(MIN_ANGLE_2_3));

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			Vec3 candidate = this.randomUnitVector();

			if (candidate.dot(from) <= minDot && (alsoSpread == null || candidate.dot(alsoSpread) <= spreadDot)) {
				return candidate;
			}
		}

		return this.randomUnitVector();
	}

	/** Picks a unit vector uniformly on the sphere (uniform z + uniform azimuth). */
	private Vec3 randomUnitVector() {
		double z = this.random.nextDouble() * 2.0D - 1.0D;
		double azimuth = this.random.nextDouble() * Math.PI * 2.0D;
		double radius = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
		return new Vec3(radius * Math.cos(azimuth), z, radius * Math.sin(azimuth));
	}

	/**
	 * Fibonacci sphere: spreads {@code count} directions over the sphere as evenly as possible.
	 *
	 * <p>The finale volley uses it, so that "spawned from all directions at once" is both even and
	 * symmetric — and because every direction is at the same distance, all the pillars reach the centre on
	 * the same tick and hit at the same moment.
	 */
	public static Vec3 fibonacciDirection(int index, int count) {
		double y = 1.0D - 2.0D * (index + 0.5D) / count;
		double radius = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
		double theta = Math.PI * (1.0D + Math.sqrt(5.0D)) * index;
		return new Vec3(Math.cos(theta) * radius, y, Math.sin(theta) * radius);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		// No fields need syncing: the position uses vanilla syncing and the target is only used on the server side.
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		// EntityType.Builder#noSave(): not written to the save file.
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;      // cannot be destroyed
	}
}
