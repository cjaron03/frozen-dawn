package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ReturnedEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class ReturnedRenderer extends HumanoidMobRenderer<ReturnedEntity, ReturnedModel> {

    private static final ResourceLocation[] TEXTURES = {
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/returned_0.png"),
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/returned_1.png"),
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/returned_2.png"),
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/returned_3.png"),
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/returned_4.png"),
    };

    public ReturnedRenderer(EntityRendererProvider.Context context) {
        super(context, new ReturnedModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(ReturnedEntity entity) {
        int variant = entity.getTextureVariant();
        if (variant < 0 || variant >= TEXTURES.length) variant = 0;
        return TEXTURES[variant];
    }

    @Override
    public void render(ReturnedEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        int deathTicks = entity.getDeathTicks();
        if (deathTicks > 0) {
            float progress = (deathTicks + partialTick) / 30.0f;
            progress = Math.min(1.0f, progress);
            poseStack.pushPose();
            // Knee collapse: lean forward
            float lean = progress * 90.0f;
            poseStack.mulPose(Axis.XP.rotationDegrees(lean * 0.5f));
            // Vertical compression at 80%+
            if (progress > 0.8f) {
                float compressRatio = 1.0f - (progress - 0.8f) * 5.0f; // 1.0 -> 0.0
                poseStack.scale(1.0f, Math.max(0.1f, compressRatio), 1.0f);
            }
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            poseStack.popPose();
        } else {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }
}
