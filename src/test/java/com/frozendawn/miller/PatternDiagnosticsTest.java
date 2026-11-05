package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatternDiagnosticsTest {
    private static final UUID WHO = new UUID(0, 7);

    private static MillerPattern.Snapshot snapshot(PatternState state, long now) {
        return MillerPattern.snapshot(state, now);
    }

    private static boolean anyHolds(List<String> lines, String... parts) {
        return lines.stream().anyMatch(line -> List.of(parts).stream().allMatch(line::contains));
    }

    @Test
    void explainNamesTheCauseOfHerFace() {
        var state = new PatternState();
        state.appraise(WHO, Deed.ARCHIVIST_KILLED, 100);
        var lines = PatternDiagnostics.explain(snapshot(state, 100));
        assertTrue(anyHolds(lines, "SAD", "DISTRESS", "0.525"), lines.toString());
        assertTrue(anyHolds(lines, "CAUSE", "ARCHIVIST_KILLED", WHO.toString()), lines.toString());
    }

    @Test
    void explainNamesHerFocus() {
        var state = new PatternState();
        state.appraise(WHO, Deed.ARCHIVIST_KILLED, 100);
        var lines = PatternDiagnostics.explain(snapshot(state, 100));
        assertTrue(anyHolds(lines, "FOCUS", "FEELING", "distress", "0.525", "URGE", "SPEAK"), lines.toString());
    }

    @Test
    void explainSaysWhenThereIsNoCause() {
        var lines = PatternDiagnostics.explain(snapshot(new PatternState(), 100));
        assertTrue(anyHolds(lines, "NEUTRAL", "0.30"), lines.toString());
        assertFalse(anyHolds(lines, "CAUSE"), lines.toString());
    }

    @Test
    void explainSaysWhenTheCauseAgedOut() {
        var state = new PatternState();
        state.appraise(WHO, Deed.ARCHIVIST_KILLED, 0);
        var full = snapshot(state, 0);
        var lines = PatternDiagnostics.explain(new MillerPattern.Snapshot(full.now(), full.mood(), full.strongest(), full.feelings(), full.drives(), List.of(),
                full.coherence(), full.stage(), full.lean(), full.walls(), full.impressions(), full.keepsakes(), full.focus(), full.urge()));
        assertFalse(anyHolds(lines, "CAUSE", "ARCHIVIST_KILLED"), lines.toString());
        assertTrue(anyHolds(lines, "No retained event"), lines.toString());
    }

    @Test
    void theStrongestCauseIsTheNewestFirst() {
        var state = new PatternState();
        state.appraise(WHO, Deed.WARM_SHELTER, 0);
        state.appraise(WHO, Deed.CLEAR_NIGHT, 50);
        var causes = PatternDiagnostics.explain(snapshot(state, 50)).stream().filter(line -> line.contains("CAUSE")).toList();
        assertTrue(causes.getFirst().contains("CLEAR_NIGHT"), causes.toString());
    }

    @Test
    void nothingShownCarriesHerFileName() {
        var state = new PatternState();
        state.poll(1, 10, false, 20);
        for (Deed deed : Deed.values()) state.appraise(WHO, deed, 100);
        state.poll(1, 10, false, 24100);
        var lines = new java.util.ArrayList<>(PatternDiagnostics.format(snapshot(state, 100)));
        lines.addAll(PatternDiagnostics.explain(snapshot(state, 100)));
        lines.addAll(PatternDiagnostics.mind(snapshot(state, 100)));
        lines.addAll(PatternDiagnostics.mind(snapshot(new PatternState(), 100)));
        lines.addAll(PatternDiagnostics.explain(snapshot(new PatternState(), 100)));
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("miller") || lower.contains("pattern"), line);
        }
    }

    @Test
    void mindShowsWhatSheHolds() {
        var state = new PatternState();
        state.poll(1, PatternState.NOT_SET, false, 0);
        state.appraise(WHO, Deed.ARCHIVIST_KILLED, 0);
        state.poll(1, PatternState.NOT_SET, false, 24000);
        var lines = PatternDiagnostics.mind(snapshot(state, 24000));
        assertTrue(anyHolds(lines, "coherence=", "stage=PRODUCT"), lines.toString());
        assertTrue(anyHolds(lines, WHO.toString(), "keepsakes=1", "KIND=-0.333"), lines.toString());
    }

    @Test
    void theSaveFileCarriesNoNameEither() {
        assertEquals("frozendawn_shepherd_state", PatternSavedData.NAME);
    }

    /** The game swallows a load that throws and writes a blank state over the file, so a newer save has to survive this way. */
    @Test
    void aFutureSaveIsWrittenBackUntouched() {
        var tag = new PatternState().save();
        tag.putInt("dataVersion", PatternState.VERSION + 1);
        tag.putString("fromTheFuture", "keepsakes");
        var data = PatternSavedData.load(tag.copy(), null);
        data.state().appraise(WHO, Deed.WARM_SHELTER, 100);
        assertEquals(tag, data.save(new net.minecraft.nbt.CompoundTag(), null));
    }

    @Test
    void savedDataRoundTrips() {
        var data = new PatternSavedData();
        data.state().appraise(WHO, Deed.ARCHIVIST_KILLED, 100);
        var tag = data.save(new net.minecraft.nbt.CompoundTag(), null);
        assertEquals(tag, PatternSavedData.load(tag, null).save(new net.minecraft.nbt.CompoundTag(), null));
    }
}
