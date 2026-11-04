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
    public static final int PROMPT_REPEAT_TICKS = 1200;
    public static final int PROMPT_SHOWS = 3;
    public static final int STRIP_HOLD_TICKS = 80;
    public static final int STRIP_FADE_TICKS = 20;

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
