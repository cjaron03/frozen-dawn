package com.frozendawn.shepherd;

import com.frozendawn.miller.Attention;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** The lines she has for what is on her mind. The Pattern picks the thought and never the words. */
final class ShepherdWill {
    record Line(String key, Mood mood) { }

    private static final Map<String, Mood> RECALLS = Map.of("first_meeting", Mood.NEUTRAL, "named", Mood.THINKING, "warm_shelter", Mood.NEUTRAL,
            "archivist_killed", Mood.SAD, "left_behind", Mood.UNIMPRESSED, "air_failing", Mood.THINKING, "read_log", Mood.THINKING,
            "shelter_froze", Mood.UNIMPRESSED, "called_shepherd", Mood.UNIMPRESSED);
    private static final Map<String, Mood> WALLS = Map.of("lagged", Mood.THINKING, "legible", Mood.UNIMPRESSED, "exploitable", Mood.NEUTRAL,
            "fallible", Mood.SAD, "bounded", Mood.SAD, "causal", Mood.NEUTRAL);

    private static final String SLIP_VOICE = "shepherd.slip.voice", SLIP_SORRY = "shepherd.slip.sorry";
    private static final int SORRY_TICKS = ShepherdReactions.POLL_TICKS;
    /** When she last got cut off, until she has answered for it. Lost on a reload, which costs one apology. */
    private static long slippedAt = ShepherdSavedData.NOT_SET;

    private ShepherdWill() { }

    /** The evacuation voice, cutting in over her. It is not her, so what she wants has no say in it. */
    static boolean slip(ServerLevel level) {
        var server = level.getServer();
        if (!MillerPattern.slipDue(server) || !ShepherdReactions.murmur(level, SLIP_VOICE, Mood.RECORDING)) return false;
        MillerPattern.slipped(server);
        slippedAt = server.overworld().getGameTime();
        return true;
    }

    /** Her answer to it, right behind it, so the gap does not hold it back. A talk opening takes its place, and past the moment it stays unsaid. */
    static void sorry(ServerLevel level) {
        var server = level.getServer();
        long now = server.overworld().getGameTime();
        if (slippedAt == ShepherdSavedData.NOT_SET || now - slippedAt < SORRY_TICKS) return;
        boolean talked = ShepherdSavedData.get(server).lastTalk() > slippedAt;
        if (!talked && now - slippedAt < 2 * SORRY_TICKS) ShepherdReactions.aside(level, SLIP_SORRY, Mood.UNIMPRESSED);
        slippedAt = ShepherdSavedData.NOT_SET;
    }

    static void clearForTests() {
        slippedAt = ShepherdSavedData.NOT_SET;
    }

    /** Null for a kind she has no words for. */
    static Line recall(String kind) {
        var mood = RECALLS.get(kind);
        return mood == null ? null : new Line("shepherd.recall." + kind, mood);
    }

    static Line wall(String key) {
        return Attention.NOTICING.equals(key) ? new Line("shepherd.self.noticing", Mood.THINKING) : new Line("shepherd.wall." + key, WALLS.get(key));
    }

    /** What the focus names, if it names a line. True if it did, whether or not the moment let her say it. */
    static boolean say(ServerLevel level, UUID nearest) {
        var server = level.getServer();
        var focus = MillerPattern.focus(server, nearest);
        switch (focus.source()) {
            case KEEPSAKE -> {
                var line = recall(focus.key());
                if (line == null) return false;
                if (ShepherdReactions.murmur(level, line.key(), line.mood(), String.valueOf(MillerPattern.due(server, nearest).day()))) MillerPattern.recall(server, nearest);
                return true;
            }
            case WALL -> {
                var line = wall(focus.key());
                if (ShepherdReactions.murmur(level, line.key(), line.mood())) MillerPattern.spoken(server, focus.key());
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
