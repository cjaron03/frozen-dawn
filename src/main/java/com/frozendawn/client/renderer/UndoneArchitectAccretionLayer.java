package com.frozendawn.client.renderer;

import com.frozendawn.entity.UndoneArchitectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Discrete frost mass makes an Architect's accumulated bodies readable at range. */
public final class UndoneArchitectAccretionLayer
        extends RenderLayer<UndoneArchitectEntity, UndoneArchitectModel> {
    public UndoneArchitectAccretionLayer(
            RenderLayerParent<UndoneArchitectEntity, UndoneArchitectModel> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            UndoneArchitectEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        int stage = entity.getAccretionVisualStage();
        if (stage == 0 || entity.isInvisible() || entity.getDeathTicks() > 0) {
            return;
        }

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        renderFragment(poseStack, bufferSource, Blocks.PACKED_ICE.defaultBlockState(),
                -0.34F, 0.10F, 0.12F, 0.19F, -18.0F, packedLight);
        renderFragment(poseStack, bufferSource, Blocks.BLUE_ICE.defaultBlockState(),
                0.22F, 0.33F, 0.15F, 0.15F, 13.0F, packedLight);
        renderFragment(poseStack, bufferSource, Blocks.PACKED_ICE.defaultBlockState(),
                0.04F, -0.14F, 0.16F, 0.13F, 31.0F, packedLight);
        if (stage >= 2) {
            renderFragment(poseStack, bufferSource, Blocks.BLUE_ICE.defaultBlockState(),
                    -0.08F, 0.45F, 0.17F, 0.21F, -9.0F, packedLight);
            renderFragment(poseStack, bufferSource, Blocks.PACKED_ICE.defaultBlockState(),
                    0.34F, 0.02F, 0.10F, 0.18F, 24.0F, packedLight);
            renderFragment(poseStack, bufferSource, Blocks.PACKED_ICE.defaultBlockState(),
                    -0.24F, 0.39F, -0.13F, 0.14F, 41.0F, packedLight);
        }
        poseStack.popPose();
    }

    private static void renderFragment(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            BlockState state,
            float x,
            float y,
            float z,
            float scale,
            float rotation,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation * 0.55F));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation * 0.35F));
        poseStack.scale(scale, scale * 0.82F, scale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state,
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
