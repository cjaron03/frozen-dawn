package com.frozendawn.homo;

import net.minecraft.util.Mth;

/**
 * The pace of the Heart's last memory node: its title, then one line every 70 ticks, each fading in
 * over 18, a 100-tick hold after the last, and a 35-tick fade out. The Heart's music leaves while it shows.
 */
public final class HeartMemoryReveal {
    public static final int LINE_INTERVAL_TICKS = 70;
    public static final int LINE_FADE_IN_TICKS = 18;
    public static final int HOLD_TICKS = 100;
    public static final int FADE_OUT_TICKS = 35;
    public static final float MUSIC_VOLUME = 0.92F;
    /** Font treats an alpha byte under 4 as unset and draws the text opaque. */
    private static final int MIN_FONT_ALPHA = 4;
    private static final float MUSIC_OUT_STEP = MUSIC_VOLUME / 20.0F;
    private static final float MUSIC_IN_STEP = MUSIC_VOLUME / 40.0F;

    private HeartMemoryReveal() {
    }

    public static int duration(int lines) {
        return Math.max(1, lines) * LINE_INTERVAL_TICKS + HOLD_TICKS + FADE_OUT_TICKS;
    }

    /** How many lines have begun to show by this age, in ticks since the panel opened. */
    public static int linesShown(float age, int lines) {
        return Mth.clamp((int) Math.floor(age / LINE_INTERVAL_TICKS), 0, lines);
    }

    /** One line's own fade in, before the panel's fade out. */
    public static float lineAlpha(float age, int index) {
        return Mth.clamp((age - (index + 1) * LINE_INTERVAL_TICKS) / LINE_FADE_IN_TICKS, 0.0F, 1.0F);
    }

    /** The title and backing: in over the first 18 ticks, out over the last 35. */
    public static float panelAlpha(float age, int duration) {
        return Mth.clamp(age / LINE_FADE_IN_TICKS, 0.0F, 1.0F)
                * Mth.clamp((duration - age) / FADE_OUT_TICKS, 0.0F, 1.0F);
    }

    /** One tick of the Heart's music toward silence while ducked, or back toward its level. */
    public static float musicVolume(float volume, boolean ducked) {
        return ducked ? Mth.approach(volume, 0.0F, MUSIC_OUT_STEP)
                : Mth.approach(volume, MUSIC_VOLUME, MUSIC_IN_STEP);
    }

    /** Whether text at this alpha can be drawn without the font forcing it opaque. */
    public static boolean visible(float alpha) {
        return Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) >= MIN_FONT_ALPHA;
    }

    /** One client tick of the panel's remaining time; it holds while the game is paused. */
    public static int countDown(int ticks, boolean paused) {
        return paused ? ticks : Math.max(0, ticks - 1);
    }
}
