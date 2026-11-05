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

    private ShepherdWill() { }

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
