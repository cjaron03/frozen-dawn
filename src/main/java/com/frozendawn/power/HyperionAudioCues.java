package com.frozendawn.power;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/** Which one-shot Hyperion sounds a second's step calls for. The loops (hum, warning) and the final whine follow the synced state instead, so recovery silences them. */
public final class HyperionAudioCues {
    public enum Cue { CHIRP, QUENCH, ALL_CLEAR }
    private HyperionAudioCues() {}

    public static EnumSet<Cue> cues(@Nullable HyperionPhysics.Readout before, HyperionPhysics.Readout after, boolean broke) {
        var out = EnumSet.noneOf(Cue.class);
        // A freshly loaded Controller has no previous second to compare, so it stays quiet until its next.
        if (before == null) return out;
        if (after.stage() == HyperionPhysics.Stage.CHIRP && after.secondsInStage() % 10 == 0) out.add(Cue.CHIRP);
        if (after.quenched() || broke) out.add(Cue.QUENCH);
        if (after.allClear()) out.add(Cue.ALL_CLEAR);
        return out;
    }
}
