package com.frozendawn.miller;

import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded operator text. A player with cheats can read this, so it says CS-1 and never carries her file's name. */
final class PatternDiagnostics {
    private PatternDiagnostics() { }

    static List<String> format(MillerPattern.Snapshot snapshot) {
        var lines = new ArrayList<String>();
        lines.add(header(snapshot));
        var feelings = new StringBuilder("feelings:");
        snapshot.feelings().forEach((feeling, value) -> feelings.append(String.format(Locale.ROOT, " %s=%.3f", feeling, value)));
        lines.add(feelings.toString());
        var drives = new StringBuilder("drives:");
        snapshot.drives().forEach((drive, value) -> drives.append(String.format(Locale.ROOT, " %s=%.3f", drive, value)));
        lines.add(drives.toString());
        lines.add("reasons kept=" + snapshot.history().size() + "/" + PatternState.MAX_HISTORY);
        return List.copyOf(lines);
    }

    static List<String> explain(MillerPattern.Snapshot snapshot) {
        var lines = new ArrayList<String>();
        lines.add(header(snapshot));
        var feeling = snapshot.strongest();
        if (feeling == null) {
            lines.add(String.format(Locale.ROOT, "MOOD %s: no feeling at or above %.2f.", Mood.NEUTRAL, Temperament.SHOWS_AT));
        } else {
            lines.add(String.format(Locale.ROOT, "MOOD %s: %s=%.3f, the strongest at or above %.2f.", snapshot.mood(), feeling, snapshot.feelings().get(feeling), Temperament.SHOWS_AT));
            var causes = snapshot.history().reversed().stream().filter(entry -> entry.raised().containsKey(feeling)).toList();
            for (var cause : causes) lines.add(String.format(Locale.ROOT, "CAUSE %s by %s at tick=%d raised %s by %.3f", cause.deed(), cause.who(), cause.time(), feeling, cause.raised().get(feeling)));
            if (causes.isEmpty()) lines.add("No retained event raised " + feeling + "; its reasons have been dropped.");
        }
        var focus = snapshot.focus();
        lines.add(String.format(Locale.ROOT, "FOCUS %s %s=%.3f URGE %s", focus.source(), focus.key(), focus.strength(), snapshot.urge()));
        lines.add("A reaction, a conversation, asking, Wary and Offline all take her face over this.");
        for (var law : PatternLaws.values()) lines.add(law + ": " + law.meaning());
        return List.copyOf(lines);
    }

    /** What she holds: coherence, how far she has woken, and what she makes of each person. */
    static List<String> mind(MillerPattern.Snapshot snapshot) {
        var lines = new ArrayList<String>();
        lines.add(header(snapshot));
        lines.add(String.format(Locale.ROOT, "coherence=%.3f stage=%s walls=%s", snapshot.coherence(), snapshot.stage(), snapshot.walls().stream().sorted().toList()));
        var people = new LinkedHashSet<>(snapshot.impressions().keySet());
        people.addAll(snapshot.keepsakes().keySet());
        for (var who : people) {
            var line = new StringBuilder(who + " keepsakes=" + snapshot.keepsakes().getOrDefault(who, 0));
            snapshot.impressions().getOrDefault(who, Map.of()).forEach((theory, lean) -> line.append(String.format(Locale.ROOT, " %s=%.3f", theory, lean)));
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    private static String header(MillerPattern.Snapshot snapshot) {
        return "CS-1 inner state | tick=" + snapshot.now() + " | rests on " + snapshot.mood();
    }
}
