package com.frozendawn.lore;

import java.util.List;

/**
 * CH-37's loop, played live during the Heart: four beats, each shown once per world. Every line is Record III's,
 * named by its page and line there, so the overlay inks it in from the raw line at the same place.
 */
public enum ThaevenLoopBeat {
    CROSSED(line("crossed", 0, 0)),
    CROSSED_AGAIN(line("crossed_again", 0, 1)),
    BODY_BROKE(line("body_broke", 3, 0), line("pieces_remained", 3, 1)),
    RETURNING(line("returning", 3, 5));

    /** The broken beat's second line follows its first by this many ticks. */
    public static final int SECOND_LINE_DELAY = 20;

    private final List<Line> lines;

    ThaevenLoopBeat(Line... lines) {
        this.lines = List.of(lines);
    }

    public List<Line> lines() {
        return lines;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static ThaevenLoopBeat byId(int id) {
        ThaevenLoopBeat[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }

    private static Line line(String name, int page, int line) {
        return new Line("overlay.frozendawn.thaeven.loop." + name, page, line);
    }

    /** One line: its lang key, and its page and line in Record III. */
    public record Line(String key, int page, int line) {
    }
}
