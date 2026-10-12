package com.sizah.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
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

/**
 * Black hole entity: spawned at the collision point when **behaviour 1 + behaviour 2** (left-click short
 * pillar × right-click long pillar) collide.
 *
 * <p>It does two things:
 *
 * <ol>
 *     <li><b>Lock (press back into position)</b>: every tick it presses the creatures "caught in the
 *         explosion" (the batch handed to it on spawn) onto the centre of the explosion — the velocity is
 *         zeroed + they are **teleported straight back to the centre** — so they can neither run away nor be
 *         dragged down by gravity;</li>
 *     <li><b>Manifestation</b>: for now it is represented by a ring of particles contracting inwards (no
 *         model has been made), and it dissipates after {@link #LIFETIME_TICKS} ticks (1 second), at which
 *         point the locked creatures are free again.</li>
 * </ol>
 *
 * <p>When two clash pillars (behaviours 3–6) hit each other they **only explode and spawn no black hole**;
 * the barrage finale explosion spawns none either.
 *
 * <p>There is no permanent lock: the entity is {@code noSave()}, so it vanishes when the server restarts and
 * a captured creature cannot be left corrupted.
 */
public class BlackHoleEntity extends Entity {
	/** Lifetime (ticks): dissipates after **1 second**, and the locked creatures are freed. */
	public static final int LIFETIME_TICKS = 20;

	/** The locked creatures (a short-lived entity, so holding direct references is enough). */
	private final List<Entity> captured = new ArrayList<>();

	private int lifeTicks;

	public BlackHoleEntity(EntityType<? extends BlackHoleEntity> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Receives the creatures caught in the explosion and hands them to this black hole to lock. */
	public void capture(List<? extends Entity> victims) {
		this.captured.addAll(victims);
	}

	@Override
	public void tick() {
		super.tick();
		this.lifeTicks++;

		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (this.lifeTicks >= LIFETIME_TICKS) {
			this.discard();      // dissipate: let go, the creatures are free again
			return;
		}

		this.emitParticles(serverLevel);
		this.holdCaptured();
	}

	/** Visual effect: a ring of particles contracting inwards (the model is left for later). */
	private void emitParticles(ServerLevel level) {
		level.sendParticles(ParticleTypes.PORTAL,
				this.getX(), this.getY() + 0.5D, this.getZ(), 12, 0.6D, 0.6D, 0.6D, 0.6D);
		level.sendParticles(ParticleTypes.SMOKE,
				this.getX(), this.getY() + 0.5D, this.getZ(), 6, 0.4D, 0.4D, 0.4D, 0.01D);
	}

	/** Presses the captured creatures onto the centre every tick: zero the velocity + teleport. */
	private void holdCaptured() {
		this.captured.removeIf(entity -> entity == null
				|| entity.isRemoved()
				|| !entity.isAlive()
				|| entity.level() != this.level());

		for (Entity entity : this.captured) {
			entity.setDeltaMovement(Vec3.ZERO);
			entity.syncVelocity = true;          // so the client sees it held in place too
			entity.fallDistance = 0.0D;          // do not let the lock cause fall damage

			// Players must go through the ServerPlayer overload, otherwise the client never receives the teleport packet (and rubber-bands them back).
			if (entity instanceof ServerPlayer serverPlayer) {
				serverPlayer.teleportTo(this.getX(), this.getY(), this.getZ());
			} else {
				entity.teleportTo(this.getX(), this.getY(), this.getZ());
			}
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		// No fields need syncing: the centre is simply the entity's own position, and only the server side cares about the lock list.
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
		return false; // cannot be destroyed by attacks
	}
}
