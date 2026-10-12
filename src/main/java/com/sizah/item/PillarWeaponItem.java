package com.sizah.item;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The pillar weapon: a left click in the main hand fires a black block pillar along the line of sight
 * (see learn/05 ~ 09).
 *
 * <p>The "Black Block Star" and the "Obsidian Cube" share this class, and every difference is
 * described by {@link PillarKind}: the pillar appearance (black block / obsidian), the touch damage
 * and whether armor is bypassed.
 *
 * <p>It also keeps two on-hit effects: apply darkness to the target, and deal bonus damage to a
 * target that is in darkness.
 * Note that **neither weapon uses the normal attack key** (the left button is taken over by the
 * skill), so these two effects only show up when an attack is triggered some other way.
 */
public class PillarWeaponItem extends Item {
	/** Duration of the darkness applied on hit (ticks, 20 ticks = 1 second). */
	private static final int DARKNESS_DURATION_TICKS = 100;

	/** Bonus damage against a target that is already in darkness. */
	private static final float DARKNESS_BONUS_DAMAGE = 2.0F;

	private final PillarKind kind;

	public PillarWeaponItem(PillarKind kind, Item.Properties properties) {
		super(properties);
		this.kind = kind;
	}

	/** Which kind of pillar this weapon fires. */
	public PillarKind kind() {
		return this.kind;
	}

	@Override
	public void hurtEnemy(ItemStack itemStack, LivingEntity target, LivingEntity attacker) {
		if (!attacker.level().isClientSide()) {
			target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS), attacker);
		}
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource damageSource) {
		return victim instanceof LivingEntity living && living.hasEffect(MobEffects.DARKNESS)
				? DARKNESS_BONUS_DAMAGE
				: 0.0F;
	}
}
