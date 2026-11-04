package com.frozendawn.client.renderer;

import com.frozendawn.init.ModItems;
import com.frozendawn.vault.VaultCradleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The wand lying in the forks, along the block as its model is built, one block long. */
public final class VaultCradleRenderer implements BlockEntityRenderer<VaultCradleBlockEntity> {
    public VaultCradleRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(VaultCradleBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.holding()) return;
        pose.pushPose();
        pose.translate(.5, .9, .5);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(ModItems.TERMINUS.get()), ItemDisplayContext.NONE,
                net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above()), OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }
}
