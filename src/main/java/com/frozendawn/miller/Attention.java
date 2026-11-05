package com.frozendawn.miller;

import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.Locale;
import java.util.UUID;

/** The one thing on her mind. Bounded from the inside: everything else she holds waits behind it. Reading it changes nothing. */
public final class Attention {
    /** A tie goes to the earlier. */
    public enum Source {
        FEELING,
        EVENT,
        DRIVE,
        KEEPSAKE,
        WALL
    }

    /** {@code key} is the feeling, deed, drive, keepsake kind or wall, in lower case. */
    public record Focus(Source source, String key, double strength) { }

    /** Her first look at herself, which comes before any wall. */
    public static final String NOTICING = "noticing";
    static final double DRIVE_WEIGHT = 0.6, KEEPSAKE_WEIGHT = 0.5, WALL_STRENGTH = 0.5;

    private Attention() { }

    static Focus on(PatternState state, UUID nearest, long now) {
        Focus best = null;
        for (Feeling feeling : Feeling.values()) best = higher(best, new Focus(Source.FEELING, key(feeling), state.feelings().value(feeling, now)));
        for (var event : state.history().reversed()) best = higher(best, event(event, now));
        for (Drive drive : Drive.values()) best = higher(best, new Focus(Source.DRIVE, key(drive), DRIVE_WEIGHT * state.drives().value(drive, now)));
        var due = higher(keepsake(state, nearest, now), wall(state));
        // Storms take the sky from her for most of the game, and a want she cannot feed must not leave her nothing to say.
        return due != null && best.source() == Source.DRIVE ? due : higher(best, due);
    }

    private static Focus event(Appraised event, long now) {
        double left = 1 - (now - event.time()) / (double) ShepherdPolicy.DAY_TICKS;
        double raised = event.raised().values().stream().max(Double::compare).orElse(0.0);
        return new Focus(Source.EVENT, event.deed().toLowerCase(Locale.ROOT), raised * Math.max(0, Math.min(1, left)));
    }

    private static Focus keepsake(PatternState state, UUID nearest, long now) {
        var due = nearest == null ? null : state.keepsakes().due(nearest, now);
        return due == null ? null : new Focus(Source.KEEPSAKE, due.kind(), KEEPSAKE_WEIGHT * due.salience());
    }

    private static Focus wall(PatternState state) {
        if (state.self().noticingUnsaid()) return new Focus(Source.WALL, NOTICING, WALL_STRENGTH);
        var wall = state.self().unspoken();
        return wall == null ? null : new Focus(Source.WALL, key(wall), WALL_STRENGTH);
    }

    /** Null for neither. The one already held stays on a tie. */
    private static Focus higher(Focus held, Focus other) {
        return held == null || other != null && other.strength() > held.strength() ? other : held;
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
