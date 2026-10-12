package com.sizah.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sizah.entity.BlackBlockPillarEntity;

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
 * The black block pillar renderer: it stacks **black block models** one by one along the pillar
 * direction.
 *
 * <p>It places no real blocks, it is purely client-side drawing, so it never damages terrain; the
 * renderer itself therefore has to take care of two things:
 *
 * <ol>
 *     <li>Move the length and the orientation to be drawn from the entity into
 *         {@link BlackBlockPillarRenderState};</li>
 *     <li>Enlarge the bounding box used for culling — the entity itself always stays at the launch
 *         point, but a pillar can reach out up to 30 blocks, so with the default 1×1 bounding box it
 *         would be culled away entirely as soon as you look up at the middle of the pillar.</li>
 * </ol>
 */
public class BlackBlockPillarRenderer extends EntityRenderer<BlackBlockPillarEntity, BlackBlockPillarRenderState> {
	public BlackBlockPillarRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public BlackBlockPillarRenderState createRenderState() {
		return new BlackBlockPillarRenderState();
	}

	@Override
	public void extractRenderState(BlackBlockPillarEntity entity, BlackBlockPillarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);

		// A filling pillar (the long black block pillar) draws only the section "filled so far": it appears a little at a time from the tip towards the base
		state.baseDistance = entity.getEffectBaseDistance();
		state.tipDistance = entity.getTipDistance();
		state.direction = entity.getBeamDirection();

		MovingBlockRenderState template = state.blockTemplate;
		// The block appearance is decided by the weapon kind: the Black Block Star gives black blocks, the Obsidian Cube gives obsidian.
		template.blockState = entity.getKind().block().defaultBlockState();
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

		// Rotate the local +Z axis onto the pillar direction: yaw around Y first, then pitch around the (already rotated) X.
		// Note that in 26.3 PoseStack#mulPose only accepts a matrix/Transformation, so a quaternion rotation has to go through rotate(Axis, radians).
		float yaw = (float) Mth.atan2(direction.x, direction.z);
		float pitch = (float) Math.asin(-direction.y);
		poseStack.rotate(Axis.YP, yaw);
		poseStack.rotate(Axis.XP, pitch);

		for (int i = 0; i < cubes; i++) {
			// The centre of the i-th block is base + i + 0.5 blocks away from the launch point.
			double center = state.baseDistance + i + 0.5D;

			// Every block needs its own render state: the vanilla submit node holds on to the reference and only
			// uses it later, in the feature render stage, so reusing one instance would make all the blocks take
			// their lighting and neighbouring blocks from the last blockPos.
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
			// The block model spans 0..1, so translate it half a block to centre it on the pillar axis.
			poseStack.translate(-0.5D, -0.5D, -0.5D);
			submitNodeCollector.submitMovingBlock(poseStack, block, 0);
			poseStack.popPose();
		}

		poseStack.popPose();

		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	@Override
	protected AABB getBoundingBoxForCulling(BlackBlockPillarEntity entity, float partialTicks) {
		// A pillar can reach out to both sides: when the back end reverses on hitting a wall it retreats behind
		// the launch point (a negative distance); a filling pillar, on the other hand, covers only the section
		// "filled so far".
		Vec3 direction = entity.getBeamDirection();
		Vec3 from = entity.position().add(direction.scale(entity.getEffectBaseDistance()));
		Vec3 to = entity.position().add(direction.scale(entity.getTipDistance()));
		return new AABB(from, to).inflate(1.0D);
	}
}
