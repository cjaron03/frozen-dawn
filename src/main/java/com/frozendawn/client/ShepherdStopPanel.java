package com.frozendawn.client;

import com.frozendawn.shepherd.ShepherdFace;
import com.frozendawn.shepherd.ShepherdGlitch;
import com.frozendawn.shepherd.ShepherdGlitch.Cell;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/** Crash panel option A: blue, a small sad face, and all the copy at once. One cell of the hand font is one GUI pixel, so it stays sharp at every GUI scale. */
final class ShepherdStopPanel {
    private static final int W = 80, H = 60, BLUE = 0xFF1E54A8, INK = 0xFFCDEFFF;
    private static final int CELL = 10, SAD_AT = 4, SAD_CELL = 4, ROWS_X = 3, ROWS_Y = 27, ROW_STEP = 6;
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

    /** Her face coming apart, a cell to ten GUI pixels. */
    static void drawGlitch(GuiGraphics g, int x, int y, int age, long seed) {
        dark(g, x, y);
        for (int cx = 0; cx < ShepherdFace.WIDTH; cx++) {
            for (int cy = 0; cy < ShepherdFace.HEIGHT; cy++) {
                var cell = ShepherdGlitch.cell(age, seed, cx, cy);
                if (cell != Cell.BLANK) g.fill(x + cx * CELL, y + cy * CELL, x + (cx + 1) * CELL, y + (cy + 1) * CELL, colour(cell));
            }
        }
    }

    static void dark(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + W, y + H, colour(Cell.BLANK));
    }

    static int colour(Cell cell) {
        return switch (cell) {
            case BLANK -> 0xFF0B1014;
            case LIT -> 0xFF7DF2C0;
            case DIM -> 0xFF3AA984;
            case STOP -> BLUE;
            case STOP_DIM -> 0xFF70A0E2;
            case STOP_TEXT -> INK;
        };
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
