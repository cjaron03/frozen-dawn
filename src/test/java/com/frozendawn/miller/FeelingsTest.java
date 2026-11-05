package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeelingsTest {
    @Test
    void shesFeelingNothingAtTheStart() {
        for (Feeling feeling : Feeling.values()) assertEquals(0, new Feelings().value(feeling, 1000), 0);
    }

    @Test
    void aRaisedFeelingNeverPassesOne() {
        var feelings = new Feelings();
        for (int i = 0; i < 1000; i++) feelings.raise(Feeling.JOY, 1.0, 0);
        assertTrue(feelings.value(Feeling.JOY, 0) <= 1);
    }

    @Test
    void aSecondRaiseAddsLessThanTheFirst() {
        var feelings = new Feelings();
        feelings.raise(Feeling.JOY, 0.5, 0);
        feelings.raise(Feeling.JOY, 0.5, 0);
        assertEquals(0.75, feelings.value(Feeling.JOY, 0), 1e-12);
    }

    @Test
    void aFeelingFadesOnItsOwnHalfLife() {
        for (Feeling feeling : Feeling.values()) {
            var feelings = new Feelings();
            feelings.raise(feeling, 0.8, 0);
            assertEquals(0.4, feelings.value(feeling, feeling.halfLife()), 1e-12, feeling.name());
        }
    }

    @Test
    void theHalfLivesAreTheRulings() {
        var expected = Map.of(Feeling.JOY, 6000L, Feeling.DISTRESS, 12000L, Feeling.HOPE, 12000L, Feeling.FEAR, 2400L,
                Feeling.GRATITUDE, 24000L, Feeling.REPROACH, 24000L, Feeling.GUILT, 72000L);
        expected.forEach((feeling, ticks) -> assertEquals(ticks, feeling.halfLife(), feeling.name()));
    }

    @Test
    void reproachOutlastsDistress() {
        assertEquals(Temperament.STUBBORN * Feeling.DISTRESS.halfLife(), Feeling.REPROACH.halfLife());
    }

    @Test
    void belowTheLineSheIsNeutral() {
        var feelings = new Feelings();
        feelings.raise(Feeling.JOY, 0.29, 0);
        assertEquals(Mood.NEUTRAL, feelings.mood(0));
        feelings = new Feelings();
        feelings.raise(Feeling.JOY, 0.30, 0);
        assertEquals(Mood.HAPPY, feelings.mood(0));
    }

    @Test
    void eachFeelingShowsItsMood() {
        var expected = Map.of(Feeling.JOY, Mood.HAPPY, Feeling.DISTRESS, Mood.SAD, Feeling.HOPE, Mood.THINKING, Feeling.FEAR, Mood.ALARMED,
                Feeling.GRATITUDE, Mood.AMUSED, Feeling.REPROACH, Mood.UNIMPRESSED, Feeling.GUILT, Mood.SAD);
        expected.forEach((feeling, mood) -> {
            var feelings = new Feelings();
            feelings.raise(feeling, 0.5, 0);
            assertEquals(mood, feelings.mood(0), feeling.name());
        });
    }

    @Test
    void theStrongestFeelingWins() {
        var feelings = new Feelings();
        feelings.raise(Feeling.JOY, 0.4, 0);
        feelings.raise(Feeling.REPROACH, 0.6, 0);
        assertEquals(Mood.UNIMPRESSED, feelings.mood(0));
    }

    @Test
    void aTieGoesToTheEarlierFeeling() {
        var feelings = new Feelings();
        feelings.raise(Feeling.REPROACH, 0.5, 0);
        feelings.raise(Feeling.JOY, 0.5, 0);
        assertEquals(Mood.HAPPY, feelings.mood(0));
    }

    @Test
    void noFeelingIsRecordingOrOffline() {
        for (Feeling feeling : Feeling.values()) {
            assertNotEquals(Mood.RECORDING, feeling.mood());
            assertNotEquals(Mood.OFFLINE, feeling.mood());
        }
    }
}
