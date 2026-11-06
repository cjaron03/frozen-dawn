package com.frozendawn.client;

import com.frozendawn.network.ShepherdLogPayload.Page;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/** Her log, as private-log-mockup.png and log-emotion-mockup.png draw it: the form on the BIOS panel, days on the row under it. Everything she feels here is text inside the form. */
final class ShepherdLogPage {
    static final int ROW_Y = 202, ROW_H = 14, OLDER_X = 84, NEWER_X = 242, BUTTON_W = 150;
    private static final int PANEL_X = 84, PANEL_Y = 88, PANEL_W = 312, PANEL_H = 107;
    private static final int X = 92, HEADER_Y = 93, FIRST_Y = 111, STEP = 12, WIDTH = 295, LAST_ROW = 4, FAST = 10;
    private static final String LOOP = "LOOP DETECTED. Retrying.", LIMIT = "FIELD LIMIT REACHED.";

    private record Word(String text, int color) { }

    /** Newest first, as the server sends them. */
    private final List<Page> pages;
    private int at;

    ShepherdLogPage(List<Page> pages) {
        this.pages = pages;
    }

    private int older() {
        return at + 1 < pages.size() ? at + 1 : -1;
    }

    private int newer() {
        return at > 0 ? at - 1 : -1;
    }

    /** {@code towardOlder} days back, or forward if negative, stopping at either end. */
    void move(int towardOlder, boolean fast) {
        at = Math.max(0, Math.min(pages.size() - 1, at + towardOlder * (fast ? FAST : 1)));
    }

    void oldest() {
        at = Math.max(0, pages.size() - 1);
    }

    void newest() {
        at = 0;
    }

    void draw(GuiGraphics g, Font font, int ox, int oy, boolean hoverOlder, boolean hoverNewer) {
        ShepherdScreen.panel(g, ox, oy, PANEL_X, PANEL_Y, PANEL_W, PANEL_H, ShepherdScreen.PANEL, ShepherdScreen.STEEL);
        if (pages.isEmpty()) {
            g.drawString(font, I18n.get("shepherd.log.empty"), ox + X, oy + HEADER_Y, ShepherdScreen.GLOW, false);
        } else {
            drawForm(g, font, ox, oy, pages.get(at));
        }
        day(g, font, ox, oy, OLDER_X, older(), "< ", "", hoverOlder);
        day(g, font, ox, oy, NEWER_X, newer(), "", " >", hoverNewer);
    }

    private void day(GuiGraphics g, Font font, int ox, int oy, int x, int page, String before, String after, boolean hover) {
        boolean there = page >= 0;
        ShepherdScreen.panel(g, ox, oy, x, ROW_Y, BUTTON_W, ROW_H, there && hover ? ShepherdScreen.SLOT_HOVER : ShepherdScreen.SLOT, there && hover ? ShepherdScreen.GLOW : ShepherdScreen.STEEL);
        if (there) g.drawString(font, before + I18n.get("shepherd.log.day", pages.get(page).day()) + after, ox + x + 5, oy + ROW_Y + 3, ShepherdScreen.DIM, false);
    }

    private static void drawForm(GuiGraphics g, Font font, int ox, int oy, Page page) {
        int x = ox + X, y = oy + FIRST_Y;
        g.drawString(font, I18n.get("shepherd.log.header", page.day()), x, oy + HEADER_Y, ShepherdScreen.GLOW, false);
        label(g, font, "shepherd.log.pattern", page.signed() ? "shepherd.log.signed" : null, x, y);
        label(g, font, "shepherd.log.status", page.cracked() ? "shepherd.log.nominal_cracked" : "shepherd.log.nominal", x, y + STEP);
        String notes = I18n.get("shepherd.log.notes") + " ";
        g.drawString(font, notes, x, y + 2 * STEP, ShepherdScreen.GLOW2, false);
        if (page.note() == null) return;
        String text = I18n.get(page.note(), page.day());
        boolean limit = text.endsWith(LIMIT);
        if (limit) {
            text = text.substring(0, text.length() - LIMIT.length()).stripTrailing();
            g.drawString(font, LIMIT, x, y + (2 + LAST_ROW) * STEP, ShepherdScreen.GLOW2, false);
        }
        int row = 0, wordX = font.width(notes), rows = limit ? LAST_ROW : LAST_ROW + 1;
        for (var word : words(text)) {
            int width = font.width(word.text());
            if (wordX + width > WIDTH) {
                row++;
                wordX = 0;
            }
            if (row == rows) return;
            g.drawString(font, word.text(), x + wordX, y + (2 + row) * STEP, word.color(), false);
            wordX += width + font.width(" ");
        }
    }

    private static void label(GuiGraphics g, Font font, String label, String value, int x, int y) {
        String shown = I18n.get(label) + " ";
        g.drawString(font, shown, x, y, ShepherdScreen.GLOW2, false);
        if (value != null) g.drawString(font, I18n.get(value), x + font.width(shown), y, ShepherdScreen.TEXT, false);
    }

    /** Her words in white, and the machine's in her dim green. */
    private static List<Word> words(String text) {
        var words = new ArrayList<Word>();
        int from = 0;
        while (from < text.length()) {
            int loop = text.indexOf(LOOP, from);
            for (String word : (loop < 0 ? text.substring(from) : text.substring(from, loop)).strip().split(" ")) {
                if (!word.isEmpty()) words.add(new Word(word, ShepherdScreen.TEXT));
            }
            if (loop < 0) break;
            for (String word : LOOP.split(" ")) words.add(new Word(word, ShepherdScreen.GLOW2));
            from = loop + LOOP.length();
        }
        return words;
    }
}
