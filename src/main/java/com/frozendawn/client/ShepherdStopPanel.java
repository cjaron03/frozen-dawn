package com.frozendawn.client;

import com.frozendawn.shepherd.ShepherdFace;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/** Crash panel option A: blue, a small sad face, and all the copy at once. One cell of the hand font is one GUI pixel, so it stays sharp at every GUI scale. */
final class ShepherdStopPanel {
    private static final int W = 80, H = 60, BLUE = 0xFF1E54A8, INK = 0xFFCDEFFF;
    private static final int SAD_AT = 4, SAD_CELL = 4, ROWS_X = 3, ROWS_Y = 27, ROW_STEP = 6;
    /** A colon and a bracket, in blocks. */
    private static final int[][] SAD = {{0, 1}, {0, 3}, {3, 0}, {2, 1}, {2, 2}, {2, 3}, {3, 4}};

    private ShepherdStopPanel() {}

    static void draw(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + W, y + H, BLUE);
        for (int[] cell : SAD) {
            int left = x + SAD_AT + cell[0] * SAD_CELL, top = y + SAD_AT + cell[1] * SAD_CELL;
            g.fill(left, top, left + SAD_CELL, top + SAD_CELL, INK);
        }
        int rowY = y + ROWS_Y;
        for (String key : ShepherdFace.STOP_ROWS) {
            row(g, x + ROWS_X, rowY, I18n.get(key));
            rowY += ROW_STEP;
        }
    }

    private static void row(GuiGraphics g, int x, int y, String text) {
        for (int i = 0; i < text.length(); i++) {
            var glyph = ShepherdFace.glyph(text.charAt(i));
            for (int r = 0; r < glyph.size(); r++) {
                String cells = glyph.get(r);
                for (int c = 0; c < cells.length(); c++) {
                    if (cells.charAt(c) == '#') g.fill(x + i * ShepherdFace.GLYPH_STEP + c, y + r, x + i * ShepherdFace.GLYPH_STEP + c + 1, y + r + 1, INK);
                }
            }
        }
    }
}
