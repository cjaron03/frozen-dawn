package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.FrostbittenEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class FrostbittenRenderer extends HumanoidMobRenderer<FrostbittenEntity, FrostbittenModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/frostbitten.png");

    public FrostbittenRenderer(EntityRendererProvider.Context context) {
        super(context, new FrostbittenModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(FrostbittenEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FrostbittenEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // Emerge animation: rise from the ground
        if (entity.isEmerging()) {
            float progress = (entity.getEmergeTicks() + partialTick) / 30.0f;
            progress = Math.min(1.0f, progress);
            float yOffset = -(1.0f - progress) * 1.8f;
            poseStack.pushPose();
            poseStack.translate(0.0, yOffset, 0.0);
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            poseStack.popPose();
        } else {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }
}
