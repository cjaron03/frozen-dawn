package com.frozendawn.client.renderer;

import com.frozendawn.block.BloomMassBlock;
import com.frozendawn.bloom.BloomBand;
import com.frozendawn.entity.BloomSporeCorpseEntity;
import com.frozendawn.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps the Spore's colonized silhouette legible after the body becomes a relay. */
public final class BloomSporeCorpseGrowthLayer
        extends RenderLayer<BloomSporeCorpseEntity, BloomSporeCorpseModel> {
    public BloomSporeCorpseGrowthLayer(
            RenderLayerParent<BloomSporeCorpseEntity, BloomSporeCorpseModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       BloomSporeCorpseEntity entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        BlockState pale = ModBlocks.BLOOM_MASS.get().defaultBlockState()
                .setValue(BloomMassBlock.BAND, BloomBand.FRONTIER);
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        BloomSporeGrowthLayer.fragment(poseStack, bufferSource,
                ModBlocks.BLOOM_CORE.get().defaultBlockState(),
                0.0F, 0.02F, -0.265F, 0.22F, 0.0F, LightTexture.FULL_BRIGHT);
        BloomSporeGrowthLayer.fragment(poseStack, bufferSource,
                ModBlocks.BLOOM_TIP.get().defaultBlockState(),
                0.0F, -0.03F, -0.31F, 0.53F, 47.0F, LightTexture.FULL_BRIGHT);
        BloomSporeGrowthLayer.fragment(poseStack, bufferSource, pale,
                -0.30F, -0.15F, 0.01F, 0.28F, -23.0F, packedLight);
        BloomSporeGrowthLayer.fragment(poseStack, bufferSource, pale,
                0.30F, -0.09F, 0.04F, 0.27F, 31.0F, packedLight);
        poseStack.popPose();
        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        BloomSporeGrowthLayer.fragment(poseStack, bufferSource, pale,
                -0.22F, -0.42F, 0.02F, 0.22F, -18.0F, packedLight);
        poseStack.popPose();
    }
}
