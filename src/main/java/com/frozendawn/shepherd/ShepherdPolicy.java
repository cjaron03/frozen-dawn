package com.frozendawn.shepherd;

import com.frozendawn.vault.TerminusVaultBuilder;

/** Her numbers, and the one rule about when a conversation has to stop. */
public final class ShepherdPolicy {
    public static final int MAX_HEALTH = 40;
    public static final int TELEPORT_BLOCKS = 24;
    public static final int REBOOT_TICKS = 2400;
    public static final int TALK_RANGE = 8;
    public static final int DANGER_RANGE = 12;
    public static final int TICKS_PER_CHAR = 2;
    public static final int BLIP_EVERY = 3;
    public static final int HEARD_BLOCKS = 16;
    public static final int CALM_HOSTILE_BLOCKS = 24;
    public static final int CALM_HURT_TICKS = 600;
    public static final float COLD_LINE_C = -10;
    public static final int PROMPT_REPEAT_TICKS = 1200;
    public static final int PROMPT_SHOWS = 3;
    public static final int STRIP_HOLD_TICKS = 80;
    public static final int STRIP_FADE_TICKS = 20;
    public static final double ASK_BLOCKS = 2.5;
    public static final int ASK_TICKS = 100;
    public static final int REACT_TICKS = 60;
    public static final int AMBIENT_GAP_TICKS = 3600, SILENCE_TICKS = 24000;
    public static final int IMAGINED_AURORA_PHASE = 4;
    public static final int WATCH_BLOCKS = 16;
    public static final int STILL_TICKS = 300, LOOK_TICKS = 100, CURIOUS_GAP_TICKS = 1200, CURIOUS_BLOCKS = 8, STILL_BLOCKS = 3;
    public static final int FLINCH_TICKS = 20;
    public static final int GLAD_TICKS = 40;
    public static final int GLAD_AWAY_TICKS = 1200;
    public static final int DAY_TICKS = 24000;
    public static final int NIGHT_STARTS = 13000, NIGHT_ENDS = 23000;
    public static final float WARM_SHELTER_C = 10, FROZEN_C = -25;
    public static final int WARM_HEATER_BLOCKS = 16, FROZEN_HEATER_BLOCKS = 8;
    public static final int WARMTH_BLOCKS = 4;
    public static final float WARMTH_C = 5f;

    private ShepherdPolicy() {}

    public static int typedChars(int age, int length) {
        return Math.min(length, age / TICKS_PER_CHAR);
    }

    /** Solid while it types and holds, then gone over the fade. */
    public static float stripAlpha(int age, int length) {
        int fading = age - length * TICKS_PER_CHAR - STRIP_HOLD_TICKS;
        return fading <= 0 ? 1 : Math.max(0, 1 - (float) fading / STRIP_FADE_TICKS);
    }

    /** Whether she would interrupt: nothing hostile in range and nobody hurt lately. */
    public static boolean calm(double nearestHostile, long ticksSinceHurt) {
        return nearestHostile > CALM_HOSTILE_BLOCKS && ticksSinceHurt >= CALM_HURT_TICKS;
    }

    public static boolean night(long dayTime) {
        long time = dayTime % DAY_TICKS;
        return time >= NIGHT_STARTS && time < NIGHT_ENDS;
    }

    /** What her heater port gives someone this far away. */
    public static float warmth(double distance, boolean offline) {
        return !offline && distance <= WARMTH_BLOCKS ? WARMTH_C : 0;
    }

    /** Whether a personal conversation could land: nobody near her is freezing or short of air. */
    public static boolean comfortable(float coldestResolved, boolean airFailing) {
        return coldestResolved > COLD_LINE_C && !airFailing;
    }

    /** Hall-relative: the cot room's side of the last door, on the way out. */
    public static boolean atLastDoor(int x, int y, int z) {
        return x >= 96 && x <= 100 && Math.abs(z) <= 3 && y > TerminusVaultBuilder.FLOOR && y < TerminusVaultBuilder.CEILING;
    }

    /** Hall-relative: the landing under the bore, and the ladder up it. */
    public static boolean atVaultExit(int x, int y, int z) {
        return Math.abs(x) <= 2 && Math.abs(z) <= 2 && y > TerminusVaultBuilder.FLOOR && y <= 0;
    }

    /** The speaker walked off, was hurt, or has a hostile on them within range. */
    public static boolean talkMustClose(double distance, boolean hurt, double nearestHostileOnSpeaker) {
        return distance > TALK_RANGE || hurt || nearestHostileOnSpeaker <= DANGER_RANGE;
    }
}
