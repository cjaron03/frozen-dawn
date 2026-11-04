package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.vault.ArgusFight;
import com.frozendawn.vault.VaultFightClockBlock;
import com.frozendawn.vault.VaultFightClockBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** The fight's clock: the last door's panel and digits, drawn double so it reads across the floor, red for the last minute. */
public final class VaultFightClockRenderer implements BlockEntityRenderer<VaultFightClockBlockEntity> {
    private static final ResourceLocation FACE = texture("vault_clock_face"), DIGITS = texture("vault_clock_digits");
    private static final float SHEET_W = 44;
    private static final int PALE = 0xFFFFFF, RED = 0xE8685C;

    public VaultFightClockRenderer(BlockEntityRendererProvider.Context context) { }

    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/" + name + ".png"); }

    @Override public void render(VaultFightClockBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // Drawn as on the west face, as the last door's is, then turned to the block's own.
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(90 - be.getBlockState().getValue(VaultFightClockBlock.FACING).toYRot()));
        pose.translate(-.5, 0, -.5);
        quad(buffers.getBuffer(RenderType.entityCutoutNoCull(FACE)), pose, -.01F, 0, 0, 48, 16, 0, 1, 0, 1, PALE);
        var digits = buffers.getBuffer(RenderType.entityCutoutNoCull(DIGITS));
        int x = 6, colour = be.seconds() <= 60 ? RED : PALE;
        for (char c : ArgusFight.text(be.seconds()).toCharArray()) {
            boolean colon = c == ':';
            int sheet = colon ? 40 : (c - '0') * 4, width = colon ? 1 : 3;
            quad(digits, pose, -.02F, x, 3, width * 2, 10, sheet / SHEET_W, (sheet + width) / SHEET_W, 0, 1, colour);
            x += colon ? 4 : 8;
        }
        pose.popPose();
    }

    /** A quad in texels from the panel's top left as seen from the west: north is left, so x runs along +z from one block north. */
    private static void quad(VertexConsumer c, PoseStack pose, float depth, int x, int y, int w, int h, float u0, float u1, float v0, float v1, int rgb) {
        float z0 = -1 + x / 16F, z1 = -1 + (x + w) / 16F, top = 1 - y / 16F, bottom = 1 - (y + h) / 16F;
        vertex(c, pose, depth, top, z0, u0, v0, rgb);
        vertex(c, pose, depth, bottom, z0, u0, v1, rgb);
        vertex(c, pose, depth, bottom, z1, u1, v1, rgb);
        vertex(c, pose, depth, top, z1, u1, v0, rgb);
    }
    private static void vertex(VertexConsumer c, PoseStack pose, float x, float y, float z, float u, float v, int rgb) {
        c.addVertex(pose.last(), x, y, z).setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), -1, 0, 0);
    }

    @Override public boolean shouldRenderOffScreen(VaultFightClockBlockEntity be) { return true; }
    @Override public AABB getRenderBoundingBox(VaultFightClockBlockEntity be) { return new AABB(be.getBlockPos()).inflate(2); }
}
