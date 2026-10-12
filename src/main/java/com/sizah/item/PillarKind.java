package com.sizah.item;

import com.sizah.block.ModBlocks;
import com.sizah.damage.ModDamageTypes;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The kinds of pillar weapon.
 *
 * <p>Both weapons share the same "left click fires a black block pillar" logic (the same entity, the
 * same network packet, the same geometry, reverse extension, destruction at both ends and knockback),
 * and only three things differ, all of them gathered here:
 *
 * <ul>
 *     <li>{@link #block()}: which block the pillar is rendered as — this also decides the texture of
 *         the held model and of the entity model;</li>
 *     <li>{@link #touchDamage()}: the damage the pillar deals when it touches a mob;</li>
 *     <li>{@link #damageType()}: the damage type — **whether it bypasses armor depends entirely on
 *         whether that type is in the {@code bypasses_armor} tag**.</li>
 * </ul>
 */
public enum PillarKind {
	/** Black Block Star: black block appearance, 6 points of damage that **bypasses armor**. */
	BLACK_BLOCK(ModBlocks.BLACK_BLOCK, 6.0F, ModDamageTypes.BLACK_BLOCK_PILLAR),

	/** Obsidian Cube: obsidian appearance, 4 points of damage that **armor reduces**. */
	OBSIDIAN(Blocks.OBSIDIAN, 4.0F, ModDamageTypes.OBSIDIAN_CUBE);

	private final Block block;
	private final float touchDamage;
	private final ResourceKey<DamageType> damageType;

	PillarKind(Block block, float touchDamage, ResourceKey<DamageType> damageType) {
		this.block = block;
		this.touchDamage = touchDamage;
		this.damageType = damageType;
	}

	/** The block used by the pillar (and by the held item). */
	public Block block() {
		return this.block;
	}

	/** Damage when the pillar touches a mob. */
	public float touchDamage() {
		return this.touchDamage;
	}

	/** Damage type key: decides the death message and whether armor is bypassed. */
	public ResourceKey<DamageType> damageType() {
		return this.damageType;
	}

	/** Look a kind up by index (used for the entity sync field); out of range falls back to the Black Block Star. */
	public static PillarKind byId(int id) {
		PillarKind[] values = values();
		return id >= 0 && id < values.length ? values[id] : BLACK_BLOCK;
	}
}
