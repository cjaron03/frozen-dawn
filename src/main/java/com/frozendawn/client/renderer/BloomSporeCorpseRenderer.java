package com.frozendawn.client.renderer;

import com.frozendawn.entity.BloomSporeCorpseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class BloomSporeCorpseRenderer extends HumanoidMobRenderer<
        BloomSporeCorpseEntity, BloomSporeCorpseModel> {
    public BloomSporeCorpseRenderer(EntityRendererProvider.Context context) {
        super(context, new BloomSporeCorpseModel(
                context.bakeLayer(ModelLayers.ZOMBIE)), 0.0F);
        addLayer(new BloomSporeCorpseGrowthLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(BloomSporeCorpseEntity entity) {
        return BloomSporeRenderer.TEXTURE;
    }

    @Override
    protected void setupRotations(BloomSporeCorpseEntity entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw,
                                  float partialTick, float scale) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw,
                partialTick, scale);
        poseStack.mulPose(Axis.ZP.rotationDegrees(88.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(14.0F));
    }
}
