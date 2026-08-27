package com.frozendawn.client.renderer;

import com.frozendawn.entity.AggregateShedChunkEntity;
import com.frozendawn.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Rotating block-volume chunk torn from the Aggregate's visible body mass. */
public final class AggregateShedChunkRenderer
        extends EntityRenderer<AggregateShedChunkEntity> {
    public AggregateShedChunkRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.35F;
    }

    @Override
    public void render(
            AggregateShedChunkEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float age = entity.tickCount + partialTick;
        float scale = 0.58F + entity.variant() * 0.07F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * (15.0F + entity.variant() * 3.0F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 11.0F));
        poseStack.scale(scale, scale * 0.82F, scale);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                blockState(entity.variant()), poseStack, buffer,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static BlockState blockState(int variant) {
        return switch (variant) {
            case 1 -> ModBlocks.AGGREGATE_RIB.get().defaultBlockState();
            case 2 -> ModBlocks.AGGREGATE_RESIDUE.get().defaultBlockState();
            default -> ModBlocks.AGGREGATE_MASS.get().defaultBlockState();
        };
    }

    @Override
    public ResourceLocation getTextureLocation(AggregateShedChunkEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
