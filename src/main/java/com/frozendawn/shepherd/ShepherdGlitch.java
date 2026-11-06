package com.frozendawn.shepherd;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/** The three seconds before her stop screen: noise, then stillness. All of it follows from an age and a seed, so the screen and her body need no state from the server. */
public final class ShepherdGlitch {
    public static final String BREAK = "shepherd.crash.break", VERDICT = "shepherd.crash.verdict";
    public static final int ROW = 44, ROWS = 3, MAX_SHIFT = 40;
    private static final int POUR_PER_TICK = 5, MAX_TEARS = 14, MAX_BLOCKS = 7, SHIFT_FROM_AGE = 12;
    private static final float MIN_REACH = 1 / 16F, MAX_REACH = 4 / 16F;
    private static final String NOISE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Cell[] COLOURS = {Cell.LIT, Cell.DIM, Cell.STOP, Cell.STOP_DIM, Cell.STOP_TEXT};
    private static final Cell[] BLOCKS = {Cell.STOP, Cell.STOP_DIM, Cell.LIT, Cell.BLANK};

    public enum Beat { NOISE, STILL, STOP }

    public enum Cell { BLANK, LIT, DIM, STOP, STOP_DIM, STOP_TEXT }

    public record Tear(int top, int bottom, int shift) {}

    public record Block(int x, int y, int w, int h, Cell cell) {}

    private ShepherdGlitch() {}

    public static Beat beat(float age) {
        if (age < ShepherdPolicy.CRASH_NOISE_TICKS) return Beat.NOISE;
        return age < ShepherdPolicy.CRASH_LEAD_TICKS ? Beat.STILL : Beat.STOP;
    }

    private static float strength(float age) {
        return Math.min(1, age / ShepherdPolicy.CRASH_NOISE_TICKS);
    }

    /** Her phrase up to where it breaks, then letters and numbers until the panel is full. */
    public static String line(String phrase, int age, long seed) {
        if (age < phrase.length()) return phrase.substring(0, age + 1);
        var rng = new Random(seed);
        var line = new StringBuilder(phrase);
        int noise = Math.min((age - phrase.length() + 1) * POUR_PER_TICK, ROW * ROWS - phrase.length());
        for (int i = 0; i < noise; i++) line.append(NOISE.charAt(rng.nextInt(NOISE.length())));
        return line.toString();
    }

    /** One texel of her display: her neutral face, slipping and fading, with more of each row overwritten by colour. A frame holds for two ticks. */
    public static Cell cell(int age, long seed, int x, int y) {
        float strength = strength(age);
        long frame = seed * 1000003L + age / 2 * 16L + y;
        var row = new Random(frame);
        boolean strip = row.nextFloat() < strength;
        int start = row.nextInt(7), end = start + 2 + row.nextInt(6), shift = age > SHIFT_FROM_AGE ? row.nextInt(5) - 2 : 0;
        var texel = new Random(frame * 8 + x);
        if (strip && x >= start && x < end) return COLOURS[texel.nextInt(COLOURS.length)];
        if (texel.nextFloat() < strength) return Cell.BLANK;
        var face = ShepherdFace.Mood.NEUTRAL;
        int from = Math.floorMod(x + shift, ShepherdFace.WIDTH);
        return face.lit(from, y) ? Cell.LIT : face.dim(from, y) ? Cell.DIM : Cell.BLANK;
    }

    /** Bands from top to bottom, with no gaps, each sliding sideways. */
    public static List<Tear> tears(int age, long seed, int top, int bottom) {
        float strength = strength(age);
        var rng = new Random(seed * 7 + age / 2);
        var cuts = new TreeSet<Integer>();
        for (int i = 0; i < (int) (MAX_TEARS * strength); i++) cuts.add(top + 1 + rng.nextInt(bottom - top - 1));
        cuts.add(bottom);
        List<Tear> tears = new ArrayList<>();
        int from = top;
        for (int cut : cuts) {
            int reach = (int) (MAX_SHIFT * strength);
            tears.add(new Tear(from, cut, cuts.size() > 1 && rng.nextBoolean() ? rng.nextInt(reach * 2 + 1) - reach : 0));
            from = cut;
        }
        return tears;
    }

    /** Flecks of colour over the tears. */
    public static List<Block> blocks(int age, long seed, int left, int top, int right, int bottom) {
        var rng = new Random(seed * 13 + age / 2);
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < (int) (MAX_BLOCKS * strength(age)); i++) {
            int w = 8 + rng.nextInt(33), h = 1 + rng.nextInt(4);
            blocks.add(new Block(left + rng.nextInt(right - left - w), top + rng.nextInt(bottom - top - h), w, h, BLOCKS[rng.nextInt(BLOCKS.length)]));
        }
        return blocks;
    }

    /** How far her body is thrown along one axis, in blocks. */
    public static float shake(float age, int axis) {
        if (beat(age) != Beat.NOISE) return 0;
        var rng = new Random((int) age * 31L + axis * 7L + 1);
        return (rng.nextFloat() * 2 - 1) * (MIN_REACH + (MAX_REACH - MIN_REACH) * strength(age));
    }
}
