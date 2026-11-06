package com.frozendawn.client;

import static com.frozendawn.shepherd.ShepherdFace.HEIGHT;
import static com.frozendawn.shepherd.ShepherdFace.WIDTH;

import com.frozendawn.FrozenDawn;
import com.frozendawn.shepherd.ShepherdFace;
import com.frozendawn.shepherd.ShepherdFace.Cells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** The same 48 texels, large on the talk screen and full bright on her display. */
public final class ShepherdFaceRenderer {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/shepherd_boot_logo.png");
    private static final int LOGO_SIZE = 30;
    private static final int SCREEN = 0xFF0B1014, GLOW = 0xFF7DF2C0, GLOW2 = 0xFF3AA984;
    /** Her display's lower left corner in voxels, and the depth just proud of it. */
    private static final int DISPLAY_X = 2, DISPLAY_TOP = 10, DISPLAY_FRONT = 10;
    private static final float PROUD = 0.05F;
    /** The texels in cs1_unit.png that hold the two face colors. */
    private static final float U_LIT = 60.5F / 64, U_DIM = 62.5F / 64, V = 60.5F / 64;
    /** And the stop screen's three. */
    private static final float U_STOP = 54.5F / 64, U_STOP_DIM = 56.5F / 64, U_STOP_TEXT = 58.5F / 64;

    private ShepherdFaceRenderer() {}

    public static void draw(GuiGraphics g, Cells cells, int mouthFrame, boolean blink, int eyeShift, int x, int y, int scale) {
        g.fill(x, y, x + WIDTH * scale, y + HEIGHT * scale, SCREEN);
        for (int cx = 0; cx < WIDTH; cx++) {
            for (int cy = 0; cy < HEIGHT; cy++) {
                boolean lit = ShepherdFace.lit(cells, blink, eyeShift, cx, cy);
                if (!lit && !ShepherdFace.dim(cells, mouthFrame, cx, cy)) continue;
                int left = x + cx * scale, top = y + cy * scale;
                g.fill(left, top, left + scale, top + scale, lit ? GLOW : GLOW2);
            }
        }
    }

    /** The full logo for the talk screen, centered on the same panel as her face. */
    public static void drawLogo(GuiGraphics g, int frame, int x, int y, int scale) {
        g.fill(x, y, x + WIDTH * scale, y + HEIGHT * scale, SCREEN);
        int size = LOGO_SIZE * 2;
        g.blit(LOGO, x + (WIDTH * scale - size) / 2, y + (HEIGHT * scale - size) / 2, size, size,
                frame * LOGO_SIZE, 0, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE * ShepherdFace.LOGO_FRAMES, LOGO_SIZE);
    }

    /** {@code pose} is the model's own space: y down, one unit to the block. */
    public static void drawOnModel(PoseStack pose, MultiBufferSource buffers, Cells cells, int mouthFrame, boolean blink, int eyeShift) {
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(ShepherdModel.TEXTURE));
        for (int cx = 0; cx < WIDTH; cx++) {
            for (int cy = 0; cy < HEIGHT; cy++) {
                boolean lit = ShepherdFace.lit(cells, blink, eyeShift, cx, cy);
                if (!lit && !ShepherdFace.dim(cells, mouthFrame, cx, cy)) continue;
                texel(buffer, pose, cx, cy, lit ? U_LIT : U_DIM);
            }
        }
    }

    /** Every texel of her display, in blue. */
    public static void drawStop(PoseStack pose, MultiBufferSource buffers) {
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(ShepherdModel.TEXTURE));
        var stop = ShepherdFace.STOP;
        for (int cx = 0; cx < WIDTH; cx++) {
            for (int cy = 0; cy < HEIGHT; cy++) {
                float u = stop.lit(cx, cy) ? U_STOP_TEXT : stop.dim(cx, cy) ? U_STOP_DIM : U_STOP;
                texel(buffer, pose, cx, cy, u);
            }
        }
    }

    private static void texel(VertexConsumer buffer, PoseStack pose, int cx, int cy, float u) {
        float left = (DISPLAY_X + cx - ShepherdModel.CENTER_X) / 16F, right = left + 1 / 16F;
        float top = (ShepherdModel.FEET_Y - DISPLAY_TOP + cy) / 16F, bottom = top + 1 / 16F;
        float z = (ShepherdModel.CENTER_Z - DISPLAY_FRONT - PROUD) / 16F;
        vertex(buffer, pose, left, top, z, u);
        vertex(buffer, pose, left, bottom, z, u);
        vertex(buffer, pose, right, bottom, z, u);
        vertex(buffer, pose, right, top, z, u);
    }

    private static void vertex(VertexConsumer buffer, PoseStack pose, float x, float y, float z, float u) {
        buffer.addVertex(pose.last(), x, y, z).setColor(255, 255, 255, 255).setUv(u, V).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, -1);
    }
}
