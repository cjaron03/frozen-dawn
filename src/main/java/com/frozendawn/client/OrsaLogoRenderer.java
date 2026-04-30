package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

final class OrsaLogoRenderer {

    private static final ResourceLocation LOGO =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_logo.png");
    private static final ResourceLocation BOOT_SHEET =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_logo_boot_sheet.png");
    private static final ResourceLocation FULL_MASTHEAD =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_masthead_full.png");
    private static final ResourceLocation AWAKENING_BASE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_awakening_base.png");
    private static final ResourceLocation AWAKENING_STRIPE_TOP_LEFT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_awakening_stripe_top_left.png");
    private static final ResourceLocation AWAKENING_STRIPE_TOP_RIGHT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_awakening_stripe_top_right.png");
    private static final ResourceLocation AWAKENING_STRIPE_MID_LEFT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_awakening_stripe_mid_left.png");
    private static final ResourceLocation AWAKENING_STRIPE_MID_RIGHT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_awakening_stripe_mid_right.png");
    private static final ResourceLocation AWAKENING_STRIPE_BOT_LEFT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_awakening_stripe_bot_left.png");
    private static final ResourceLocation AWAKENING_STRIPE_BOT_RIGHT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_awakening_stripe_bot_right.png");
    private static final int LOGO_TEXTURE_SIZE = 256;
    private static final int FULL_MASTHEAD_WIDTH = 817;
    private static final int FULL_MASTHEAD_HEIGHT = 362;
    private static final int BOOT_DRAW_SIZE = 52;
    private static final int BOOT_FRAME_SIZE = 208;
    private static final int BOOT_FRAME_COUNT = 32;
    private static final int BOOT_SHEET_WIDTH = BOOT_FRAME_SIZE * BOOT_FRAME_COUNT;
    private static final int BOOT_SHEET_HEIGHT = BOOT_FRAME_SIZE;

    private OrsaLogoRenderer() {
    }

    static void draw(GuiGraphics graphics, int x, int y, int size) {
        drawLogoQuad(graphics, x, y, size, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    static void drawTinted(GuiGraphics graphics, int x, int y, int size, float red, float green, float blue, float alpha) {
        drawLogoQuad(graphics, x, y, size, red, green, blue, alpha);
    }

    static void drawBoot(GuiGraphics graphics, int centerX, int centerY, int size, float orbitPhase) {
        float normalizedPhase = orbitPhase - (float) Math.floor(orbitPhase);
        int frame = Math.floorMod((int) Math.floor(normalizedPhase * BOOT_FRAME_COUNT), BOOT_FRAME_COUNT);
        drawBootQuad(graphics, centerX - size / 2.0F, centerY - size / 2.0F, size, frame);
    }

    static int bootDrawSize() {
        return BOOT_DRAW_SIZE;
    }

    static float mastheadAspectRatio() {
        return FULL_MASTHEAD_WIDTH / (float) FULL_MASTHEAD_HEIGHT;
    }

    static void drawMasthead(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        drawTextureQuad(graphics, FULL_MASTHEAD, x, y, width, height, 1.0F, 1.0F, 1.0F, alpha);
    }

    static void drawAwakeningBase(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        drawTextureQuad(graphics, AWAKENING_BASE, x, y, width, height, 1.0F, 1.0F, 1.0F, alpha);
    }

    static void drawAwakeningStripe(GuiGraphics graphics, int row, boolean rightSide, int x, int y, int width,
                                    int height, float alpha) {
        ResourceLocation location = switch (row) {
            case 0 -> rightSide ? AWAKENING_STRIPE_TOP_RIGHT : AWAKENING_STRIPE_TOP_LEFT;
            case 1 -> rightSide ? AWAKENING_STRIPE_MID_RIGHT : AWAKENING_STRIPE_MID_LEFT;
            default -> rightSide ? AWAKENING_STRIPE_BOT_RIGHT : AWAKENING_STRIPE_BOT_LEFT;
        };
        drawTextureQuad(graphics, location, x, y, width, height, 1.0F, 1.0F, 1.0F, alpha);
    }

    private static void drawBootQuad(GuiGraphics graphics, float x, float y, int size, int frame) {
        graphics.flush();
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(BOOT_SHEET);
        texture.setBlurMipmap(true, false);
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, BOOT_SHEET);
            Matrix4f matrix = graphics.pose().last().pose();
            float x2 = x + size;
            float y2 = y + size;
            float minU = (float) (frame * BOOT_FRAME_SIZE) / BOOT_SHEET_WIDTH;
            float maxU = (float) ((frame + 1) * BOOT_FRAME_SIZE) / BOOT_SHEET_WIDTH;
            float minV = 0.0F;
            float maxV = (float) BOOT_FRAME_SIZE / BOOT_SHEET_HEIGHT;
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(matrix, x, y, 0.0F).setUv(minU, minV);
            buffer.addVertex(matrix, x, y2, 0.0F).setUv(minU, maxV);
            buffer.addVertex(matrix, x2, y2, 0.0F).setUv(maxU, maxV);
            buffer.addVertex(matrix, x2, y, 0.0F).setUv(maxU, minV);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            texture.restoreLastBlurMipmap();
            RenderSystem.disableBlend();
        }
    }

    private static void drawLogoQuad(GuiGraphics graphics, int x, int y, int size, float red, float green, float blue,
                                     float alpha) {
        drawTextureQuad(graphics, LOGO, x, y, size, size, red, green, blue, alpha);
    }

    private static void drawTextureQuad(GuiGraphics graphics, ResourceLocation location, int x, int y, int width,
                                        int height, float red, float green, float blue, float alpha) {
        graphics.flush();
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        texture.setBlurMipmap(true, false);
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, location);
            Matrix4f matrix = graphics.pose().last().pose();
            float x1 = x;
            float y1 = y;
            float x2 = x + width;
            float y2 = y + height;
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.addVertex(matrix, x1, y1, 0.0F).setUv(0.0F, 0.0F).setColor(red, green, blue, alpha);
            buffer.addVertex(matrix, x1, y2, 0.0F).setUv(0.0F, 1.0F).setColor(red, green, blue, alpha);
            buffer.addVertex(matrix, x2, y2, 0.0F).setUv(1.0F, 1.0F).setColor(red, green, blue, alpha);
            buffer.addVertex(matrix, x2, y1, 0.0F).setUv(1.0F, 0.0F).setColor(red, green, blue, alpha);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            texture.restoreLastBlurMipmap();
            RenderSystem.disableBlend();
        }
    }
}
