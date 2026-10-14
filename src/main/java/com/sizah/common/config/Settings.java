package com.sizah.common.config;

import com.sizah.content.effect.Explosions;

import net.minecraft.world.level.Level;

/**
 * Gameplay toggles (**server-side authoritative**).
 *
 * <p>Both toggles can be changed from the client-side settings screen:
 *
 * <ul>
 *     <li>{@link #breakBedrock()}: whether pillars **break bedrock along with everything else**
 *         (off = bedrock is ignored and holds them back);</li>
 *     <li>{@link #explosionBreaksBlocks()}: whether the **explosions** from pillar collisions and the
 *         finishing move **destroy blocks** (off = they only hurt creatures).</li>
 * </ul>
 *
 * <p>The client sends a {@code settings} packet immediately after a change on the settings screen, and the server
 * side only keeps it in memory (a restart falls back to the default values); the client re-sends it every time it
 * joins a server/world, so the two sides always agree.
 */
public final class Settings {
	/** Default: break bedrock (consistent with the requirement that "everything except behaviour 1 can break bedrock"). */
	public static final boolean DEFAULT_BREAK_BEDROCK = true;

	/** Default: explosions destroy blocks. */
	public static final boolean DEFAULT_EXPLOSION_BREAKS_BLOCKS = true;

	private static boolean breakBedrock = DEFAULT_BREAK_BEDROCK;
	private static boolean explosionBreaksBlocks = DEFAULT_EXPLOSION_BREAKS_BLOCKS;

	private Settings() {
	}

	/** Whether pillars can break bedrock and other "unbreakable blocks". */
	public static boolean breakBedrock() {
		return breakBedrock;
	}

	/** Whether explosions destroy blocks. */
	public static boolean explosionBreaksBlocks() {
		return explosionBreaksBlocks;
	}

	/** Which interaction an explosion should use under the current settings: TNT-style when it breaks blocks, MOB-style (creatures only) when it does not. */
	public static Level.ExplosionInteraction explosionInteraction() {
		return explosionBreaksBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.MOB;
	}

	/** Called on the server side after receiving the client's settings packet. */
	public static void apply(boolean newBreakBedrock, boolean newExplosionBreaksBlocks) {
		breakBedrock = newBreakBedrock;
		explosionBreaksBlocks = newExplosionBreaksBlocks;
	}
}
