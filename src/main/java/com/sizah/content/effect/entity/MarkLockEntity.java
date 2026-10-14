package com.sizah.content.effect.entity;

import com.sizah.content.effect.entity.PillarBarrageEntity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mark lock: the side effect of **mark 1** — **clears all directional velocity of the marked creature for
 * 5 seconds**.
 *
 * <p>Note that the requirement only says "clear the velocity", so this does **not hold the position**: every
 * tick it only does three things
 *
 * <ol>
 *     <li>set the velocity to 0 (and {@code syncVelocity}, so the client can see it "held still");</li>
 *     <li>clear the fall distance, so that it does not take fall damage from accumulated falling when the
 *         lock ends;</li>
 *     <li>stick itself onto the creature (purely so that /kill is easy to find while debugging; it does not
 *         affect the creature's position).</li>
 * </ol>
 *
 * <p>It lives alongside the target for {@link #LOCK_TICKS} ticks and then ends by itself; if the target dies
 * or changes dimension it stops immediately too. To upgrade this into a "black hole style" hold (pinning the
 * position as well, which is what the barrage finale does), add two teleport lines following
 * {@code holdVictim} in {@link PillarBarrageEntity}.
 */
public class MarkLockEntity extends Entity {
	/** Lock duration (ticks): 5 seconds. */
	public static final int LOCK_TICKS = 100;

	private @Nullable Entity target;

	private int lifeTicks;

	public MarkLockEntity(EntityType<? extends MarkLockEntity> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Sets the creature to lock. */
	public void setTarget(Entity target) {
		this.target = target;
	}

	@Override
	public void tick() {
		super.tick();
		this.lifeTicks++;

		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;      // only the server side can control the velocity
		}

		Entity victim = this.target;

		if (victim == null || victim.isRemoved() || !victim.isAlive() || victim.level() != serverLevel
				|| this.lifeTicks > LOCK_TICKS) {
			this.discard();
			return;
		}

		this.setPos(victim.getX(), victim.getY(), victim.getZ());

		// "Clear all directional velocity": zero it and sync every tick, so that the client sees it held still
		victim.setDeltaMovement(Vec3.ZERO);
		victim.syncVelocity = true;
		victim.fallDistance = 0.0D;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		// No fields need syncing: the lock itself is a server-side behaviour and the client does not need to know about it.
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
		return false;      // cannot be destroyed; the only way out is to wait 5 seconds or use /kill
	}
}
