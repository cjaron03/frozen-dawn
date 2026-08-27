package com.frozendawn.client.renderer;

import com.frozendawn.block.BloomMassBlock;
import com.frozendawn.bloom.BloomBand;
import com.frozendawn.bloom.BloomSporePolicy;
import com.frozendawn.entity.BloomSporeEntity;
import com.frozendawn.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/** Heavy skull, shoulder, forearm, and chest growth breaks the humanoid silhouette. */
public final class BloomSporeGrowthLayer
        extends RenderLayer<BloomSporeEntity, BloomSporeModel> {
    public BloomSporeGrowthLayer(
            RenderLayerParent<BloomSporeEntity, BloomSporeModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       BloomSporeEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        BlockState pale = ModBlocks.BLOOM_MASS.get().defaultBlockState()
                .setValue(BloomMassBlock.BAND, BloomBand.FRONTIER);
        float rooting = entity.isRooting()
                ? Mth.clamp(entity.rootingProgress(partialTick)
                        * BloomSporePolicy.COLLAPSE_TICKS
                        / BloomSporePolicy.COLLAPSE_IMPACT_TICKS, 0.0F, 1.0F)
                : 0.0F;
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        fragment(poseStack, bufferSource, ModBlocks.BLOOM_CORE.get().defaultBlockState(),
                0.0F, 0.02F, -0.265F, 0.12F + rooting * 0.10F,
                0.0F, LightTexture.FULL_BRIGHT);
        if (rooting > 0.0F) {
            fragment(poseStack, bufferSource, ModBlocks.BLOOM_TIP.get().defaultBlockState(),
                    0.0F, -0.03F, -0.31F, 0.18F + rooting * 0.35F,
                    47.0F, LightTexture.FULL_BRIGHT);
        }
        fragment(poseStack, bufferSource, pale,
                -0.30F, -0.15F, 0.01F, 0.16F + rooting * 0.12F,
                -23.0F, packedLight);
        fragment(poseStack, bufferSource, pale,
                0.30F, -0.09F, 0.04F, 0.14F + rooting * 0.13F,
                31.0F, packedLight);
        poseStack.popPose();

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        fragment(poseStack, bufferSource, pale,
                -0.22F, -0.42F, 0.02F, 0.14F + rooting * 0.08F,
                -18.0F, packedLight);
        fragment(poseStack, bufferSource, pale,
                0.18F, -0.34F, 0.09F, 0.10F, 24.0F, packedLight);
        poseStack.popPose();

        poseStack.pushPose();
        getParentModel().rightArm.translateAndRotate(poseStack);
        fragment(poseStack, bufferSource, pale,
                0.02F, 0.37F, 0.0F, 0.11F, 28.0F, packedLight);
        poseStack.popPose();
        poseStack.pushPose();
        getParentModel().leftArm.translateAndRotate(poseStack);
        fragment(poseStack, bufferSource, pale,
                -0.02F, 0.30F, 0.02F, 0.13F, -31.0F, packedLight);
        poseStack.popPose();
    }

    static void fragment(PoseStack poseStack, MultiBufferSource bufferSource,
                         BlockState state, float x, float y, float z,
                         float scale, float rotation, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation * 0.35F));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation * 0.28F));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
