package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdFace.Cells;
import com.frozendawn.shepherd.ShepherdFace.Idle;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import org.junit.jupiter.api.Test;

class ShepherdFlowTest {
    private static String rows(Cells cells) {
        var out = new StringBuilder();
        for (int y = 0; y < ShepherdFace.HEIGHT; y++) {
            for (int x = 0; x < ShepherdFace.WIDTH; x++) out.append(cells.lit(x, y) ? '#' : cells.dim(x, y) ? '+' : '.');
            out.append('/');
        }
        return out.toString();
    }

    /** One lit texel, for watching a single walker. */
    private record Dot(int at) implements Cells {
        @Override public boolean lit(int x, int y) {
            return x == at && y == 0;
        }

        @Override public boolean dim(int x, int y) {
            return false;
        }
    }

    private static ShepherdFlow settled(Cells face) {
        var flow = new ShepherdFlow();
        flow.shown(face, 0);
        return flow;
    }

    @Test void theFirstFaceJustShows() {
        assertSame(Mood.SAD, new ShepherdFlow().shown(Mood.SAD, 0));
    }

    @Test void flowEndsOnTheNewFace() {
        for (Mood from : Mood.values()) {
            for (Mood to : Mood.values()) {
                var flow = settled(from);
                flow.shown(to, 40);
                assertSame(to, flow.shown(to, 40 + 2 * ShepherdFace.WIDTH + 2), from + " to " + to);
            }
        }
    }

    @Test void aFrameHoldsTwoTicks() {
        var flow = settled(Mood.NEUTRAL);
        var first = flow.shown(Mood.BEAMING, 10);
        assertNotEquals(rows(Mood.BEAMING), rows(first));
        assertNotEquals(rows(Mood.NEUTRAL), rows(first));
        assertEquals(rows(first), rows(flow.shown(Mood.BEAMING, 11)), "Two ticks a frame");
    }

    @Test void flowMovesOneTexelAFrame() {
        var frames = ShepherdFlow.frames(new Dot(1), new Dot(5));
        assertEquals(3, frames.size());
        for (int i = 0; i < frames.size(); i++) assertEquals(rows(new Dot(2 + i)), rows(frames.get(i)), "frame " + i);
    }

    @Test void flowStartsFromWhatIsShowing() {
        var flow = settled(Mood.NEUTRAL);
        flow.shown(Mood.BEAMING, 10);
        var midway = flow.shown(Mood.BEAMING, 12);
        var next = flow.shown(Mood.SAD, 13);
        assertEquals(rows(ShepherdFlow.frames(midway, Mood.SAD).getFirst()), rows(next), "A new flow picks up where the last one was");
    }

    @Test void flowLeavesAnUnchangedFaceAlone() {
        var flow = settled(Mood.GRIM);
        for (int tick = 1; tick < 60; tick++) assertSame(Mood.GRIM, flow.shown(Mood.GRIM, tick));
    }

    @Test void idleExpressionsPassThroughWithoutOne() {
        var flow = settled(Mood.NEUTRAL);
        assertSame(Idle.WINK, flow.shown(Idle.WINK, 1));
        assertSame(Mood.NEUTRAL, flow.shown(Mood.NEUTRAL, 2));
    }

    @Test void theTraceFlowsInOnceAndThenRuns() {
        var flow = settled(Mood.NEUTRAL);
        assertNotEquals(rows(ShepherdFace.trace(1)), rows(flow.shown(ShepherdFace.trace(1), 1)), "Her face becomes the trace");
        int after = 1 + 2 * ShepherdFace.WIDTH + 2;
        flow.shown(ShepherdFace.trace(after), after);
        for (int tick = after; tick < after + 30; tick++) assertEquals(rows(ShepherdFace.trace(tick)), rows(flow.shown(ShepherdFace.trace(tick), tick)), "at " + tick);
    }

    @Test void aMoodFlowsOutOfAThinkingWait() {
        var flow = settled(Mood.GRIM);
        var home = ShepherdFace.thinking(ShepherdFace.thinkingTicks(2) - 1, ShepherdFace.thinkingTicks(2));
        assertSame(home, flow.shown(home, 5));
        assertNotSame(Mood.GRIM, flow.shown(Mood.GRIM, 6), "The same mood still flows in, as the blink did");
    }
}
