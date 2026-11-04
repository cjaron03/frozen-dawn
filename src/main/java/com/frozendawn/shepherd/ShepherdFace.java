package com.frozendawn.shepherd;

import java.util.Locale;

/** Her face: lit texels on the 8 by 6 display, the same 48 on her body and, large, on the talk screen. */
public final class ShepherdFace {
    public static final int WIDTH = 8;
    public static final int HEIGHT = 6;

    private ShepherdFace() {}

    /** A lit texel, as this frame shows it. A blink shuts each eye down to its lowest row. */
    public static boolean lit(Mood mood, boolean blink, int x, int y) {
        return mood.lit(x, y) && !(blink && y + 1 < HEIGHT && mood.lit(x, y + 1));
    }

    /** A dim texel, as this frame shows it. Mouth 0 is shut, 1 open, 2 wide; the recording voice has no mouth to move. */
    public static boolean dim(Mood mood, int mouth, int x, int y) {
        if (mood.dim(x, y)) return true;
        if (mood == Mood.RECORDING || mood == Mood.OFFLINE || y != HEIGHT - 1) return false;
        return mouth == 1 && (x == 3 || x == 4) || mouth == 2 && x >= 2 && x <= 5;
    }

    /** '#' is a lit texel, '+' a dim one, as face-moods-mockup.png draws them. */
    public enum Mood {
        NEUTRAL("........", ".##..##.", ".##..##.", "........", "..++++..", "........"),
        AMUSED("........", ".##.....", ".##..##.", ".....+..", "..+++...", "........"),
        UNIMPRESSED("........", "........", ".##..##.", "........", "...++...", "........"),
        HAPPY("........", ".##..##.", ".##..##.", ".+....+.", "..++++..", "........"),
        SAD("........", "..#..#..", ".#....#.", "........", "..++++..", ".+....+."),
        ALARMED(".##..##.", ".##..##.", ".##..##.", "........", "...++...", "...++..."),
        THINKING("...##.##", "...##.##", "........", "........", "...++...", "........"),
        OFFLINE("........", "........", "........", "........", "........", "........"),
        RECORDING("........", ".++..++.", ".++..++.", "........", "........", "........");

        private final String[] rows;

        Mood(String... rows) {
            this.rows = rows;
        }

        public boolean lit(int x, int y) {
            return rows[y].charAt(x) == '#';
        }

        public boolean dim(int x, int y) {
            return rows[y].charAt(x) == '+';
        }

        /** Its name in a dialogue file. Throws IllegalArgumentException on anything else. */
        public static Mood byName(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }
}
