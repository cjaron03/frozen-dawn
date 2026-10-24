package com.frozendawn.power;

import com.frozendawn.power.FusionPhysics.State;
import com.frozendawn.power.FusionPhysics.Status;
import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/** Which SFR-1 one-shots a second plays, and when the warning loop sounds. Pure: the controller plays the cues. */
public final class SfrAudioCues {
    public enum Cue { READY, REFUSE, DISRUPTION, QUENCH }
    private SfrAudioCues() {}

    /** Cues for the step from before to after. before is null on the first second after a load, which plays nothing.
     * A refusal buzzes when a pressed Start is refused or an ignition pulse aborts, never for a status nobody acted on. */
    public static EnumSet<Cue> cues(@Nullable FusionPhysics.Readout before, FusionPhysics.Readout after, boolean refusedStart) {
        var cues = EnumSet.noneOf(Cue.class);
        if (before == null) return cues;
        if (before.state() == State.COOLDOWN && after.state() == State.READY) cues.add(Cue.READY);
        if (refusedStart || before.state() == State.IGNITION && after.state() == State.READY) cues.add(Cue.REFUSE);
        if (after.disrupted()) cues.add(after.status() == Status.MAGNET_QUENCH ? Cue.QUENCH : Cue.DISRUPTION);
        else if (before.state() == State.BURNING && after.status() == Status.ASH_POISONED) cues.add(Cue.DISRUPTION);
        return cues;
    }

    /** True while burning near the disruption limit, with ash building (pump off), or drawing more than it makes. */
    public static boolean alarm(FusionPhysics.Readout r, boolean pumping) {
        return r.state() == State.BURNING && (r.loopC() >= 1000 || !pumping || r.netKw() < 0);
    }
}
