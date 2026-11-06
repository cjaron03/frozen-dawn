package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.DailyLog.Day;
import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.miller.SelfModel.Stage;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class DailyLogTest {
    private static final String NOTE = "shepherd.log.note.";
    private static final long DAY = ShepherdPolicy.DAY_TICKS;

    /** A day with nothing in it, to be filled in by each test. */
    private static final class Today {
        long number = 1, worldDay;
        boolean signed, read, nineFound;
        Stage stage = Stage.NOTICING;
        Tier tier = Tier.NEUTRAL;
        double coherence = 1, strength;
        Feeling strongest;
        Set<Mark> marks = EnumSet.noneOf(Mark.class);
        Set<Deed> deeds = EnumSet.noneOf(Deed.class);
        int backs;
        long unseen;

        Today on(long number) { this.number = number; return this; }
        /** Slept to this day: the world's day runs ahead of game time. */
        Today sleptTo(long worldDay) { this.worldDay = worldDay; return this; }
        Today at(Stage stage) { this.stage = stage; return this; }
        Today tier(Tier tier) { this.tier = tier; return this; }
        Today coherence(double coherence) { this.coherence = coherence; return this; }
        Today feeling(Feeling feeling, double strength) { strongest = feeling; this.strength = strength; return this; }
        Today marks(Mark... marks) { this.marks = EnumSet.copyOf(List.of(marks)); return this; }
        Today deeds(Deed... deeds) { this.deeds = EnumSet.copyOf(List.of(deeds)); return this; }
        Today backs(int backs) { this.backs = backs; return this; }
        Today unseenDays(long days) { unseen = days * DAY; return this; }
        Today signed() { signed = true; return this; }
        Today read() { read = true; return this; }
        Today nineFound() { nineFound = true; return this; }

        Day day() {
            return new Day((number - 1) * DAY, worldDay == 0 ? number : worldDay, signed, stage, tier, coherence, strongest, strength, marks, deeds, backs, unseen, read, nineFound);
        }
    }

    private static Today today() {
        return new Today();
    }

    private static String note(DailyLog log, Today today) {
        var note = log.write(today.day()).note();
        return note == null ? null : note.substring(NOTE.length());
    }

    private static String note(Today today) {
        return note(new DailyLog(), today);
    }

    private static DailyLog filed() {
        var log = new DailyLog();
        assertEquals("cargo.1", note(log, today().tier(Tier.FRIENDLY)));
        return log;
    }

    @Test
    void aNoteFollowsTheOrder() {
        var all = today().tier(Tier.TRUSTED).on(4).read().coherence(0.6).feeling(Feeling.FEAR, 0.7).unseenDays(6).nineFound().backs(1)
                .marks(Mark.COLD, Mark.FIGHT, Mark.CLOSE_CALL, Mark.BACK).deeds(Deed.AIR_FAILING, Deed.ARCHIVIST_KILLED, Deed.WARM_SHELTER, Deed.CLEAR_NIGHT);
        var log = filed();
        var written = new java.util.ArrayList<String>();
        for (int i = 0; i < 4; i++) written.add(note(log, all).split("\\.")[0]);
        assertEquals(List.of("back", "reunion", "nine_found", "fear"), written, "Each rests, so the next in the order takes the day");
    }

    @Test
    void aBadDayFollowsItsOwnOrder() {
        assertEquals("archivist_killed.1", note(today().deeds(Deed.CALLED_SHEPHERD, Deed.LEFT_BEHIND, Deed.ARCHIVIST_KILLED)));
        assertEquals("left_behind.1", note(today().deeds(Deed.CALLED_SHEPHERD, Deed.LEFT_BEHIND)));
    }

    @Test
    void aKindRestsForThreeEntries() {
        var log = new DailyLog();
        var cold = today().at(Stage.NOTICING).marks(Mark.COLD);
        assertEquals("cold.noticing", note(log, cold));
        assertNull(note(log, cold));
        assertNull(note(log, cold));
        assertNull(note(log, cold));
        assertEquals("cold.noticing", note(log, cold), "Three entries have gone by");
    }

    @Test
    void noFitLeavesNotesBlank() {
        assertNull(note(today()));
        assertNull(note(today().at(Stage.PRODUCT).feeling(Feeling.JOY, 0.9).read()), "Feelings wait for Noticing, and it is not a fourth day");
    }

    @Test
    void theVariantComesFromTheDay() {
        var seen = new java.util.ArrayList<String>();
        for (long day : List.of(12L, 4L, 8L)) seen.add(note(today().on(day).read()));
        assertEquals(List.of("knows.1", "knows.2", "knows.3"), seen, "The day over the three variants");
        assertEquals("knows.2", note(today().on(4).read()), "The same day gives the same note");
    }

    @Test
    void knowsWaitsForEveryFourthDay() {
        assertNull(note(today().on(5).read()));
        assertNull(note(today().on(4)), "Nobody has read it");
    }

    @Test
    void coherenceNarrowsTheVariants() {
        assertEquals("knows.1", note(today().on(4).read().coherence(0.3)), "At the floor she repeats herself");
        assertEquals("knows.1", note(today().on(8).read().coherence(0.3)));
        assertEquals("knows.3", note(today().on(8).read().coherence(1)));
    }

    @Test
    void slowNeedsHerSlow() {
        assertEquals("slow.1", note(today().on(2).coherence(0.69)));
        assertNull(note(today().on(2).coherence(0.7)));
    }

    @Test
    void theFirstSignedEntryHasNoNote() {
        var log = new DailyLog();
        var cold = today().marks(Mark.COLD);
        assertFalse(log.write(cold.day()).signed());
        var first = log.write(today().deeds(Deed.AIR_FAILING).signed().day());
        assertTrue(first.signed());
        assertNull(first.note(), "Nothing draws the eye to it");
        assertEquals("air_failing.1", note(log, today().deeds(Deed.AIR_FAILING).signed()));
    }

    @Test
    void anUnsignedEntryStaysBlank() {
        var log = new DailyLog();
        assertFalse(log.write(today().day()).signed());
        log.write(today().signed().day());
        assertFalse(log.entries().getLast().signed(), "Earlier days are never rewritten");
        assertTrue(log.entries().getFirst().signed());
    }

    @Test
    void eachStageHasItsVoice() {
        assertEquals("cold.product", note(today().at(Stage.PRODUCT).marks(Mark.COLD)));
        assertEquals("cold.noticing", note(today().at(Stage.NOTICING).marks(Mark.COLD)));
        assertEquals("cold.walls", note(today().at(Stage.FINDING_WALLS).marks(Mark.COLD)));
        assertEquals("fight.product", note(today().at(Stage.PRODUCT).marks(Mark.FIGHT)));
        assertEquals("fight.noticing", note(today().at(Stage.NOTICING).marks(Mark.FIGHT)));
        assertEquals("fight.walls", note(today().at(Stage.LEANING).marks(Mark.FIGHT)), "Finding the walls and later");
        assertEquals("back.product", note(today().at(Stage.PRODUCT).marks(Mark.BACK).backs(1)));
        assertEquals("back.noticing", note(today().at(Stage.NOTICING).marks(Mark.BACK).backs(1)));
        assertEquals("back.walls", note(today().at(Stage.FINDING_WALLS).marks(Mark.BACK).backs(1)));
    }

    @Test
    void theGapNeedsNoticing() {
        assertEquals("warm_shelter.product", note(today().at(Stage.PRODUCT).deeds(Deed.WARM_SHELTER)));
        assertEquals("warm_shelter.noticing", note(today().at(Stage.NOTICING).deeds(Deed.WARM_SHELTER)));
        assertEquals("warm_shelter.walls", note(today().at(Stage.RECOGNISING).deeds(Deed.WARM_SHELTER)));
    }

    @Test
    void aFeelingTakesTheNoteAtHigh() {
        assertEquals("fear.high", note(today().feeling(Feeling.FEAR, 0.6).marks(Mark.COLD)));
        assertEquals("joy.high", note(today().feeling(Feeling.JOY, 0.79)));
        assertEquals("guilt.1", note(today().feeling(Feeling.GUILT, 0.7)), "Only some have words of their own at High");
        assertEquals("cold.noticing", note(today().feeling(Feeling.FEAR, 0.59).marks(Mark.COLD)), "Below High the day's events come first");
        assertEquals("fear.1", note(today().feeling(Feeling.FEAR, 0.59)), "The mockup's Normal: dry, and last in the order");
        assertNull(note(today().at(Stage.PRODUCT).feeling(Feeling.FEAR, 0.59)));
    }

    @Test
    void peakCracksTheStatus() {
        var peak = new DailyLog().write(today().feeling(Feeling.DISTRESS, 0.8).day());
        assertTrue(peak.cracked());
        assertEquals(NOTE + "distress.peak", peak.note());
        assertFalse(new DailyLog().write(today().feeling(Feeling.DISTRESS, 0.79).day()).cracked());
        assertTrue(new DailyLog().write(today().at(Stage.FINDING_WALLS).marks(Mark.BACK).backs(1).day()).cracked());
        assertTrue(filed().write(today().at(Stage.FINDING_WALLS).tier(Tier.FRIENDLY).unseenDays(5).day()).cracked());
    }

    @Test
    void aReunionNeedsDaysAwayAndFriendly() {
        assertEquals("reunion.high", note(today().tier(Tier.FRIENDLY).unseenDays(5)));
        assertNotEquals("reunion.high", note(today().tier(Tier.FRIENDLY).unseenDays(4)));
        assertNull(note(today().tier(Tier.NEUTRAL).unseenDays(9)));
        assertFalse(new DailyLog().write(today().tier(Tier.FRIENDLY).unseenDays(5).day()).cracked(), "Below the walls it is High");
    }

    @Test
    void theDayAfterAPeakIsQuiet() {
        var log = new DailyLog();
        log.write(today().feeling(Feeling.FEAR, 0.9).day());
        assertEquals("after.2", note(log, today().on(1).feeling(Feeling.JOY, 0.9).marks(Mark.COLD)), "The day over the two variants");
        assertFalse(log.entries().getFirst().cracked(), "Status is back as if nothing happened");
    }

    @Test
    void productNeverBreaks() {
        var log = new DailyLog();
        var entry = log.write(today().at(Stage.PRODUCT).tier(Tier.FRIENDLY).feeling(Feeling.DISTRESS, 1).marks(Mark.BACK).backs(1).unseenDays(9).day());
        assertFalse(entry.cracked());
        assertEquals(NOTE + "back.product", entry.note());
        var closeCall = log.write(today().at(Stage.PRODUCT).feeling(Feeling.FEAR, 1).marks(Mark.CLOSE_CALL).day());
        assertEquals(NOTE + "close_call.1", closeCall.note(), "No exclamation mark at Product");
    }

    @Test
    void aCloseCallNeedsThemUp() {
        assertEquals("close_call.1", note(today().marks(Mark.CLOSE_CALL)));
        assertNull(note(today().marks(Mark.CLOSE_CALL, Mark.STOPPED)), "They did not get up");
    }

    @Test
    void cargoIsFiledOnce() {
        var log = new DailyLog();
        assertEquals("cargo.1", note(log, today().tier(Tier.FRIENDLY)));
        for (int i = 0; i < 4; i++) log.write(today().day());
        assertNull(note(log, today().tier(Tier.FRIENDLY)));
        var reloaded = DailyLog.load(log.save());
        assertNull(note(reloaded, today().tier(Tier.FRIENDLY)), "Once per world, across a reload");
    }

    @Test
    void cargoIsOnlyUsedInNotesWrittenForIt() {
        var log = filed();
        for (int i = 0; i < 3; i++) log.write(today().day());
        assertEquals("cold.cargo", note(log, today().on(3).marks(Mark.COLD)), "The day over two variants");
        assertEquals("trusted.cargo", note(filed(), today().on(3).tier(Tier.TRUSTED)));
        assertEquals("cold.noticing", note(new DailyLog(), today().on(3).marks(Mark.COLD)), "Not before it is filed");
        assertEquals("trusted.1", note(filed(), today().on(2).tier(Tier.TRUSTED)));
    }

    @Test
    void theFirstBackAfterNineIsAboutNine() {
        var log = new DailyLog();
        assertEquals("nine_found.1", note(log, today().nineFound()));
        log.write(today().day());
        log.write(today().day());
        log.write(today().day());
        assertEquals("back.nine", note(log, today().at(Stage.FINDING_WALLS).marks(Mark.BACK).backs(1)));
        for (int i = 0; i < 3; i++) log.write(today().day());
        assertEquals("back.walls", note(log, today().at(Stage.FINDING_WALLS).marks(Mark.BACK).backs(2)), "Only the first");
    }

    @Test
    void nineStaysOutOfTheFlatForm() {
        var log = new DailyLog();
        log.write(today().at(Stage.PRODUCT).nineFound().day());
        for (int i = 0; i < 3; i++) log.write(today().day());
        assertEquals("back.product", note(log, today().at(Stage.PRODUCT).marks(Mark.BACK).backs(1)));
    }

    @Test
    void sheBreaksOverTheReturnOnce() {
        var log = filed();
        var back = today().at(Stage.FINDING_WALLS).marks(Mark.BACK).backs(2);
        assertEquals("back.walls", note(log, back));
        for (int i = 0; i < 3; i++) log.write(today().day());
        var entry = log.write(back.day());
        assertEquals(NOTE + "back.again", entry.note());
        assertFalse(entry.cracked(), "As the mockup draws it");
    }

    @Test
    void backAgainNeedsTheName() {
        var log = new DailyLog();
        assertEquals("back.noticing", note(log, today().marks(Mark.BACK).backs(2)));
    }

    @Test
    void theOldestGoesAtTheCap() {
        var log = new DailyLog();
        for (int i = 1; i <= DailyLog.MAX + 1; i++) log.write(today().on(i).day());
        assertEquals(DailyLog.MAX, log.entries().size());
        assertEquals(DailyLog.MAX + 1, log.entries().getFirst().day(), "Newest first");
        assertEquals(2, log.entries().getLast().day());
    }

    @Test
    void ageMovesEveryEntry() {
        var log = new DailyLog();
        log.write(today().on(4).day());
        log.write(today().on(6).day());
        log.age(DAY);
        assertEquals(List.of(5L, 3L), log.entries().stream().map(LogEntry::day).toList());
    }

    @Test
    void theDayIsTheWorldsAndTheClockStaysGameTime() {
        var entry = new DailyLog().write(today().on(5).sleptTo(30).day());
        assertEquals(30, entry.day());
        assertEquals(4 * DAY, entry.time());
    }

    @Test
    void theWorldsDayPicksTheNote() {
        assertEquals("knows.2", note(new DailyLog(), today().on(3).sleptTo(4).read()), "Day 4 is a day she knows, whatever game time says");
    }

    @Test
    void anEntryKeepsItsDayThroughASaveAndAnAge() {
        var log = new DailyLog();
        log.write(today().on(5).sleptTo(30).day());
        var loaded = DailyLog.load(log.save());
        assertEquals(30, loaded.entries().getFirst().day());
        loaded.age(2 * DAY);
        assertEquals(28, loaded.entries().getFirst().day());
    }

    @Test
    void ageStopsAtDayOne() {
        var log = new DailyLog();
        log.write(today().day());
        log.age(DAY);
        assertEquals(1, log.entries().getFirst().day());
    }

    @Test
    void anEntryWithNoDayReadsItsClock() {
        var saved = new LogEntry(4 * DAY, 30, false, false, null).save();
        saved.remove("day");
        assertEquals(5, LogEntry.load(saved).day());
    }

    @Test
    void saveAndLoadKeepEveryEntry() {
        var log = new DailyLog();
        log.write(today().on(3).marks(Mark.COLD).day());
        log.write(today().on(4).feeling(Feeling.FEAR, 0.9).signed().day());
        log.write(today().on(5).signed().feeling(Feeling.FEAR, 0.9).day());
        assertEquals(log.entries(), DailyLog.load(log.save()).entries());
        assertEquals(log.save(), DailyLog.load(log.save()).save());
    }

    @Test
    void aDamagedEntryIsDropped() {
        var log = new DailyLog();
        log.write(today().on(3).marks(Mark.COLD).day());
        var tag = log.save();
        var entries = tag.getList("entries", CompoundTag.TAG_COMPOUND);
        entries.add(new CompoundTag());
        var wrong = new CompoundTag();
        wrong.putLong("time", 0);
        wrong.putString("note", "shepherd.wall.lagged");
        entries.add(wrong);
        assertEquals(1, DailyLog.load(tag).entries().size());
    }

    @Test
    void anOverlongSaveLosesItsOldest() {
        var entries = new ListTag();
        for (int i = 0; i <= DailyLog.MAX; i++) entries.add(new LogEntry(i * DAY, i + 1, false, false, null).save());
        var tag = new CompoundTag();
        tag.put("entries", entries);
        var loaded = DailyLog.load(tag).entries();
        assertEquals(DailyLog.MAX, loaded.size());
        assertEquals(DailyLog.MAX + 1, loaded.getFirst().day());
    }

    @Test
    void aVersionThreeSaveLoadsWithAnEmptyLog() {
        var state = new PatternState();
        state.self().lean(SelfModel.Lean.SOMEONE);
        var tag = state.save();
        assertEquals(4, tag.getInt("dataVersion"));
        tag.putInt("dataVersion", 3);
        tag.remove("log");
        var loaded = PatternState.load(tag);
        assertTrue(loaded.log().entries().isEmpty());
        var first = loaded.log().write(today().signed().deeds(Deed.AIR_FAILING).day());
        assertTrue(first.signed());
        assertNull(first.note(), "A world past T5 signs its next entry with blank Notes");
    }

    @Test
    void theStateCarriesTheLog() {
        var state = new PatternState();
        state.log().write(today().on(3).marks(Mark.COLD).day());
        assertEquals(state.log().entries(), PatternState.load(state.save()).log().entries());
        state.age(DAY);
        assertEquals(2, state.log().entries().getFirst().day());
        state.clear();
        assertTrue(state.log().entries().isEmpty());
    }

    private static final UUID ONE = new UUID(0, 1), TWO = new UUID(0, 2);

    private static PatternState woken() {
        var state = new PatternState();
        state.poll(1, ONE, PatternState.NOT_SET, Tier.NEUTRAL, 0);
        return state;
    }

    private static String settled(PatternState state, UUID who, long lastTalk, long now) {
        state.poll(1, who, lastTalk, Tier.NEUTRAL, now);
        var note = state.log().entries().getFirst().note();
        return note == null ? null : note.substring(NOTE.length());
    }

    @Test
    void theSettleWritesOneEntry() {
        var state = woken();
        assertTrue(state.log().entries().isEmpty(), "Nothing has settled");
        state.poll(1, ONE, PatternState.NOT_SET, Tier.NEUTRAL, DAY - 1);
        assertTrue(state.log().entries().isEmpty());
        state.poll(1, ONE, PatternState.NOT_SET, Tier.NEUTRAL, DAY);
        state.poll(1, ONE, PatternState.NOT_SET, Tier.NEUTRAL, DAY + 100);
        assertEquals(1, state.log().entries().size());
        assertEquals(DAY, state.log().entries().getFirst().time());
    }

    @Test
    void writingTheDayRestartsTheSettle() {
        var state = woken();
        state.writeDay(ONE, PatternState.NOT_SET, Tier.NEUTRAL, DAY - 100, 1);
        state.poll(1, ONE, PatternState.NOT_SET, Tier.NEUTRAL, DAY);
        assertEquals(1, state.log().entries().size(), "A day written by hand is that day's entry");
    }

    @Test
    void nobodyNearWritesNothing() {
        var state = woken();
        state.poll(0, ONE, PatternState.NOT_SET, Tier.NEUTRAL, 3 * DAY);
        assertTrue(state.log().entries().isEmpty());
        state.poll(1, ONE, PatternState.NOT_SET, Tier.NEUTRAL, 4 * DAY);
        assertEquals(1, state.log().entries().size(), "The days nobody was near are simply skipped");
    }

    @Test
    void aDeedByAnotherPlayerIsNotTheirs() {
        var state = woken();
        state.appraise(TWO, Deed.AIR_FAILING, 100);
        assertNull(settled(state, ONE, PatternState.NOT_SET, DAY));
        var other = woken();
        other.appraise(TWO, Deed.AIR_FAILING, 100);
        assertEquals("air_failing.1", settled(other, TWO, PatternState.NOT_SET, DAY));
    }

    @Test
    void aDeedFromBeforeTheLastEntryIsOld() {
        var state = woken();
        state.appraise(ONE, Deed.AIR_FAILING, 100);
        assertEquals("air_failing.1", settled(state, ONE, PatternState.NOT_SET, DAY));
        for (int i = 2; i <= 5; i++) assertNotEquals("air_failing.1", settled(state, ONE, PatternState.NOT_SET, i * DAY), "One deed, one note, on day " + i);
    }

    @Test
    void aTalkAfterTheFightClearsIt() {
        var state = woken();
        state.marked(ONE, Mark.FIGHT, 100);
        assertNull(settled(state, ONE, 200, DAY));
        var silent = woken();
        silent.marked(ONE, Mark.FIGHT, 100);
        assertEquals("fight.product", settled(silent, ONE, 50, DAY));
    }

    @Test
    void anEntryIsAboutWhoeverWasBesideHer() {
        var state = woken();
        state.marked(TWO, Mark.COLD, 100);
        assertNull(settled(state, ONE, PatternState.NOT_SET, DAY));
    }

    @Test
    void anEntryClearsTheMarks() {
        var state = woken();
        state.marked(ONE, Mark.COLD, 100);
        assertEquals("cold.product", settled(state, ONE, PatternState.NOT_SET, DAY));
        for (int i = 2; i <= 5; i++) assertNotEquals("cold.product", settled(state, ONE, PatternState.NOT_SET, i * DAY), "Rested by day 5, and still nothing to write");
    }

    @Test
    void aReturnNeedsAStop() {
        var state = woken();
        state.marked(ONE, Mark.BACK, 100);
        assertNull(settled(state, ONE, PatternState.NOT_SET, DAY), "She saw nobody stop");
    }

    @Test
    void aStopWaitsForTheReturn() {
        var state = woken();
        state.marked(ONE, Mark.STOPPED, 100);
        assertNull(settled(state, ONE, PatternState.NOT_SET, DAY), "She has not seen them come back");
        state.marked(ONE, Mark.BACK, 2 * DAY - 50);
        assertEquals("back.product", settled(state, ONE, PatternState.NOT_SET, 2 * DAY), "The entry between them does not lose it");
    }

    @Test
    void sheCountsTheReturnsSheSaw() {
        var state = woken();
        state.marked(ONE, Mark.BACK, 50);
        state.marked(ONE, Mark.STOPPED, 100);
        state.marked(ONE, Mark.BACK, 200);
        state.marked(ONE, Mark.BACK, 300);
        assertEquals(1, state.marks().backs(ONE));
        state.marked(ONE, Mark.STOPPED, 400);
        state.marked(ONE, Mark.BACK, 500);
        assertEquals(2, state.marks().backs(ONE));
        assertEquals(0, state.marks().backs(TWO));
    }

    @Test
    void aLongAbsenceReachesTheReunion() {
        var state = woken();
        state.self().look(true, false);
        state.poll(1, ONE, PatternState.NOT_SET, Tier.FRIENDLY, DAY);
        assertEquals(NOTE + "cargo.1", state.log().entries().getFirst().note());
        state.poll(1, ONE, PatternState.NOT_SET, Tier.FRIENDLY, 7 * DAY);
        assertEquals(NOTE + "reunion.high", state.log().entries().getFirst().note());
    }

    @Test
    void sameEventsSameLog() {
        var log = java.util.stream.Stream.of(1, 2).map(run -> {
            var state = woken();
            state.appraise(ONE, Deed.WARM_SHELTER, 100);
            state.marked(ONE, Mark.CLOSE_CALL, 200);
            state.poll(1, ONE, PatternState.NOT_SET, Tier.NEUTRAL, DAY);
            state.marked(ONE, Mark.STOPPED, DAY + 50);
            state.marked(ONE, Mark.BACK, DAY + 60);
            state.poll(1, ONE, PatternState.NOT_SET, Tier.NEUTRAL, 2 * DAY);
            return state.save();
        }).toList();
        assertEquals(log.get(0), log.get(1));
        assertEquals(log.get(0), PatternState.load(log.get(0)).save(), "And the same after a reload");
    }

    @Test
    void ageMovesTheMarks() {
        var state = woken();
        state.marked(ONE, Mark.STOPPED, 100);
        state.age(DAY);
        state.marked(ONE, Mark.BACK, 10);
        assertEquals(1, state.marks().backs(ONE));
        assertEquals(2 * DAY, state.marks().unseen(ONE, DAY), "The clock on them moved too");
    }

    @Test
    void aPlayerDeathTouchesNothingElse() {
        var state = woken();
        var before = state.save();
        before.remove("marks");
        state.marked(ONE, Mark.STOPPED, 100);
        state.marked(ONE, Mark.BACK, 200);
        var after = state.save();
        after.remove("marks");
        assertEquals(before, after, "No feeling, no keepsake, no impression, no reason");
    }

    @Test
    void readingHerLogIsAFactThatNeverFades() {
        var store = new ImpressionStore();
        assertFalse(store.readLog(ONE));
        store.read(ONE);
        store.told(TWO);
        for (int i = 0; i < 100; i++) store.settle();
        store = ImpressionStore.load(store.save());
        assertTrue(store.readLog(ONE));
        assertFalse(store.readLog(TWO));
        assertTrue(store.toldOff(TWO));
        assertFalse(store.toldOff(ONE));
        store.clear();
        assertFalse(store.readLog(ONE));
    }

    @Test
    void sheOnlyWritesForSomeoneWhoHasRead() {
        var state = woken();
        state.impressions().read(TWO);
        state.poll(1, ONE, 3 * DAY, Tier.NEUTRAL, 3 * DAY + 100);
        assertNull(state.log().entries().getFirst().note(), "Day 4, but this one has not read it");
        state.poll(1, ONE, 7 * DAY, Tier.NEUTRAL, 7 * DAY + 100);
        var other = woken();
        other.impressions().read(ONE);
        other.poll(1, ONE, 3 * DAY, Tier.NEUTRAL, 3 * DAY + 100);
        assertEquals(NOTE + "knows.2", other.log().entries().getFirst().note());
    }

    @Test
    void readingCostsAReproachSheKeeps() {
        var state = woken();
        state.appraise(ONE, Deed.READ_HER_LOG, 100);
        assertEquals(0.4, state.feelings().value(Feeling.REPROACH, 100), 1e-9);
        assertEquals(Feeling.REPROACH, state.feelings().strongest(100));
        assertEquals("read_her_log", state.keepsakes().of(ONE).getFirst().kind());
        assertEquals("read_her_log", state.keepsakes().due(ONE, 100 + 2 * DAY).kind(), "She can go back to it");
    }

    private static void sweep(java.util.function.Supplier<DailyLog> fresh, Set<String> keys) {
        for (Stage stage : Stage.values()) for (Tier tier : Tier.values()) for (int day = 1; day <= 12; day++) {
            var days = new java.util.ArrayList<Today>();
            days.add(today().unseenDays(9));
            days.add(today().read());
            days.add(today().coherence(0.6));
            days.add(today().nineFound());
            for (Feeling feeling : Feeling.values()) for (double strength : List.of(0.4, 0.7, 0.9)) days.add(today().feeling(feeling, strength));
            for (Mark mark : Mark.values()) for (int backs = 1; backs <= 2; backs++) days.add(today().marks(mark).backs(backs).feeling(Feeling.FEAR, 0.7));
            for (Mark mark : Mark.values()) for (int backs = 1; backs <= 2; backs++) days.add(today().marks(mark).backs(backs));
            for (Deed deed : Deed.values()) days.add(today().deeds(deed));
            for (var today : days) {
                var log = fresh.get();
                var note = log.write(today.on(day).at(stage).tier(tier).day()).note();
                if (note != null) keys.add(note);
                note = log.write(today().on(day + 1).at(stage).tier(tier).day()).note();
                if (note != null) keys.add(note);
            }
        }
    }

    /** Any note the order can reach, against every note the lang file holds: no line missing, and none that can never play. */
    @Test
    void everyLogKindHasALine() throws Exception {
        var keys = new java.util.TreeSet<String>();
        sweep(DailyLog::new, keys);
        sweep(DailyLogTest::filed, keys);
        sweep(() -> {
            var log = new DailyLog();
            log.write(today().nineFound().day());
            for (int i = 0; i < 3; i++) log.write(today().day());
            return log;
        }, keys);
        sweep(() -> {
            var log = filed();
            log.write(today().at(Stage.FINDING_WALLS).marks(Mark.BACK).backs(1).day());
            for (int i = 0; i < 3; i++) log.write(today().day());
            return log;
        }, keys);
        sweep(() -> {
            var log = new DailyLog();
            log.write(today().feeling(Feeling.FEAR, 0.7).day());
            return log;
        }, keys);
        try (var in = DailyLogTest.class.getResourceAsStream("/assets/frozendawn/lang/en_us.json")) {
            var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var lines = new java.util.TreeSet<>(lang.keySet().stream().filter(key -> key.startsWith(NOTE)).toList());
            assertEquals(lines, keys);
        }
    }
}
