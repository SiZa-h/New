package com.sizah.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * The generic "draw nothing" renderer: for those entities that are logic only and should have no
 * appearance (the blank axes, the barrage controller, the mark/lock, ...).
 *
 * <p>It has to be registered even though it draws nothing — the vanilla render dispatcher throws a
 * null pointer outright for an entity type that is not registered.
 */
public class InvisibleRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {
	public InvisibleRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}
}
