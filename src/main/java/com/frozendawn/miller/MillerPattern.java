package com.frozendawn.miller;

import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.miller.ImpressionStore.Theory;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * What runs under her, the counterpart to {@link com.frozendawn.maeve.MaeveDirector}. It decides what she feels, never
 * what she says: her words stay authored. It knows only what comes through
 * {@link com.frozendawn.shepherd.ShepherdReactions#saw}, {@link #heard} and the poll, and scans nothing itself.
 */
public final class MillerPattern {
    private MillerPattern() { }

    private static PatternRuntime current(MinecraftServer server) { return PatternRuntime.current(server); }

    /** She saw it happen. Callers have already checked that she could. */
    public static void appraise(MinecraftServer server, UUID who, Deed deed) {
        var runtime = current(server);
        runtime.state().appraise(who, deed, runtime.now());
        runtime.data.setDirty();
    }

    /** Her hearing, as {@code saw} is her seeing: what someone said or asked of her, for what she makes of them. */
    public static void heard(MinecraftServer server, UUID who, Heard heard) {
        var runtime = current(server);
        runtime.state().heard(who, heard, runtime.now());
        runtime.data.setDirty();
    }

    /** The 100-tick poll, with what it already knows. */
    public static void poll(MinecraftServer server, int near, long lastTalk, boolean trusted) {
        var runtime = current(server);
        runtime.state().poll(near, lastTalk, trusted, runtime.now());
        runtime.data.setDirty();
    }

    /** The face her feelings leave her on. Runs every tick: it only reads. */
    public static Mood restingMood(MinecraftServer server) {
        var runtime = current(server);
        return runtime.state().mood(runtime.now());
    }

    /** How sure she is of this about them, from -1 to 1. */
    public static double confidence(MinecraftServer server, UUID who, Theory theory) {
        return current(server).state().impressions().confidence(who, theory);
    }

    /** The one thing on her mind, with this person beside her. Only reads. */
    public static Attention.Focus focus(MinecraftServer server, UUID nearest) {
        var runtime = current(server);
        return Attention.on(runtime.state(), nearest, runtime.now());
    }

    /** What she wants to do about it. Only reads. */
    public static Urges.Urge urge(MinecraftServer server, UUID nearest) {
        var runtime = current(server);
        return runtime.state().urge(nearest, runtime.now());
    }

    /** How long a reply waits, in half laps of her thinking indicator: 0, 1 or 2. */
    public static int halfLaps(MinecraftServer server) {
        var runtime = current(server);
        return runtime.state().coherence().halfLaps(runtime.now());
    }

    /** How many of a pool's first lines she can reach right now. */
    public static int variety(MinecraftServer server, int lines) {
        var runtime = current(server);
        return runtime.state().coherence().variety(lines, runtime.now());
    }

    /** {@code /fd shepherd coherence}, for looking at a slow reply. */
    public static void setCoherence(MinecraftServer server, double value) {
        var runtime = current(server);
        runtime.state().coherence().set(value, runtime.now());
        runtime.data.setDirty();
    }

    /** {@code /fd shepherd skipday}: a day has gone by for her too. */
    public static void skipDay(MinecraftServer server) {
        var runtime = current(server);
        runtime.state().age(ShepherdPolicy.DAY_TICKS);
        runtime.data.setDirty();
    }

    /** {@code /fd shepherd feel}: only this feeling, at its fullest, for looking at a face. Not something she saw, so it leaves no reason behind. */
    public static void feel(MinecraftServer server, Feeling feeling) {
        var runtime = current(server);
        runtime.state().feelings().clear();
        runtime.state().feelings().raise(feeling, 1, runtime.now());
        runtime.data.setDirty();
    }

    /** Back to the moment she woke, for a replay of the first meeting and between GameTests. */
    public static void reset(MinecraftServer server) {
        var runtime = current(server);
        runtime.state().clear();
        runtime.data.setDirty();
    }

    public static void onServerStopped(MinecraftServer server) { PatternRuntime.stopped(server); }

    public static Snapshot snapshot(MinecraftServer server) {
        var runtime = current(server);
        return snapshot(runtime.state(), runtime.now());
    }

    static Snapshot snapshot(PatternState state, long now) {
        var feelings = new EnumMap<Feeling, Double>(Feeling.class);
        for (Feeling feeling : Feeling.values()) feelings.put(feeling, state.feelings().value(feeling, now));
        var drives = new EnumMap<Drive, Double>(Drive.class);
        for (Drive drive : Drive.values()) drives.put(drive, state.drives().value(drive, now));
        return new Snapshot(now, state.mood(now), state.feelings().strongest(now), feelings, drives, state.history(), state.coherence().value(now),
                state.self().stage(), state.self().walls(), state.impressions().all(), state.keepsakes().counts(), Attention.on(state, null, now));
    }

    public static List<String> diagnostics(MinecraftServer server) { return PatternDiagnostics.format(snapshot(server)); }
    public static List<String> explain(MinecraftServer server) { return PatternDiagnostics.explain(snapshot(server)); }
    public static List<String> mind(MinecraftServer server) { return PatternDiagnostics.mind(snapshot(server)); }

    /** {@code strongest} is the feeling on her face, or null when she is Neutral. {@code focus} is what is on her mind beside nobody in particular. */
    public record Snapshot(long now, Mood mood, Feeling strongest, Map<Feeling, Double> feelings, Map<Drive, Double> drives, List<Appraised> history,
                           double coherence, SelfModel.Stage stage, Set<PatternLaws> walls, Map<UUID, Map<Theory, Double>> impressions, Map<UUID, Integer> keepsakes,
                           Attention.Focus focus) {
        public Snapshot {
            feelings = Collections.unmodifiableMap(new EnumMap<>(feelings));
            drives = Collections.unmodifiableMap(new EnumMap<>(drives));
            history = List.copyOf(history);
            walls = Set.copyOf(walls);
            impressions = Collections.unmodifiableMap(new LinkedHashMap<>(impressions));
            keepsakes = Collections.unmodifiableMap(new LinkedHashMap<>(keepsakes));
        }
    }
}
