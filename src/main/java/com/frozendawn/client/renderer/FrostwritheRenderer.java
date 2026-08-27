package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.FrostwritheEntity;
import com.frozendawn.entity.FrostwritheState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** A single grounded render pass for the assembled colony. */
public final class FrostwritheRenderer extends EntityRenderer<FrostwritheEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,
                    "textures/entity/frostwrithe.png");
    private final FrostwritheModel model;

    public FrostwritheRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new FrostwritheModel(
                context.bakeLayer(FrostwritheModel.LAYER_LOCATION));
        shadowRadius = 0.95F;
    }

    @Override
    public ResourceLocation getTextureLocation(FrostwritheEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FrostwritheEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.activityState() == FrostwritheState.BURROWING
                || entity.activityState() == FrostwritheState.ERUPTING) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        // The authored model's lowest points sit at y=24, Minecraft's ground plane.
        poseStack.translate(0.0F, -1.5F, 0.0F);

        FrostwritheState state = entity.activityState();
        if (state == FrostwritheState.ASSEMBLING) {
            float gather = Mth.clamp((entity.stateTicks() + partialTick) / 60.0F,
                    0.0F, 1.0F);
            float scale = Mth.lerp(gather, 0.72F, 1.0F);
            poseStack.scale(scale, scale, scale);
        } else if (state == FrostwritheState.SHELL) {
            poseStack.scale(1.04F, 0.97F, 0.96F);
        }

        float age = entity.tickCount + partialTick;
        float limbSwing = entity.walkAnimation.position(partialTick);
        float limbSwingAmount = entity.walkAnimation.speed(partialTick);
        model.setupAnim(entity, limbSwing, limbSwingAmount, age, 0.0F, 0.0F);
        int overlay = entity.hurtTime > 0
                ? LivingEntityRenderer.getOverlayCoords(entity, 0.0F)
                : OverlayTexture.NO_OVERLAY;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToBuffer(poseStack, consumer, packedLight, overlay,
                FastColor.ARGB32.color(255, 255, 255, 255));
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
