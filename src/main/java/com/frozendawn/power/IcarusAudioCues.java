package com.frozendawn.power;

import com.frozendawn.power.IcarusPhysics.State;
import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/** Which AMR-1 Icarus one-shots a second plays. Pure: the Controller plays the cues at the core and relays the alarm
 * tiers to sealed suits. */
public final class IcarusAudioCues {
    public enum Cue { FRAME_HUM_ON, STABILIZER_SPINUP, SHIELD_FORM, CAPSULE_DOCK, INJECTOR_FEED, IGNITION, CLIP_SLAM, COLE_COMPLETE,
        ALARM_CAUTION, ALARM_WARNING, ALARM_CRITICAL }
    /** The suit repeats the critical line this often while the alarm stays critical. */
    public static final int CRITICAL_REPEAT_S = 60;
    private IcarusAudioCues() {}

    /** Cues for the step from before to after. before is null on the first second after a load, which plays nothing.
     * An alarm sounds only when its tier rises, and only the new tier. */
    public static EnumSet<Cue> cues(@Nullable IcarusPhysics.Readout before, IcarusPhysics.Readout after) {
        var cues = EnumSet.noneOf(Cue.class);
        if (before == null) return cues;
        State from = before.state(), to = after.state();
        if (from == State.COLD && to == State.POWER_CHECK) cues.add(Cue.FRAME_HUM_ON);
        if (entered(from, to, State.STABILIZER_SPINUP)) cues.add(Cue.STABILIZER_SPINUP);
        // The spin-up's clean end; an abort to COLD is not the shield forming.
        if (from == State.STABILIZER_SPINUP && to != State.STABILIZER_SPINUP && to != State.COLD) cues.add(Cue.SHIELD_FORM);
        if (to == State.INJECTION && after.injected() > 0) cues.add(Cue.CAPSULE_DOCK);
        if (entered(from, to, State.PRIMING)) cues.add(Cue.INJECTOR_FEED);
        if (entered(from, to, State.IGNITION)) cues.add(Cue.IGNITION);
        if (entered(from, to, State.CLIPPED)) cues.add(Cue.CLIP_SLAM);
        if (from == State.COLE_DISCHARGE && to == State.COLD) cues.add(Cue.COLE_COMPLETE);
        if (after.alarm().compareTo(before.alarm()) > 0) {
            switch (after.alarm()) {
                case CAUTION -> cues.add(Cue.ALARM_CAUTION);
                case WARNING -> cues.add(Cue.ALARM_WARNING);
                case CRITICAL -> cues.add(Cue.ALARM_CRITICAL);
                default -> {}
            }
        }
        return cues;
    }

    /** The alarm tier the suit announces this second, or null. criticalSeconds counts seconds since the alarm rose to
     * CRITICAL. */
    public static @Nullable IcarusPhysics.Alarm suitLine(@Nullable IcarusPhysics.Readout before, IcarusPhysics.Readout after, int criticalSeconds) {
        if (before == null || after.alarm() == IcarusPhysics.Alarm.NONE) return null;
        if (after.alarm().compareTo(before.alarm()) > 0) return after.alarm();
        boolean repeat = after.alarm() == IcarusPhysics.Alarm.CRITICAL && criticalSeconds > 0 && criticalSeconds % CRITICAL_REPEAT_S == 0;
        return repeat ? after.alarm() : null;
    }

    private static boolean entered(State from, State to, State target) { return from != target && to == target; }
}
