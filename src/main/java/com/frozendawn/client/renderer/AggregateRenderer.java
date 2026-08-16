package com.frozendawn.client.renderer;

import com.frozendawn.entity.AggregateEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class AggregateRenderer extends GeoEntityRenderer<AggregateEntity> {
    public AggregateRenderer(EntityRendererProvider.Context context) {
        super(context, new AggregateModel());
        shadowRadius = 2.6F;
    }

    @Override
    public RenderType getRenderType(AggregateEntity entity, ResourceLocation texture,
                                    MultiBufferSource buffer, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    protected float getDeathMaxRotation(AggregateEntity entity) {
        // The Aggregate implodes around its own center; vanilla's corpse roll fights that read.
        return 0.0F;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, AggregateEntity entity,
                                  GeoBone bone, RenderType renderType,
                                  MultiBufferSource buffer, VertexConsumer consumer,
                                  boolean reRender, float partialTick,
                                  int packedLight, int packedOverlay, int renderColor) {
        boolean luminousCore = bone.getName().equals("core_inner")
                || bone.getName().equals("core_membrane");
        int light = luminousCore ? 0x00F000F0 : minimumReadableLight(packedLight);
        int overlay = entity.phase() == com.frozendawn.aggregate.AggregatePhase.DYING
                ? OverlayTexture.NO_OVERLAY : packedOverlay;
        super.renderRecursively(poseStack, entity, bone, renderType, buffer,
                consumer, reRender, partialTick, light, overlay, renderColor);
    }

    private static int minimumReadableLight(int packedLight) {
        int block = packedLight & 0xFFFF;
        int sky = packedLight >>> 16 & 0xFFFF;
        return Math.max(block, 0x50) | Math.max(sky, 0x70) << 16;
    }
}
