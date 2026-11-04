package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.vault.VaultClock;
import com.frozendawn.vault.VaultClockBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** The backward clock: a panel three blocks wide on the wall's west face over the last door, digits three by five texels. */
public final class VaultClockRenderer implements BlockEntityRenderer<VaultClockBlockEntity> {
    private static final ResourceLocation FACE = texture("vault_clock_face"), DIGITS = texture("vault_clock_digits");
    private static final float SHEET_W = 44, SHEET_H = 5;

    public VaultClockRenderer(BlockEntityRendererProvider.Context context) { }

    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/block/" + name + ".png"); }

    @Override public void render(VaultClockBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        var face = buffers.getBuffer(RenderType.entityCutoutNoCull(FACE));
        quad(face, pose, -.01F, 0, 0, 48, 16, 0, 1, 0, 1);
        var digits = buffers.getBuffer(RenderType.entityCutoutNoCull(DIGITS));
        int x = 6;
        for (char c : VaultClock.text(be.start(), be.getLevel().getGameTime()).toCharArray()) {
            boolean colon = c == ':';
            int sheet = colon ? 40 : (c - '0') * 4, width = colon ? 1 : 3;
            quad(digits, pose, -.02F, x, 5, width, 5, sheet / SHEET_W, (sheet + width) / SHEET_W, 0, 1);
            x += colon ? 2 : 4;
        }
    }

    /** A quad in texels from the panel's top left as seen from the west: north is left, so x runs along +z from one block north. */
    private static void quad(VertexConsumer c, PoseStack pose, float depth, int x, int y, int w, int h, float u0, float u1, float v0, float v1) {
        float z0 = -1 + x / 16F, z1 = -1 + (x + w) / 16F, top = 1 - y / 16F, bottom = 1 - (y + h) / 16F;
        vertex(c, pose, depth, top, z0, u0, v0);
        vertex(c, pose, depth, bottom, z0, u0, v1);
        vertex(c, pose, depth, bottom, z1, u1, v1);
        vertex(c, pose, depth, top, z1, u1, v0);
    }
    private static void vertex(VertexConsumer c, PoseStack pose, float x, float y, float z, float u, float v) {
        c.addVertex(pose.last(), x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), -1, 0, 0);
    }

    @Override public boolean shouldRenderOffScreen(VaultClockBlockEntity be) { return true; }
}
