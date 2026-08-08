package com.frozendawn.client.renderer;

import com.frozendawn.block.BloomMassBlock;
import com.frozendawn.bloom.BloomBand;
import com.frozendawn.entity.UndoneEntity;
import com.frozendawn.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

/** Crystalline mass and an exposed chest knot distinguish the Bloombound variant. */
public final class BloomboundGrowthLayer extends RenderLayer<UndoneEntity, UndoneModel> {
    public BloomboundGrowthLayer(RenderLayerParent<UndoneEntity, UndoneModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       UndoneEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!entity.isBloombound() || entity.isInvisible()) {
            return;
        }

        float pulse = 0.95F + 0.08F * (float) Math.sin((entity.tickCount + partialTick) * 0.14F);
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        renderFragment(poseStack, bufferSource,
                ModBlocks.BLOOM_CORE.get().defaultBlockState(),
                0.0F, 0.03F, -0.265F, 0.155F * pulse, 9.0F,
                LightTexture.FULL_BRIGHT);
        renderFragment(poseStack, bufferSource,
                ModBlocks.BLOOM_MASS.get().defaultBlockState()
                        .setValue(BloomMassBlock.BAND, BloomBand.CORE),
                -0.31F, -0.05F, 0.02F, 0.13F, -24.0F, packedLight);
        renderFragment(poseStack, bufferSource,
                ModBlocks.BLOOM_MASS.get().defaultBlockState()
                        .setValue(BloomMassBlock.BAND, BloomBand.MID),
                0.27F, 0.20F, 0.08F, 0.11F, 31.0F, packedLight);
        poseStack.popPose();
    }

    private static void renderFragment(PoseStack poseStack, MultiBufferSource bufferSource,
                                       BlockState state, float x, float y, float z,
                                       float scale, float rotation, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation * 0.45F));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation * 0.30F));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
