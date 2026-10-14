package com.sizah.content.effect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Explosion helpers for the black block entities.
 *
 * <p>Requirement: "an explosion produced by a black block entity **gives no velocity**". A vanilla explosion
 * casually gives nearby creatures a kick (changing their velocity); here we first record everyone's velocity
 * before the explosion, and immediately **restore** it afterwards — so the damage and the block destruction are
 * unchanged, and only that "kick" is erased.
 *
 * <p>Why restore instead of zeroing: the target may currently be pushed along by a pillar (there are other
 * sources of velocity too), and zeroing would wipe those out as well.
 */
public final class Explosions {
	private Explosions() {
	}

	/** Explodes and settles as usual, but does not change any creature's velocity. */
	public static void explodeWithoutKnockback(
			ServerLevel level, Entity source, Vec3 center, float power, Level.ExplosionInteraction interaction
	) {
		// Cast the net a little wider: the explosion's area of effect is larger than power, and anyone missed still gets a kick
		double range = power + 2.0D;
		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
				new AABB(center, center).inflate(range));

		Map<Entity, Vec3> velocities = new HashMap<>();

		for (LivingEntity entity : nearby) {
			velocities.put(entity, entity.getDeltaMovement());
		}

		level.explode(source, center.x, center.y, center.z, power, interaction);

		for (Map.Entry<Entity, Vec3> entry : velocities.entrySet()) {
			Entity entity = entry.getKey();

			if (entity.isRemoved()) {
				continue;
			}

			entity.setDeltaMovement(entry.getValue());
			entity.syncVelocity = true;
		}
	}
}
