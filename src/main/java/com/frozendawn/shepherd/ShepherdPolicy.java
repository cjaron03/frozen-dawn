package com.frozendawn.shepherd;

import com.frozendawn.phase.PhaseManager;
import com.frozendawn.vault.TerminusVaultBuilder;
import java.util.Locale;
import java.util.regex.Pattern;

/** Her numbers, and the one rule about when a conversation has to stop. */
public final class ShepherdPolicy {
    public static final int MAX_HEALTH = 40;
    public static final int TELEPORT_BLOCKS = 24;
    public static final int REBOOT_TICKS = 2400;
    /** The word: the stop screen, then the logo. Her drop is drawn, so it never moves her. */
    public static final int CRASH_TICKS = 160, CRASH_DROP_TICKS = 10;
    /** She comes apart, then everything stops, and only then the stop screen's eight seconds. */
    public static final int CRASH_NOISE_TICKS = 40, CRASH_STILL_TICKS = 20, CRASH_LEAD_TICKS = CRASH_NOISE_TICKS + CRASH_STILL_TICKS, CRASH_DOWN_TICKS = CRASH_LEAD_TICKS + CRASH_TICKS;
    public static final float CRASH_DROP_BLOCKS = 0.5F;
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
    /** A day between personal conversations, so one never lands on the heels of the last. */
    public static final int PERSONAL_GAP_TICKS = 24000;
    /** She does not bring up the pit with someone already at it. */
    public static final int PIT_PROMPT_BLOCKS = 96;
    /** How near the husk she has to be to know it for what it is. */
    public static final int NINE_SEEN_BLOCKS = 12;
    /** Wider than {@link #NINE_SEEN_BLOCKS}, so her drifting on the line does not raise it again and again. */
    public static final int NINE_LEFT_BLOCKS = 24;
    /** High enough to call it the top. A tower counts. */
    public static final int SKY_HIGH_Y = 110;
    public static final float STARS_SHOW_AT = 0.5F;
    /** A sky with this much rain in it shows her nothing. */
    public static final float AURORA_RAIN_BELOW = 0.2F;
    public static final int STRIP_HOLD_TICKS = 80;
    public static final int STRIP_FADE_TICKS = 20;
    public static final double ASK_BLOCKS = 2.5;
    public static final int ASK_TICKS = 100;
    public static final int REACT_TICKS = 60;
    public static final int AMBIENT_GAP_TICKS = 3600, SILENCE_TICKS = 24000, QUIET_SPELL_TICKS = 6000;
    public static final int IMAGINED_AURORA_PHASE = 4;
    public static final int FIGHT_LINE_TICKS = 2400;
    public static final int SWARM_HOSTILES = 4, YOUR_PART_TICKS = 6000, WALL_BLOCKS = 32;
    public static final int WATCH_BLOCKS = 16;
    public static final int STILL_TICKS = 300, LOOK_TICKS = 100, CURIOUS_GAP_TICKS = 1200, CURIOUS_BLOCKS = 8, STILL_BLOCKS = 3;
    public static final int FLINCH_TICKS = 20;
    /** Two hearts: how little someone has left for her to call it close. */
    public static final float CLOSE_CALL_HEALTH = 4;
    public static final int GLAD_TICKS = 40;
    public static final int GLAD_AWAY_TICKS = 1200;
    public static final int DAY_TICKS = 24000;
    /** Her gag, at most this often. */
    public static final int GAG_EVERY_TICKS = 3 * DAY_TICKS;
    /** How long since anyone talked to her before she gets up to anything of her own. */
    public static final int MOMENT_IDLE_TICKS = 2400;
    /** How long she keeps up what she thinks nobody sees, and how soon she tries again if they didn't. */
    public static final int CAUGHT_WINDOW_TICKS = 200, CAUGHT_RETRY_TICKS = 6000;
    /** Their view has to point this close to her to catch her, as a cosine: about 45 degrees. */
    public static final double LOOKING_AT = 0.7;
    /** How far a heater's burn reaches, as {@code ReturnedEntity} has it. */
    public static final double HEATER_BURN_BLOCKS = 4;
    /** How many greetings rotate at Close, one a day. */
    public static final int GREETINGS = 7;
    /** "We don't have time." She lets it go for this long. */
    public static final int SKY_AGAIN_TICKS = 3 * DAY_TICKS;
    public static final int ENOCH_DAYS = 14, OFFLINE_DAYS_FLOOR = 2;
    public static final int NIGHT_STARTS = 13000, NIGHT_ENDS = 23000;
    public static final float WARM_SHELTER_C = 10, FROZEN_C = -25;
    public static final int WARM_HEATER_BLOCKS = 16, FROZEN_HEATER_BLOCKS = 8;
    public static final int WARMTH_BLOCKS = 4;
    public static final float WARMTH_C = 5f;

    private static final Pattern LESS_PLURAL = Pattern.compile("\\bless [a-z]+s\\b");
    private static final String[] BEARINGS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

    private ShepherdPolicy() {}

    /** The compass word for a way of going: +x east, +z south, as the game has it. */
    public static String bearing(int dx, int dz) {
        double turns = StrictMath.atan2(dx, -dz) / (2 * StrictMath.PI);
        return BEARINGS[Math.floorMod((int) StrictMath.round(turns * BEARINGS.length), BEARINGS.length)];
    }

    /** Phase 5 and early phase 6 draw no sky at all, and from phase 6 mid the stars fade in. Bright enough to be worth the climb. */
    public static boolean starsShow(int phase, float progress) {
        return PhaseManager.isPhase6MidOrLater(phase, progress) && PhaseManager.getPhase6MidFadeProgress(progress) >= STARS_SHOW_AT;
    }

    /** Whether the world's clock at this point had not yet brought the stars back: a clear night then was the sky as it used to be. */
    public static boolean beforeTheStars(long apocalypseTicks, int totalDays) {
        int day = (int) Math.floorDiv(apocalypseTicks, (long) DAY_TICKS);
        return !PhaseManager.isPhase6MidOrLater(PhaseManager.getPhase(day, totalDays), PhaseManager.getProgress(day, totalDays));
    }

    /** The day a game time falls on, as she says it. */
    public static long day(long gameTime) {
        return Math.max(1, Math.floorDiv(gameTime, DAY_TICKS) + 1);
    }

    /** How long her clock says she was out: Enoch's weeks off the day she woke. Never fewer than two, so it stays "days". */
    public static int offlineDays(long wokeAt) {
        return (int) Math.max(OFFLINE_DAYS_FLOOR, day(wokeAt) - ENOCH_DAYS);
    }

    /** A false hit costs one odd line. */
    public static boolean lessBeforeAPlural(String text) {
        return LESS_PLURAL.matcher(text.toLowerCase(Locale.ROOT)).find();
    }

    public static int groundBlocks(int dx, int dz) {
        return (int) StrictMath.round(StrictMath.sqrt((double) dx * dx + (double) dz * dz));
    }

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
