package com.frozendawn.client;

import com.frozendawn.init.ModSounds;
import com.frozendawn.shepherd.ShepherdFace;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Her voice during play, as `ambient-lines-mockup.png` draws it: a strip over the hotbar with her face where the badge goes. */
public final class ShepherdPromptStrip {
    private static final int MIN_HEIGHT = 26, PADDING = 8, FROM_BOTTOM = 54, FACE_X = 7, FACE_SCALE = 3, TEXT_X = 38, TEXT_Y = 4, ROW = 11, ROW_WIDTH = 170;
    /** What the panel takes round the text. Past {@link #MAX_ROWS} it widens, up to the screen, so a long line does not climb over what she is showing you. */
    private static final int FRAME_WIDTH = 44, MAX_ROWS = 3, WIDE_ROW_WIDTH = 330, SCREEN_EDGE = 8;
    private static final int PROMPT_EDGE = 0xFF7DF2C0, LINE_EDGE = 0xFF3AA984, BORDER = 0xFF0B1217;
    private static final long NONE = -1;

    private static long started = NONE;
    private static int typed;
    private static List<String> rows = List.of();
    private static int length, rowWidth = ROW_WIDTH;
    private static Mood face = Mood.THINKING;
    private static boolean prompt;

    private ShepherdPromptStrip() {}

    /** "Got a minute?" */
    public static void show() {
        start("shepherd.prompt", Mood.THINKING, null);
        prompt = true;
    }

    /** A line of hers, which replaces whatever is showing. */
    public static void say(String key, Mood mood, String arg) {
        start(key, mood, arg);
    }

    /** Wraps the whole line first, so a word never jumps down a row halfway through typing. */
    private static void start(String key, Mood mood, String arg) {
        var minecraft = Minecraft.getInstance();
        started = minecraft.level == null ? NONE : minecraft.level.getGameTime();
        typed = 0;
        face = mood;
        prompt = false;
        String text = arg == null ? I18n.get(key) : I18n.get(key, arg);
        var splitter = minecraft.font.getSplitter();
        rowWidth = ROW_WIDTH;
        if (splitter.splitLines(text, ROW_WIDTH, Style.EMPTY).size() > MAX_ROWS) {
            rowWidth = Math.max(ROW_WIDTH, Math.min(WIDE_ROW_WIDTH, minecraft.getWindow().getGuiScaledWidth() - FRAME_WIDTH - SCREEN_EDGE * 2));
        }
        rows = new ArrayList<>();
        for (var row : splitter.splitLines(text, rowWidth, Style.EMPTY)) rows.add(row.getString());
        length = rows.stream().mapToInt(String::length).sum();
    }

    public static void clear() {
        started = NONE;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        var minecraft = Minecraft.getInstance();
        if (started == NONE || minecraft.level == null || minecraft.options.hideGui) return;
        int age = (int) (minecraft.level.getGameTime() - started);
        float alpha = ShepherdPolicy.stripAlpha(age, length);
        // Game time jumps backward on a new world.
        if (age < 0 || alpha <= 0) {
            clear();
            return;
        }
        int shown = ShepherdPolicy.typedChars(age, length);
        if (shown > typed) {
            typed = shown;
            if (typed % ShepherdPolicy.BLIP_EVERY == 0) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SHEPHERD_BLIP.get(), 1, 0.5F));
        }
        boolean recording = face == Mood.RECORDING;
        int height = Math.max(MIN_HEIGHT, PADDING + ROW * (rows.size() + (prompt ? 1 : 0)));
        int width = rowWidth + FRAME_WIDTH, x = (g.guiWidth() - width) / 2, y = g.guiHeight() - FROM_BOTTOM - height;
        g.setColor(1, 1, 1, alpha);
        OrsaHudPanel.frame(g, x, y, width, height, recording ? OrsaHudPanel.MUTED_COLOR : prompt ? PROMPT_EDGE : LINE_EDGE, BORDER);
        ShepherdFaceRenderer.draw(g, ShepherdFace.stripFace(face, age, length), 0, false, 0, x + FACE_X, y + (height - 18) / 2, FACE_SCALE);
        int left = shown, rowY = y + TEXT_Y;
        for (String row : rows) {
            g.drawString(minecraft.font, row.substring(0, Math.min(left, row.length())), x + TEXT_X, rowY, recording ? OrsaHudPanel.MUTED_COLOR : OrsaHudPanel.VALUE_COLOR, false);
            left -= Math.min(left, row.length());
            rowY += ROW;
        }
        if (prompt && shown == length) g.drawString(minecraft.font, Component.translatable("shepherd.prompt.hint"), x + TEXT_X, rowY, OrsaHudPanel.MUTED_COLOR, false);
        g.setColor(1, 1, 1, 1);
    }
}
