package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomSporePolicy;
import com.frozendawn.entity.BloomSporeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class BloomSporeRenderer
        extends HumanoidMobRenderer<BloomSporeEntity, BloomSporeModel> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/entity/bloom_spore.png");

    public BloomSporeRenderer(EntityRendererProvider.Context context) {
        super(context, new BloomSporeModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.48F);
        addLayer(new BloomSporeGrowthLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(BloomSporeEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(BloomSporeEntity entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw,
                                  float partialTick, float scale) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw,
                partialTick, scale);
        if (entity.isRooting()) {
            float progress = entity.rootingProgress(partialTick);
            float impact = Math.min(1.0F, progress
                    * BloomSporePolicy.COLLAPSE_TICKS
                    / BloomSporePolicy.COLLAPSE_IMPACT_TICKS);
            float fall = impact * impact * (3.0F - 2.0F * impact);
            poseStack.mulPose(Axis.ZP.rotationDegrees(fall * 88.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(fall * 14.0F));
        }
    }
}
