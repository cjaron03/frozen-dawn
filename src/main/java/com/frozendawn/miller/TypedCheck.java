package com.frozendawn.miller;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** What is looked at before a line is matched. She never hears the word, and the guarded words always miss. */
public final class TypedCheck {
    public static final int MAX_CHARS = 120;
    private static final String WORD = "continuity";
    private static final String SWAPPED = "013457@$!", SWAPPED_TO = "oieastasi";
    private static final Set<String> GUARDED = Set.of("sophie", "miller", "tithonus", "anderson", "maeve", "minecraft", "game", "respawn");

    private TypedCheck() {}

    /** Held for the one call that judges it, cut here as well as on the client. */
    public static String clean(String text) {
        String line = text.replaceAll("\\p{Cntrl}", " ").replaceAll(" +", " ").strip();
        return line.length() > MAX_CHARS ? line.substring(0, MAX_CHARS) : line;
    }

    /** However it is typed: through case, accents, spaces, punctuation, repeats, number swaps and a one-letter typo. */
    public static boolean crashes(String text) {
        String plain = plain(text);
        if (collapse(plain.replaceAll("\\P{L}", "")).contains(WORD)) return true;
        return words(plain).stream().anyMatch(word -> oneEditAway(collapse(word)));
    }

    public static boolean guarded(String text) {
        return words(plain(text)).stream().anyMatch(GUARDED::contains);
    }

    private static List<String> words(String plain) {
        return List.of(plain.split("\\P{L}+"));
    }

    private static String plain(String text) {
        String lower = Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("\\p{M}", "");
        var swapped = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            int at = SWAPPED.indexOf(c);
            swapped.append(at < 0 ? c : SWAPPED_TO.charAt(at));
        }
        return swapped.toString();
    }

    private static String collapse(String text) {
        var collapsed = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (collapsed.isEmpty() || collapsed.charAt(collapsed.length() - 1) != c) collapsed.append(c);
        }
        return collapsed.toString();
    }

    /** One letter wrong, missing or added, or two neighbours swapped. */
    private static boolean oneEditAway(String word) {
        int a = 0, end = word.length(), target = WORD.length();
        if (Math.abs(end - target) > 1) return false;
        while (a < end && a < target && word.charAt(a) == WORD.charAt(a)) a++;
        if (a == end && a == target) return true;
        if (word.substring(Math.min(a + 1, end)).equals(WORD.substring(Math.min(a + 1, target)))) return end == target;
        if (word.substring(a).equals(WORD.substring(Math.min(a + 1, target)))) return true;
        if (word.substring(Math.min(a + 1, end)).equals(WORD.substring(a))) return true;
        return end == target && a + 1 < end && word.charAt(a) == WORD.charAt(a + 1) && word.charAt(a + 1) == WORD.charAt(a)
                && word.substring(a + 2).equals(WORD.substring(a + 2));
    }
}
