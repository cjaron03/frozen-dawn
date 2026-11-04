package com.frozendawn.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frozendawn.vault.TwoPersonRule.Phase;
import com.frozendawn.vault.TwoPersonRule.Pose;
import org.junit.jupiter.api.Test;

class TwoPersonRuleTest {
    private static final int A = 0, B = 1;
    private final TwoPersonRule rule = new TwoPersonRule();
    private long now;

    private static Pose at(int x) { return new Pose(x, 0, 0, x, 0); }
    private void tick(Pose body) { rule.tick(++now, body); }
    /** A whole take, the recorder walking one block a tick and holding {@code key} from {@code from} on. */
    private void record(int key, int from) {
        assertTrue(rule.press());
        for (int i = 0; i < TwoPersonRule.TAKE_TICKS; i++) {
            if (i >= from) rule.hold(key, now, true);
            tick(at(i));
        }
    }

    @Test void pressingRecordsTwentySecondsThenPlaysThemBackInOrder() {
        assertEquals(400, TwoPersonRule.TAKE_TICKS);
        assertEquals(Phase.IDLE, rule.phase());
        assertTrue(rule.press());
        assertEquals(Phase.RECORDING, rule.phase());
        for (int i = 0; i < TwoPersonRule.TAKE_TICKS; i++) { assertNull(rule.ghost()); tick(at(i)); }
        assertEquals(Phase.PLAYING, rule.phase());
        for (int i = 0; i < TwoPersonRule.TAKE_TICKS; i++) {
            assertNotNull(rule.ghost());
            assertEquals(at(i), rule.ghost().pose());
            tick(null);
        }
        assertEquals(Phase.IDLE, rule.phase());
        assertNull(rule.ghost());
    }

    @Test void theGhostHoldsWhatTheRecordingHeld() {
        record(A, 100);
        for (int i = 0; i < 9; i++) tick(null);
        assertFalse(rule.held(A, now), "The real hand has gone, and frame 9 was before it came down");
        for (int i = 9; i < 99; i++) tick(null);
        assertFalse(rule.held(A, now));
        tick(null);
        assertTrue(rule.held(A, now), "From the frame it took hold");
        assertFalse(rule.held(B, now));
    }

    @Test void aHandLetGoIsLetGo() {
        rule.hold(A, now, false);
        assertTrue(rule.held(A, now));
        for (int i = 0; i < 8; i++) tick(null);
        assertTrue(rule.held(A, now), "A client repeats every four ticks: a gap of eight is still a hold");
        tick(null);
        assertFalse(rule.held(A, now));
    }

    @Test void oneKeyOpensNothing() {
        for (int i = 0; i < 40; i++) { rule.hold(A, now, false); tick(null); }
        assertFalse(rule.doorOpen());
    }

    @Test void twoHandsNeedNoRecorder() {
        rule.hold(A, now, false);
        rule.hold(B, now, false);
        tick(null);
        assertTrue(rule.doorOpen());
    }

    @Test void aHandAndTheGhostOpenTheDoorEitherWayRound() {
        for (int key : new int[] {A, B}) {
            record(key, 0);
            assertFalse(rule.doorOpen(), "The ghost alone");
            rule.hold(1 - key, now, false);
            tick(null);
            assertTrue(rule.doorOpen(), "Ghost on " + key + ", a hand on the other");
            for (int i = 0; i < TwoPersonRule.TAKE_TICKS + TwoPersonRule.OPEN_TICKS; i++) tick(null);
            assertFalse(rule.doorOpen());
        }
    }

    @Test void theDoorGivesYouTimeToReachItThenShuts() {
        rule.hold(A, now, false);
        rule.hold(B, now, false);
        tick(null);
        for (int i = 0; i < TwoPersonRule.OPEN_TICKS + 8; i++) {
            tick(null);
            if (i < TwoPersonRule.OPEN_TICKS) assertTrue(rule.doorOpen(), "Still open at " + i);
        }
        assertFalse(rule.doorOpen());
    }

    @Test void aKeySomeoneElseHoldsIsNotOnTheRecording() {
        assertTrue(rule.press());
        for (int i = 0; i < TwoPersonRule.TAKE_TICKS; i++) { rule.hold(A, now, false); tick(at(i)); }
        for (int i = 0; i < 20; i++) tick(null);
        assertFalse(rule.held(A, now));
    }

    @Test void pressingMidTakeDoesNothingAndAReplayCanBeThrownAway() {
        assertTrue(rule.press());
        tick(at(1));
        assertFalse(rule.press(), "One take at a time");
        assertEquals(Phase.RECORDING, rule.phase());
        for (int i = 1; i < TwoPersonRule.TAKE_TICKS; i++) tick(at(2));
        assertEquals(Phase.PLAYING, rule.phase());
        assertTrue(rule.press(), "Retry is free");
        assertEquals(Phase.RECORDING, rule.phase());
        assertNull(rule.ghost());
        tick(at(9));
        for (int i = 1; i < TwoPersonRule.TAKE_TICKS; i++) tick(at(9));
        assertEquals(at(9), rule.ghost().pose(), "Nothing of the old take is left");
    }

    @Test void aRecorderWhoLeavesLeavesNoTake() {
        assertTrue(rule.press());
        tick(at(1));
        tick(null);
        assertEquals(Phase.IDLE, rule.phase());
        assertTrue(rule.press());
        for (int i = 0; i < TwoPersonRule.TAKE_TICKS; i++) tick(at(5));
        assertEquals(at(5), rule.ghost().pose());
    }

    @Test void resetClearsEverything() {
        record(A, 0);
        rule.hold(B, now, false);
        tick(null);
        assertTrue(rule.doorOpen());
        rule.reset();
        assertEquals(Phase.IDLE, rule.phase());
        assertFalse(rule.doorOpen());
        assertFalse(rule.held(A, now));
        assertFalse(rule.held(B, now));
        assertEquals(0, rule.ticksLeft());
    }

    @Test void itCountsDownEachHalf() {
        assertTrue(rule.press());
        tick(at(0));
        assertEquals(TwoPersonRule.TAKE_TICKS - 1, rule.ticksLeft());
        for (int i = 1; i < TwoPersonRule.TAKE_TICKS; i++) tick(at(0));
        assertEquals(TwoPersonRule.TAKE_TICKS, rule.ticksLeft());
    }
}
