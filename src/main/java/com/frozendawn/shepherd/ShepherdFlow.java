package com.frozendawn.shepherd;

import com.frozendawn.shepherd.ShepherdFace.Cells;
import com.frozendawn.shepherd.ShepherdFace.Idle;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One view's face between two moods: each texel slides to the nearest place in the new face, and any left over fade, from face-transitions-mockup.gif. */
public final class ShepherdFlow {
    private static final int TICKS_A_FRAME = 2;
    private static final int WIDTH = ShepherdFace.WIDTH, HEIGHT = ShepherdFace.HEIGHT;

    private Cells showing;
    private Mood face;
    private List<Cells> frames = List.of();
    private int started;

    /** What to draw on this tick. A new face starts a flow from whatever is showing. An idle expression belongs to the face it plays on; anything else just shows. */
    public Cells shown(Cells wanted, int tick) {
        Mood next = wanted instanceof Idle ? face : wanted instanceof Mood mood ? mood : ShepherdFace.tracing(wanted) ? Mood.RECORDING : null;
        if (next == null && !(wanted instanceof Idle)) {
            face = null;
            frames = List.of();
            return showing = wanted;
        }
        if (next != face && showing != null) {
            frames = frames(showing, wanted);
            started = tick;
        }
        face = next;
        int frame = (tick - started) / TICKS_A_FRAME;
        if (frame < frames.size()) return showing = frames.get(frame);
        frames = List.of();
        return showing = wanted;
    }

    private record Move(int from, int to, int distance) {}

    private static final class Walker {
        int x, y, level;
        final int toX, toY, wanted;

        Walker(int from, int to, int level, int wanted) {
            x = from % WIDTH;
            y = from / WIDTH;
            this.level = level;
            toX = to % WIDTH;
            toY = to / WIDTH;
            this.wanted = wanted;
        }

        boolean step() {
            x += Integer.signum(toX - x);
            y += Integer.signum(toY - y);
            level += Integer.signum(wanted - level);
            return x == toX && y == toY && level == wanted;
        }
    }

    /** The frames between two faces, without either end. Nearest pairs first and ties in a fixed order, so the same two faces always flow the same way. */
    public static List<Cells> frames(Cells from, Cells to) {
        int[] source = levels(from), target = levels(to);
        var moves = new ArrayList<Move>();
        for (int p = 0; p < source.length; p++) {
            for (int q = 0; q < target.length; q++) {
                if (source[p] > 0 && target[q] > 0) moves.add(new Move(p, q, Math.max(Math.abs(p % WIDTH - q % WIDTH), Math.abs(p / WIDTH - q / WIDTH))));
            }
        }
        moves.sort(Comparator.comparingInt(Move::distance).thenComparingInt(move -> move.from() % WIDTH).thenComparingInt(move -> move.from() / WIDTH)
                .thenComparingInt(move -> move.to() % WIDTH).thenComparingInt(move -> move.to() / WIDTH));
        var walkers = new ArrayList<Walker>();
        var moved = new boolean[source.length];
        var taken = new boolean[target.length];
        for (var move : moves) {
            if (moved[move.from()] || taken[move.to()]) continue;
            moved[move.from()] = taken[move.to()] = true;
            walkers.add(new Walker(move.from(), move.to(), source[move.from()], target[move.to()]));
        }
        var leaving = new HashMap<Integer, Integer>();
        var arriving = new HashMap<Integer, Integer>();
        for (int i = 0; i < source.length; i++) {
            if (source[i] > 0 && !moved[i]) leaving.put(i, source[i]);
            if (target[i] > 0 && !taken[i]) arriving.put(i, 0);
        }
        var out = new ArrayList<Cells>();
        for (int step = 0; step < WIDTH; step++) {
            boolean done = true;
            var levels = new int[source.length];
            for (var walker : walkers) {
                done &= walker.step();
                int at = walker.y * WIDTH + walker.x;
                levels[at] = Math.max(levels[at], walker.level);
            }
            for (var key : List.copyOf(leaving.keySet())) {
                int left = leaving.get(key) - 1;
                if (left > 0) {
                    leaving.put(key, left);
                    levels[key] = Math.max(levels[key], left);
                    done = false;
                } else {
                    leaving.remove(key);
                }
            }
            for (Map.Entry<Integer, Integer> entry : arriving.entrySet()) {
                int level = Math.min(entry.getValue() + 1, target[entry.getKey()]);
                entry.setValue(level);
                done &= level == target[entry.getKey()];
                levels[entry.getKey()] = Math.max(levels[entry.getKey()], level);
            }
            if (done) break;
            out.add(new Frame(levels));
        }
        return out;
    }

    /** 2 lit, 1 dim, 0 dark, a texel each. */
    private static int[] levels(Cells cells) {
        var levels = new int[WIDTH * HEIGHT];
        for (int i = 0; i < levels.length; i++) levels[i] = cells.lit(i % WIDTH, i / WIDTH) ? 2 : cells.dim(i % WIDTH, i / WIDTH) ? 1 : 0;
        return levels;
    }

    private record Frame(int[] levels) implements Cells {
        @Override public boolean lit(int x, int y) {
            return levels[y * WIDTH + x] == 2;
        }

        @Override public boolean dim(int x, int y) {
            return levels[y * WIDTH + x] == 1;
        }
    }
}
