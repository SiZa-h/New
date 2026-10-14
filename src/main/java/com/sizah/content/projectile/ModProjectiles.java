package com.sizah.content.projectile;

/**
 * Placeholder for **projectiles and self-propelled spell entities** — the same family as this mod's pillars.
 *
 * <p>{@code com.sizah.content.weapon.entity.BlackBlockPillarEntity} is the reference implementation for a
 * "hitscan-ish" entity: it extends {@code Entity} directly (not {@code Projectile}), carries its differences in
 * the entity type's spec, syncs only what the renderer needs, and does all of its work on the server.
 *
 * <p>When adding a real thrown/fired projectile, the useful pieces to copy from it are:
 *
 * <ul>
 *     <li>a spec record per entity type (see {@code ModEntities.PillarSpec}) instead of one class per variant;</li>
 *     <li>{@code SynchedEntityData} only for what the client must draw, everything else server-side;</li>
 *     <li>damage through {@code com.sizah.common.damage.ModDamageTypes} so the death message is localised;</li>
 *     <li>knockback through {@code com.sizah.content.effect.PushConflicts} if two sources may hit at once.</li>
 * </ul>
 */
public final class ModProjectiles {
	private ModProjectiles() {
	}

	/** Registers every projectile entity type. Called from {@code NewMod#onInitialize} (entity types register before items that spawn them). */
	public static void register() {
		// TODO: register the first projectile entity type here (and its renderer in NewModClient).
	}
}
