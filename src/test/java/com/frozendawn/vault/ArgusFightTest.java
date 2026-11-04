package com.frozendawn.vault;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.vault.ArgusFight.Phase;
import com.frozendawn.vault.ArgusFight.Pull;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** ISS-4 "Argus" without a world: the rounds, the clock and what a fuse does. */
class ArgusFightTest {
    private static ArgusFight started(long seed) { var fight = new ArgusFight(new Random(seed)); fight.start(); return fight; }
    private static void ticks(ArgusFight fight, int n, boolean up) { for (int i = 0; i < n; i++) assertFalse(fight.tick(up)); }
    /** Up to the board going dark, with a reader on the upper level. */
    private static void read(ArgusFight fight) { while (fight.phase() != Phase.PULL) assertFalse(fight.tick(true)); }
    private static int slotOf(ArgusFight fight, int glyph) { return IntStream.range(0, 6).filter(s -> fight.fuses()[s] == glyph).findFirst().orElseThrow(); }
    private static int wrongSlot(ArgusFight fight) {
        return IntStream.range(0, 6).filter(s -> Arrays.stream(fight.wanted()).noneMatch(g -> g == fight.fuses()[s])).findFirst().orElseThrow();
    }
    private static void pullAll(ArgusFight fight) { for (int glyph : fight.wanted()) fight.pull(slotOf(fight, glyph)); }
    private static void winRound(ArgusFight fight) { read(fight); pullAll(fight); assertEquals(Phase.BURN, fight.phase()); ticks(fight, ArgusFight.BURN_TICKS, false); }

    @Test
    void theRoundsGetHarderAsDrawn() {
        assertEquals(new ArgusFight.Round(3, 120, false), ArgusFight.round(0));
        assertEquals(new ArgusFight.Round(4, 100, false), ArgusFight.round(1));
        assertEquals(new ArgusFight.Round(4, 100, true), ArgusFight.round(2));
    }

    @Test
    void theBoardWaitsForSomeoneOnTheUpperLevel() {
        var fight = started(1);
        ticks(fight, 200, false);
        assertEquals(Phase.WAITING, fight.phase());
        assertEquals(ArgusFight.CLOCK_TICKS - 200, fight.clock(), "The clock does not wait");
        fight.tick(true);
        assertEquals(Phase.READ, fight.phase());
    }

    @Test
    void theBoardLightsTheRoundsGlyphsThenGoesDarkAndTheFusesShuffle() {
        var fight = started(2);
        fight.tick(true);
        var before = fight.fuses().clone();
        for (int glyph = 0; glyph < 6; glyph++) {
            int g = glyph;
            assertEquals(Arrays.stream(fight.wanted()).anyMatch(w -> w == g), fight.boardLit(glyph));
        }
        assertEquals(3, fight.wanted().length);
        ticks(fight, 119, false);
        assertEquals(Phase.READ, fight.phase());
        fight.tick(false);
        assertEquals(Phase.PULL, fight.phase());
        for (int glyph = 0; glyph < 6; glyph++) assertFalse(fight.boardLit(glyph), "Dark once read");
        assertFalse(Arrays.equals(before, fight.fuses()), "They have shuffled");
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, Arrays.stream(fight.fuses()).sorted().toArray(), "Each glyph on one fuse");
    }

    @Test
    void everyRoundShufflesTheFuses() {
        for (long seed = 0; seed < 40; seed++) {
            var fight = started(seed);
            read(fight);
            var first = fight.fuses().clone();
            pullAll(fight);
            ticks(fight, ArgusFight.BURN_TICKS, false);
            read(fight);
            assertFalse(Arrays.equals(first, fight.fuses()), "Seed " + seed);
        }
    }

    @Test
    void fusesDoNothingOutsideThePull() {
        var fight = started(3);
        fight.tick(true);
        assertEquals(Pull.NOTHING, fight.pull(0));
        assertEquals(ArgusFight.CLOCK_TICKS - 1, fight.clock());
        assertTrue(fight.clean());
    }

    @Test
    void aWrongFuseTakesTwentySecondsAndSpoilsTheCleanRun() {
        var fight = started(4);
        read(fight);
        int clock = fight.clock();
        assertEquals(Pull.WRONG, fight.pull(wrongSlot(fight)));
        assertEquals(clock - 400, fight.clock());
        assertFalse(fight.clean());
        assertEquals(Phase.PULL, fight.phase(), "The round goes on");
    }

    @Test
    void aFusePulledTwiceIsNoSecondPullAndNoPenalty() {
        var fight = started(5);
        read(fight);
        int slot = slotOf(fight, fight.wanted()[0]);
        assertEquals(Pull.RIGHT, fight.pull(slot));
        int clock = fight.clock();
        assertEquals(Pull.NOTHING, fight.pull(slot));
        assertEquals(clock, fight.clock());
    }

    @Test
    void allPulledOpensTheShellForTwelveSecondsThenBackToTheBoard() {
        var fight = started(6);
        read(fight);
        var wanted = fight.wanted();
        assertEquals(Pull.RIGHT, fight.pull(slotOf(fight, wanted[0])));
        assertEquals(Pull.RIGHT, fight.pull(slotOf(fight, wanted[1])));
        assertEquals(1f, fight.floor());
        assertEquals(Pull.COMPLETE, fight.pull(slotOf(fight, wanted[2])));
        assertEquals(Phase.BURN, fight.phase());
        assertEquals(2 / 3f, fight.floor(), 1e-4, "A third of its health a round");
        ticks(fight, ArgusFight.BURN_TICKS - 1, false);
        assertEquals(Phase.BURN, fight.phase());
        fight.tick(false);
        assertEquals(Phase.WAITING, fight.phase());
        ticks(fight, ArgusFight.GAP_TICKS, false);
        assertEquals(4, fight.wanted().length, "Round 2");
    }

    @Test
    void aSpentOpeningGoesStraightBackToTheBoard() {
        var fight = started(6);
        fight.spent();
        assertEquals(Phase.WAITING, fight.phase(), "Nothing to spend before an opening");
        read(fight);
        pullAll(fight);
        ticks(fight, 20, false);
        assertEquals(Phase.BURN, fight.phase());
        fight.spent();
        assertEquals(Phase.WAITING, fight.phase());
        ticks(fight, ArgusFight.GAP_TICKS, false);
        assertEquals(Phase.READ, fight.phase());
    }

    @Test
    void afterAnOpeningTheBoardLightsFiveSecondsOnWhereverYouStand() {
        var fight = started(6);
        winRound(fight);
        assertEquals(100, ArgusFight.GAP_TICKS);
        ticks(fight, ArgusFight.GAP_TICKS - 1, true);
        assertEquals(Phase.WAITING, fight.phase(), "Not even for someone on the upper level");
        fight.tick(false);
        assertEquals(Phase.READ, fight.phase());
    }

    @Test
    void theBoardFlashesAgainDuringThePull() {
        var fight = started(6);
        read(fight);
        int glyph = fight.wanted()[0];
        ticks(fight, ArgusFight.REMIND_EVERY - 1, false);
        assertFalse(fight.boardLit(glyph), "Dark until the reminder");
        fight.tick(false);
        assertTrue(fight.boardLit(glyph));
        assertFalse(IntStream.range(0, 6).filter(g -> Arrays.stream(fight.wanted()).noneMatch(w -> w == g)).anyMatch(fight::boardLit), "Only the round's glyphs");
        ticks(fight, ArgusFight.REMIND_TICKS - 1, false);
        assertTrue(fight.boardLit(glyph));
        fight.tick(false);
        assertFalse(fight.boardLit(glyph), "Dark again");
        ticks(fight, ArgusFight.REMIND_EVERY - ArgusFight.REMIND_TICKS, false);
        assertTrue(fight.boardLit(glyph), "And again after that");
    }

    @Test
    void roundThreesReminderKeepsItsOrder() {
        var fight = started(6);
        winRound(fight);
        winRound(fight);
        read(fight);
        ticks(fight, ArgusFight.REMIND_EVERY, false);
        for (int i = 0; i < fight.wanted().length; i++) {
            assertTrue(fight.boardLit(fight.wanted()[i]), "Glyph " + i + " in its turn");
            ticks(fight, ArgusFight.REMIND_TICKS / fight.wanted().length, false);
        }
    }

    @Test
    void eachRightFusePutsOutALampAndARepeatedRoundPutsOutNoMore() {
        var fight = started(6);
        assertEquals(0, fight.lampsOut());
        read(fight);
        var wanted = fight.wanted();
        fight.pull(slotOf(fight, wanted[0]));
        assertEquals(1, fight.lampsOut());
        fight.pull(slotOf(fight, wanted[0]));
        assertEquals(1, fight.lampsOut(), "One already pulled is no second lamp");
        fight.pull(slotOf(fight, wanted[1]));
        fight.pull(slotOf(fight, wanted[2]));
        assertEquals(3, fight.lampsOut());
        ticks(fight, ArgusFight.BURN_TICKS, false);
        assertEquals(3, fight.lampsOut(), "Still three through the next read");
        winRound(fight);
        assertEquals(7, fight.lampsOut());
        winRound(fight);
        assertEquals(ArgusFight.LAMPS, fight.lampsOut());
        assertEquals(11, ArgusFight.LAMPS);
        winRound(fight);
        assertEquals(ArgusFight.LAMPS, fight.lampsOut(), "Round 3 again has none left to put out");
    }

    @Test
    void roundThreeShowsOneGlyphAtATimeAndMustBePulledInThatOrder() {
        var fight = started(7);
        winRound(fight);
        winRound(fight);
        ticks(fight, ArgusFight.GAP_TICKS, false);
        assertEquals(new ArgusFight.Round(4, 100, true), fight.currentRound());
        var wanted = fight.wanted();
        for (int shown = 0; shown < 4; shown++) {
            for (int glyph = 0; glyph < 6; glyph++) assertEquals(glyph == wanted[shown], fight.boardLit(glyph));
            ticks(fight, 25, false);
        }
        assertEquals(Phase.PULL, fight.phase());
        int clock = fight.clock();
        assertEquals(Pull.WRONG, fight.pull(slotOf(fight, wanted[1])), "In the wanted set, out of order");
        assertEquals(clock - 400, fight.clock());
        assertFalse(fight.pulled(slotOf(fight, wanted[1])));
        assertEquals(Pull.RIGHT, fight.pull(slotOf(fight, wanted[0])));
        assertEquals(Pull.RIGHT, fight.pull(slotOf(fight, wanted[1])));
        assertEquals(Pull.RIGHT, fight.pull(slotOf(fight, wanted[2])));
        assertEquals(Pull.COMPLETE, fight.pull(slotOf(fight, wanted[3])));
        assertEquals(0f, fight.floor(), "The third opening can finish it");
    }

    @Test
    void aCustodianStillStandingAfterThreeOpeningsMeansRoundThreeAgain() {
        var fight = started(8);
        for (int i = 0; i < 3; i++) winRound(fight);
        fight.tick(true);
        assertEquals(ArgusFight.round(2), fight.currentRound());
    }

    @Test
    void theClockAtZeroPurgesAndTheFightStartsOverDirty() {
        var fight = started(9);
        winRound(fight);
        ticks(fight, fight.clock() - 1, false);
        assertTrue(fight.tick(false), "Zero is the purge");
        fight.purge();
        assertEquals(Phase.DORMANT, fight.phase());
        assertEquals(ArgusFight.CLOCK_TICKS, fight.clock());
        assertEquals(1f, fight.floor());
        assertFalse(fight.clean(), "A purge is never a clean run");
        fight.start();
        fight.tick(true);
        assertEquals(3, fight.wanted().length, "From round 1");
        assertFalse(fight.clean(), "And the retry does not wash it");
    }

    @Test
    void aWrongFuseCannotTakeTheClockBelowZero() {
        var fight = started(10);
        read(fight);
        ticks(fight, fight.clock() - 100, false);
        fight.pull(wrongSlot(fight));
        assertEquals(0, fight.clock());
        assertTrue(fight.tick(false));
    }

    @Test
    void anAbandonedFightStartsOverWithoutSpoilingACleanRun() {
        var fight = started(11);
        winRound(fight);
        fight.abandon();
        assertEquals(Phase.DORMANT, fight.phase());
        assertEquals(1f, fight.floor());
        assertTrue(fight.clean());
    }

    @Test
    void theCustodianFallingEndsItAndTheClockStops() {
        var fight = started(12);
        read(fight);
        pullAll(fight);
        fight.fell();
        assertEquals(Phase.WON, fight.phase());
        int clock = fight.clock();
        assertFalse(fight.tick(true));
        assertEquals(clock, fight.clock());
    }

    @Test
    void itFallsOnlyInAFight() {
        var fight = new ArgusFight(new Random(13));
        fight.fell();
        assertEquals(Phase.DORMANT, fight.phase());
    }

    @Test
    void holdingAFuseTakesTwoUnbrokenSeconds() {
        var fight = started(14);
        read(fight);
        assertFalse(fight.hold(0, 100));
        for (long t = 104; t < 140; t += 4) assertFalse(fight.hold(0, t), "Still holding at " + t);
        assertTrue(fight.hold(0, 140), "Two seconds");
        assertFalse(fight.hold(1, 200));
        assertFalse(fight.hold(1, 230), "Let go: it starts again");
        for (long t = 234; t < 270; t += 4) assertFalse(fight.hold(1, t));
        assertTrue(fight.hold(1, 270));
    }

    @Test
    void theLightsDieWithTheClockAndTheLastMinuteIsNearlyDark() {
        var fight = started(15);
        assertEquals(24, fight.lights(24));
        ticks(fight, ArgusFight.CLOCK_TICKS / 2, false);
        assertEquals(6, fight.lights(24));
        ticks(fight, ArgusFight.CLOCK_TICKS / 4, false);
        assertEquals(2, fight.lights(24), "The last minute");
        ticks(fight, ArgusFight.CLOCK_TICKS / 4 - 1, false);
        assertEquals(1, fight.lights(24), "One holds to the end");
    }

    @Test
    void thePulseQuickensAsTheClockFalls() {
        var fight = started(16);
        assertEquals(24, fight.pulseEvery(), "A resting heart");
        assertEquals(.45f, fight.pulseVolume(), .001, "Barely there");
        ticks(fight, ArgusFight.CLOCK_TICKS / 2, false);
        assertEquals(17, fight.pulseEvery());
        ticks(fight, ArgusFight.CLOCK_TICKS / 2 - 1, false);
        assertEquals(10, fight.pulseEvery(), "A frightened one");
        assertEquals(1, fight.pulseVolume(), .001);
    }

    @Test
    void theDisplayRoundsUpSoZeroIsTheEnd() {
        var fight = started(17);
        assertEquals("04:00", ArgusFight.text(fight.seconds()));
        fight.tick(false);
        assertEquals("04:00", ArgusFight.text(fight.seconds()));
        ticks(fight, 19, false);
        assertEquals("03:59", ArgusFight.text(fight.seconds()));
        assertEquals("01:12", ArgusFight.text(72));
    }

    @Test
    void aFightInProgressSurvivesAReload() {
        var fight = started(18);
        winRound(fight);
        read(fight);
        fight.pull(slotOf(fight, fight.wanted()[0]));
        fight.pull(wrongSlot(fight));
        var again = new ArgusFight(new Random(0));
        again.load(fight.save());
        assertEquals(fight.save(), again.save());
        assertEquals(Phase.PULL, again.phase());
        assertArrayEquals(fight.fuses(), again.fuses());
        assertArrayEquals(fight.wanted(), again.wanted());
        assertEquals(fight.clock(), again.clock());
        assertEquals(fight.floor(), again.floor());
        assertFalse(again.clean());
        assertEquals(Pull.NOTHING, again.pull(slotOf(again, again.wanted()[0])), "Pulled stays pulled");
    }
}
