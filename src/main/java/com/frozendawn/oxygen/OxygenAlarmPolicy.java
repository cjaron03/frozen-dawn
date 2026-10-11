package com.frozendawn.oxygen;

/** Per-listener warning cadence; unknown observations never re-arm an existing warning. */
public final class OxygenAlarmPolicy {
    public enum Cue { NONE, STRAINED, CRITICAL }
    private Cue previous = Cue.NONE;
    private long criticalAt = Long.MIN_VALUE, strainedAt = Long.MIN_VALUE;
    private long safeSince = Long.MIN_VALUE;

    public Cue update(OxygenStatus.Mode mode, boolean imminent, long tick) {
        Cue cue = imminent ? Cue.CRITICAL : mode == OxygenStatus.Mode.STRAINED ? Cue.STRAINED : Cue.NONE;
        if (mode == OxygenStatus.Mode.WAITING && !imminent) { safeSince = Long.MIN_VALUE; return Cue.NONE; }
        if (cue == Cue.NONE) {
            if (safeSince == Long.MIN_VALUE) safeSince = tick;
            if (tick - safeSince >= 40) previous = Cue.NONE;
            return Cue.NONE;
        }
        safeSince = Long.MIN_VALUE;
        boolean play = cue == Cue.CRITICAL ? tick >= criticalAt
                : previous != Cue.STRAINED && tick >= strainedAt;
        previous = cue;
        if (!play) return Cue.NONE;
        if (cue == Cue.CRITICAL) criticalAt = tick + 200;
        else strainedAt = tick + 600;
        return cue;
    }
}
