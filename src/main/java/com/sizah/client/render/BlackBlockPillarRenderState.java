package com.sizah.client.render;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * The black block pillar render state: it takes "how long this frame is drawn, which way it points
 * and which block it uses" off the entity so the render thread can use it (26.3 renderers use the
 * render state pattern).
 */
public class BlackBlockPillarRenderState extends EntityRenderState {
	/** Distance from the back end to the launch point (blocks). */
	public double baseDistance;

	/** Distance from the tip to the launch point (blocks). */
	public double tipDistance;

	/** Pillar direction (unit vector). */
	public Vec3 direction = Vec3.ZERO;

	/** Block template: blockState / biome / light engine and so on; every block copies it again and only changes blockPos. */
	public final MovingBlockRenderState blockTemplate = new MovingBlockRenderState();
}
