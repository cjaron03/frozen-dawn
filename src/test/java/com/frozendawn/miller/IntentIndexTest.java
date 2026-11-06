package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.IntentIndex.Band;
import com.frozendawn.miller.IntentIndex.Ranked;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class IntentIndexTest {
    private static final Intent KEVIN = new Intent("kevin", "orsa.kevin", "tier_friendly", "label.kevin", "guess.kevin", List.of());
    private static final Intent ORSA = new Intent("orsa", "orsa.orsa", null, "label.orsa", "guess.orsa", List.of());
    private static final Intent LOCKED = new Intent("deng", null, null, null, null, List.of());
    private static final Intent HELLO = new Intent("hello", "chat.hello", null, null, null, List.of());
    private static final int ACCEPTED_TOP_ONE = 59, ACCEPTED_TOP_TWO = 66;
    private static final Predicate<String> ALL = condition -> true, NONE = condition -> false;

    private static JsonElement file(String path) throws IOException {
        try (var in = IntentIndexTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static List<Intent> shipped() throws IOException {
        var intents = new ArrayList<>(Intent.parse(file("/data/frozendawn/shepherd/intents/orsa.json")));
        intents.addAll(Intent.parse(file("/data/frozendawn/shepherd/intents/chat.json")));
        return intents;
    }

    private static IntentIndex index() throws IOException {
        return new IntentIndex(shipped(), Embeddings.load());
    }

    @Test void strongNeedsItsLead() {
        assertEquals(Band.STRONG, IntentIndex.judge(new Ranked(ORSA, 0.56, KEVIN, 0.46), ALL).band());
        assertEquals(Band.CLOSE, IntentIndex.judge(new Ranked(ORSA, 0.56, KEVIN, 0.47), ALL).band());
        assertEquals(Band.CLOSE, IntentIndex.judge(new Ranked(ORSA, 0.55, KEVIN, 0.1), ALL).band());
        assertEquals(Band.NONE, IntentIndex.judge(new Ranked(ORSA, 0.42, KEVIN, 0.1), ALL).band());
    }

    @Test void closeShowsTwoGuesses() {
        var match = IntentIndex.judge(new Ranked(ORSA, 0.5, KEVIN, 0.45), ALL);
        assertEquals(ORSA, match.first());
        assertEquals(KEVIN, match.second());
        assertNull(IntentIndex.judge(new Ranked(ORSA, 0.5, KEVIN, 0.42), ALL).second(), "too far back to guess");
    }

    @Test void aLockedSecondIsNotOffered() {
        var match = IntentIndex.judge(new Ranked(ORSA, 0.5, LOCKED, 0.45), ALL);
        assertEquals(Band.CLOSE, match.band());
        assertEquals(ORSA, match.first());
        assertNull(match.second());
    }

    @Test void aLockedWinnerIsNone() {
        var strong = IntentIndex.judge(new Ranked(LOCKED, 0.9, ORSA, 0.3), ALL);
        assertEquals(Band.NONE, strong.band());
        assertNull(strong.first());
        assertEquals(Band.NONE, IntentIndex.judge(new Ranked(LOCKED, 0.5, ORSA, 0.45), ALL).band());
    }

    @Test void noLabelIsNotOffered() {
        assertEquals(Band.NONE, IntentIndex.judge(new Ranked(HELLO, 0.5, ORSA, 0.45), ALL).band());
        assertNull(IntentIndex.judge(new Ranked(ORSA, 0.5, HELLO, 0.45), ALL).second());
        assertEquals(Band.STRONG, IntentIndex.judge(new Ranked(HELLO, 0.8, ORSA, 0.45), ALL).band(), "a label is only for offering");
    }

    @Test void aFailedWhenIsLocked() {
        assertEquals(Band.NONE, IntentIndex.judge(new Ranked(KEVIN, 0.9, ORSA, 0.3), NONE).band());
        assertNull(IntentIndex.judge(new Ranked(ORSA, 0.5, KEVIN, 0.45), NONE).second());
    }

    @Test void hiIsSmallTalkNotATopic() throws IOException {
        assertEquals("hello", index().rank("hi").first().id());
    }

    @Test void howAreYouIsSmallTalk() throws IOException {
        assertEquals("how_are_you", index().rank("how are you").first().id());
        assertEquals(Band.STRONG, index().judge("how are you?", ALL).band());
    }

    /** Whoever types what a button says gets what the button gives. */
    @Test void everyLabelOpensItsOwnTopic() throws IOException {
        var lang = file("/assets/frozendawn/lang/en_us.json").getAsJsonObject();
        var index = index();
        for (Intent intent : shipped()) {
            if (intent.label() == null) continue;
            var match = index.judge(lang.get(intent.label()).getAsString(), ALL);
            assertEquals(Band.STRONG, match.band(), intent.id());
            assertEquals(intent, match.first());
        }
    }

    /**
     * M0's marks, on lines written before this ran (Ruling 10). The set read 59 and 66 of 74, one line short of 80 and 90 percent on each, and the owner accepted that on
     * 2026-11-05, so those two hold at the accepted counts. If this fails, stop and report: never tune against it.
     */
    @Test void theFreshSetHoldsTheMarks() throws IOException {
        var index = index();
        var fresh = file("/frozendawn/intent_fresh.json").getAsJsonObject();
        Set<String> examples = new HashSet<>();
        for (Intent intent : shipped()) intent.examples().forEach(example -> examples.add(example.toLowerCase()));
        int lines = 0, top1 = 0, top2 = 0, strongWrong = 0, strongRight = 0, closeHit = 0, closeMiss = 0, none = 0, outside = 0, outsideStrong = 0, outsideClose = 0;
        var wrong = new StringBuilder();
        for (var intent : fresh.getAsJsonObject("in_scope").entrySet()) {
            for (String line : flat(intent.getValue().getAsJsonObject())) {
                assertFalse(examples.contains(line.toLowerCase()), "the set is held out: " + line);
                var ranked = index.rank(line);
                lines++;
                boolean first = ranked.first().id().equals(intent.getKey());
                if (first) top1++;
                if (first || ranked.second().id().equals(intent.getKey())) top2++;
                else wrong.append(String.format("%n  miss: %s | %s -> %s %.3f, %s %.3f", line, intent.getKey(), ranked.first().id(), ranked.firstScore(), ranked.second().id(), ranked.secondScore()));
                var match = index.judge(line, ALL);
                if (match.band() == Band.STRONG && !match.first().id().equals(intent.getKey())) {
                    strongWrong++;
                    wrong.append(String.format("%n  strong wrong: %s | %s -> %s", line, intent.getKey(), match.first().id()));
                } else if (match.band() == Band.STRONG) {
                    strongRight++;
                } else if (match.band() == Band.NONE) {
                    none++;
                } else if (match.first().id().equals(intent.getKey()) || match.second() != null && match.second().id().equals(intent.getKey())) {
                    closeHit++;
                } else {
                    closeMiss++;
                }
            }
        }
        for (String line : flat(fresh.getAsJsonObject("out_of_scope"))) {
            assertFalse(examples.contains(line.toLowerCase()), "the set is held out: " + line);
            outside++;
            var match = index.judge(line, ALL);
            if (match.band() == Band.CLOSE) outsideClose++;
            if (match.band() == Band.STRONG) {
                outsideStrong++;
                wrong.append(String.format("%n  out of scope strong: %s -> %s", line, match.first().id()));
            }
        }
        String report = String.format("in-scope n=%d top1 %d top2 %d strong right %d strong wrong %d close-with-answer %d close-without %d none %d | out-of-scope n=%d strong %d close %d%s",
                lines, top1, top2, strongRight, strongWrong, closeHit, closeMiss, none, outside, outsideStrong, outsideClose, wrong);
        System.out.println(report);
        assertTrue(lines >= 66 && outside >= 66, report);
        assertTrue(top1 >= ACCEPTED_TOP_ONE, report);
        assertTrue(top2 >= ACCEPTED_TOP_TWO, report);
        assertTrue(strongWrong <= 0.05 * lines, report);
        assertTrue(outsideStrong <= 0.05 * outside, report);
    }

    private static List<String> flat(JsonObject object) {
        var lines = new ArrayList<String>();
        for (var entry : object.entrySet()) {
            if (entry.getValue().isJsonArray()) entry.getValue().getAsJsonArray().forEach(line -> lines.add(line.getAsString()));
            else lines.addAll(flat(entry.getValue().getAsJsonObject()));
        }
        return lines;
    }
}
