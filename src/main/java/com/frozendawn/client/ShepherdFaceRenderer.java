package com.frozendawn.client;

import static com.frozendawn.shepherd.ShepherdFace.HEIGHT;
import static com.frozendawn.shepherd.ShepherdFace.WIDTH;

import com.frozendawn.shepherd.ShepherdFace;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** The same 48 texels, large on the talk screen and full bright on her display. */
public final class ShepherdFaceRenderer {
    private static final int SCREEN = 0xFF0B1014, GLOW = 0xFF7DF2C0, GLOW2 = 0xFF3AA984;
    /** Her display's lower left corner in voxels, and the depth just proud of it. */
    private static final int DISPLAY_X = 2, DISPLAY_TOP = 10, DISPLAY_FRONT = 10;
    private static final float PROUD = 0.05F;
    /** The texels in cs1_unit.png that hold the two face colors. */
    private static final float U_LIT = 60.5F / 64, U_DIM = 62.5F / 64, V = 60.5F / 64;

    private ShepherdFaceRenderer() {}

    public static void draw(GuiGraphics g, Mood mood, int mouthFrame, boolean blink, int x, int y, int scale) {
        g.fill(x, y, x + WIDTH * scale, y + HEIGHT * scale, SCREEN);
        for (int cx = 0; cx < WIDTH; cx++) {
            for (int cy = 0; cy < HEIGHT; cy++) {
                boolean lit = ShepherdFace.lit(mood, blink, cx, cy);
                if (!lit && !ShepherdFace.dim(mood, mouthFrame, cx, cy)) continue;
                int left = x + cx * scale, top = y + cy * scale;
                g.fill(left, top, left + scale, top + scale, lit ? GLOW : GLOW2);
            }
        }
    }

    /** {@code pose} is the model's own space: y down, one unit to the block. */
    public static void drawOnModel(PoseStack pose, MultiBufferSource buffers, Mood mood, int mouthFrame, boolean blink) {
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(ShepherdModel.TEXTURE));
        for (int cx = 0; cx < WIDTH; cx++) {
            for (int cy = 0; cy < HEIGHT; cy++) {
                boolean lit = ShepherdFace.lit(mood, blink, cx, cy);
                if (!lit && !ShepherdFace.dim(mood, mouthFrame, cx, cy)) continue;
                float left = (DISPLAY_X + cx - ShepherdModel.CENTER_X) / 16F, right = left + 1 / 16F;
                float top = (ShepherdModel.FEET_Y - DISPLAY_TOP + cy) / 16F, bottom = top + 1 / 16F;
                float z = (ShepherdModel.CENTER_Z - DISPLAY_FRONT - PROUD) / 16F;
                float u = lit ? U_LIT : U_DIM;
                vertex(buffer, pose, left, top, z, u);
                vertex(buffer, pose, left, bottom, z, u);
                vertex(buffer, pose, right, bottom, z, u);
                vertex(buffer, pose, right, top, z, u);
            }
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack pose, float x, float y, float z, float u) {
        buffer.addVertex(pose.last(), x, y, z).setColor(255, 255, 255, 255).setUv(u, V).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, -1);
    }
}
