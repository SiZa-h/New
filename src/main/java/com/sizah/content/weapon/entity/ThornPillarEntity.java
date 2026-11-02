package com.sizah.content.weapon.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The **thorn white block pillar**: what gets pulled out of subspace once a creature has been hit by white
 * pillars thirty times in a row (see {@link WhitePillarStrikes}).
 *
 * <p>It is a {@link WhitePillarEntity} in everything but behaviour — same 0.1 x 5 shape, same damage, same mark —
 * so the visual and the size stay one implementation. What is different:
 *
 * <ul>
 *     <li><b>it does not disappear after attacking</b>: it keeps existing and keeps coming back;</li>
 *     <li><b>it never interacts with blocks</b>: it is not stopped by terrain, walls or bedrock;</li>
 *     <li><b>it wanders randomly</b> around the victim (a smooth random walk inside
 *         {@link #WANDER_RADIUS} blocks of it), and <b>every {@link #PIERCE_INTERVAL_TICKS} ticks (1 s) it dashes
 *         straight through the creature</b>, dealing white pillar damage as it passes.</li>
 * </ul>
 *
 * <p>It goes away {@link #IDLE_TIMEOUT_TICKS} ticks after the last time the victim was damaged by a white pillar
 * (see {@link WhitePillarStrikes}): as long as the white pillars keep landing, the thorns keep wandering; once they
 * stop landing, the summoned thorns fade two seconds later. Its own pierces deliberately do **not** refresh that
 * timer and do **not** count towards the storm — otherwise the storm would feed itself.
 */
public class ThornPillarEntity extends WhitePillarEntity {
	/** How long (ticks) it keeps wandering after the victim was last damaged by a white pillar: 2 seconds. */
	public static final int IDLE_TIMEOUT_TICKS = 40;

	/** Ticks between two pierces from the same thorn: 20 = **1 second**. */
	public static final int PIERCE_INTERVAL_TICKS = 20;

	/** How far from the victim's centre it is allowed to wander (blocks). */
	public static final double WANDER_RADIUS = 2.5D;

	/** Blocks it drifts per tick while wandering. */
	public static final double WANDER_STEP = 0.22D;

	/** Chance per tick of picking a new wandering direction (a smooth, not jittery, random walk). */
	public static final float WANDER_TURN_CHANCE = 0.07F;

	/** Blocks per tick while dashing through the victim (the pierce itself). */
	public static final double PIERCE_SPEED = 2.2D;

	/** How far the dash travels, in total (blocks): long enough to clearly cross the victim. */
	public static final double PIERCE_LENGTH = 4.0D;

	/** The creature this thorn was summoned for. */
	private LivingEntity target;

	/** Ticks left before it fades because the victim stopped taking white pillar damage. */
	private int idleTicks;

	/** Ticks left before the next pierce. */
	private int pierceTicks = PIERCE_INTERVAL_TICKS;

	/** Current random-walk direction. */
	private Vec3 wanderDir = Vec3.ZERO;

	/** True while the thorn is in the middle of a dash through the victim. */
	private boolean piercing;
	private Vec3 pierceDir = Vec3.ZERO;
	private double pierceTravelled;
	private boolean pierceHit;

	/** Slice of the victim's health the last hit took, kept for tooling and debugging. */
	private float lastHit;

	public ThornPillarEntity(EntityType<? extends ThornPillarEntity> type, Level level) {
		super(type, level);
	}

	/** Which creature it was summoned for. */
	public void setTarget(LivingEntity target) {
		this.target = target;
		this.idleTicks = IDLE_TIMEOUT_TICKS;
		this.pierceTicks = PIERCE_INTERVAL_TICKS;      // the first pierce is one second after the summon
	}

	public LivingEntity getTarget() {
		return this.target;
	}

	/** Called by the strike tracker whenever the victim takes white pillar damage: this is the "keep going" pulse. */
	public void refresh() {
		this.idleTicks = IDLE_TIMEOUT_TICKS;
	}

	@Override
	public void tick() {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}

		LivingEntity victim = this.target;

		// target gone, dead, unloaded or in another dimension: nothing left to hunt
		if (victim == null || victim.isRemoved() || !victim.isAlive() || victim.level() != level) {
			this.discard();
			return;
		}

		if (this.idleTicks-- <= 0) {
			this.discard();
			return;
		}

		Vec3 center = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);

		if (this.piercing) {
			this.tickPierce(level, victim, center);
		} else {
			this.tickWander(victim, center);
		}
	}

	/** Phase 1: a smooth random walk inside {@link #WANDER_RADIUS} of the victim, counting down to the next pierce. */
	private void tickWander(LivingEntity victim, Vec3 center) {
		if (--this.pierceTicks <= 0) {
			this.beginPierce(victim, center);
			return;
		}

		Vec3 fromCenter = this.position().subtract(center);

		// Pick a fresh random direction every now and then, and whenever the current one is unset.
		if (this.wanderDir.lengthSqr() < 1.0E-6D
				|| this.random.nextFloat() < WANDER_TURN_CHANCE
				|| fromCenter.length() > WANDER_RADIUS) {
			this.wanderDir = this.randomDirection();
		}

		// Keep it near the victim: past the radius, aim back instead of continuing outwards.
		if (fromCenter.length() > WANDER_RADIUS) {
			this.wanderDir = center.subtract(this.position()).normalize();
		}

		Vec3 next = this.position().add(this.wanderDir.scale(WANDER_STEP));

		// A hard clamp as well, so a fast-moving victim cannot leave the thorn behind.
		Vec3 offset = next.subtract(center);

		if (offset.length() > WANDER_RADIUS) {
			next = center.add(offset.normalize().scale(WANDER_RADIUS));
		}

		this.setFlightDirection(this.wanderDir);       // the bar points where it is heading
		this.setPos(next.x, next.y, next.z);
	}

	/** Phase 2: start a dash through the victim, from one side to the other. */
	private void beginPierce(LivingEntity victim, Vec3 center) {
		this.piercing = true;
		this.pierceDir = this.randomDirection();
		this.pierceTravelled = 0.0D;
		this.pierceHit = false;

		// Start half a length before the victim, so the dash clearly goes through it.
		Vec3 start = center.subtract(this.pierceDir.scale(PIERCE_LENGTH * 0.5D));
		this.setFlightDirection(this.pierceDir);
		this.setPos(start.x, start.y, start.z);
	}

	/** Phase 2: the dash itself — damage once as it crosses the creature, then go back to wandering. */
	private void tickPierce(ServerLevel level, LivingEntity victim, Vec3 center) {
		Vec3 next = this.position().add(this.pierceDir.scale(PIERCE_SPEED));
		this.pierceTravelled += PIERCE_SPEED;

		this.setFlightDirection(this.pierceDir);
		this.setPos(next.x, next.y, next.z);

		// Damage as it passes through the body, once per dash: either it is within a small radius of the centre,
		// or inside the (slightly enlarged) hitbox — the hitbox test covers a victim that stepped aside mid-dash.
		if (!this.pierceHit && (next.distanceToSqr(center) <= Mth.square(0.6D)
				|| victim.getBoundingBox().inflate(0.6D).contains(next))) {
			this.pierceHit = true;
			this.pierce(level, victim);
		}

		if (this.pierceTravelled >= PIERCE_LENGTH) {
			// The dash is over. If it somehow slipped past the body, pay the hit out anyway: the requirement is
			// "pierce the target again every second", so a second must never pass without damage.
			if (!this.pierceHit) {
				this.pierce(level, victim);
			}

			this.piercing = false;
			this.wanderDir = this.randomDirection();
			this.pierceTicks = PIERCE_INTERVAL_TICKS;    // one second of wandering, then again
		}
	}

	/** One pierce: white pillar damage, but **no storm credit** — a thorn must not count as a white pillar hit. */
	private void pierce(ServerLevel level, LivingEntity victim) {
		float before = victim.getHealth();
		WhitePillarEntity.damage(level, victim, this.owner);
		this.lastHit = before - victim.getHealth();
	}

	/** A unit vector in a uniformly random direction (3D). */
	private Vec3 randomDirection() {
		double theta = this.random.nextDouble() * Math.PI * 2.0D;
		double z = this.random.nextDouble() * 2.0D - 1.0D;
		double r = Math.sqrt(1.0D - z * z);
		return new Vec3(r * Math.cos(theta), r * Math.sin(theta), z);
	}

	/** The last pierce's damage, for tooling and debugging. */
	public float lastHit() {
		return this.lastHit;
	}
}
