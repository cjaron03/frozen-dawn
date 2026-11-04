package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.vault.MorgueGate;
import com.frozendawn.vault.VaultVitalsBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** The scanner's screen over the Morgue's door, seven blocks by one, on its housing's west face: its word, a pulse trace and the
 * temperature it reads in figures twice the size, red for the warm, gold under 30, a flat green NONE for the dead, grey with nobody
 * on the plate. Texels run from its top left as seen from the west, where north is left; the block entity is its middle cell. */
public final class VaultVitalsRenderer implements BlockEntityRenderer<VaultVitalsBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/vault_vitals.png");
    /** The sheet: idle, warm, cooling and dead panels down it, then the digits, a point and a C, in white, four texels apart. */
    private static final float SHEET_W = 112, SHEET_H = 72, PANEL_W = 112, PANEL_H = 16, GLYPHS_V = 64, GLYPH_W = 3, GLYPH_H = 5, SCALE = 2;
    private static final float FACE = 12 / 16F, FIGURES_X = 54, FIGURES_Y = 3, PITCH = 8, UNIT_X = 96;
    private static final String GLYPHS = "0123456789.C";
    private static final int WHITE = 0xFFFFFF, RED = 0xE8685C, GOLD = 0xFFD65C, GREEN = 0x78C882;

    public VaultVitalsRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(VaultVitalsBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var c = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        boolean read = be.tenths() >= 0, cooling = read && be.tenths() < MorgueGate.COOLING * 10;
        int row = be.none() ? 3 : cooling ? 2 : read ? 1 : 0;
        quad(c, pose, FACE - .01F, 0, 0, PANEL_W, PANEL_H, 0, 1, row * PANEL_H / SHEET_H, (row + 1) * PANEL_H / SHEET_H, WHITE);
        if (!read) return;
        int colour = be.none() ? GREEN : cooling ? GOLD : RED;
        String figures = be.tenths() / 10 + "." + be.tenths() % 10;
        for (int i = 0; i < figures.length(); i++)
            glyph(c, pose, figures.charAt(i), FIGURES_X + (4 - figures.length() + i) * PITCH, colour);
        glyph(c, pose, 'C', UNIT_X, colour);
    }

    private static void glyph(VertexConsumer c, PoseStack pose, char glyph, float x, int colour) {
        float g = GLYPHS.indexOf(glyph) * 4;
        quad(c, pose, FACE - .02F, x, FIGURES_Y, GLYPH_W * SCALE, GLYPH_H * SCALE, g / SHEET_W, (g + GLYPH_W) / SHEET_W, GLYPHS_V / SHEET_H, (GLYPHS_V + GLYPH_H) / SHEET_H, colour);
    }

    private static void quad(VertexConsumer c, PoseStack pose, float depth, float x, float y, float w, float h, float u0, float u1, float v0, float v1, int rgb) {
        float z0 = -3 + x / 16, z1 = -3 + (x + w) / 16, top = 1 - y / 16, bottom = 1 - (y + h) / 16;
        vertex(c, pose, depth, top, z0, u0, v0, rgb);
        vertex(c, pose, depth, bottom, z0, u0, v1, rgb);
        vertex(c, pose, depth, bottom, z1, u1, v1, rgb);
        vertex(c, pose, depth, top, z1, u1, v0, rgb);
    }
    private static void vertex(VertexConsumer c, PoseStack pose, float x, float y, float z, float u, float v, int rgb) {
        c.addVertex(pose.last(), x, y, z).setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), -1, 0, 0);
    }

    @Override public boolean shouldRenderOffScreen(VaultVitalsBlockEntity be) { return true; }
    @Override public AABB getRenderBoundingBox(VaultVitalsBlockEntity be) { return new AABB(be.getBlockPos()).inflate(0, 0, 3); }
}
