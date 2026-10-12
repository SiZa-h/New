package com.sizah.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * "Blank transparent axis": two **invisible** entities spawned **with the black hole as their midpoint**
 * when the long black block pillar and the short black block pillar collide and a black hole is created.
 *
 * <ul>
 *     <li>{@code new-mod:horizontal_axis}: **horizontal**, perpendicular to the player's view,
 *         {@link #LENGTH} blocks long;</li>
 *     <li>{@code new-mod:vertical_axis}: **vertical**, perpendicular to the player's view, {@link #LENGTH}
 *         blocks long.</li>
 * </ul>
 *
 * <p>It has **no functionality of its own** (invisible, deals no damage, breaks nothing); it merely gives
 * the later mouse gestures two **endpoints**: whichever endpoint the gesture hits is where the clash pillar
 * is spawned (see {@code ModNetworking}).
 *
 * <p>The orientation reuses the vanilla entity's yRot/xRot (set by whoever spawned it), so the "left / right
 * / top / bottom endpoint" only needs {@code position ± direction × half length}.
 */
public class BlankAxisEntity extends Entity {
	/** Axis length (blocks): the black hole is the midpoint, so the two endpoints sit at ±10 blocks. */
	public static final double LENGTH = 20.0D;

	/** Lifetime (ticks): leaves enough time to perform the "click + swipe" gesture. */
	public static final int LIFETIME_TICKS = 200;

	/** Who this axis belongs to (who created it by colliding); only they can use the gestures; server side only. */
	private @Nullable Entity owner;

	/** Who this axis follows (the creature that got mark 1); when it is {@code null} the axis just stays where it is. */
	private @Nullable Entity target;

	private int lifeTicks;

	public BlankAxisEntity(EntityType<? extends BlankAxisEntity> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	public void tick() {
		super.tick();

		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		// The axis **moves with the marked creature**: when it is pushed away or walks around on its own,
		// the two endpoints move with it, so the pillar created by the gesture is always based on its
		// current position.
		Entity victim = this.target;

		if (victim != null && !victim.isRemoved() && victim.isAlive() && victim.level() == serverLevel) {
			this.setPos(victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ());
		}

		if (++this.lifeTicks >= LIFETIME_TICKS) {
			this.discard();
		}
	}

	/** Endpoint coordinates.
	 *
	 * @param positive {@code true} takes the endpoint along the facing direction (for the horizontal axis,
	 *                 the "left" end agreed on at spawn time; for the vertical axis, the "top" end)
	 */
	public Vec3 endpoint(boolean positive) {
		double offset = positive ? LENGTH * 0.5D : -LENGTH * 0.5D;
		return this.position().add(this.getBeamDirection().scale(offset));
	}

	public void setOwner(@Nullable Entity owner) {
		this.owner = owner;
	}

	/** Makes it follow a creature (its position is aligned to that creature's body centre every tick). */
	public void setTarget(@Nullable Entity target) {
		this.target = target;
	}

	/** Whether this axis was created by {@code entity} colliding. */
	public boolean isOwnedBy(Entity entity) {
		return this.owner != null && this.owner == entity;
	}

	/** The axis orientation (unit vector): for the horizontal axis it is the player's left-hand direction, for the vertical axis the direction above the player's head. */
	public Vec3 getBeamDirection() {
		float yaw = this.getYRot() * ((float) Math.PI / 180.0F);
		float pitch = this.getXRot() * ((float) Math.PI / 180.0F);
		float cosPitch = Mth.cos(pitch);
		return new Vec3(-Mth.sin(yaw) * cosPitch, -Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		// No fields need syncing: the position and orientation use the vanilla entity's syncing.
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
		return false; // cannot be destroyed
	}
}
