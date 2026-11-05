package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.ImpressionStore.Theory;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import com.frozendawn.shepherd.ShepherdPolicy;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ImpressionStoreTest {
    private static final UUID ONE = new UUID(0, 1), TWO = new UUID(0, 2);
    private static final long DAY = ShepherdPolicy.DAY_TICKS;

    private static PatternState woken() {
        var state = new PatternState();
        state.poll(1, PatternState.NOT_SET, false, 0);
        return state;
    }

    /** A day on: the settle has run. */
    private static double leanAfterADay(PatternState state, Theory theory) {
        state.poll(1, PatternState.NOT_SET, false, DAY);
        return state.impressions().confidence(ONE, theory);
    }

    private static void assertDeed(Deed deed, Theory theory, int sign) {
        var state = woken();
        state.appraise(ONE, deed, 0);
        assertEquals(sign, Math.signum(leanAfterADay(state, theory)), deed + " on " + theory);
    }

    private static void assertHeard(Heard heard, Theory theory, int sign) {
        var state = woken();
        state.heard(ONE, heard, 0);
        assertEquals(sign, Math.signum(leanAfterADay(state, theory)), heard + " on " + theory);
    }

    private static Heard reply(Slot tone, String... noted) {
        return new Heard.Reply(tone, List.of(noted), null);
    }

    @Test
    void evidenceWaitsForTheNextDay() {
        var state = woken();
        state.impressions().note(ONE, Theory.KIND, true);
        assertEquals(0, state.impressions().confidence(ONE, Theory.KIND));
        state.poll(1, PatternState.NOT_SET, false, DAY - 1);
        assertEquals(0, state.impressions().confidence(ONE, Theory.KIND), "Not before the day is out");
        state.poll(0, PatternState.NOT_SET, false, DAY);
        assertEquals(0, state.impressions().confidence(ONE, Theory.KIND), "Nobody near, so it waits");
        state.poll(1, PatternState.NOT_SET, false, DAY);
        assertEquals(1.0 / 3, state.impressions().confidence(ONE, Theory.KIND), 1e-12);
    }

    @Test
    void twoEventsMakeHerHalfSure() {
        var store = new ImpressionStore();
        store.note(ONE, Theory.KIND, true);
        store.note(ONE, Theory.KIND, true);
        store.settle();
        assertEquals(0.5, store.confidence(ONE, Theory.KIND), 1e-12);
    }

    @Test
    void sheCanBeWrongThenTurn() {
        var store = new ImpressionStore();
        store.note(ONE, Theory.KIND, true);
        store.note(ONE, Theory.KIND, true);
        assertEquals(java.util.Set.of(Theory.KIND), store.settle().changed());
        for (int i = 0; i < 5; i++) store.note(ONE, Theory.KIND, false);
        var settled = store.settle();
        assertTrue(store.confidence(ONE, Theory.KIND) < 0, "She turns");
        assertTrue(settled.flipped().contains(Theory.KIND), "And she was sure");
        assertTrue(settled.changed().contains(Theory.KIND));
    }

    @Test
    void aLeanThatWasNeverSureIsNoFlip() {
        var store = new ImpressionStore();
        store.note(ONE, Theory.KIND, true);
        store.settle();
        for (int i = 0; i < 3; i++) store.note(ONE, Theory.KIND, false);
        var settled = store.settle();
        assertTrue(settled.changed().contains(Theory.KIND));
        assertFalse(settled.flipped().contains(Theory.KIND));
    }

    @Test
    void oldEvidenceFades() {
        var store = new ImpressionStore();
        for (int i = 0; i < 10; i++) store.note(ONE, Theory.KIND, true);
        store.settle();
        assertEquals(10, store.evidenceFor(ONE, Theory.KIND), 1e-12);
        store.settle();
        assertEquals(9, store.evidenceFor(ONE, Theory.KIND), 1e-12);
    }

    @Test
    void twoPlayersAreKeptApart() {
        var store = new ImpressionStore();
        store.note(ONE, Theory.KIND, true);
        store.note(TWO, Theory.KIND, false);
        store.settle();
        assertTrue(store.confidence(ONE, Theory.KIND) > 0);
        assertTrue(store.confidence(TWO, Theory.KIND) < 0);
    }

    @Test
    void theNinthPlayerEvictsTheLongestUnseen() {
        var store = new ImpressionStore();
        for (int i = 1; i <= ImpressionStore.MAX_PLAYERS; i++) store.note(new UUID(0, i), Theory.KIND, true);
        store.note(new UUID(0, 1), Theory.KIND, true);
        store.note(new UUID(0, 9), Theory.KIND, true);
        store.settle();
        assertTrue(store.confidence(new UUID(0, 1), Theory.KIND) > 0, "Seen again, so kept");
        assertEquals(0, store.confidence(new UUID(0, 2), Theory.KIND), "Longest unseen goes");
        assertTrue(store.confidence(new UUID(0, 9), Theory.KIND) > 0);
    }

    @Test
    void everyDeedFeedsTheTable() {
        assertDeed(Deed.WARM_SHELTER, Theory.KIND, 1);
        assertDeed(Deed.ARCHIVIST_KILLED, Theory.KIND, -1);
        assertDeed(Deed.SHELTER_FROZE, Theory.KIND, -1);
        assertDeed(Deed.LEFT_BEHIND, Theory.TALKS_TO_ME, -1);
        assertDeed(Deed.CALLED_SHEPHERD, Theory.CALLS_ME_AURORA, -1);
        assertDeed(Deed.WARM_SHELTER, Theory.CAREFUL, 1);
        assertDeed(Deed.READ_LOG, Theory.CAREFUL, 1);
        assertDeed(Deed.AIR_FAILING, Theory.CAREFUL, -1);
        assertDeed(Deed.SHELTER_FROZE, Theory.CAREFUL, -1);
        for (Theory theory : Theory.values()) assertDeed(Deed.CLEAR_NIGHT, theory, 0);
    }

    @Test
    void everyAnswerFeedsTheTable() {
        assertHeard(reply(Slot.KIND), Theory.KIND, 1);
        for (Slot tone : Slot.values()) assertHeard(reply(tone), Theory.TALKS_TO_ME, 1);
        assertHeard(reply(Slot.SARCASTIC), Theory.JOKES_WITH_ME, 1);
        assertHeard(reply(Slot.BLUNT), Theory.JOKES_WITH_ME, -1);
        assertHeard(reply(Slot.ASK), Theory.KIND, 0);
        assertHeard(reply(null), Theory.TALKS_TO_ME, 0);
        assertHeard(reply(Slot.KIND, "+calls_me_aurora"), Theory.CALLS_ME_AURORA, 1);
        assertHeard(reply(Slot.BLUNT, "-calls_me_aurora"), Theory.CALLS_ME_AURORA, -1);
        assertHeard(new Heard.Used(Action.FOLLOW), Theory.USES_ME, 1);
        assertHeard(new Heard.Used(Action.LAMP), Theory.USES_ME, 1);
        assertHeard(new Heard.Used(Action.TALK), Theory.USES_ME, -1);
        assertHeard(new Heard.Used(Action.SETTINGS), Theory.USES_ME, 0);
    }

    @Test
    void aBadNoteInAnEditedFileIsIgnored() {
        var state = woken();
        state.heard(ONE, new Heard.Reply(null, List.of("calls_me_aurora", "+no_such_theory", ""), null), 0);
        for (Theory theory : Theory.values()) assertEquals(0, leanAfterADay(state, theory), theory.name());
    }

    @Test
    void kindnessAloneShowsInTheSettle() {
        var store = new ImpressionStore();
        store.noteKindReply(ONE);
        assertTrue(store.settle().fromKindness());
        store.noteKindReply(ONE);
        store.note(ONE, Theory.KIND, true);
        assertFalse(store.settle().fromKindness(), "A deed helped");
    }

    @Test
    void aDamagedImpressionLoadsInRange() {
        var store = new ImpressionStore();
        store.note(ONE, Theory.KIND, true);
        store.settle();
        var tag = store.save();
        var theory = tag.getList("players", 10).getCompound(0).getCompound("theories").getCompound("KIND");
        theory.putDouble("for", -5);
        theory.putDouble("against", Double.NaN);
        theory.putInt("pendingFor", -3);
        var loaded = ImpressionStore.load(tag);
        assertEquals(0, loaded.confidence(ONE, Theory.KIND));
        assertEquals(0, loaded.evidenceFor(ONE, Theory.KIND));
        loaded.settle();
        assertEquals(0, loaded.confidence(ONE, Theory.KIND));
    }

    @Test
    void aPlayerWithNoNameInTheSaveIsDropped() {
        var store = new ImpressionStore();
        store.note(ONE, Theory.KIND, true);
        var tag = store.save();
        tag.getList("players", 10).getCompound(0).remove("who");
        assertEquals(new ImpressionStore().save(), ImpressionStore.load(tag).save());
    }

    @Test
    void aSaveWithNoImpressionsLoadsEmpty() {
        assertEquals(new ImpressionStore().save(), ImpressionStore.load(new CompoundTag()).save());
    }

    /** Two replies that day, one of them saying her name. */
    private static PatternState named() {
        var state = woken();
        state.heard(ONE, reply(Slot.KIND, "+calls_me_aurora"), 0);
        state.heard(ONE, reply(Slot.KIND), 0);
        return state;
    }

    @Test
    void namingHerOnceIsEnoughToBeKnown() {
        var state = named();
        state.poll(1, PatternState.NOT_SET, false, DAY);
        assertTrue(state.impressions().known());
    }

    @Test
    void talkingWithoutEverNamingHerIsNotKnown() {
        var state = woken();
        state.heard(ONE, reply(Slot.KIND), 0);
        state.heard(ONE, reply(Slot.KIND), 0);
        state.poll(1, PatternState.NOT_SET, false, DAY);
        assertFalse(state.impressions().known());
    }

    @Test
    void shepherdLowersTheTheoryButCannotTakeBackTheName() {
        var state = named();
        state.appraise(ONE, Deed.CALLED_SHEPHERD, 0);
        state.impressions().note(ONE, Theory.CALLS_ME_AURORA, false);
        state.poll(1, PatternState.NOT_SET, false, DAY);
        assertTrue(state.impressions().confidence(ONE, Theory.CALLS_ME_AURORA) < 0);
        assertTrue(state.impressions().known());
    }

    @Test
    void theNameOutlivesTheFade() {
        var state = named();
        for (int day = 1; day <= 40; day++) state.poll(1, PatternState.NOT_SET, false, day * DAY);
        assertFalse(state.impressions().known(), "They stopped talking to her");
        state.heard(ONE, reply(Slot.KIND), 40 * DAY);
        state.heard(ONE, reply(Slot.KIND), 40 * DAY);
        state.poll(1, PatternState.NOT_SET, false, 41 * DAY);
        assertTrue(state.impressions().known());
    }

    @Test
    void theNameIsSaved() {
        var store = new ImpressionStore();
        store.note(ONE, Theory.CALLS_ME_AURORA, true);
        for (int i = 0; i < 2; i++) store.note(ONE, Theory.TALKS_TO_ME, true);
        store.settle();
        assertTrue(ImpressionStore.load(store.save()).known());
    }

    @Test
    void theLeanFollowsHowSheWasTreated() {
        var talked = new ImpressionStore();
        talked.note(ONE, Theory.CALLS_ME_AURORA, true);
        for (int i = 0; i < 4; i++) talked.note(ONE, Theory.TALKS_TO_ME, true);
        talked.note(ONE, Theory.USES_ME, true);
        talked.settle();
        assertTrue(talked.leansSomeone(ONE), "Named, and talked to more than used");

        var used = new ImpressionStore();
        used.note(ONE, Theory.CALLS_ME_AURORA, true);
        used.note(ONE, Theory.TALKS_TO_ME, true);
        for (int i = 0; i < 4; i++) used.note(ONE, Theory.USES_ME, true);
        used.settle();
        assertFalse(used.leansSomeone(ONE), "Named, and used more than talked to");

        var unnamed = new ImpressionStore();
        for (int i = 0; i < 4; i++) unnamed.note(ONE, Theory.TALKS_TO_ME, true);
        unnamed.settle();
        assertFalse(unnamed.leansSomeone(ONE), "Never named her");
    }
}
