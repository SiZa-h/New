package com.sizah.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sizah.content.weapon.entity.WhitePillarEntity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The white block pillar renderer: the same "stack block models along the beam" trick as
 * {@link BlackBlockPillarRenderer}, with three differences that come from the shape of the thing:
 *
 * <ul>
 *     <li>the appearance block is the **white block** rather than the weapon kind's block;</li>
 *     <li>the bolt is **thin**: each block model is scaled down to {@link WhitePillarEntity#THICKNESS}
 *         (0.1 blocks) across, so the drawing matches the collision box instead of looking like a full block
 *         wide — a stair of 1x1 models would have looked nothing like the 0.1 x 5 shape it actually is;</li>
 *     <li>it is drawn **from where it currently is** and along its flight direction (a flying bolt, not a beam
 *         anchored to a launch point), with the length coming from {@link WhitePillarEntity#LENGTH} (5 blocks).</li>
 * </ul>
 *
 * <p>Nothing is placed in the world: it is pure drawing, so the renderer has to move the direction into the
 * render state and enlarge the culling box (a 0.1 block wide entity would otherwise be culled the moment it is
 * off centre).
 */
public class WhitePillarRenderer extends EntityRenderer<WhitePillarEntity, BlackBlockPillarRenderState> {
	public WhitePillarRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public BlackBlockPillarRenderState createRenderState() {
		return new BlackBlockPillarRenderState();
	}

	@Override
	public void extractRenderState(WhitePillarEntity entity, BlackBlockPillarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);

		// The bolt spans from the entity's own position forward: base 0, tip = its length.
		state.baseDistance = 0.0D;
		state.tipDistance = WhitePillarEntity.LENGTH;
		state.direction = entity.getFlightDirection();

		MovingBlockRenderState template = state.blockTemplate;
		template.blockState = com.sizah.content.block.ModBlocks.WHITE_BLOCK.defaultBlockState();
		template.blockPos = entity.blockPosition();
		template.randomSeedPos = entity.blockPosition();

		if (entity.level() instanceof ClientLevel clientLevel) {
			template.biome = clientLevel.getBiome(template.blockPos);
			template.cardinalLighting = clientLevel.cardinalLighting();
			template.lightEngine = clientLevel.getLightEngine();
		}
	}

	@Override
	public void submit(
		BlackBlockPillarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera
	) {
		Vec3 direction = state.direction;

		if (direction.lengthSqr() < 1.0E-6D) {
			return;
		}

		int cubes = (int) Math.floor(state.tipDistance - state.baseDistance);

		if (cubes <= 0) {
			return;
		}

		poseStack.pushPose();

		// Local +Z onto the flight direction: yaw around Y, then pitch around the already rotated X.
		float yaw = (float) Mth.atan2(direction.x, direction.z);
		float pitch = (float) Math.asin(-direction.y);
		poseStack.rotate(Axis.YP, yaw);
		poseStack.rotate(Axis.XP, pitch);

		for (int i = 0; i < cubes; i++) {
			double center = state.baseDistance + i + 0.5D;

			// One render state per block: the submit node keeps the reference and reads it later, so a shared
			// instance would give every block the lighting of the last position.
			MovingBlockRenderState block = new MovingBlockRenderState();
			block.blockState = state.blockTemplate.blockState;
			block.biome = state.blockTemplate.biome;
			block.cardinalLighting = state.blockTemplate.cardinalLighting;
			block.lightEngine = state.blockTemplate.lightEngine;
			block.randomSeedPos = state.blockTemplate.randomSeedPos;
			block.blockPos = BlockPos.containing(
					state.x + direction.x * center,
					state.y + direction.y * center,
					state.z + direction.z * center);

			poseStack.pushPose();
			poseStack.translate(0.0D, 0.0D, center);

			// The bolt is a tenth of a block thick, so the block model has to be **scaled down** as well: drawing
			// it at its natural 1x1 size would make the pillar look like a full block wide — which is exactly what
			// it must not look like, since it also collides as a 0.1 wide box.
			poseStack.scale((float) WhitePillarEntity.THICKNESS, (float) WhitePillarEntity.THICKNESS, 1.0F);
			poseStack.translate(-0.5D, -0.5D, -0.5D);   // the block model spans 0..1, centre it on the axis

			submitNodeCollector.submitMovingBlock(poseStack, block, 0);
			poseStack.popPose();
		}

		poseStack.popPose();

		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	@Override
	protected AABB getBoundingBoxForCulling(WhitePillarEntity entity, float partialTicks) {
		Vec3 direction = entity.getFlightDirection();
		Vec3 from = entity.position();
		Vec3 to = from.add(direction.scale(WhitePillarEntity.LENGTH));
		return new AABB(from, to).inflate(1.0D);
	}
}
