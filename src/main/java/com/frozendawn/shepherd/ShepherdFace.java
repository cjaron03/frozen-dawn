package com.frozendawn.shepherd;

import java.util.Locale;

/** Her face: lit texels on the 8 by 6 display, the same 48 on her body and, large, on the talk screen. */
public final class ShepherdFace {
    public static final int WIDTH = 8;
    public static final int HEIGHT = 6;

    private ShepherdFace() {}

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
