package com.sizah.content.weapon;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The pillar weapon: a left click in the main hand fires a black block pillar along the line of sight
 * (see learn/05 ~ 09).
 *
 * <p>The "Black Block Star" and the "Obsidian Cube" share this class, and every difference is
 * described by {@link PillarKind}: the pillar appearance (black block / obsidian), the touch damage
 * and whether armor is bypassed.
 *
 * <p>It also carries the passive a pillar applies to whatever it damages: the darkness effect, plus
 * {@link #PILLAR_BONUS_DAMAGE} extra damage — see {@link #applyPillarPassive}, which the pillar calls when it
 * hurts someone.
 */
public class PillarWeaponItem extends Item {
	/** Duration of the darkness applied on hit (ticks, 20 ticks = 1 second). */
	private static final int DARKNESS_DURATION_TICKS = 100;

	/** Extra damage the passive adds to **every** black block pillar hit. */
	public static final float PILLAR_BONUS_DAMAGE = 3.0F;

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

	/**
	 * What a **black block pillar** leaves on the creature it hits: the darkness effect, plus
	 * {@link #PILLAR_BONUS_DAMAGE} extra damage for that hit.
	 *
	 * <p>As specified: *"being damaged by a black block pillar applies the passive, and black block pillar damage
	 * is increased by 3"*. The extra damage is paid here, by the pillar itself
	 * ({@link com.sizah.content.weapon.entity.BlackBlockPillarEntity}), not by
	 * {@link #getAttackDamageBonus}: that hook only runs for a **melee attack with this item in hand**, which is
	 * something these weapons never do — their left button is the skill — so a bonus placed there would never
	 * trigger. This is also why the old "extra damage against a creature in darkness" passive was rewritten.
	 *
	 * @param attacker the shooting player, or {@code null} when the pillar has no owner
	 * @return the damage the pillar should deal
	 */
	public static float applyPillarPassive(LivingEntity target, @Nullable Entity attacker, float damage) {
		if (!target.level().isClientSide()) {
			// the darkness stays as the visible sign of the passive
			target.addEffect(
					new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS),
					attacker instanceof LivingEntity living ? living : null);
		}

		return damage + PILLAR_BONUS_DAMAGE;
	}
}
