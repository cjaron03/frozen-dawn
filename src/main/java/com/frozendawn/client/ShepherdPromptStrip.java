package com.frozendawn.client;

import com.frozendawn.init.ModSounds;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdPolicy;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

/** "Got a minute?" as `ambient-lines-mockup.png` draws it: a strip over the hotbar with her face where the badge goes. */
public final class ShepherdPromptStrip {
    private static final int WIDTH = 214, HEIGHT = 30, FROM_BOTTOM = 54, FACE_X = 7, FACE_SCALE = 3, TEXT_X = 38, TEXT_Y = 4, ROW = 11;
    private static final int GLOW = 0xFF7DF2C0, EDGE = 0xFF0B1217;
    private static final long NONE = -1;

    private static long started = NONE;
    private static int typed;

    private ShepherdPromptStrip() {}

    public static void show() {
        var level = Minecraft.getInstance().level;
        started = level == null ? NONE : level.getGameTime();
        typed = 0;
    }

    public static void clear() {
        started = NONE;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        var minecraft = Minecraft.getInstance();
        if (started == NONE || minecraft.level == null || minecraft.options.hideGui) return;
        String line = I18n.get("shepherd.prompt");
        int age = (int) (minecraft.level.getGameTime() - started);
        float alpha = ShepherdPolicy.stripAlpha(age, line.length());
        // Game time jumps backward on a new world.
        if (age < 0 || alpha <= 0) {
            clear();
            return;
        }
        int shown = ShepherdPolicy.typedChars(age, line.length());
        if (shown > typed) {
            typed = shown;
            if (typed % ShepherdPolicy.BLIP_EVERY == 0) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SHEPHERD_BLIP.get(), 1, 0.5F));
        }
        int x = (g.guiWidth() - WIDTH) / 2, y = g.guiHeight() - FROM_BOTTOM - HEIGHT;
        g.setColor(1, 1, 1, alpha);
        OrsaHudPanel.frame(g, x, y, WIDTH, HEIGHT, GLOW, EDGE);
        ShepherdFaceRenderer.draw(g, Mood.THINKING, 0, false, 0, x + FACE_X, y + (HEIGHT - 18) / 2, FACE_SCALE);
        g.drawString(minecraft.font, line.substring(0, shown), x + TEXT_X, y + TEXT_Y, OrsaHudPanel.VALUE_COLOR, false);
        if (shown == line.length()) {
            g.drawString(minecraft.font, Component.translatable("shepherd.prompt.hint"), x + TEXT_X, y + TEXT_Y + ROW, OrsaHudPanel.MUTED_COLOR, false);
        }
        g.setColor(1, 1, 1, 1);
    }
}
