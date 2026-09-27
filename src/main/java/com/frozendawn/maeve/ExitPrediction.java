package com.frozendawn.maeve;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** One conditional level, scoped to a retained observed shelter rather than a named door. */
final class ExitPrediction {
    static final double THRESHOLD = .90;
    private static final Pattern KEY = Pattern.compile("EXIT_AFTER_([NESW])_([NESW])_([0-9A-F]{32})");
    private ExitPrediction() { }

    record Pair(String from, String to, String area) { }

    static String key(String from, String to, UUID area) {
        return "EXIT_AFTER_" + from.substring(from.length() - 1) + "_" + to.substring(to.length() - 1)
                + "_" + token(area);
    }

    static String token(UUID area) { return area.toString().replace("-", "").toUpperCase(Locale.ROOT); }

    static Pair parse(String pattern) {
        var match = KEY.matcher(pattern);
        return !match.matches() || match.group(1).equals(match.group(2)) ? null
                : new Pair("RETREAT_BEARING_" + match.group(1), "RETREAT_BEARING_" + match.group(2), match.group(3));
    }

    static boolean spatial(String pattern) { return WorldModel.BEARINGS.contains(pattern) || parse(pattern) != null; }
    static boolean meets(String pattern, double confidence) {
        return confidence + (parse(pattern) == null ? 0 : 1e-9) >= threshold(pattern);
    }
    static double threshold(String pattern) { return parse(pattern) == null ? CommitmentPolicy.THRESHOLD : THRESHOLD; }
}
