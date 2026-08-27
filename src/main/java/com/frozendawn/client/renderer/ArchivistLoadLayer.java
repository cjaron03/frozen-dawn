package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchivistEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The asymmetric archive carried on the entity rather than encoded in its skin. */
public final class ArchivistLoadLayer
        extends RenderLayer<ArchivistEntity, ArchivistModel> {
    private final ItemRenderer itemRenderer;

    public ArchivistLoadLayer(
            RenderLayerParent<ArchivistEntity, ArchivistModel> parent,
            ItemRenderer itemRenderer) {
        super(parent);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       ArchivistEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);

        // A crooked carrying frame rises above both shoulders and buries the head.
        fragment(poseStack, buffer, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),
                -0.31F, -0.02F, 0.27F, 0.20F, -8.0F, packedLight);
        fragment(poseStack, buffer, Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                0.28F, 0.08F, 0.30F, 0.25F, 13.0F, packedLight);
        fragment(poseStack, buffer, Blocks.IRON_BARS.defaultBlockState(),
                -0.02F, -0.43F, 0.22F, 0.28F, 4.0F, packedLight);
        fragment(poseStack, buffer, Blocks.CHAIN.defaultBlockState(),
                0.39F, 0.42F, 0.27F, 0.16F, -19.0F, packedLight);
        fragment(poseStack, buffer, Blocks.PACKED_ICE.defaultBlockState(),
                -0.42F, -0.28F, 0.20F, 0.18F, -31.0F, packedLight);
        fragment(poseStack, buffer, ModBlocks.INERT_ACHERONITE.get().defaultBlockState(),
                0.42F, -0.18F, 0.19F, 0.16F, 23.0F, packedLight);

        // The load reads as preserved lives and tools, not a pile of building blocks.
        item(poseStack, buffer, entity, new ItemStack(Items.WRITABLE_BOOK),
                -0.29F, 0.03F, 0.43F, 0.52F, -18.0F, 78.0F, packedLight);
        item(poseStack, buffer, entity, new ItemStack(Items.SPYGLASS),
                0.30F, -0.12F, 0.42F, 0.58F, 24.0F, 22.0F, packedLight);
        item(poseStack, buffer, entity, new ItemStack(Items.COMPASS),
                0.04F, 0.16F, 0.47F, 0.42F, 8.0F, 82.0F, packedLight);
        item(poseStack, buffer, entity, new ItemStack(Items.GLASS_BOTTLE),
                0.40F, 0.28F, 0.44F, 0.46F, -11.0F, 34.0F, packedLight);
        item(poseStack, buffer, entity, new ItemStack(ModItems.ORSA_ID_BADGE.get()),
                -0.43F, 0.31F, 0.45F, 0.47F, 16.0F, 82.0F, packedLight);

        // One warm-looking object remains; fullbright is visual only.
        fragment(poseStack, buffer, Blocks.SHROOMLIGHT.defaultBlockState(),
                0.20F, -0.16F, 0.39F, 0.13F, 11.0F,
                LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private void item(PoseStack poseStack, MultiBufferSource buffer,
                      ArchivistEntity entity, ItemStack stack,
                      float x, float y, float z, float scale,
                      float zRotation, float xRotation, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(zRotation));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRotation));
        poseStack.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED,
                packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }

    private static void fragment(PoseStack poseStack, MultiBufferSource buffer,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 float x, float y, float z, float scale,
                                 float rotation, int packedLight) {
        BloomSporeGrowthLayer.fragment(poseStack, buffer, state,
                x, y, z, scale, rotation, packedLight);
    }
}
