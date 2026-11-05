package com.frozendawn.miller;

import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.miller.ImpressionStore.Theory;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    public static void poll(MinecraftServer server, int near, UUID nearest, long lastTalk, Tier tier) {
        var runtime = current(server);
        runtime.state().poll(near, nearest, lastTalk, tier, runtime.now());
        runtime.data.setDirty();
    }

    /** She saw it, and it is for the log alone. Callers have already checked that she could. */
    public static void marked(MinecraftServer server, UUID who, Mark mark) {
        var runtime = current(server);
        runtime.state().marked(who, mark, runtime.now());
        runtime.data.setDirty();
    }

    /** Her log, newest first. Only reads. */
    public static List<LogEntry> log(MinecraftServer server) {
        return current(server).state().log().entries();
    }

    /** Whether this person has paid for reading it. Only reads. */
    public static boolean hasRead(MinecraftServer server, UUID who) {
        return current(server).state().impressions().readLog(who);
    }

    /** They have paid, once. Charge first: a read she could not see is not marked. */
    public static void markRead(MinecraftServer server, UUID who) {
        var runtime = current(server);
        runtime.state().impressions().read(who);
        runtime.data.setDirty();
    }

    /** Whether she has said what she had to about it, or been unable to. Only reads. */
    public static boolean toldOff(MinecraftServer server, UUID who) {
        return current(server).state().impressions().toldOff(who);
    }

    public static void markTold(MinecraftServer server, UUID who) {
        var runtime = current(server);
        runtime.state().impressions().told(who);
        runtime.data.setDirty();
    }

    /** {@code /fd shepherd log write}: an entry now, about this person, without waiting for the day to settle. */
    public static LogEntry writeDay(MinecraftServer server, UUID who, long lastTalk, Tier tier) {
        var runtime = current(server);
        var entry = runtime.state().writeDay(who, lastTalk, tier, runtime.now());
        runtime.data.setDirty();
        return entry;
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

    /** The moment she would go back to with this person, or null. Only reads. */
    public static Keepsake due(MinecraftServer server, UUID who) {
        var runtime = current(server);
        return runtime.state().keepsakes().due(who, runtime.now());
    }

    /** She went back to it, which strengthens it. */
    public static void recall(MinecraftServer server, UUID who) {
        var runtime = current(server);
        runtime.state().keepsakes().recall(who, runtime.now());
        runtime.data.setDirty();
    }

    public static List<Keepsake> keepsakes(MinecraftServer server, UUID who) {
        return current(server).state().keepsakes().of(who);
    }

    /** Whether she holds a moment of this kind with this person. A keepsake, because her reasons only go back sixteen. */
    public static boolean remembers(MinecraftServer server, UUID who, String kind) {
        return keepsakes(server, who).stream().anyMatch(moment -> moment.kind().equals(kind));
    }

    /** How far she has got in her picture of herself. Only reads. */
    public static SelfModel.Stage stage(MinecraftServer server) {
        return current(server).state().self().stage();
    }

    /** What she made of the register, or NONE. Only reads. */
    public static SelfModel.Lean leaning(MinecraftServer server) {
        return current(server).state().self().lean();
    }

    /** She read it with this person beside her, and what she makes of them is what she makes of it. */
    public static void lean(MinecraftServer server, UUID who) {
        var runtime = current(server);
        runtime.state().self().lean(runtime.state().impressions().leansSomeone(who) ? SelfModel.Lean.SOMEONE : SelfModel.Lean.LEFT);
        runtime.data.setDirty();
    }

    /** What she wants of the sky, as a reason to stay under it. */
    public static double skyWanted(MinecraftServer server) {
        var runtime = current(server);
        return runtime.state().skyWanted(runtime.now());
    }

    /** She said what the focus named. {@code key} is a wall in lower case or {@link Attention#NOTICING}. */
    public static void spoken(MinecraftServer server, String key) {
        var runtime = current(server);
        if (Attention.NOTICING.equals(key)) runtime.state().self().spokenNoticing();
        else runtime.state().self().spoken(PatternLaws.valueOf(key.toUpperCase(Locale.ROOT)));
        runtime.data.setDirty();
    }

    /** The urge to look was acted on. */
    public static void looked(MinecraftServer server) {
        var runtime = current(server);
        runtime.state().looked(runtime.now());
        runtime.data.setDirty();
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

    /** {@code /fd shepherd want}, for looking at what a want does. */
    public static void want(MinecraftServer server, Drive drive, double value) {
        var runtime = current(server);
        runtime.state().drives().set(drive, value, runtime.now());
        runtime.data.setDirty();
    }

    /** {@code /fd shepherd witness}: she has reached the walls and has seen this one this many times. */
    public static void witness(MinecraftServer server, PatternLaws wall, int times) {
        var runtime = current(server);
        runtime.state().self().look(true, true);
        for (int i = 0; i < times; i++) runtime.state().self().witness(wall);
        runtime.data.setDirty();
    }

    /** Whether the recording is due to cut in. Only reads. */
    public static boolean slipDue(MinecraftServer server) {
        var runtime = current(server);
        return runtime.state().slipDue(runtime.now());
    }

    /** It cut in, so the next one starts counting from here. */
    public static void slipped(MinecraftServer server) {
        var runtime = current(server);
        runtime.state().slipped(runtime.now());
        runtime.data.setDirty();
    }

    /** {@code /fd shepherd slip}: due at the next poll. */
    public static void slipNow(MinecraftServer server) {
        var runtime = current(server);
        runtime.state().slipNow(runtime.now());
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
        return snapshot(server, null);
    }

    private static Snapshot snapshot(MinecraftServer server, UUID nearest) {
        var runtime = current(server);
        return snapshot(runtime.state(), nearest, runtime.now());
    }

    static Snapshot snapshot(PatternState state, long now) {
        return snapshot(state, null, now);
    }

    static Snapshot snapshot(PatternState state, UUID nearest, long now) {
        var feelings = new EnumMap<Feeling, Double>(Feeling.class);
        for (Feeling feeling : Feeling.values()) feelings.put(feeling, state.feelings().value(feeling, now));
        var drives = new EnumMap<Drive, Double>(Drive.class);
        for (Drive drive : Drive.values()) drives.put(drive, state.drives().value(drive, now));
        return new Snapshot(now, state.mood(now), state.feelings().strongest(now), feelings, drives, state.history(), state.coherence().value(now),
                state.self().stage(), state.self().lean(), state.self().walls(), state.impressions().all(), state.keepsakes().counts(), Attention.on(state, nearest, now), state.urge(nearest, now),
                state.log().entries().isEmpty() ? null : String.valueOf(state.log().entries().getFirst().note()));
    }

    public static List<String> diagnostics(MinecraftServer server) { return PatternDiagnostics.format(snapshot(server)); }
    /** {@code nearest} is whose side she is asked at, which may be nobody: a keepsake is only on her mind beside its person. */
    public static List<String> explain(MinecraftServer server, UUID nearest) { return PatternDiagnostics.explain(snapshot(server, nearest)); }
    public static List<String> mind(MinecraftServer server) { return PatternDiagnostics.mind(snapshot(server)); }

    /** {@code strongest} is the feeling on her face, or null when she is Neutral. {@code focus} and {@code urge} are for one person's side, or nobody's. {@code lastNote} is the last entry's key, or null with no entries. */
    public record Snapshot(long now, Mood mood, Feeling strongest, Map<Feeling, Double> feelings, Map<Drive, Double> drives, List<Appraised> history,
                           double coherence, SelfModel.Stage stage, SelfModel.Lean lean, Set<PatternLaws> walls, Map<UUID, Map<Theory, Double>> impressions, Map<UUID, Integer> keepsakes,
                           Attention.Focus focus, Urges.Urge urge, String lastNote) {
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
