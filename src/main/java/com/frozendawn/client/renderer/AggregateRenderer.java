package com.frozendawn.client.renderer;

import com.frozendawn.entity.AggregateEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class AggregateRenderer extends GeoEntityRenderer<AggregateEntity> {
    public AggregateRenderer(EntityRendererProvider.Context context) {
        super(context, new AggregateModel());
        shadowRadius = 2.6F;
    }

    @Override
    public void renderRecursively(PoseStack poseStack, AggregateEntity entity,
                                  GeoBone bone, RenderType renderType,
                                  MultiBufferSource buffer, VertexConsumer consumer,
                                  boolean reRender, float partialTick,
                                  int packedLight, int packedOverlay, int renderColor) {
        int light = bone.getName().startsWith("core_") ? 0x00F000F0 : packedLight;
        super.renderRecursively(poseStack, entity, bone, renderType, buffer,
                consumer, reRender, partialTick, light, packedOverlay, renderColor);
    }
}
