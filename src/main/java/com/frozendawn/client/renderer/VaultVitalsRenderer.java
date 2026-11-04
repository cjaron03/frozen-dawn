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

/** The scanner's panel on the wall's west face, two blocks square: its word, a pulse trace and the temperature it reads, red for
 * the warm, gold for the cooling, a flat green line and NONE for the dead, dim with nobody on the plate. Texels run from its top
 * left as seen from the west, where north is left. */
public final class VaultVitalsRenderer implements BlockEntityRenderer<VaultVitalsBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/vault_vitals.png");
    /** The sheet: warm, cooling and dead panels, then the digits, a point and a C, in white, four texels apart. */
    private static final float SHEET_W = 144, SIZE = 32, GLYPHS_U = 96, GLYPH_W = 3, GLYPH_H = 5;
    private static final String GLYPHS = "0123456789.C";
    private static final int WHITE = 0xFFFFFF, DIM = 0x707070, RED = 0xE8685C, GOLD = 0xFFD65C, GREEN = 0x78C882;

    public VaultVitalsRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(VaultVitalsBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var c = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        boolean read = be.tenths() >= 0, cooling = read && be.tenths() < MorgueGate.COOLING * 10;
        float u = be.none() ? 2 * SIZE : cooling ? SIZE : 0;
        quad(c, pose, -.01F, 0, 0, SIZE, SIZE, u / SHEET_W, (u + SIZE) / SHEET_W, 0, 1, read ? WHITE : DIM);
        if (!read) return;
        int colour = be.none() ? GREEN : cooling ? GOLD : RED;
        String text = be.tenths() / 10 + "." + be.tenths() % 10 + "C";
        float x = 3;
        for (char glyph : text.toCharArray()) {
            float g = GLYPHS_U + GLYPHS.indexOf(glyph) * 4;
            quad(c, pose, -.02F, x, 22, GLYPH_W, GLYPH_H, g / SHEET_W, (g + GLYPH_W) / SHEET_W, 0, GLYPH_H / SIZE, colour);
            x += 4;
        }
    }

    private static void quad(VertexConsumer c, PoseStack pose, float depth, float x, float y, float w, float h, float u0, float u1, float v0, float v1, int rgb) {
        float z0 = x / 16, z1 = (x + w) / 16, top = 2 - y / 16, bottom = 2 - (y + h) / 16;
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
    @Override public AABB getRenderBoundingBox(VaultVitalsBlockEntity be) { return new AABB(be.getBlockPos()).expandTowards(-1, 2, 2); }
}
