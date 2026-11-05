package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.Keepsakes.Outcome;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class KeepsakesTest {
    private static final UUID ONE = new UUID(0, 1), TWO = new UUID(0, 2);
    private static final long DAY = 24000, THREE_DAYS = 3 * DAY;

    private static Keepsakes full(double salience) {
        var kept = new Keepsakes();
        for (int i = 0; i < Keepsakes.MAX_KEPT; i++) assertEquals(Outcome.KEPT, kept.keep(ONE, "kind" + i, salience, i));
        return kept;
    }

    @Test
    void aSlightMomentIsNotKept() {
        var kept = new Keepsakes();
        assertEquals(Outcome.TOO_SLIGHT, kept.keep(ONE, "a", 0.29, 0));
        assertEquals(Outcome.KEPT, kept.keep(ONE, "a", 0.30, 0));
        assertEquals(1, kept.of(ONE).size());
    }

    @Test
    void aRepeatStrengthensInstead() {
        var kept = new Keepsakes();
        kept.keep(ONE, "a", 0.5, 0);
        assertEquals(Outcome.STRENGTHENED, kept.keep(ONE, "a", 0.5, THREE_DAYS - 1));
        assertEquals(1, kept.of(ONE).size());
        assertEquals(0.5 + 0.5 * Keepsakes.RECALL_GAIN, kept.of(ONE).getFirst().salience(), 1e-12);
        assertEquals(0, kept.of(ONE).getFirst().time(), "The moment stays the moment it was");
        assertEquals(Outcome.KEPT, kept.keep(ONE, "a", 0.5, THREE_DAYS));
        assertEquals(2, kept.of(ONE).size());
    }

    @Test
    void theSixtyFifthDisplacesTheLeast() {
        var kept = full(0.5);
        assertEquals(Outcome.STRENGTHENED, kept.keep(ONE, "kind3", 0.5, 100));
        assertEquals(Outcome.DISPLACED, kept.keep(ONE, "new", 0.6, 200));
        assertEquals(Keepsakes.MAX_KEPT, kept.of(ONE).size());
        assertTrue(kept.of(ONE).stream().anyMatch(k -> k.kind().equals("new")));
        assertTrue(kept.of(ONE).stream().anyMatch(k -> k.kind().equals("kind3")), "The strengthened one stays");
    }

    @Test
    void aTieDisplacesTheOldest() {
        var kept = full(0.5);
        kept.keep(ONE, "new", 0.6, 200);
        assertTrue(kept.of(ONE).stream().noneMatch(k -> k.kind().equals("kind0")), "The oldest of the slightest went");
        assertTrue(kept.of(ONE).stream().anyMatch(k -> k.kind().equals("kind1")));
    }

    @Test
    void aSlighterOneDisplacesNothing() {
        var kept = full(0.5);
        assertEquals(Outcome.TOO_SLIGHT, kept.keep(ONE, "new", 0.4, 200));
        assertEquals(Outcome.TOO_SLIGHT, kept.keep(ONE, "new", 0.5, 200), "It must have mattered more");
        assertTrue(kept.of(ONE).stream().noneMatch(k -> k.kind().equals("new")));
    }

    @Test
    void recallStrengthensAndRests() {
        var kept = new Keepsakes();
        kept.keep(ONE, "low", 0.4, 0);
        kept.keep(ONE, "high", 0.8, 0);
        var first = kept.recall(ONE, DAY);
        assertEquals("high", first.kind(), "The one that mattered most");
        assertEquals(0.8 + 0.2 * Keepsakes.RECALL_GAIN, first.salience(), 1e-12);
        assertEquals(1, first.recalled());
        assertNull(kept.recall(ONE, 2 * DAY - 1), "One a day");
        assertEquals("low", kept.recall(ONE, 2 * DAY).kind(), "That one rests");
        assertNull(kept.recall(ONE, 3 * DAY), "Both rest");
        assertEquals("high", kept.recall(ONE, DAY + THREE_DAYS).kind());
    }

    @Test
    void aLesserMomentGetsItsTurn() {
        var kept = new Keepsakes();
        kept.keep(ONE, "first", 0.9, 0);
        kept.keep(ONE, "second", 0.8, 0);
        kept.keep(ONE, "third", 0.7, 0);
        kept.keep(ONE, "fourth", 0.4, 0);
        var said = new java.util.HashSet<String>();
        for (long day = 1; day <= 8; day++) {
            var moment = kept.recall(ONE, day * DAY);
            if (moment != null) said.add(moment.kind());
        }
        assertTrue(said.contains("fourth"), said.toString());
    }

    @Test
    void aMomentWaitsADayToBeAMemory() {
        var kept = new Keepsakes();
        kept.keep(ONE, "a", 0.5, 0);
        assertNull(kept.due(ONE, DAY - 1));
        assertEquals("a", kept.due(ONE, DAY).kind());
    }

    @Test
    void lookingAtWhatIsDueChangesNothing() {
        var kept = new Keepsakes();
        kept.keep(ONE, "a", 0.5, 0);
        var saved = kept.save();
        kept.due(ONE, DAY);
        assertEquals(saved, kept.save());
    }

    @Test
    void aDeedWithNoWordsStaysUnsaid() {
        var kept = new Keepsakes();
        kept.keep(ONE, "clear_night", 0.9, 0);
        assertNull(kept.due(ONE, DAY));
    }

    @Test
    void readingHerListChangesNothing() {
        var kept = new Keepsakes();
        kept.keep(ONE, "a", 0.5, 0);
        var saved = kept.save();
        kept.of(ONE);
        kept.of(TWO);
        assertEquals(saved, kept.save());
    }

    @Test
    void eachPlayerHasTheirOwn() {
        var kept = new Keepsakes();
        kept.keep(ONE, "a", 0.5, 0);
        assertEquals(Outcome.KEPT, kept.keep(TWO, "a", 0.5, 0));
        assertEquals(1, kept.of(ONE).size());
        assertEquals(1, kept.of(TWO).size());
        assertNull(new Keepsakes().recall(ONE, 0));
        assertEquals(List.of(), new Keepsakes().of(ONE));
    }

    @Test
    void silenceTakesNothing() {
        var state = new PatternState();
        state.keepsakes().keep(ONE, "a", 0.5, 0);
        state.age(365 * DAY);
        assertEquals(1, state.keepsakes().of(ONE).size());
        assertEquals(0.5, state.keepsakes().of(ONE).getFirst().salience(), 1e-12);
        assertEquals(-365 * DAY, state.keepsakes().of(ONE).getFirst().time(), "It has only got older");
    }

    @Test
    void ageingMovesARecall() {
        var state = new PatternState();
        state.keepsakes().keep(ONE, "a", 0.5, 0);
        state.keepsakes().recall(ONE, DAY);
        state.age(THREE_DAYS);
        assertNotNull(state.keepsakes().recall(ONE, DAY), "Three days have gone by for it too");
    }

    @Test
    void aDeedIsKeptAtTheMostItRaised() {
        var state = new PatternState();
        state.poll(1, PatternState.NOT_SET, false, 0);
        state.appraise(ONE, Deed.ARCHIVIST_KILLED, 0);
        var moment = state.keepsakes().of(ONE).getFirst();
        assertEquals("archivist_killed", moment.kind());
        assertEquals(state.history().getFirst().raised().values().stream().max(Double::compare).orElseThrow(), moment.salience(), 1e-12);
    }

    @Test
    void aNodeKeepsItsMomentAtNamedSalience() {
        var state = new PatternState();
        state.heard(ONE, new Heard.Reply(null, List.of(), "named"), 0);
        var moment = state.keepsakes().of(ONE).getFirst();
        assertEquals("named", moment.kind());
        assertEquals(Keepsakes.NAMED_SALIENCE, moment.salience(), 1e-12);
    }

    @Test
    void aDamagedKeepsakeIsDropped() {
        var kept = new Keepsakes();
        kept.keep(ONE, "good", 0.5, 0);
        kept.keep(ONE, "nan", 0.5, 0);
        kept.keep(ONE, "negative", 0.5, 0);
        kept.keep(ONE, "nameless", 0.5, 0);
        var tag = kept.save();
        ListTag list = tag.getList("players", 10).getCompound(0).getList("kept", 10);
        list.getCompound(1).putDouble("salience", Double.NaN);
        list.getCompound(2).putDouble("salience", -1);
        list.getCompound(3).remove("kind");
        var loaded = Keepsakes.load(tag);
        assertEquals(List.of("good"), loaded.of(ONE).stream().map(Keepsake::kind).toList());
    }

    @Test
    void moreThanTheCapLoadsAtTheCap() {
        var tag = full(0.5).save();
        ListTag list = tag.getList("players", 10).getCompound(0).getList("kept", 10);
        var extra = list.getCompound(0).copy();
        extra.putString("kind", "extra");
        extra.putDouble("salience", 0.9);
        list.add(extra);
        var loaded = Keepsakes.load(tag);
        assertEquals(Keepsakes.MAX_KEPT, loaded.of(ONE).size());
        assertTrue(loaded.of(ONE).stream().anyMatch(k -> k.kind().equals("extra")), "The least goes, as it would have");
    }

    @Test
    void aSaveWithNoKeepsakesLoadsEmpty() {
        assertEquals(new Keepsakes().save(), Keepsakes.load(new CompoundTag()).save());
    }

    /** She says nothing at the husk. That is not the same as having nothing to say about it. */
    @Test
    void nineComesDueThoughSheWasSilentThere() {
        var keepsakes = new Keepsakes();
        var who = new java.util.UUID(0, 9);
        keepsakes.keep(who, "nine_found", 0.6, 0);
        assertEquals("nine_found", keepsakes.due(who, com.frozendawn.shepherd.ShepherdPolicy.DAY_TICKS).kind());
    }
}
