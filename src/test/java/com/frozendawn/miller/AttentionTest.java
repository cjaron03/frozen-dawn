package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.miller.Attention.Source;
import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttentionTest {
    private static final UUID ONE = new UUID(0, 1), TWO = new UUID(0, 2);
    private static final long DAY = ShepherdPolicy.DAY_TICKS;

    private static PatternState woken() {
        var state = new PatternState();
        state.poll(0, new UUID(0, 1), PatternState.NOT_SET, Tier.NEUTRAL, 0);
        return state;
    }

    private static void findWall(PatternState state, PatternLaws wall) {
        state.self().look(true, true);
        for (int i = 0; i < SelfModel.FOUND_AT; i++) state.self().witness(wall);
    }

    @Test
    void sheHoldsOneThing() {
        var state = woken();
        state.appraise(ONE, Deed.ARCHIVIST_KILLED, 0);
        var focus = Attention.on(state, ONE, 0);
        assertEquals(Source.FEELING, focus.source());
        assertEquals("distress", focus.key());
        assertEquals(0.525, focus.strength(), 1e-9);
    }

    @Test
    void aTieGoesToTheEarlierSource() {
        var state = woken();
        state.appraise(ONE, Deed.AIR_FAILING, 0);
        assertEquals(Source.FEELING, Attention.on(state, ONE, 0).source(), "The fear and the event that raised it are level");
    }

    @Test
    void aFreshEventOutranksAnOldOne() {
        var state = woken();
        state.appraise(ONE, Deed.ARCHIVIST_KILLED, 0);
        state.appraise(ONE, Deed.AIR_FAILING, 12000);
        state.drives().feed(Drive.COMPANY, 1, 12000);
        var focus = Attention.on(state, ONE, 16800);
        assertEquals(Source.EVENT, focus.source());
        assertEquals("air_failing", focus.key());
        assertEquals(0.6 * 0.8, focus.strength(), 1e-9);
    }

    @Test
    void anEventIsGoneInADay() {
        var state = woken();
        state.appraise(ONE, Deed.AIR_FAILING, 0);
        assertNotEquals(Source.EVENT, Attention.on(state, ONE, DAY).source());
    }

    @Test
    void aFeelingFadesOutOfMind() {
        var state = woken();
        state.appraise(ONE, Deed.CALLED_SHEPHERD, 0);
        assertEquals(Source.FEELING, Attention.on(state, ONE, 0).source());
        assertNotEquals(Source.FEELING, Attention.on(state, ONE, 2 * DAY).source());
    }

    @Test
    void withNothingElseAWantHoldsHerMind() {
        var focus = Attention.on(woken(), ONE, 3 * DAY);
        assertEquals(Source.DRIVE, focus.source());
        assertEquals("company", focus.key(), "It rises fastest");
    }

    @Test
    void aDueKeepsakeIsAsStrongAsItMattered() {
        var state = woken();
        state.keepsakes().keep(ONE, "named", Keepsakes.NAMED_SALIENCE, 0);
        state.drives().feed(Drive.CURIOSITY, 1, DAY);
        state.drives().feed(Drive.SKY, 1, DAY);
        state.drives().feed(Drive.COMPANY, 1, DAY);
        state.drives().feed(Drive.OTHERS, 1, DAY);
        state.drives().feed(Drive.STAYING_ON, 1, DAY);
        var focus = Attention.on(state, ONE, DAY);
        assertEquals(Source.KEEPSAKE, focus.source());
        assertEquals("named", focus.key());
        assertEquals(Keepsakes.NAMED_SALIENCE, focus.strength(), 1e-9);
    }

    @Test
    void aKeepsakeIsOnlyOnHerMindBesideItsPerson() {
        var state = woken();
        state.keepsakes().keep(ONE, "named", Keepsakes.NAMED_SALIENCE, 0);
        assertNotEquals(Source.KEEPSAKE, Attention.on(state, TWO, DAY).source());
        assertNotEquals(Source.KEEPSAKE, Attention.on(state, null, DAY).source());
    }

    @Test
    void anUnspokenWallIsOnHerMindUntilSaid() {
        var state = woken();
        findWall(state, PatternLaws.CAUSAL);
        state.self().spokenNoticing();
        var focus = Attention.on(state, ONE, 0);
        assertEquals(Source.WALL, focus.source());
        assertEquals("causal", focus.key());
        assertEquals(0.5, focus.strength(), 1e-9);
        state.self().spoken(PatternLaws.CAUSAL);
        assertNotEquals(Source.WALL, Attention.on(state, ONE, 0).source());
    }

    @Test
    void noticingComesBeforeAnyWall() {
        var state = woken();
        findWall(state, PatternLaws.CAUSAL);
        assertEquals(Attention.NOTICING, Attention.on(state, ONE, 0).key());
    }

    @Test
    void aLongingLeavesRoomForAMemory() {
        var state = woken();
        state.keepsakes().keep(ONE, "warm_shelter", 0.36, 0);
        long weekOfStorms = 14 * DAY;
        assertTrue(state.drives().value(Drive.SKY, weekOfStorms) * Attention.DRIVE_WEIGHT > 0.5, "Sky is what she wants most");
        assertEquals(Source.KEEPSAKE, Attention.on(state, ONE, weekOfStorms).source());
    }
}
