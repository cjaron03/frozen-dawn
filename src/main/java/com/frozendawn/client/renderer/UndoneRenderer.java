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
    private static final ResourceLocation BLOOMBOUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "textures/entity/bloombound_undone.png");

    public UndoneRenderer(EntityRendererProvider.Context context) {
        super(context, new UndoneModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        addLayer(new BloomboundGrowthLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(UndoneEntity entity) {
        return entity.isBloombound() ? BLOOMBOUND_TEXTURE : TEXTURE;
    }

    @Override
    public void render(UndoneEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        if (entity.isBloombound()) {
            poseStack.scale(1.06F, 1.06F, 1.06F);
            if (entity.getBloomEmergenceTicks() > 0) {
                float remaining = Math.max(0.0F,
                        (entity.getBloomEmergenceTicks() - partialTick)
                                / UndoneEntity.BLOOM_EMERGENCE_DURATION);
                poseStack.translate(0.0F, -1.35F * remaining * remaining, 0.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(
                        (float) Math.sin((entity.tickCount + partialTick) * 1.4F)
                                * remaining * 2.5F));
            }
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(-3.5F));
        if (entity.getStumbleTicks() > 0) {
            float stagger = (float) Math.sin((entity.tickCount + partialTick) * 1.7F);
            poseStack.mulPose(Axis.XP.rotationDegrees(9.0F + stagger * 2.5F));
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
