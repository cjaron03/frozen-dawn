package com.frozendawn.power;

import com.frozendawn.power.FusionPhysics.State;
import com.frozendawn.power.FusionPhysics.Status;
import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/** Which SFR-1 one-shots a second plays, and when the warning loop sounds. Pure: the controller plays the cues. */
public final class SfrAudioCues {
    public enum Cue { READY, REFUSE, DISRUPTION, QUENCH }
    private static final EnumSet<Status> REFUSALS = EnumSet.of(Status.TORUS_OPEN, Status.NO_VACUUM, Status.NO_FUEL, Status.WALL_WORN, Status.ABORTED);
    private SfrAudioCues() {}

    /** Cues for the step from before to after. before is null on the first second after a load, which plays nothing.
     * A refusal buzzes once when READY enters it, not every second it stands. */
    public static EnumSet<Cue> cues(@Nullable FusionPhysics.Readout before, FusionPhysics.Readout after) {
        var cues = EnumSet.noneOf(Cue.class);
        if (before == null) return cues;
        if (before.state() == State.COOLDOWN && after.state() == State.READY) cues.add(Cue.READY);
        if (after.state() == State.READY && REFUSALS.contains(after.status()) && before.status() != after.status()) cues.add(Cue.REFUSE);
        if (after.disrupted()) cues.add(after.status() == Status.MAGNET_QUENCH ? Cue.QUENCH : Cue.DISRUPTION);
        else if (before.state() == State.BURNING && after.status() == Status.ASH_POISONED) cues.add(Cue.DISRUPTION);
        return cues;
    }

    /** True while burning near the disruption limit, with ash building (pump off), or drawing more than it makes. */
    public static boolean alarm(FusionPhysics.Readout r, boolean pumping) {
        return r.state() == State.BURNING && (r.loopC() >= 1000 || !pumping || r.netKw() < 0);
    }
}
