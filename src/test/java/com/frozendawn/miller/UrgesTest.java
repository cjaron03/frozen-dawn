package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.miller.Attention.Focus;
import com.frozendawn.miller.Attention.Source;
import com.frozendawn.miller.Urges.Urge;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UrgesTest {
    private static final UUID ONE = new UUID(0, 1);

    private static Focus feeling(double strength) {
        return new Focus(Source.FEELING, "joy", strength);
    }

    @Test
    void aDriveNeverSpeaks() {
        assertEquals(Urge.STAY_QUIET, Urges.pick(new Focus(Source.DRIVE, "sky", 1), 0, 0));
    }

    @Test
    void whatIsOnHerMindSpeaksPastTheQuiet() {
        assertEquals(Urge.SPEAK, Urges.pick(feeling(0.36), 0, 0));
    }

    @Test
    void quietWinsATie() {
        assertEquals(Urge.STAY_QUIET, Urges.pick(feeling(Urges.QUIET), 0, 0));
        assertEquals(Urge.STAY_QUIET, Urges.pick(feeling(0), 0.5, 0.5), "Fresh wants are not enough");
    }

    @Test
    void aTieBetweenBodyAndWordsGoesToTheBody() {
        assertEquals(Urge.LOOK, Urges.pick(feeling(0.7 - 0.2), 0.7, 0));
        assertEquals(Urge.APPROACH, Urges.pick(feeling(0), 0.7, 0.7));
    }

    @Test
    void curiosityLooksAndLonelinessApproaches() {
        assertEquals(Urge.LOOK, Urges.pick(feeling(0), 0.6, 0));
        assertEquals(Urge.APPROACH, Urges.pick(feeling(0), 0, 0.6));
    }

    private static PatternState feeling() {
        var state = new PatternState();
        state.poll(1, new UUID(0, 1), PatternState.NOT_SET, Tier.NEUTRAL, 0);
        state.appraise(ONE, Deed.ARCHIVIST_KILLED, 10);
        return state;
    }

    @Test
    void sameStateSameUrge() {
        var one = feeling();
        var two = feeling();
        assertEquals(Attention.on(one, ONE, 500), Attention.on(two, ONE, 500));
        assertEquals(one.urge(ONE, 500), two.urge(ONE, 500));
    }

    @Test
    void readingTheUrgeChangesNothing() {
        var state = feeling();
        var before = state.save();
        state.urge(ONE, 500);
        Attention.on(state, ONE, 500);
        assertEquals(before, state.save());
    }
}
