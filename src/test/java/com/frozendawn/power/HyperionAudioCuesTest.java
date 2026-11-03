package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.power.HyperionAudioCues.Cue;
import com.frozendawn.power.HyperionPhysics.Readout;
import com.frozendawn.power.HyperionPhysics.Stage;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class HyperionAudioCuesTest {
    private static Readout at(Stage stage, int secondsInStage) { return new Readout(stage, -190, 0, 100, false, false, secondsInStage); }

    @Test void chirpsEveryTenSeconds() {
        for (int s : new int[]{0, 10, 20}) assertEquals(EnumSet.of(Cue.CHIRP), HyperionAudioCues.cues(at(Stage.CHIRP, Math.max(0, s - 1)), at(Stage.CHIRP, s), false), "at " + s);
        assertEquals(EnumSet.noneOf(Cue.class), HyperionAudioCues.cues(at(Stage.CHIRP, 4), at(Stage.CHIRP, 5), false));
        assertEquals(EnumSet.noneOf(Cue.class), HyperionAudioCues.cues(at(Stage.WARNING, 9), at(Stage.WARNING, 10), false), "the warning has its own loop");
    }
    @Test void finalLeavesTheWhineToTheClient() {
        // A one-shot whine could not be stopped when the cells recover; the client plays it while the stage is FINAL.
        assertEquals(EnumSet.noneOf(Cue.class), HyperionAudioCues.cues(at(Stage.WARNING, 40), at(Stage.FINAL, 0), false));
        assertEquals(EnumSet.noneOf(Cue.class), HyperionAudioCues.cues(at(Stage.FINAL, 0), at(Stage.FINAL, 1), false));
    }
    @Test void quenchOnQuenchOrBreak() {
        var quenched = new Readout(Stage.WARM, -160, 0, Double.POSITIVE_INFINITY, true, false, 0);
        assertTrue(HyperionAudioCues.cues(at(Stage.FINAL, 24), quenched, false).contains(Cue.QUENCH));
        assertEquals(EnumSet.of(Cue.QUENCH), HyperionAudioCues.cues(at(Stage.COLD, 3), at(Stage.COLD, 4), true));
        assertEquals(EnumSet.noneOf(Cue.class), HyperionAudioCues.cues(at(Stage.FINAL, 24), new Readout(Stage.WARM, -180, 0, Double.POSITIVE_INFINITY, false, false, 0), false),
                "warming up empty is silent");
    }
    @Test void allClearOnRecovery() {
        var clear = new Readout(Stage.COLD, -181, 0, Double.POSITIVE_INFINITY, false, true, 0);
        assertEquals(EnumSet.of(Cue.ALL_CLEAR), HyperionAudioCues.cues(at(Stage.WARNING, 12), clear, false));
    }
    @Test void nothingAfterLoad() {
        assertEquals(EnumSet.noneOf(Cue.class), HyperionAudioCues.cues(null, at(Stage.CHIRP, 0), false));
        assertEquals(EnumSet.noneOf(Cue.class), HyperionAudioCues.cues(null, at(Stage.FINAL, 0), false));
    }
}
