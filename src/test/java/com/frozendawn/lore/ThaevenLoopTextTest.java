package com.frozendawn.lore;

import com.frozendawn.client.ThaevenRecordDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CH-37's loop text: Record I's last page and its re-ink. */
class ThaevenLoopTextTest {
    private static final String RAW = "Vel-thae thaeven.\nVel-thae thaeven.\nThae ethven.";
    private static final String LINES = "The warm-one crossed here.\nThen crossed here again.\n"
            + "The body broke.\nThe returning was not.\n";

    @Test
    void recordOneEndsOnTheLoopPage() {
        JsonObject root = json("vel_an");
        JsonArray raw = root.getAsJsonArray("rawPages");
        JsonArray translated = root.getAsJsonArray("translatedPages");
        assertEquals(6, raw.size());
        assertEquals(6, translated.size());
        assertEquals(RAW, raw.get(5).getAsString());

        JsonArray segments = translated.get(5).getAsJsonObject().getAsJsonArray("segments");
        assertEquals(1, segments.size());
        JsonObject segment = segments.get(0).getAsJsonObject();
        assertEquals("vel_an_06", segment.get("id").getAsString());
        assertEquals(58, segment.get("revealTiming").getAsInt());
        assertTrue(segment.get("uncertainty").getAsBoolean());
        assertEquals(LINES + "Source: unresolved.", segment.get("baseText").getAsString());
        JsonObject overrides = segment.getAsJsonObject("semanticOverrides");
        assertNotNull(overrides.get("1"));
        assertEquals(LINES + "Source: the warm-one.", overrides.get("1").getAsString());
    }

    @Test
    void segmentResolvesTheReInk() {
        ThaevenRecordDefinition record = definition("vel_an");
        assertTrue(record.translatedPage(5, 0).endsWith("\nSource: unresolved."));
        assertTrue(record.translatedPage(5, 1).endsWith("\nSource: the warm-one."));
    }

    @Test
    void onlyTheChangedPageReInks() {
        ThaevenRecordDefinition recordOne = definition("vel_an");
        assertEquals(List.of(5), recordOne.changedPages(0, 1));
        assertEquals(List.of(0, 1, 2, 3, 4, 5), recordOne.changedPages(-1, 1));
        assertEquals(List.of(7), definition("the_passage").changedPages(0, 1));
    }

    @Test
    void takeoverBeatsRunInOrderOnce() {
        int mask = 0;
        assertEquals(Optional.of(ThaevenLoopBeat.CROSSED), ThaevenLoopPolicy.takeoverBeat(mask));
        mask |= ThaevenLoopBeat.CROSSED.bit();
        assertEquals(Optional.of(ThaevenLoopBeat.CROSSED_AGAIN), ThaevenLoopPolicy.takeoverBeat(mask));
        mask |= ThaevenLoopBeat.CROSSED_AGAIN.bit();
        assertEquals(Optional.empty(), ThaevenLoopPolicy.takeoverBeat(mask));
        assertEquals(Optional.empty(), ThaevenLoopPolicy.takeoverBeat(-1), "Every beat shown");
    }

    @Test
    void arenaNeedsALiveHeartInItsDimension() {
        assertTrue(ThaevenLoopPolicy.inArena(112.0D * 112.0D, true, true), "112 blocks counts");
        assertFalse(ThaevenLoopPolicy.inArena(113.0D * 113.0D, true, true), "113 does not");
        assertTrue(ThaevenLoopPolicy.inArena(0.0D, true, true));
        assertFalse(ThaevenLoopPolicy.inArena(0.0D, false, true), "A fallen Heart has no arena");
        assertFalse(ThaevenLoopPolicy.inArena(0.0D, true, false), "Another dimension is not the arena");
    }

    @Test
    void heartLinesAreRecordThree() {
        JsonObject lang = lang();
        assertEquals("The warm-one crossed here.", lang.get("overlay.frozendawn.thaeven.loop.crossed").getAsString());
        assertEquals("Then crossed here again.", lang.get("overlay.frozendawn.thaeven.loop.crossed_again").getAsString());
        assertEquals("The body broke.", lang.get("overlay.frozendawn.thaeven.loop.body_broke").getAsString());
        assertEquals("The pieces remained.", lang.get("overlay.frozendawn.thaeven.loop.pieces_remained").getAsString());
        assertEquals("The returning was not.", lang.get("overlay.frozendawn.thaeven.loop.returning").getAsString());

        JsonObject three = json("pattern_residue");
        JsonArray raw = three.getAsJsonArray("rawPages");
        JsonArray translated = three.getAsJsonArray("translatedPages");
        String recordOnePage = json("vel_an").getAsJsonArray("translatedPages").get(5).getAsJsonObject()
                .getAsJsonArray("segments").get(0).getAsJsonObject().get("baseText").getAsString();
        int lines = 0;
        for (ThaevenLoopBeat beat : ThaevenLoopBeat.values()) {
            for (ThaevenLoopBeat.Line line : beat.lines()) {
                String english = lang.get(line.key()).getAsString();
                String page = translated.get(line.page()).getAsJsonObject().getAsJsonArray("segments").get(0)
                        .getAsJsonObject().get("baseText").getAsString();
                assertEquals(english, page.split("\n")[line.line()], "Record III's translation, " + line.key());
                assertFalse(raw.get(line.page()).getAsString().split("\n")[line.line()].isBlank(),
                        "Its raw line, " + line.key());
                if (!line.key().endsWith("pieces_remained")) {
                    assertTrue(recordOnePage.contains(english), "Record I page 6 shares " + line.key());
                }
                lines++;
            }
        }
        assertEquals(5, lines, "Four beats, the broken one carrying two lines");
    }

    private static JsonObject lang() {
        try (Reader reader = new InputStreamReader(ThaevenLoopTextTest.class.getClassLoader()
                .getResourceAsStream("assets/frozendawn/lang/en_us.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static JsonObject json(String name) {
        try (Reader reader = reader(name)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static ThaevenRecordDefinition definition(String name) {
        try (Reader reader = reader(name)) {
            return ThaevenRecordDefinition.parse(reader);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static Reader reader(String name) {
        return new InputStreamReader(ThaevenLoopTextTest.class.getClassLoader()
                .getResourceAsStream("assets/frozendawn/thaeven_records/" + name + ".json"),
                StandardCharsets.UTF_8);
    }
}
