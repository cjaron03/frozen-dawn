package com.frozendawn.vault;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The vault's paper: filed in the manual, the Keats as printed, and nothing that names what the wand does. */
class VaultDocumentsTest {
    private static List<String> all() {
        var docs = new ArrayList<>(List.of(VaultDocuments.DRAIN_SCHEDULE, VaultDocuments.MANIFEST, VaultDocuments.NAME_REVIEW, VaultDocuments.SEAL_ORDER));
        docs.addAll(VaultDocuments.TALE_LOGS);
        return docs;
    }
    private static JsonObject entry(String doc) throws IOException {
        try (var in = VaultDocumentsTest.class.getResourceAsStream("/assets/frozendawn/patchouli_books/frozen_dawn_guide/en_us/entries/orsa_intel/" + doc + ".json")) {
            assertNotNull(in, doc);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static String text(JsonObject entry, int page) { return entry.getAsJsonArray("pages").get(page).getAsJsonObject().get("text").getAsString(); }

    @Test
    void everyDocumentHasAnEntryAndAnAdvancement() throws IOException {
        for (var doc : all()) {
            assertEquals("frozendawn:found_" + doc, entry(doc).get("advancement").getAsString());
            assertNotNull(getClass().getResource("/data/frozendawn/advancement/found_" + doc + ".json"), doc);
        }
    }

    /** Project Gutenberg #23684, Keats: Poems Published in 1820, the opening of Hyperion, checked 2026-11-03. */
    @Test
    void theManifestOpensWithTheKeatsAsPrinted() throws IOException {
        var opening = text(entry(VaultDocuments.MANIFEST), 0).replace("$(br)", "\n").replace("$(italic)", "").replace("$()", "");
        assertTrue(opening.startsWith("""
                Deep in the shady sadness of a vale
                Far sunken from the healthy breath of morn,
                Far from the fiery noon, and eve's one star,
                Sat gray-hair'd Saturn, quiet as a stone,"""), opening);
        assertTrue(opening.contains("John Keats"), "Credited to Keats");
    }

    @Test
    void theManifestLeavesItsBlanksBlank() throws IOException {
        var manifest = text(entry(VaultDocuments.MANIFEST), 1);
        assertTrue(manifest.contains("Issuing division:$(br)Budget line:$(br2)"), "Blank, not redacted");
        assertTrue(manifest.contains("Containment of RELIQUUS COGNITIVE ECHO."));
        assertTrue(manifest.endsWith("Approver signature:"), "Signature absent");
    }

    @Test
    void theManifestListsTheUnit() throws IOException {
        assertTrue(text(entry(VaultDocuments.MANIFEST), 1).contains("Item: TR-0 Terminus, one$(br)Item: CS-1 unit 4, one$(br)Issuing division:"), "Her row, above the blanks");
    }

    @Test
    void theLastLogIsEnochs() throws IOException {
        var log = text(entry("terminus_log_last"), 0);
        assertTrue(log.contains("Okoye, E."), log);
        assertFalse(log.contains("Okoye, A."), log);
    }

    @Test
    void haleCountsTheUnit() throws IOException {
        assertTrue(text(entry("terminus_log_security"), 1).contains("CS-1 unit"), "Nothing down here but the cradle, the unit and them");
    }

    @Test
    void theClueReadsAsTheOwnerSawIt() throws IOException {
        var sheet = text(entry(VaultDocuments.DRAIN_SCHEDULE), 0);
        assertTrue(sheet.contains("Floor drains drawn: 5$(br)Floor drains in hall: 4"), sheet);
    }

    @Test
    void theSealOrderSaysWhatWasDrawn() throws IOException {
        var order = text(entry(VaultDocuments.SEAL_ORDER), 0);
        for (var line : List.of("All CVF-1 staff to Exodus by 2200", "ISS-4 armed on last badge-out", "CSU-3 remains on duty", "Nobody goes back for personal effects"))
            assertTrue(order.contains(line), line);
    }

    @Test
    void noVaultPaperNamesContinuity() throws IOException {
        for (var doc : all()) assertFalse(entry(doc).toString().toLowerCase().contains("continuity"), doc);
    }

    @Test
    void theLastLogIsTheTechniciansAndComesLast() {
        assertEquals("terminus_log_last", VaultDocuments.TALE_LOGS.getLast());
    }
}
