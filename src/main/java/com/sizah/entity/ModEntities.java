package com.sizah.entity;

import com.sizah.NewMod;
import com.sizah.effect.Marks;
import com.sizah.item.PillarKind;

import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity type table.
 *
 * <p>All six pillar kinds share the same {@link BlackBlockPillarEntity} class, and every difference is
 * described by {@link PillarSpec}: the appearance block and the damage ({@link PillarKind}), the maximum
 * length, the speed, whether it dissipates, whether it destroys blocks along the path, whether it can knock
 * back the owner, the collision group and collision mark, and whether it is "full length on spawn + fill
 * from tip to base". Because the kind is carried by the **entity type itself**, the entity needs no extra
 * synced field, and the client can work out on its own which block to render.
 *
 * <table>
 *     <caption>Type overview</caption>
 *     <tr><th>ID</th><th>Name</th><th>Status</th></tr>
 *     <tr><td>{@code short_black_block_pillar}</td><td>Short black block pillar</td><td>Fired by the Black Block Star with <b>left click</b></td></tr>
 *     <tr><td>{@code obsidian_pillar}</td><td>Obsidian pillar</td><td>Fired by the Obsidian Block with <b>left click</b></td></tr>
 *     <tr><td>{@code long_black_block_pillar}</td><td>Long black block pillar</td><td>Fired by the Black Block Star with <b>right click</b> (full length on spawn, fills from tip to base)</td></tr>
 *     <tr><td>{@code eternal_black_block_pillar}</td><td>Eternal black block pillar</td><td><b>Unused/backup</b>: does not dissipate once fully grown</td></tr>
 *     <tr><td>{@code horizontal_axis_pillar}</td><td>Horizontal clash pillar</td><td>Gesture: right click + horizontal swipe (20 blocks, destroys blocks along the path)</td></tr>
 *     <tr><td>{@code vertical_axis_pillar}</td><td>Vertical clash pillar</td><td>Gesture: left click + vertical swipe (20 blocks, destroys blocks along the path)</td></tr>
 *     <tr><td>{@code horizontal_axis} / {@code vertical_axis}</td><td>Blank transparent axis</td><td>Spawned alongside a black hole; only provides the endpoints</td></tr>
 *     <tr><td>{@code black_hole}</td><td>Black hole</td><td><b>Unused/backup</b>: no longer spawned by collisions, only via /summon</td></tr>
 *     <tr><td>{@code mark_lock}</td><td>Mark lock</td><td>Spawned when mark 1 lands; clears velocity for 5 seconds</td></tr>
 * </table>
 */
public final class ModEntities {
	/**
	 * Every difference between one pillar kind and another.
	 *
	 * @param collisionGroup    {@code -1} = does not take part in collisions; {@code 0} = long pillar
	 *                          (hitting any other pillar → black hole + mark 1);
	 *                          {@code 2} = horizontal pair; {@code 3} = vertical pair (they collide only
	 *                          when they are in the same group and face opposite directions);
	 *                          {@code -2} = takes no part in collisions at all (barrage pillar)
	 * @param collisionMark     the mark number applied to creatures in range after a collision explosion
	 *                          (see {@link Marks})
	 * @param breaksUnbreakable whether it can destroy "unbreakable blocks" (bedrock and the like,
	 *                          {@code getDestroySpeed < 0}).
	 *                          **Behaviour 1 (left-click short pillar, including the obsidian block) stays
	 *                          {@code false}**; every other behaviour is {@code true}
	 * @param knockbackScale    knockback scale: {@code 0.5} for the barrage pillar (requirement "barrage
	 *                          knockback effect halved"), {@code 1.0} for everything else
	 * @param damageScale       touch damage scale: {@code 0.5} for the barrage pillar (requirement
	 *                          "barrage damage reduced"), {@code 1.0} for everything else
	 */
	public record PillarSpec(
			PillarKind kind,
			double maxLength,
			double speedPerTick,
			boolean dissipates,
			boolean destroysPath,
			boolean knocksBackOwner,
			int collisionGroup,
			int collisionMark,
			boolean fillsFromTip,
			boolean breaksUnbreakable,
			double knockbackScale,
			double damageScale
	) {
	}

	/** Standard length (blocks). */
	public static final double SHORT_LENGTH = 30.0D;

	/** Length of the long black block pillar (blocks). */
	public static final double LONG_LENGTH = 60.0D;

	/** Speed of the long black block pillar: 60 blocks/second. */
	public static final double LONG_SPEED_PER_TICK = 3.0D;

	/** Length of the clash pillar (blocks): it grows from an endpoint of the blank axis towards the other end. */
	public static final double PAIR_PILLAR_LENGTH = 20.0D;

	/** Length of the barrage pillar (blocks): it must be far greater than the spawn distance for the whole pillar to "pierce" through. */
	public static final double BARRAGE_LENGTH = 60.0D;

	/** Speed of the barrage pillar: 240 blocks/second (12 blocks/tick) — requirement "speed doubled". */
	public static final double BARRAGE_SPEED_PER_TICK = 12.0D;

	/** Length of the dash pillar (blocks): used by sneak + right click, it pushes the player forward from behind the whole way. */
	public static final double DASH_LENGTH = 60.0D;

	/** Speed of the dash pillar: 60 blocks/second (3 blocks/tick). */
	public static final double DASH_SPEED_PER_TICK = 3.0D;

	/** Collision group: does not actively check for collisions, but **can be hit by the long pillar** (regular pillar). */
	public static final int GROUP_NONE = -1;

	/** Collision group: long black block pillar — hitting any "collidable" pillar → black hole + mark 1. */
	public static final int GROUP_LONG = 0;

	/** Collision group: horizontal pair (they collide with each other only when in the same group and facing opposite directions → explosion only + mark 2). */
	public static final int GROUP_HORIZONTAL = 2;

	/** Collision group: vertical pair (they collide with each other only when in the same group and facing opposite directions → explosion only + mark 3). */
	public static final int GROUP_VERTICAL = 3;

	/** Collision group: **takes no part in collisions at all** (the barrage pillar uses it so barrages do not interfere with one another). */
	public static final int GROUP_IMMUNE = -2;

	public static final ResourceKey<EntityType<?>> SHORT_BLACK_BLOCK_PILLAR_KEY = key("short_black_block_pillar");
	public static final ResourceKey<EntityType<?>> OBSIDIAN_PILLAR_KEY = key("obsidian_pillar");
	public static final ResourceKey<EntityType<?>> LONG_BLACK_BLOCK_PILLAR_KEY = key("long_black_block_pillar");
	public static final ResourceKey<EntityType<?>> ETERNAL_BLACK_BLOCK_PILLAR_KEY = key("eternal_black_block_pillar");
	public static final ResourceKey<EntityType<?>> HORIZONTAL_AXIS_PILLAR_KEY = key("horizontal_axis_pillar");
	public static final ResourceKey<EntityType<?>> VERTICAL_AXIS_PILLAR_KEY = key("vertical_axis_pillar");
	public static final ResourceKey<EntityType<?>> BARRAGE_PILLAR_KEY = key("barrage_pillar");

	/** Dash pillar: spawned behind the player on sneak + right click, pushing the player out along the view direction. */
	public static final ResourceKey<EntityType<?>> DASH_PILLAR_KEY = key("dash_pillar");

	/** Black block pillar barrage: triggered once all three marks are collected, lasting 10 seconds. */
	public static final ResourceKey<EntityType<?>> PILLAR_BARRAGE_KEY = key("pillar_barrage");

	/** Mark lock: the side effect of mark 1 — clears all directional velocity of the marked creature for 5 seconds. */
	public static final ResourceKey<EntityType<?>> MARK_LOCK_KEY = key("mark_lock");

	/** Black hole: spawned at the collision point when the long black block pillar and the short black block pillar collide. */
	public static final ResourceKey<EntityType<?>> BLACK_HOLE_KEY = key("black_hole");

	/** Horizontal / vertical blank transparent axis: spawned alongside a black hole, with the black hole as its midpoint. */
	public static final ResourceKey<EntityType<?>> HORIZONTAL_AXIS_KEY = key("horizontal_axis");
	public static final ResourceKey<EntityType<?>> VERTICAL_AXIS_KEY = key("vertical_axis");

	/** Pillar fired by the Black Block Star with left click: black block appearance, 30 blocks, dissipates after 1 second. */
	public static final EntityType<BlackBlockPillarEntity> SHORT_BLACK_BLOCK_PILLAR =
			build(SHORT_BLACK_BLOCK_PILLAR_KEY, true);

	/** Pillar fired by the Obsidian Block with left click: obsidian appearance, lower damage, and it does not ignore armour. */
	public static final EntityType<BlackBlockPillarEntity> OBSIDIAN_PILLAR =
			build(OBSIDIAN_PILLAR_KEY, true);

	/**
	 * Pillar fired by the Black Block Star with right click: **full length on spawn** (60 blocks,
	 * transparent, no functionality at all), after which the black blocks and the functionality are filled in
	 * together **from tip to base** (`fillsFromTip`).
	 */
	public static final EntityType<BlackBlockPillarEntity> LONG_BLACK_BLOCK_PILLAR =
			build(LONG_BLACK_BLOCK_PILLAR_KEY, true);

	/** Unused/backup: a black block pillar that does not dissipate once fully grown (can be spawned with /summon). */
	public static final EntityType<BlackBlockPillarEntity> ETERNAL_BLACK_BLOCK_PILLAR =
			build(ETERNAL_BLACK_BLOCK_PILLAR_KEY, false);

	/** Horizontal clash pillar: spawned at an endpoint of the horizontal blank axis, growing 20 blocks towards the other end, and **destroys blocks along the path** (can be tried with /summon). */
	public static final EntityType<BlackBlockPillarEntity> HORIZONTAL_AXIS_PILLAR =
			build(HORIZONTAL_AXIS_PILLAR_KEY, false);

	/** Vertical clash pillar: spawned at an endpoint of the vertical blank axis, growing 20 blocks towards the other end, and **destroys blocks along the path** (can be tried with /summon). */
	public static final EntityType<BlackBlockPillarEntity> VERTICAL_AXIS_PILLAR =
			build(VERTICAL_AXIS_PILLAR_KEY, false);

	/** Barrage pillar: spawned from all directions by the barrage once all three marks are collected, **piercing** the target and dealing damage (can be tried with /summon). */
	public static final EntityType<BlackBlockPillarEntity> BARRAGE_PILLAR =
			build(BARRAGE_PILLAR_KEY, false);

	/** Dash pillar: spawned behind the player on sneak + right click, pushing the player out along the view direction (can be tried with /summon). */
	public static final EntityType<BlackBlockPillarEntity> DASH_PILLAR =
			build(DASH_PILLAR_KEY, false);

	/** Black block pillar barrage: follows the target for 10 seconds (20 seconds in practice, see {@link PillarBarrageEntity#DURATION_TICKS}), one pillar every 0.3 seconds. */
	public static final EntityType<PillarBarrageEntity> PILLAR_BARRAGE = EntityType.Builder
			.of(PillarBarrageEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.clientTrackingRange(12)
			.updateInterval(1)
			.noSave()
			.build(PILLAR_BARRAGE_KEY);

	/** Mark lock: spawned when mark 1 is applied, holding the creature in place for 5 seconds. */
	public static final EntityType<MarkLockEntity> MARK_LOCK = EntityType.Builder
			.of(MarkLockEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.clientTrackingRange(12)
			.updateInterval(1)
			.noSave()
			.build(MARK_LOCK_KEY);

	/** Black hole entity: locks the creatures caught in the explosion and dissipates after 5 seconds (can be spawned with /summon to try on its own). */
	public static final EntityType<BlackHoleEntity> BLACK_HOLE = EntityType.Builder
			.of(BlackHoleEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.noSave()
			.build(BLACK_HOLE_KEY);

	/** Horizontal blank transparent axis: perpendicular to the player's view, 20 blocks. */
	public static final EntityType<BlankAxisEntity> HORIZONTAL_AXIS = buildAxis(HORIZONTAL_AXIS_KEY);

	/** Vertical blank transparent axis: perpendicular to the player's view, 20 blocks. */
	public static final EntityType<BlankAxisEntity> VERTICAL_AXIS = buildAxis(VERTICAL_AXIS_KEY);

	private static final Map<EntityType<?>, PillarSpec> SPECS = Map.of(
			// Behaviour 1 (left-click short pillar): the only type that keeps "bedrock is unbreakable"
			SHORT_BLACK_BLOCK_PILLAR,
			new PillarSpec(PillarKind.BLACK_BLOCK, SHORT_LENGTH, BlackBlockPillarEntity.SPEED_PER_TICK,
					true, false, false, GROUP_NONE, Marks.NONE, false, false, 1.0D, 1.0D),
			// The obsidian block goes through the same "behaviour 1", so it keeps the bedrock protection too
			OBSIDIAN_PILLAR,
			new PillarSpec(PillarKind.OBSIDIAN, SHORT_LENGTH, BlackBlockPillarEntity.SPEED_PER_TICK,
					true, false, false, GROUP_NONE, Marks.NONE, false, false, 1.0D, 1.0D),
			// Every other behaviour: breaks bedrock as well
			LONG_BLACK_BLOCK_PILLAR,
			new PillarSpec(PillarKind.BLACK_BLOCK, LONG_LENGTH, LONG_SPEED_PER_TICK,
					true, true, true, GROUP_LONG, Marks.PILLAR_CLASH, true, true, 1.0D, 1.0D),
			ETERNAL_BLACK_BLOCK_PILLAR,
			new PillarSpec(PillarKind.BLACK_BLOCK, SHORT_LENGTH, BlackBlockPillarEntity.SPEED_PER_TICK,
					false, false, false, GROUP_NONE, Marks.NONE, false, true, 1.0D, 1.0D),
			HORIZONTAL_AXIS_PILLAR,
			new PillarSpec(PillarKind.BLACK_BLOCK, PAIR_PILLAR_LENGTH, BlackBlockPillarEntity.SPEED_PER_TICK,
					true, true, false, GROUP_HORIZONTAL, Marks.HORIZONTAL_PAIR, false, true, 1.0D, 1.0D),
			VERTICAL_AXIS_PILLAR,
			new PillarSpec(PillarKind.BLACK_BLOCK, PAIR_PILLAR_LENGTH, BlackBlockPillarEntity.SPEED_PER_TICK,
					true, true, false, GROUP_VERTICAL, Marks.VERTICAL_PAIR, false, true, 1.0D, 1.0D),
			// Barrage pillar: half knockback (both the push force and the distance shrink) and half damage
			BARRAGE_PILLAR,
			new PillarSpec(PillarKind.BLACK_BLOCK, BARRAGE_LENGTH, BARRAGE_SPEED_PER_TICK,
					true, true, false, GROUP_IMMUNE, Marks.NONE, false, true, 0.5D, 0.5D),
			// Dash pillar: spawned behind the player and pushes them out along the view direction; takes part in no collisions
			DASH_PILLAR,
			new PillarSpec(PillarKind.BLACK_BLOCK, DASH_LENGTH, DASH_SPEED_PER_TICK,
					true, true, true, GROUP_IMMUNE, Marks.NONE, false, true, 1.0D, 1.0D)
	);

	private ModEntities() {
	}

	/** Returns the pillar type that **left click** fires for the given weapon kind. */
	public static EntityType<BlackBlockPillarEntity> typeFor(PillarKind kind) {
		return kind == PillarKind.OBSIDIAN ? OBSIDIAN_PILLAR : SHORT_BLACK_BLOCK_PILLAR;
	}

	/** Returns the difference table of a pillar type; unknown types are treated as a regular pillar. */
	public static PillarSpec specOf(EntityType<?> type) {
		PillarSpec spec = SPECS.get(type);

		if (spec != null) {
			return spec;
		}

		return SPECS.get(SHORT_BLACK_BLOCK_PILLAR);
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, SHORT_BLACK_BLOCK_PILLAR_KEY, SHORT_BLACK_BLOCK_PILLAR);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, OBSIDIAN_PILLAR_KEY, OBSIDIAN_PILLAR);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, LONG_BLACK_BLOCK_PILLAR_KEY, LONG_BLACK_BLOCK_PILLAR);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, ETERNAL_BLACK_BLOCK_PILLAR_KEY, ETERNAL_BLACK_BLOCK_PILLAR);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, HORIZONTAL_AXIS_PILLAR_KEY, HORIZONTAL_AXIS_PILLAR);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, VERTICAL_AXIS_PILLAR_KEY, VERTICAL_AXIS_PILLAR);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, BARRAGE_PILLAR_KEY, BARRAGE_PILLAR);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, DASH_PILLAR_KEY, DASH_PILLAR);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, PILLAR_BARRAGE_KEY, PILLAR_BARRAGE);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, MARK_LOCK_KEY, MARK_LOCK);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, BLACK_HOLE_KEY, BLACK_HOLE);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, HORIZONTAL_AXIS_KEY, HORIZONTAL_AXIS);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, VERTICAL_AXIS_KEY, VERTICAL_AXIS);
	}

	private static ResourceKey<EntityType<?>> key(String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE, NewMod.id(path));
	}

	/**
	 * @param noSummon whether /summon is forbidden: the types fired by weapons forbid it (so that nobody can
	 *                 spawn piles of pillars); the reserved "backup" types allow it, so the effect can be
	 *                 tried on its own first
	 */
	private static EntityType<BlackBlockPillarEntity> build(ResourceKey<EntityType<?>> key, boolean noSummon) {
		EntityType.Builder<BlackBlockPillarEntity> builder = EntityType.Builder
				.of(BlackBlockPillarEntity::new, MobCategory.MISC)
				.sized(1.0F, 1.0F)
				.clientTrackingRange(12)
				.updateInterval(1)
				.noSave();

		if (noSummon) {
			builder = builder.noSummon();
		}

		return builder.build(key);
	}

	/** Blank transparent axis: invisible and with no functionality; only its position and orientation need to sync to the client (which does not render it either, in fact). */
	private static EntityType<BlankAxisEntity> buildAxis(ResourceKey<EntityType<?>> key) {
		return EntityType.Builder
				.of(BlankAxisEntity::new, MobCategory.MISC)
				.sized(0.5F, 0.5F)
				.clientTrackingRange(12)
				.updateInterval(1)
				.noSave()
				.build(key);
	}
}
