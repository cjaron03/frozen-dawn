package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * The replay's screen, left untouched for a long time (owner mockup, 2026-11-09): burn-in of the readout panel and
 * the compact masthead, a stuck column and row, uneven backlight, dust and dead pixels, a little snow, and worn
 * textures with rows gone dark and blocks faded. The marks sit in the same places every frame, as on a real screen.
 * tools/generate_orsa_neglect.py draws the two layers at GUI scale, one texture pixel to a GUI pixel at 320 x 180.
 */
final class OrsaNeglect {
    private static final ResourceLocation UNDER =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_replay_neglect_under.png");
    private static final ResourceLocation OVER =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_replay_neglect_over.png");
    /** Worn rows and faded blocks, as fractions of the texture's box: rows {y}, blocks {x, y}. */
    private static final float[] WORN_ROWS = {0.17F, 0.41F, 0.63F, 0.88F};
    private static final float[][] FADED_BLOCKS = {{0.08F, 0.22F}, {0.55F, 0.12F}, {0.71F, 0.58F}, {0.27F, 0.70F},
            {0.43F, 0.44F}};

    private OrsaNeglect() {
    }

    /** The neglected screen over everything else, faded by alpha as the view clears. */
    static void render(GuiGraphics graphics, int width, int height, int tick, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        OrsaLogoRenderer.drawBlocky(graphics, UNDER, 0, 0, width, height, alpha);
        RandomSource random = RandomSource.create(tick * 7919L);
        for (int k = width * height / 220; k > 0; k--) {
            int v = 70 + random.nextInt(80);
            int a = Math.round((40 + random.nextInt(70)) * alpha);
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            graphics.fill(x, y, x + 1, y + 1, a << 24 | v << 16 | (v + 3) << 8 | (v + 6));
        }
        OrsaLogoRenderer.drawBlocky(graphics, OVER, 0, 0, width, height, alpha);
    }

    /** A texture that has sat too long: thin rows gone dark and blocks of it faded, at fixed places in its box. */
    static void wear(GuiGraphics graphics, float x, float y, float width, float height, float alpha) {
        if (alpha <= 0.0F || width < 4 || height < 4) {
            return;
        }
        int rowHeight = Math.max(1, Math.round(height / 60.0F));
        int dark = Math.round(255 * alpha) << 24;
        for (float row : WORN_ROWS) {
            int top = Math.round(y + row * height);
            graphics.fill(Math.round(x), top, Math.round(x + width), top + rowHeight, dark);
        }
        int faded = Math.round(135 * alpha) << 24;
        for (float[] block : FADED_BLOCKS) {
            int left = Math.round(x + block[0] * width);
            int top = Math.round(y + block[1] * height);
            graphics.fill(left, top, left + Math.max(1, Math.round(width * 0.12F)),
                    top + Math.max(1, Math.round(height * 0.08F)), faded);
        }
    }
}
