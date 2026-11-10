package com.frozendawn.lore;

import com.frozendawn.client.OrsaAwakeningIntro;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The payoff flag's rules and the canon text it reveals. */
class PayoffStateTest {
    @Test
    void firesOnlyOnceAfterErasure() {
        assertFalse(PayoffState.firesOnDeath(false, false, false), "Not before the Heart falls");
        assertTrue(PayoffState.firesOnDeath(true, false, false), "The first death after erasure");
        assertFalse(PayoffState.firesOnDeath(true, true, false), "Never when Maeve escaped");
        assertFalse(PayoffState.firesOnDeath(true, false, true), "Never twice");
    }

    @Test
    void onlyNewSavesAreEligible() {
        assertTrue(PayoffState.eligibleAt(0));
        assertTrue(PayoffState.eligibleAt(1199));
        assertFalse(PayoffState.eligibleAt(1200));

        var old = PayoffState.create(5_000_000, true);
        assertFalse(old.isEligible(), "A world from before the payoff");
        assertFalse(old.isFired());
        assertTrue(old.isTerminusTurned(), "Keeps the Terminus turn it already had");

        var fresh = PayoffState.create(0, false);
        assertTrue(fresh.isEligible(), "A new world");
        assertFalse(fresh.isFired());
        assertFalse(fresh.isTerminusTurned());
    }

    @Test
    void theAdvancementIsHidden() {
        JsonObject advancement = json("data/frozendawn/advancement/payoff_fired.json");
        assertFalse(advancement.has("parent"), "No parent");
        assertFalse(advancement.has("display"), "No display");
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        assertEquals(1, criteria.size(), "One criterion");
        var only = criteria.entrySet().iterator().next().getValue().getAsJsonObject();
        assertEquals("minecraft:impossible", only.get("trigger").getAsString());
    }

    @Test
    void theDeathLineIsCanon() {
        JsonObject lang = json("assets/frozendawn/lang/en_us.json");
        assertEquals("Continuity protocol: active.", lang.get("screen.frozendawn.payoff.death_line.1").getAsString());
        assertEquals("1 enrollee remaining.", lang.get("screen.frozendawn.payoff.death_line.2").getAsString());
    }

    private static final String ENTRIES =
            "assets/frozendawn/patchouli_books/frozen_dawn_guide/en_us/entries/orsa_intel/";
    private static final String UNLOCK = "frozendawn:payoff_fired";
    private static final String REDACTED = "\u2588".repeat(10);

    @Test
    void theLandingChangesToCanon() {
        String landing = json("assets/frozendawn/lang/en_us.json")
                .get("book.frozendawn.frozen_dawn_guide.landing_text.fired").getAsString();
        assertEquals("$(br2)$(br2)$(br2)$(l)Issued field survival manual.$()$(br2)Continuity protocol: active."
                + "$(br)1 enrollee remaining.$(br)Civilian edition.", landing);
        assertEquals("Issued field survival manual. Continuity protocol: active. 1 enrollee remaining. Civilian edition.",
                strip(landing).replace('\n', ' ').replaceAll(" +", " ").trim());
    }

    @Test
    void theThirdFileFootnoteFollowsTheThirdFile() {
        var pages = json(ENTRIES + "what_is_orsa.json").getAsJsonArray("pages");
        assertEquals(22, pages.size());
        var footnote = pages.get(12).getAsJsonObject();
        String text = footnote.get("text").getAsString();
        assertEquals("A third file exists. It does not belong to a director. The name is " + REDACTED
                + ". The role listed is " + REDACTED + ". The file has a termination date.", text);
        var runs = java.util.regex.Pattern.compile("\u2588+").matcher(text).results().map(r -> r.group().length()).toList();
        assertEquals(java.util.List.of(10, 10), runs, "Two redactions of exactly ten");
        assertEquals(UNLOCK, footnote.get("advancement").getAsString());
        assertEquals("2074 \u2014 Denial", pages.get(13).getAsJsonObject().get("title").getAsString());
    }

    @Test
    void theProjectionReviewClosesTheTimeline() {
        var pages = json(ENTRIES + "projected_timeline.json").getAsJsonArray("pages");
        assertEquals(9, pages.size());
        assertEquals("Projected enrollees remaining at terminus: 1.$(br2)You are the unluckiest person alive, and the luckiest,"
                + " because you are alive.$(br2)$(italic)ORSA Science Division. Rooting for you, quietly, from Mars.$()",
                pages.get(7).getAsJsonObject().get("text").getAsString(), "The sign-off is unchanged");
        var review = pages.get(8).getAsJsonObject();
        assertEquals(String.join("\n", "PROJECTION REVIEW", "All phases observed as forecast.",
                "Enrollees remaining at terminus: 1.", "Variance: 0.", "Projection source: enrollee record."),
                strip(review.get("text").getAsString()));
        assertEquals(UNLOCK, review.get("advancement").getAsString());
    }

    @Test
    void noRevealPageSaysTheWord() {
        String footnote = json(ENTRIES + "what_is_orsa.json").getAsJsonArray("pages").get(12).getAsJsonObject().get("text").getAsString();
        String review = json(ENTRIES + "projected_timeline.json").getAsJsonArray("pages").get(8).getAsJsonObject().get("text").getAsString();
        assertFalse(footnote.toLowerCase().contains("continuity"));
        assertFalse(review.toLowerCase().contains("continuity"));
    }

    @Test
    void theReplayReadsAsProjected() {
        JsonObject lang = json("assets/frozendawn/lang/en_us.json");
        var replay = OrsaAwakeningIntro.readout(true).stream().map(k -> lang.get(k).getAsString()).toList();
        assertEquals(java.util.List.of(
                "VITALS: PULSE ELEVATED / RESPIRATION IRREGULAR",
                "TRAUMA: NONE RECORDED",
                "ENROLLEES REMAINING: 1 / AS PROJECTED",
                "CURRENT TIME: 88:14 LOCAL / CLOCK COMPROMISED"), replay);
        var opening = OrsaAwakeningIntro.readout(false).stream().map(k -> lang.get(k).getAsString()).toList();
        assertEquals(java.util.List.of(
                "VITALS: PULSE ELEVATED / RESPIRATION IRREGULAR",
                "TRAUMA: MINOR CRANIAL CONCUSSION",
                "FINAL SURFACE SHUTTLE: DEPARTED 03:17 UTC",
                "CURRENT TIME: 88:14 LOCAL / CLOCK COMPROMISED"), opening);
        assertEquals("FIRST AWAKENING TRIAGE", lang.get("screen.frozendawn.awakening.header").getAsString());
        for (String line : replay) assertFalse(line.toLowerCase().contains("continuity"), line);
    }

    /** Owner, 2026-11-09: once the ring is audible, a respawn's boot cue rests one minute. */
    @Test
    void theBootCueRestsOneMinute() {
        assertTrue(OrsaAwakeningIntro.bootCueDue(0, Long.MIN_VALUE), "Nothing has played yet");
        assertTrue(OrsaAwakeningIntro.bootCueDue(Long.MAX_VALUE, Long.MIN_VALUE));
        assertFalse(OrsaAwakeningIntro.bootCueDue(10_000 + 1199, 10_000));
        assertTrue(OrsaAwakeningIntro.bootCueDue(10_000 + 1200, 10_000));
    }

    /** Patchouli formatting removed: line breaks become newlines, every other code goes. */
    static String strip(String text) {
        return text.replace("$(br2)", "\n").replace("$(br)", "\n").replaceAll("\\$\\([^)]*\\)", "").replaceAll("^\n+", "");
    }

    static JsonObject json(String path) {
        var stream = PayoffStateTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
