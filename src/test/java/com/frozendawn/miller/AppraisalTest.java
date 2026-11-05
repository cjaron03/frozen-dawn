package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.Appraisal.Agent;
import com.frozendawn.miller.Appraisal.Rule;
import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppraisalTest {
    @Test
    void everyDeedHasARule() {
        for (Deed deed : Deed.values()) assertNotNull(Appraisal.rule(deed), deed.name());
    }

    @Test
    void aGoodThingGivesJoyLandingDry() {
        var feelings = Appraisal.intensities(new Rule(0.5, 0, false, null, 0, Agent.WORLD), 1);
        assertEquals(Map.of(Feeling.JOY, 0.5 * Temperament.DRY), feelings);
    }

    @Test
    void aBadThingGivesDistress() {
        assertEquals(Map.of(Feeling.DISTRESS, 0.5), Appraisal.intensities(new Rule(-0.5, 0, false, null, 0, Agent.WORLD), 1));
    }

    @Test
    void aProspectGivesHopeOrFear() {
        assertEquals(Map.of(Feeling.HOPE, 0.5), Appraisal.intensities(new Rule(0.5, 0, true, null, 0, Agent.WORLD), 1));
        assertEquals(Map.of(Feeling.FEAR, 0.5), Appraisal.intensities(new Rule(-0.5, 0, true, null, 0, Agent.WORLD), 1));
    }

    @Test
    void whoCausedItDecidesThanksOrBlame() {
        assertEquals(0.4 * Temperament.WARM, Appraisal.intensities(new Rule(0, 0.4, false, null, 0, Agent.PLAYER), 1).get(Feeling.GRATITUDE));
        assertEquals(0.4, Appraisal.intensities(new Rule(0, -0.4, false, null, 0, Agent.PLAYER), 1).get(Feeling.REPROACH));
        assertEquals(0.4, Appraisal.intensities(new Rule(0, -0.4, false, null, 0, Agent.SELF), 1).get(Feeling.GUILT));
        assertTrue(Appraisal.intensities(new Rule(0, -0.4, false, null, 0, Agent.WORLD), 1).isEmpty());
    }

    @Test
    void theScaleWeighsEverything() {
        assertEquals(Map.of(Feeling.DISTRESS, 0.25, Feeling.REPROACH, 0.15), Appraisal.intensities(new Rule(-0.5, -0.3, false, null, 0, Agent.PLAYER), 0.5));
    }

    @Test
    void noDriveMeansFullWeight() {
        assertEquals(1, Appraisal.scale(new Rule(0, 0, false, null, 0, Agent.WORLD), new Drives(), 0));
    }

    @Test
    void aDriveWeighsHalfToFull() {
        var drives = new Drives();
        drives.begin(0);
        var rule = new Rule(0.5, 0, false, Drive.SKY, 0, Agent.WORLD);
        assertEquals(0.75, Appraisal.scale(rule, drives, 0), 1e-12);
        drives.feed(Drive.SKY, 1, 0);
        assertEquals(0.5, Appraisal.scale(rule, drives, 0), 1e-12);
    }

    @Test
    void nineLeavesHerSad() {
        var state = new PatternState();
        var who = java.util.UUID.randomUUID();
        state.appraise(who, com.frozendawn.shepherd.ShepherdAffinity.Deed.READ_LOG, 0);
        long later = 40 * com.frozendawn.shepherd.ShepherdPolicy.DAY_TICKS;
        double others = state.drives().value(Drive.OTHERS, later);
        state.appraise(who, com.frozendawn.shepherd.ShepherdAffinity.Deed.NINE_FOUND, later);
        assertEquals(com.frozendawn.shepherd.ShepherdFace.Mood.SAD, state.mood(later));
        assertTrue(state.drives().value(Drive.OTHERS, later) < others, "Finding Nine feeds what she wanted to know");
        assertTrue(state.keepsakes().of(who).stream().anyMatch(moment -> moment.kind().equals("nine_found")));
    }
}
