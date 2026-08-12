package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.RimeboundState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class RimeboundRenderer
        extends HumanoidMobRenderer<RimeboundEntity, RimeboundModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/entity/rimebound.png");

    public RimeboundRenderer(EntityRendererProvider.Context context) {
        super(context, new RimeboundModel(context.bakeLayer(
                RimeboundModel.LAYER_LOCATION)), 0.48F);
    }

    @Override
    public ResourceLocation getTextureLocation(RimeboundEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(RimeboundEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        RimeboundState state = entity.activityState();
        if (state == RimeboundState.BURROWING || state == RimeboundState.ERUPTING) {
            return;
        }
        poseStack.pushPose();
        if (state == RimeboundState.DORMANT) {
            poseStack.translate(0.0D, -1.58D, 0.0D);
        } else if (state == RimeboundState.EMERGING) {
            float progress = Mth.clamp((entity.stateTicks() + partialTick) / 30.0F,
                    0.0F, 1.0F);
            poseStack.translate(0.0D, -1.58D * (1.0F - progress), 0.0D);
        }
        if (state == RimeboundState.DEAD) {
            float freeze = Mth.clamp((entity.stateTicks() + partialTick) / 20.0F,
                    0.0F, 1.0F);
            float shake = Mth.sin((entity.tickCount + partialTick) * 2.9F)
                    * (1.0F - freeze) * 0.025F;
            poseStack.translate(shake, 0.0D, -shake);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
