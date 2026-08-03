package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.UndoneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class UndoneRenderer
        extends HumanoidMobRenderer<UndoneEntity, UndoneModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "textures/entity/undone.png");

    public UndoneRenderer(EntityRendererProvider.Context context) {
        super(context, new UndoneModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(UndoneEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(UndoneEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(-3.5F));
        if (entity.getStumbleTicks() > 0) {
            float stagger = (float) Math.sin((entity.tickCount + partialTick) * 1.7F);
            poseStack.mulPose(Axis.XP.rotationDegrees(9.0F + stagger * 2.5F));
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
