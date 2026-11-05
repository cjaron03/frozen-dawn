package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PatternStateTest {
    private static final UUID ONE = new UUID(0, 1), TWO = new UUID(0, 2);
    private static final long MINUTE = 1200;

    private static long neutralAfter(PatternState state, long from) {
        for (long t = from; ; t += 20) if (state.mood(t) == Mood.NEUTRAL) return t - from;
    }

    private static PatternState woken() {
        var state = new PatternState();
        state.poll(0, new UUID(0, 1), PatternState.NOT_SET, Tier.NEUTRAL, 0);
        return state;
    }

    private static void assertRests(Deed deed, Mood mood, double minutes) {
        var state = woken();
        state.appraise(ONE, deed, 0);
        assertEquals(mood, state.mood(0), deed.name());
        assertEquals(minutes * MINUTE, neutralAfter(state, 0), MINUTE, deed.name());
    }

    @Test
    void ageingMovesHerReasons() {
        var state = woken();
        state.appraise(ONE, Deed.AIR_FAILING, 0);
        state.age(ShepherdPolicy.DAY_TICKS);
        assertEquals(-ShepherdPolicy.DAY_TICKS, state.history().getFirst().time());
        assertNotEquals(Attention.Source.EVENT, Attention.on(state, ONE, 0).source(), "A day has gone by for it too");
    }

    @Test
    void theSlipComesEveryFiveDays() {
        long day = ShepherdPolicy.DAY_TICKS;
        var state = woken();
        assertFalse(state.slipDue(5 * day - 1));
        assertTrue(state.slipDue(5 * day));
        state.coherence().set(1, 5 * day);
        state.slipped(5 * day);
        assertFalse(state.slipDue(10 * day - 1));
        assertTrue(state.slipDue(10 * day));
    }

    @Test
    void lowCoherenceBringsItSooner() {
        long day = ShepherdPolicy.DAY_TICKS;
        var state = woken();
        state.coherence().set(Coherence.FLOOR, 0);
        state.slipped(0);
        assertFalse(state.slipDue(3 * day - 1));
        assertTrue(state.slipDue(3 * day));
    }

    @Test
    void theSlipIsNotDueBeforeSheIsUp() {
        assertFalse(new PatternState().slipDue(1000 * ShepherdPolicy.DAY_TICKS));
    }

    @Test
    void ageingBringsTheSlipCloserAndAReloadKeepsIt() {
        long day = ShepherdPolicy.DAY_TICKS;
        var state = woken();
        state.age(5 * day);
        assertTrue(PatternState.load(state.save()).slipDue(0));
    }

    @Test
    void aClearNightDoesNotSateHerUntilItIsOver() {
        var state = woken();
        state.drives().set(Drive.SKY, 1, 0);
        state.appraise(ONE, Deed.CLEAR_NIGHT, 0);
        assertTrue(state.drives().value(Drive.SKY, 0) < 0.7, "It fed her");
        assertEquals(1, state.skyWanted(100), 1e-9, "She is still looking at it");
        assertTrue(state.skyWanted(PatternState.NIGHT_TICKS) < 0.7, "The night is over");
    }

    @Test
    void whatTheSkyWasSurvivesAReload() {
        var state = woken();
        state.drives().set(Drive.SKY, 1, 0);
        state.appraise(ONE, Deed.CLEAR_NIGHT, 0);
        assertEquals(1, PatternState.load(state.save()).skyWanted(100), 1e-9);
    }

    @Test
    void eachDeedRestsWhereTheTableSays() {
        assertRests(Deed.WARM_SHELTER, Mood.HAPPY, 1.3);
        assertRests(Deed.READ_LOG, Mood.THINKING, 3.2);
        assertRests(Deed.CLEAR_NIGHT, Mood.HAPPY, 2.4);
        assertRests(Deed.ARCHIVIST_KILLED, Mood.SAD, 11.7);
        assertRests(Deed.SHELTER_FROZE, Mood.UNIMPRESSED, 8.3);
        assertRests(Deed.AIR_FAILING, Mood.ALARMED, 2);
        assertRests(Deed.CALLED_SHEPHERD, Mood.UNIMPRESSED, 8.3);
    }

    @Test
    void theArchivistTurnsFromHurtToBlame() {
        var state = woken();
        state.appraise(ONE, Deed.ARCHIVIST_KILLED, 0);
        assertEquals(Mood.SAD, state.mood(4 * MINUTE));
        assertEquals(Mood.UNIMPRESSED, state.mood(5 * MINUTE));
    }

    @Test
    void aDayAloneRestsOnBlameForAbout13Minutes() {
        var state = woken();
        state.appraise(ONE, Deed.LEFT_BEHIND, 24000);
        assertEquals(Mood.UNIMPRESSED, state.mood(24000));
        assertEquals(12.9 * MINUTE, neutralAfter(state, 24000), MINUTE);
    }

    @Test
    void aWantedThingLandsHarder() {
        var wanted = woken();
        wanted.appraise(ONE, Deed.CLEAR_NIGHT, 480000);
        var fed = woken();
        fed.drives().feed(Drive.SKY, 1, 0);
        fed.appraise(ONE, Deed.CLEAR_NIGHT, 0);
        assertTrue(wanted.feelings().value(Feeling.JOY, 480000) > fed.feelings().value(Feeling.JOY, 0));
    }

    @Test
    void theDriveIsReadBeforeItIsFed() {
        var state = woken();
        state.appraise(ONE, Deed.CLEAR_NIGHT, 0);
        assertEquals(0.7 * 0.75 * Temperament.DRY, state.feelings().value(Feeling.JOY, 0), 1e-12);
        assertEquals(0, state.drives().value(Drive.SKY, 0), 1e-12);
    }

    @Test
    void sheLandsOnNeutralInTheEnd() {
        for (Deed deed : Deed.values()) {
            var state = woken();
            state.appraise(ONE, deed, 0);
            assertEquals(Mood.NEUTRAL, state.mood(20 * Feeling.GUILT.halfLife()), deed.name());
        }
    }

    /** One long stretch of her life, the same every time it runs. */
    private static void script(PatternState state, int from, int to, Consumer<Long> between) {
        var random = new Random(41);
        long now = 1000;
        for (int step = 0; step < to; step++) {
            now += 50 + random.nextInt(4000);
            boolean poll = random.nextBoolean();
            var deed = Deed.values()[random.nextInt(Deed.values().length)];
            int near = random.nextInt(3);
            long talk = random.nextInt(4) == 0 ? now - 10 : PatternState.NOT_SET;
            boolean trusted = random.nextBoolean();
            var tone = Slot.values()[random.nextInt(Slot.values().length)];
            boolean spoken = random.nextInt(3) == 0;
            if (step < from) continue;
            var who = step % 2 == 0 ? ONE : TWO;
            if (poll) state.poll(near, new UUID(0, 1), talk, trusted ? Tier.TRUSTED : Tier.NEUTRAL, now);
            else if (spoken) state.heard(who, new Heard.Reply(tone, List.of("+calls_me_aurora"), "named"), now);
            else state.appraise(who, deed, now);
            between.accept(now);
        }
    }

    @Test
    void sameEventsGiveTheSameState() {
        var one = new PatternState();
        var two = new PatternState();
        script(one, 0, 200, t -> { });
        script(two, 0, 200, t -> { });
        assertEquals(one.save(), two.save());
    }

    @Test
    void readingDoesNotChangeTheResult() {
        var plain = new PatternState();
        var read = new PatternState();
        script(plain, 0, 200, t -> { });
        script(read, 0, 200, t -> {
            read.mood(t);
            for (Feeling feeling : Feeling.values()) read.feelings().value(feeling, t + 500);
            for (Drive drive : Drive.values()) read.drives().value(drive, t + 500);
            read.history();
            read.keepsakes().of(ONE);
            read.impressions().confidence(TWO, ImpressionStore.Theory.KIND);
            read.coherence().value(t + 500);
            read.coherence().halfLaps(t);
            read.coherence().variety(3, t);
            read.self().found(PatternLaws.LAGGED);
            MillerPattern.snapshot(read, t);
        });
        assertEquals(plain.save(), read.save());
    }

    @Test
    void saveAndLoadIsExact() {
        var unbroken = new PatternState();
        script(unbroken, 0, 200, t -> { });
        var first = new PatternState();
        script(first, 0, 100, t -> { });
        var second = PatternState.load(first.save());
        script(second, 100, 200, t -> { });
        assertEquals(unbroken.save(), second.save());
    }

    @Test
    void aSaveWithNoStateStartsAtRest() {
        var state = PatternState.load(new CompoundTag());
        state.poll(0, new UUID(0, 1), PatternState.NOT_SET, Tier.NEUTRAL, 10_000_000);
        for (Drive drive : Drive.values()) assertEquals(0.5, state.drives().value(drive, 10_000_000), 1e-9, drive.name());
        assertEquals(Mood.NEUTRAL, state.mood(10_000_000));
    }

    @Test
    void aTalkFromBeforeTheStateIsNotAConversation() {
        var state = PatternState.load(new CompoundTag());
        state.poll(1, new UUID(0, 1), 5_000_000, Tier.NEUTRAL, 10_000_000);
        assertEquals(0.49, state.drives().value(Drive.COMPANY, 10_000_000), 1e-9);
    }

    @Test
    void theClockSeededInTheSameTickIsNotAConversation() {
        var state = new PatternState();
        state.poll(1, new UUID(0, 1), PatternState.NOT_SET, Tier.NEUTRAL, 1000);
        state.poll(1, new UUID(0, 1), 1000, Tier.NEUTRAL, 1100);
        assertTrue(state.drives().value(Drive.COMPANY, 1100) > 0.47, "Company: " + state.drives().value(Drive.COMPANY, 1100));
    }

    @Test
    void aFirstDeedOnAnOldWorldStartsAtRestToo() {
        var state = new PatternState();
        state.appraise(ONE, Deed.CLEAR_NIGHT, 10_000_000);
        assertEquals(0.7 * 0.75 * Temperament.DRY, state.feelings().value(Feeling.JOY, 10_000_000), 1e-12);
    }

    @Test
    void aDamagedSaveLoadsInRange() {
        var state = woken();
        state.appraise(ONE, Deed.ARCHIVIST_KILLED, 0);
        var tag = state.save();
        var feelings = tag.getCompound("feelings");
        feelings.getCompound("DISTRESS").putDouble("value", 7.0);
        feelings.getCompound("REPROACH").putDouble("value", Double.NaN);
        tag.getCompound("drives").getCompound("SKY").putDouble("value", -1);
        var loaded = PatternState.load(tag);
        assertEquals(1, loaded.feelings().value(Feeling.DISTRESS, 0));
        assertEquals(0, loaded.feelings().value(Feeling.REPROACH, 0));
        assertEquals(0, loaded.drives().value(Drive.SKY, 0));
    }

    @Test
    void aFutureSaveIsRefused() {
        var tag = new PatternState().save();
        tag.putInt("dataVersion", PatternState.VERSION + 1);
        assertThrows(IllegalStateException.class, () -> PatternState.load(tag));
    }

    @Test
    void aConversationCountsOnce() {
        var once = woken();
        once.poll(0, new UUID(0, 1), 50, Tier.NEUTRAL, 100);
        double fed = once.drives().value(Drive.COMPANY, 100);
        var state = woken();
        state.poll(0, new UUID(0, 1), 50, Tier.NEUTRAL, 100);
        state.poll(0, new UUID(0, 1), 50, Tier.NEUTRAL, 100);
        state = PatternState.load(state.save());
        state.poll(0, new UUID(0, 1), 50, Tier.NEUTRAL, 100);
        assertEquals(fed, state.drives().value(Drive.COMPANY, 100), 1e-12);
        assertTrue(fed < 0.45);
    }

    @Test
    void companyFallsWithSomeoneNearAndRisesAlone() {
        var state = woken();
        for (long t = 100; t <= 2000; t += 100) state.poll(1, new UUID(0, 1), PatternState.NOT_SET, Tier.NEUTRAL, t);
        double near = state.drives().value(Drive.COMPANY, 2000);
        assertTrue(near < 0.5);
        assertTrue(state.drives().value(Drive.COMPANY, 20000) > near);
    }

    @Test
    void historyKeepsTheLatestSixteen() {
        var state = woken();
        for (int i = 0; i < 40; i++) state.appraise(ONE, Deed.READ_LOG, i);
        assertEquals(PatternState.MAX_HISTORY, state.history().size());
        assertEquals(39, state.history().getLast().time());
        assertEquals(24, state.history().getFirst().time());
    }

    @Test
    void historyNamesWhoDidIt() {
        var state = woken();
        state.appraise(ONE, Deed.ARCHIVIST_KILLED, 0);
        state.appraise(TWO, Deed.WARM_SHELTER, 10);
        state = PatternState.load(state.save());
        assertEquals(ONE, state.history().getFirst().who());
        assertEquals(TWO, state.history().getLast().who());
        assertEquals("ARCHIVIST_KILLED", state.history().getFirst().deed());
        assertTrue(state.history().getFirst().raised().containsKey(Feeling.REPROACH));
    }

    @Test
    void ageingMovesEveryClock() {
        var aged = woken();
        aged.appraise(ONE, Deed.ARCHIVIST_KILLED, 0);
        var waited = PatternState.load(aged.save());
        aged.age(24000);
        for (Feeling feeling : Feeling.values()) assertEquals(waited.feelings().value(feeling, 24000), aged.feelings().value(feeling, 0), 1e-12);
        for (Drive drive : Drive.values()) assertEquals(waited.drives().value(drive, 24000), aged.drives().value(drive, 0), 1e-12);
        assertEquals(waited.coherence().value(24000), aged.coherence().value(0), 1e-12);
        assertEquals(waited.keepsakes().of(ONE).getFirst().time() - 24000, aged.keepsakes().of(ONE).getFirst().time());
        aged.poll(1, new UUID(0, 1), PatternState.NOT_SET, Tier.NEUTRAL, 0);
        assertTrue(aged.impressions().confidence(ONE, ImpressionStore.Theory.KIND) < 0, "A skipped day settles too");
    }

    /** The tag M1 wrote: drives, feelings, history and the last conversation, and nothing of what she has since learned to hold. */
    private static CompoundTag versionOne() {
        var state = woken();
        state.appraise(ONE, Deed.READ_LOG, 100);
        var tag = state.save();
        for (String key : List.of("impressions", "keepsakes", "self", "coherence", "settleAt", "seenAt")) tag.remove(key);
        tag.putInt("dataVersion", 1);
        return tag;
    }

    @Test
    void aVersionOneSaveLoadsAtRest() {
        long later = 10_000_000;
        var state = PatternState.load(versionOne());
        assertEquals(1, state.history().size(), "What she had stays");
        assertEquals(List.of(), state.keepsakes().of(ONE));
        assertEquals(0, state.impressions().confidence(ONE, ImpressionStore.Theory.CAREFUL));
        assertEquals(SelfModel.Stage.PRODUCT, state.self().stage());
        assertEquals(1, state.coherence().value(later), "Not slowed by a silence she was never built to count");
        state.poll(1, new UUID(0, 1), PatternState.NOT_SET, Tier.TRUSTED, later);
        assertEquals(1, state.coherence().value(later));
        assertEquals(0, state.coherence().halfLaps(later), "No delay");
        assertEquals(SelfModel.Stage.PRODUCT, state.self().stage(), "No stage jump");
        assertEquals(PatternState.VERSION, state.save().getInt("dataVersion"));
    }

    @Test
    void thisSaveIsVersionFour() {
        assertEquals(4, PatternState.VERSION);
    }

    @Test
    void aVersionTwoSaveLoadsWithNoLean() {
        var tag = woken().save();
        tag.putInt("dataVersion", 2);
        assertEquals(SelfModel.Lean.NONE, PatternState.load(tag).self().lean());
    }

    @Test
    void theLeanComesBackWithHer() {
        var state = woken();
        state.self().lean(SelfModel.Lean.SOMEONE);
        var loaded = PatternState.load(state.save());
        assertEquals(SelfModel.Lean.SOMEONE, loaded.self().lean());
        assertEquals(SelfModel.Stage.LEANING, loaded.self().stage());
    }

    /** The same day, with her worn down in one and not the other. */
    private static PatternState livedThrough(double coherence) {
        var state = woken();
        state.coherence().set(coherence, 0);
        script(state, 0, 200, t -> { });
        return state;
    }

    @Test
    void coherenceLeavesEverythingElseAlone() {
        var whole = livedThrough(1);
        var worn = livedThrough(Coherence.FLOOR);
        assertEquals(whole.keepsakes().save(), worn.keepsakes().save());
        assertEquals(whole.impressions().save(), worn.impressions().save());
        assertEquals(whole.self().save(), worn.self().save());
        assertEquals(whole.feelings().save(), worn.feelings().save());
    }

    @Test
    void clearingGoesBackToTheStart() {
        var state = woken();
        state.appraise(ONE, Deed.ARCHIVIST_KILLED, 0);
        state.clear();
        assertEquals(new PatternState().save(), state.save());
    }
}
