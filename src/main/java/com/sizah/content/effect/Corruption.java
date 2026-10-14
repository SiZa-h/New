package com.sizah.content.effect;

import com.sizah.content.block.ModBlocks;
import com.sizah.common.config.Settings;
import com.sizah.content.item.ModItems;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Infection": sneaking and left-clicking turns a patch of blocks in front of the player into
 * **black blocks** (the black block star) or **white blocks** (the white block star), and **releasing sneak**
 * settles them into drops.
 *
 * <p>The block it paints with is a parameter of {@link #start}, not a constant, so both weapons share this class:
 * the black star passes {@code ModBlocks.BLACK_BLOCK} and the white star {@code ModBlocks.WHITE_BLOCK}. The drop
 * at the end follows the same parameter, so an infection with white blocks drops white blocks.
 *
 * <p>Rules (as required):
 *
 * <ol>
 *     <li>**The first block is infected instantly on the press** (starting from the block the crosshair points at);</li>
 *     <li>afterwards, **while sneak is held**, it spreads to the surrounding area at an
 *         **observable speed** — the speed
 *         <b>{@code V = (1 - 0.1 × n) × 5}</b> blocks/second, where {@code n} is the **number of blocks already infected**:
 *         at n=0 it is 5 blocks/second, at n=5 it is 2.5 blocks/second, and **at n ≥ 10, V ≤ 0, so the spread stops on its own**;</li>
 *     <li>the spread is a **random walk** (each step moves to a random neighbouring block), and it always stays inside a
 *         sphere of radius {@link #RADIUS} blocks centred on the first block;</li>
 *     <li>when the player **releases sneak** it settles: **everything drops** — the infected blocks themselves disappear and drop
 *         their **original drops + one black block item**.</li>
 * </ol>
 *
 * <p>"Bedrock and other unbreakable blocks" is handled the same way as elsewhere: it follows the "bedrock" switch on the
 * settings screen — while it is off, they are not infected.
 * Quitting the game also settles immediately (so we do not leave a map full of black blocks behind).
 */
public final class Corruption {
	/** Spread radius (blocks): always centred on the first block. */
	public static final int RADIUS = 50;

	/** Base spread speed (blocks/second): the 5 in the formula. */
	public static final double BASE_SPEED_PER_SECOND = 50.0D;

	/** How much the speed falls off per extra infected block (the 0.01 in the formula, in blocks/second per block). */
	public static final double SPEED_DECAY_PER_BLOCK = 0.01D;

	/** The six neighbouring directions. */
	private static final Direction[] NEIGHBOURS = {
			Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
	};

	/** One player's infection state: the block it paints with, where it started, and how far it has spread. */
	private static final class Infection {
		private final BlockPos origin;
		private final Block target;
		private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		private BlockPos current;
		private double progress;

		private Infection(BlockPos origin, Block target) {
			this.origin = origin;
			this.target = target;
			this.current = origin;
		}
	}

	private static final Map<UUID, Infection> INFECTIONS = new HashMap<>();

	private Corruption() {
	}

	/** Current speed V = max(0, (1 - 0.1n) × 5), in blocks/second. */
	public static double speedPerSecond(int infected) {
		return Math.max(0.0D, (1.0D - SPEED_DECAY_PER_BLOCK * infected) * BASE_SPEED_PER_SECOND);
	}

	/**
	 * Called on press: **instantly** infects the first block and starts "keep spreading while held".
	 *
	 * @return whether a block was actually infected (if nothing was infected, the cooldown should not be consumed)
	 */
	public static boolean start(ServerPlayer player, BlockPos start, Block target) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}

		finish(player);      // settle any previous infection first, so two records never fight each other

		Infection infection = new Infection(start.immutable(), target);
		INFECTIONS.put(player.getUUID(), infection);

		return infectAt(level, infection, infection.current) > 0;
	}

	/** Called every tick: advances the spread while sneaking, and settles once sneak is released. */
	public static void tick(ServerPlayer player) {
		Infection infection = INFECTIONS.get(player.getUUID());

		if (infection == null) {
			return;
		}

		if (!player.isShiftKeyDown()) {
			finish(player);
			return;
		}

		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		double speed = speedPerSecond(infection.blocks.size());

		if (speed <= 0.0D) {
			return;      // n ≥ 10: V ≤ 0, so the spread stops on its own
		}

		// The speed is in blocks/second but this runs once per tick: accumulate until one block is due, then step
		infection.progress += speed / 20.0D;

		while (infection.progress >= 1.0D) {
			infection.progress -= 1.0D;
			spread(player, level, infection);
		}
	}

	/** One random step: try the current block first, then pick a random neighbour (stepping outside the radius falls back to the origin). */
	private static void spread(ServerPlayer player, ServerLevel level, Infection infection) {
		infectAt(level, infection, infection.current);

		BlockPos next = infection.current.relative(
				NEIGHBOURS[player.getRandom().nextInt(NEIGHBOURS.length)]);
		int radiusSqr = RADIUS * RADIUS;

		infection.current = next.distSqr(infection.origin) > radiusSqr ? infection.origin : next;
	}

	/** Try to infect a single block: if it works, record it and replace it with a black block, returning 1 (otherwise 0). */
	private static int infectAt(ServerLevel level, Infection infection, BlockPos pos) {
		int radiusSqr = RADIUS * RADIUS;

		if (pos.distSqr(infection.origin) > radiusSqr || infection.blocks.containsKey(pos)) {
			return 0;
		}

		BlockState state = level.getBlockState(pos);

		if (state.isAir() || state.is(infection.target) || !state.getFluidState().isEmpty()) {
			return 0;      // air, already the block we are painting with, water/lava: skip
		}

		if (state.getDestroySpeed(level, pos) < 0.0F && !Settings.breakBedrock()) {
			return 0;      // bedrock and the like: see the switch on the settings screen
		}

		infection.blocks.put(pos, state);
		level.setBlockAndUpdate(pos, infection.target.defaultBlockState());
		return 1;
	}

	/** Settle immediately when the player quits, so no map full of black blocks is left behind. */
	public static void onDisconnect(ServerPlayer player) {
		finish(player);
	}

	/** The item form of the block an infection painted with (so the drop matches the paint). */
	private static net.minecraft.world.item.Item itemFor(Block block) {
		return block == ModBlocks.WHITE_BLOCK ? ModItems.WHITE_BLOCK : ModItems.BLACK_BLOCK;
	}

	/** Settle: **everything drops** — the infected blocks themselves disappear and drop "original drops + one black block". */
	public static void finish(ServerPlayer player) {
		Infection infection = INFECTIONS.remove(player.getUUID());

		if (infection == null || infection.blocks.isEmpty() || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		for (Map.Entry<BlockPos, BlockState> entry : infection.blocks.entrySet()) {
			BlockPos pos = entry.getKey();
			BlockState original = entry.getValue();

			if (!level.getBlockState(pos).is(infection.target)) {
				continue;      // mined or swapped out by someone else in the meantime: leave it alone
			}

			level.removeBlock(pos, false);                                              // everything drops: the block itself disappears
			Block.dropResources(original, level, pos);                                  // its original drops
			Block.popResource(level, pos, new ItemStack(itemFor(infection.target)));       // plus one of the block it painted with
		}
	}
}
