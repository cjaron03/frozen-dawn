package com.frozendawn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The first boot's ORSA beat (owner mockup, 2026-11-09), between the opening's black lead-in and its masthead: a
 * point of light grows into the spinning ORSA icon as the ORSA sting starts; the icon glides into the masthead's O
 * while the rest of the logo spreads out from it, its spin easing to a stop on the masthead's pose; then the O
 * blends into the masthead, which the intro carries on from, already drawn.
 */
final class OrsaBootBeat {
    static final int GLINT = 4;
    static final int GROW = 30;
    static final int EXPAND = 32;
    static final int BLEND = 8;
    static final int TICKS = GLINT + GROW + EXPAND + BLEND;

    /** The spinner frame the masthead's O shows: the moon at the upper right. */
    static final int STOP_FRAME = 8;
    private static final int FRAMES = 64;
    /** The terminals' spin: one orbit every 22 ticks. */
    private static final float ORBIT_PER_TICK = 1.0F / 22.0F;
    /** The icon's size at full growth, against the screen's height. */
    private static final float FULL_SIZE = 0.42F;
    /** The spinner's white ring, as a fraction of its frame. */
    private static final float RING_FRACTION = 74.0F / 208.0F;
    /** The masthead texture's width, and its O's centre and outer radius in it. */
    private static final float MASTHEAD_WIDTH = 817.0F;
    private static final float O_X = 180.0F;
    private static final float O_Y = 127.0F;
    private static final float O_RADIUS = 90.0F;

    private OrsaBootBeat() {
    }

    /** How far the icon has grown from its point, easing out. */
    static float growth(float t) {
        float u = Mth.clamp((t - GLINT) / GROW, 0.0F, 1.0F);
        float inverse = 1.0F - u;
        return 1.0F - inverse * inverse * inverse;
    }

    /** How far the icon has glided into the O. */
    static float expand(float t) {
        return smooth((t - GLINT - GROW) / EXPAND);
    }

    /** The icon's alpha: whole until the blend, then gone into the masthead. */
    static float iconAlpha(float t) {
        return 1.0F - smooth((t - GLINT - GROW - EXPAND) / BLEND);
    }

    /** The orbit phase: the terminals' speed while growing, then a quadratic ease to rest on STOP_FRAME. */
    static float spinPhase(float t) {
        float decel = GLINT + GROW;
        float stop = decel + EXPAND;
        float remaining;
        if (t >= stop) {
            remaining = 0.0F;
        } else if (t >= decel) {
            float left = 1.0F - (t - decel) / EXPAND;
            remaining = ORBIT_PER_TICK * EXPAND * left * left * left / 3.0F;
        } else {
            remaining = ORBIT_PER_TICK * (decel - t) + ORBIT_PER_TICK * EXPAND / 3.0F;
        }
        return STOP_FRAME / (float) FRAMES + 3.0F - remaining;
    }

    /** The opening's large masthead box, before it lifts: x, y, width, height. */
    static int[] mastheadBox(int width, int height) {
        int logoWidth = Math.min((int) (width * 0.56F), 480);
        int logoHeight = Math.max(1, (int) (logoWidth / OrsaLogoRenderer.mastheadAspectRatio()));
        return new int[] {width / 2 - logoWidth / 2, (int) (height * 0.37F - logoHeight / 2.0F), logoWidth, logoHeight};
    }

    static void render(GuiGraphics graphics, int width, int height, float t, int scanColor) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        for (int y = Math.floorMod((int) t, 6); y < height; y += 6) {
            graphics.fill(0, y, width, y + 1, 18 << 24 | scanColor);
        }
        float cx = width / 2.0F;
        float cy = height / 2.0F;
        if (t < GLINT) {
            int alpha = Math.round(255 * smooth(t / 2.0F));
            int reach = t < 2 ? 1 : 2;
            int x = Math.round(cx);
            int y = Math.round(cy);
            graphics.fill(x - reach, y, x + reach + 1, y + 1, alpha << 24 | 0xE9FBFF);
            graphics.fill(x, y - reach, x + 1, y + reach + 1, alpha << 24 | 0xE9FBFF);
            return;
        }

        int[] box = mastheadBox(width, height);
        float scale = box[2] / MASTHEAD_WIDTH;
        float ox = box[0] + O_X * scale;
        float oy = box[1] + O_Y * scale;
        float oRadius = O_RADIUS * scale;
        float oSize = oRadius / RING_FRACTION;
        float full = height * FULL_SIZE;

        float size;
        float px;
        float py;
        float e = expand(t);
        if (t < GLINT + GROW) {
            size = Math.max(1.0F, full * growth(t));
            px = cx;
            py = cy;
        } else {
            size = Mth.lerp(e, full, oSize);
            px = Mth.lerp(e, cx, ox);
            py = Mth.lerp(e, cy, oy);
            renderSpreadingMasthead(graphics, box, ox, oy, oRadius, size / oSize, px, py, e, iconAlpha(t) < 1.0F);
        }
        OrsaLogoRenderer.drawBoot(graphics, px, py, size, spinPhase(t), iconAlpha(t));
    }

    /**
     * The masthead travels and scales with the icon, so its O always sits under the icon, and shows only inside a
     * square growing out from it. Until the blend the O itself stays hidden, so its still planet never shows
     * through the spinning one.
     */
    private static void renderSpreadingMasthead(GuiGraphics graphics, int[] box, float ox, float oy, float oRadius,
                                                float scale, float px, float py, float e, boolean blending) {
        int w = Math.round(box[2] * scale);
        int h = Math.round(box[3] * scale);
        int x = Math.round(px - (ox - box[0]) * scale);
        int y = Math.round(py - (oy - box[1]) * scale);
        float radius = oRadius * scale;
        float reach = radius + 2.0F + e * w * 1.05F;
        int left = Math.round(px - reach);
        int top = Math.round(py - reach);
        int right = Math.round(px + reach);
        int bottom = Math.round(py + reach);
        if (blending) {
            drawMastheadIn(graphics, x, y, w, h, left, top, right, bottom);
            return;
        }
        int oLeft = Math.round(px - radius - 1);
        int oTop = Math.round(py - radius - 1);
        int oRight = Math.round(px + radius + 1);
        int oBottom = Math.round(py + radius + 1);
        drawMastheadIn(graphics, x, y, w, h, left, top, oLeft, bottom);
        drawMastheadIn(graphics, x, y, w, h, oRight, top, right, bottom);
        drawMastheadIn(graphics, x, y, w, h, oLeft, top, oRight, oTop);
        drawMastheadIn(graphics, x, y, w, h, oLeft, oBottom, oRight, bottom);
    }

    private static void drawMastheadIn(GuiGraphics graphics, int x, int y, int w, int h,
                                       int left, int top, int right, int bottom) {
        if (right <= left || bottom <= top) {
            return;
        }
        graphics.enableScissor(left, top, right, bottom);
        OrsaLogoRenderer.drawAwakeningBase(graphics, x, y, w, h, 1.0F, false);
        graphics.disableScissor();
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
