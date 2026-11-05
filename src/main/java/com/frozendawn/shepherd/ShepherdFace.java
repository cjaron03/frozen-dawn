package com.frozendawn.shepherd;

import java.util.List;
import java.util.Locale;

/** Her face and her flame, as pure logic: lit texels on the 8 by 6 display, and how they move from tick to tick. */
public final class ShepherdFace {
    public static final int WIDTH = 8;
    public static final int HEIGHT = 6;

    private ShepherdFace() {}

    /** What the display can show: a mood, an idle expression, a frame of the logo. */
    public interface Cells {
        boolean lit(int x, int y);
        boolean dim(int x, int y);
    }

    /** A lit texel, as this frame shows it. A blink shuts each eye down to its lowest row; a shift slides the eyes sideways. */
    public static boolean lit(Cells cells, boolean blink, int eyeShift, int x, int y) {
        int from = x - eyeShift;
        if (from < 0 || from >= WIDTH || !cells.lit(from, y)) return false;
        return !(blink && y + 1 < HEIGHT && cells.lit(from, y + 1));
    }

    /** A dim texel, as this frame shows it. Mouth 0 is shut, 1 open, 2 wide; the recording voice has no mouth to move. */
    public static boolean dim(Cells cells, int mouth, int x, int y) {
        if (cells.dim(x, y)) return true;
        if (cells == Mood.RECORDING || cells == Mood.OFFLINE || y != HEIGHT - 1) return false;
        return mouth == 1 && (x == 3 || x == 4) || mouth == 2 && x >= 2 && x <= 5;
    }

    /** How long her eyes shut for when her mood changes, so the change reads as a reaction. */
    public static final int MOOD_BEAT_TICKS = 3;
    /** Her flame's tallest frame, in layers. */
    public static final int FLAME_FRAMES = 4;
    private static final int WINDOW_TICKS = 48;
    private static final int SPUTTER_WINDOW_TICKS = 120;
    private static final int IDLE_START = 8, IDLE_TICKS = 30, IDLE_ONE_IN = 5;
    private static final int ROLL_START = 12, ROLL_FRAME_TICKS = 4, ROLL_ONE_IN = 250;
    /** The terminal's boot sound runs about three seconds, and the moon goes round twice. */
    public static final int LOGO_TICKS = 64;
    public static final int LOGO_FRAMES = 8;
    private static final int LOGO_FRAME_TICKS = 4;
    private static final String[] LOGO_ROWS = {"...##...", "..####.+", "..##+++.", ".+++##..", "+..##...", "........"};
    /** The moon's x, y and cell: lit round the front of the orbit, dim round the back. */
    private static final int[][] LOGO_MOON = {{7, 1, '#'}, {5, 2, '#'}, {3, 3, '#'}, {1, 3, '#'}, {0, 4, '#'}, {1, 1, '+'}, {2, 0, '+'}, {6, 0, '+'}};

    /** What her eyes are doing on one tick. */
    public record Motion(int eyeShift, boolean blink, Idle idle) {
        public Motion(int eyeShift, boolean blink) {
            this(eyeShift, blink, null);
        }

        /** Her expression when she is quiet; speech always shows the mood. */
        public Cells cells(Mood mood, boolean talking) {
            return talking || idle == null ? mood : idle;
        }
    }

    private static int hash(int a, int b) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        return (h ^ h >>> 13) & Integer.MAX_VALUE;
    }

    /** Idle life, the same on her body and the talk screen. {@code seed} keeps two views from moving in lockstep. */
    public static Motion motion(Mood mood, int age, int seed) {
        return motion(mood, age, seed, false);
    }

    /** {@code resting} is a face she has settled on, which keeps its idle life whatever the mood. */
    public static Motion motion(Mood mood, int age, int seed, boolean resting) {
        if (mood == Mood.RECORDING || mood == Mood.OFFLINE) return new Motion(0, false);
        int window = age / WINDOW_TICKS, tick = age % WINDOW_TICKS, h = hash(window, seed);
        var idle = resting || mood == Mood.NEUTRAL ? idle(tick, h) : null;
        if (idle != null) return new Motion(0, false, idle);
        return new Motion(eyeShift(mood, age, window, tick, h), blink(mood, tick, h));
    }

    private static Idle idle(int tick, int h) {
        if (h / 7 % ROLL_ONE_IN == 0) {
            int frame = (tick - ROLL_START) / ROLL_FRAME_TICKS;
            return tick >= ROLL_START && frame < ROLL.size() ? ROLL.get(frame) : null;
        }
        if (h / 1024 % IDLE_ONE_IN != 0 || tick < IDLE_START || tick >= IDLE_START + IDLE_TICKS) return null;
        return EXPRESSIONS.get(h / 8192 % EXPRESSIONS.size());
    }

    private static boolean blink(Mood mood, int tick, int h) {
        if (mood == Mood.ALARMED && h % 3 != 0) return false; // she stares
        int length = mood == Mood.SAD ? 6 : 3;
        int at = h % (WINDOW_TICKS - 12);
        boolean twice = mood == Mood.HAPPY || h % 5 == 0;
        return tick >= at && tick < at + length || twice && tick >= at + length + 2 && tick < at + 2 * length + 2;
    }

    private static int eyeShift(Mood mood, int age, int window, int tick, int h) {
        return switch (mood) {
            case SAD, RECORDING, OFFLINE -> 0;
            // Eyes dart while she is frightened, in bursts.
            case ALARMED -> h % 2 == 0 ? new int[] {0, -1, 0, 1}[age / 5 % 4] : 0;
            // She looks up and away, and drifts back. Her eyes already sit against the right edge.
            case THINKING -> window % 2 == 0 ? 0 : -1;
            default -> h / 8 % 3 == 0 && tick >= 8 && tick < 26 ? (h / 32 % 2 == 0 ? -1 : 1) : 0;
        };
    }

    /** One of the mouth frames while she talks and nobody is typing it out: mostly moving, shut now and then. */
    public static int talkingMouth(int age) {
        return age / 2 % 7 == 6 ? 0 : age / 2 % 3;
    }

    public static int logoFrame(int age) {
        return age / LOGO_FRAME_TICKS % LOGO_FRAMES;
    }

    /** One frame of the ORSA logo, as much of it as 48 texels can hold: a planet, its orbit and the moon riding it. */
    public static Cells logo(int frame) {
        return new LogoFrame(LOGO_MOON[frame]);
    }

    private record LogoFrame(int[] moon) implements Cells {
        private char at(int x, int y) {
            return x == moon[0] && y == moon[1] ? (char) moon[2] : LOGO_ROWS[y].charAt(x);
        }

        @Override public boolean lit(int x, int y) {
            return at(x, y) == '#';
        }

        @Override public boolean dim(int x, int y) {
            return at(x, y) == '+';
        }
    }

    /** Layers of flame on this tick: a restless flicker, with a short sputter out now and then. */
    public static int flameHeight(int age, int seed) {
        int window = age / SPUTTER_WINDOW_TICKS, tick = age % SPUTTER_WINDOW_TICKS, h = hash(window, seed + 1);
        if (h % 2 == 0) {
            int at = h / 2 % (SPUTTER_WINDOW_TICKS - 16);
            if (tick >= at && tick < at + 14) return new int[] {0, 1, 0, 0, 2, 0, 1}[(tick - at) / 2];
        }
        return 2 + hash(age / 2, seed) % (FLAME_FRAMES - 1);
    }

    /** '#' is a lit texel, '+' a dim one, as face-moods-mockup.png draws them. */
    public enum Mood implements Cells {
        NEUTRAL("........", ".##..##.", ".##..##.", "........", "..++++..", "........"),
        AMUSED("........", ".##.....", ".##..##.", "......+.", "..++++..", "........"),
        UNIMPRESSED("........", "........", ".##..##.", "........", "...++...", "........"),
        HAPPY("........", ".##..##.", ".##..##.", ".+....+.", "..++++..", "........"),
        SAD("........", "..#..#..", ".##..##.", "........", "...++...", "..+..+.."),
        ALARMED(".##..##.", ".##..##.", ".##..##.", "........", "..++++..", "..++++.."),
        THINKING("...##.##", "...##.##", "........", "........", "....++..", "........"),
        OFFLINE("........", "........", "........", "........", "........", "........"),
        RECORDING("........", ".++..++.", ".++..++.", "........", "........", "........");

        private final String[] rows;

        Mood(String... rows) {
            this.rows = rows;
        }

        @Override public boolean lit(int x, int y) {
            return rows[y].charAt(x) == '#';
        }

        @Override public boolean dim(int x, int y) {
            return rows[y].charAt(x) == '+';
        }

        /** Its name in a dialogue file. Throws IllegalArgumentException on anything else. */
        public static Mood byName(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** What she does with her face while nothing is happening, from boot-and-idle-mockup.png. */
    public enum Idle implements Cells {
        HEAVY_LIDS("........", ".++..++.", ".##..##.", "........", "..++++..", "........"),
        BROW_UP(".##.....", ".##..##.", ".##..##.", "........", "...++...", "........"),
        LOOKS_UP(".##..##.", ".##..##.", "........", "........", "..++++..", "........"),
        WINK("........", ".##.....", ".##.+++.", "........", "..++++..", "........"),
        SMIRK("........", ".##..##.", ".##..##.", "......+.", "...+++..", "........"),
        ROLL_UP(".##..##.", ".##..##.", "........", "........", "...++...", "........"),
        ROLL_LEFT("##..##..", "##..##..", "........", "........", "...++...", "........"),
        ROLL_RIGHT("..##..##", "..##..##", "........", "........", "...++...", "........"),
        ROLL_DOWN("........", ".##..##.", ".##..##.", "........", ".++++++.", "........");

        private final String[] rows;

        Idle(String... rows) {
            this.rows = rows;
        }

        @Override public boolean lit(int x, int y) {
            return rows[y].charAt(x) == '#';
        }

        @Override public boolean dim(int x, int y) {
            return rows[y].charAt(x) == '+';
        }
    }

    public static final List<Idle> EXPRESSIONS = List.of(Idle.HEAVY_LIDS, Idle.BROW_UP, Idle.LOOKS_UP, Idle.WINK, Idle.SMIRK);
    /** Rare: about once in ten minutes of her being neutral. */
    public static final List<Idle> ROLL = List.of(Idle.ROLL_UP, Idle.ROLL_LEFT, Idle.ROLL_RIGHT, Idle.ROLL_DOWN);
}
