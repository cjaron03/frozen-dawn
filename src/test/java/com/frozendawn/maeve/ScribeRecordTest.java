package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScribeRecordTest {
    private static final UUID PLAYER = new UUID(0, 9);
    private static final String DIM = "minecraft:overworld";

    private static MaeveDirector.BeliefSnapshot belief(String pattern, double confidence) {
        return belief(pattern, confidence, 3, 0);
    }

    private static MaeveDirector.BeliefSnapshot belief(String pattern, double confidence, int evidence, int contradictions) {
        return new MaeveDirector.BeliefSnapshot(pattern, confidence, evidence, contradictions, 10, 10, 0, false, confidence, 10, 10, List.of());
    }

    private static MaeveDirector.WorldPointSnapshot point(String label, BlockPos at, BlockPos inside, String state, double confidence, String dimension) {
        return new MaeveDirector.WorldPointSnapshot(label, dimension, at, inside, state, confidence, 0, 1, 0, 10, List.of());
    }

    /** Builds encounter-separated supporting evidence through the real store, as observers would. */
    private static BeliefStore trained(String pattern, int supports, int contradictions) {
        var store = new BeliefStore(); long tick = 0;
        for (int i = 0; i < supports; i++, tick += BeliefPolicy.ENCOUNTER_GAP + 1)
            store.record(PLAYER, new UUID(1, i), DIM, BlockPos.ZERO, tick, pattern, true, "TEST_SUPPORT");
        for (int i = 0; i < contradictions; i++, tick += BeliefPolicy.ENCOUNTER_GAP + 1)
            store.record(PLAYER, new UUID(2, i), DIM, BlockPos.ZERO, tick, pattern, false, "TEST_CONTRADICTION");
        return store;
    }

    @Test
    void writesAtMostFiveHighestConfidenceNotesAboveTheFloor() {
        var notes = ScribeRecordWriter.notes(List.of(
                belief(BeliefStore.RANGED, .40), belief(BeliefStore.SWORD, .95), belief("RETREAT_BEARING_E", .80),
                belief(BeliefStore.PURSUIT, .76), belief(BeliefStore.RECOVERY, .55), belief("RETREAT_BEARING_W", .30),
                belief("RETREAT_BEARING_N", .19)));
        assertEquals(List.of(BeliefStore.SWORD, "RETREAT_BEARING_E", BeliefStore.PURSUIT, BeliefStore.RECOVERY, BeliefStore.RANGED),
                notes.stream().map(MaeveDirector.ScribeNote::pattern).toList());
        assertEquals(ScribePolicy.MAX_NOTES, notes.size());
        assertTrue(ScribeRecordWriter.notes(List.of(belief("RETREAT_BEARING_N", .19))).isEmpty(), "Below one witnessed support is not written");
    }

    @Test
    void confidenceIsPhrasingNeverANumber() {
        var notes = ScribeRecordWriter.notes(List.of(belief(BeliefStore.SWORD, .95), belief(BeliefStore.RANGED, .80), belief(BeliefStore.RECOVERY, .50)));
        assertEquals("Ka vel-an. Vel-an.", notes.get(0).thaeven());
        assertEquals("ALWAYS", notes.get(0).certainty());
        assertEquals("Eth orren.", notes.get(1).thaeven());
        assertEquals("FLAT", notes.get(1).certainty());
        assertEquals("Mor vel-thaeven…", notes.get(2).thaeven());
        assertEquals("HEDGED", notes.get(2).certainty());
        for (var note : notes) assertFalse(note.thaeven().matches(".*\\d.*"), note.thaeven());
        assertEquals("record.frozendawn.scribe.sword", notes.get(0).translation());
    }

    @Test
    void spatialNotesNameQuartersAndKeepVerbLast() {
        var bearing = ScribeRecordWriter.note(belief("RETREAT_BEARING_E", .80));
        assertEquals("Vel-sorr aren thaeven.", bearing.thaeven());
        assertEquals(List.of("record.frozendawn.scribe.direction.east"), bearing.arguments());
        String area = "0123456789ABCDEF0123456789ABCDEF";
        var exit = ScribeRecordWriter.note(belief("EXIT_AFTER_W_E_" + area, .92));
        assertEquals("Eth-sorr aren vaen. Vel-sorr aren thaeven. Thaeven.", exit.thaeven());
        assertEquals(List.of("record.frozendawn.scribe.direction.west", "record.frozendawn.scribe.direction.east"), exit.arguments());
        assertNull(ScribeRecordWriter.note(belief("UNAUTHORED_PATTERN", .99)), "Unknown patterns are omitted, never guessed");
    }

    @Test
    void wrongBeliefsAreWrittenExactlyAsHeld() {
        // Two witnessed supports, never contradicted: 0.40, written hedged rather than dropped.
        var store = trained(BeliefStore.RECOVERY, 2, 0);
        var note = ScribeRecordWriter.notes(store.snapshot(PLAYER, 10_000)).getFirst();
        assertEquals("HEDGED", note.certainty());
        assertEquals("Mor vel-thaeven…", note.thaeven());
    }

    @Test
    void splitBeliefsAreInconclusiveAndUnseenOnesAreNeverWritten() {
        // Four witnessed supports and one contradiction: 0.80 - 0.35 = 0.45, seen both ways.
        var store = trained(BeliefStore.RECOVERY, 4, 1);
        var held = store.snapshot(PLAYER, 10_000).getFirst();
        assertEquals(.45, held.confidence(), 1e-9);
        var note = ScribeRecordWriter.notes(store.snapshot(PLAYER, 10_000)).getFirst();
        assertEquals("INCONCLUSIVE", note.certainty());
        assertEquals("Mor vel-thaeven. Liss.", note.thaeven());
        // An even split falls below the floor but is still something she saw.
        var even = ScribeRecordWriter.notes(trained("RETREAT_BEARING_E", 1, 1).snapshot(PLAYER, 10_000));
        assertEquals(List.of("INCONCLUSIVE"), even.stream().map(MaeveDirector.ScribeNote::certainty).toList());
        assertEquals("Vel-sorr aren thaeven. Liss.", even.getFirst().thaeven());
        // Contradictions outnumbering supports are settled against, not split; nothing unseen is ever written.
        assertTrue(ScribeRecordWriter.notes(trained("RETREAT_BEARING_W", 1, 3).snapshot(PLAYER, 10_000)).isEmpty());
        assertTrue(ScribeRecordWriter.notes(List.of(belief("RETREAT_BEARING_N", 0, 0, 0))).isEmpty());
        // A confident belief that was once contradicted is not split.
        assertEquals("FLAT", ScribeRecordWriter.note(belief(BeliefStore.SWORD, .80, 6, 1)).certainty());
    }

    @Test
    void badLuckProtectionRisesEachMissAndIsCertainByTheEighth() {
        assertEquals(5 * 24000L, ScribePolicy.COOLDOWN, "Owner decision 2026-10-08: five in-game days");
        assertFalse(ScribePolicy.known(List.of()), "A miss needs Maeve to know something");
        assertFalse(ScribePolicy.known(List.of(belief(BeliefStore.SWORD, .19))));
        assertTrue(ScribePolicy.known(List.of(belief(BeliefStore.SWORD, .20))));
        assertTrue(ScribePolicy.pity(1, .124) && !ScribePolicy.pity(1, .125), "The first miss is one in eight");
        assertTrue(ScribePolicy.pity(4, .499) && !ScribePolicy.pity(4, .5));
        assertTrue(ScribePolicy.pity(ScribePolicy.PITY_MISSES, .9999), "The eighth is guaranteed");
        assertFalse(ScribePolicy.pity(0, 0));
        var memory = new ScribeMemory(); memory.misses = 6;
        assertEquals(6, ScribeMemory.load(memory.save()).misses, "Misses survive a reload");
        var tag = memory.save(); tag.putInt("misses", 99);
        assertEquals(ScribePolicy.PITY_MISSES, ScribeMemory.load(tag).misses);
        assertEquals(0, ScribeMemory.load(new net.minecraft.nbt.CompoundTag()).misses, "Older saves start with no misses");
    }

    @Test
    void gateRequiresSeveralConfidentBeliefsActiveLifecycleAndCooldown() {
        var two = List.of(belief(BeliefStore.SWORD, .9), belief(BeliefStore.RANGED, .75), belief(BeliefStore.RECOVERY, .74));
        var three = List.of(belief(BeliefStore.SWORD, .9), belief(BeliefStore.RANGED, .75), belief(BeliefStore.RECOVERY, .75));
        assertEquals("TOO_FEW_CONFIDENT_BELIEFS", ScribePolicy.gate("ACTIVE", two, false, -1, 100));
        assertEquals("ELIGIBLE", ScribePolicy.gate("ACTIVE", three, false, -1, 100));
        assertEquals("LIFECYCLE_ERASED", ScribePolicy.gate("ERASED", three, false, -1, 100));
        assertEquals("LIFECYCLE_DORMANT", ScribePolicy.gate("DORMANT", three, false, -1, 100));
        assertEquals("SCRIBE_ALREADY_ACTIVE", ScribePolicy.gate("ACTIVE", three, true, -1, 100));
        assertEquals("COOLDOWN", ScribePolicy.gate("ACTIVE", three, false, 100, 100 + ScribePolicy.COOLDOWN - 1));
        assertEquals("ELIGIBLE", ScribePolicy.gate("ACTIVE", three, false, 100, 100 + ScribePolicy.COOLDOWN));
    }

    @Test
    void claimExpiresIntoCooldownAndSurvivesReload() {
        var memory = new ScribeMemory();
        memory.active = new ScribeMemory.Claim(new UUID(3, 3), PLAYER, DIM, new BlockPos(4, 70, 9), "OPENING", 1000);
        var loaded = ScribeMemory.load(memory.save());
        assertEquals(memory.active, loaded.active);
        assertFalse(loaded.expire(1000 + ScribePolicy.LIFETIME - 1));
        assertTrue(loaded.expire(1000 + ScribePolicy.LIFETIME + 50));
        assertNull(loaded.active);
        assertEquals(1000 + ScribePolicy.LIFETIME, loaded.lastEnded, "Cooldown counts from the lifetime end, not discovery");
        var routeOnly = new ScribeMemory();
        routeOnly.active = new ScribeMemory.Claim(new UUID(3, 4), PLAYER, DIM, null, "ROUTE", 5);
        assertNull(ScribeMemory.load(routeOnly.save()).active.watch());
    }

    @Test
    void mapMarksComeOnlyFromHeldWorldPoints() {
        var opening = point("ACCESS_POINT", new BlockPos(10, 70, 0), new BlockPos(9, 70, 0), "OPEN", .6, DIM);
        var sealed = point("ACCESS_POINT", new BlockPos(0, 70, 10), new BlockPos(0, 70, 9), "BLOCKED", .9, DIM);
        var loss = point("DANGER_ZONE", new BlockPos(30, 70, 30), null, "OBSERVED", .4, DIM);
        var faint = point("DANGER_ZONE", new BlockPos(31, 70, 30), null, "OBSERVED", .1, DIM);
        var heat = point("HEAT_SOURCE", new BlockPos(2, 70, 2), null, "OBSERVED", .2, DIM);
        var elsewhere = point("HEAT_SOURCE", new BlockPos(2, 70, 2), null, "OBSERVED", .9, "minecraft:the_nether");
        var marks = ScribeRecordWriter.marks(List.of(opening, sealed, loss, faint, heat, elsewhere), DIM);
        assertEquals(List.of("ACCESS_POINT", "DANGER_ZONE", "HEAT_SOURCE"), marks.stream().map(MaeveDirector.ScribeMark::label).toList());
        // Outward east crossing: Minecraft yaw -90 faces +X.
        assertEquals(-90, marks.getFirst().rotation(), 1e-4);
        var many = java.util.stream.IntStream.range(0, 12).mapToObj(i -> point("DANGER_ZONE", new BlockPos(i, 70, 0), null, "OBSERVED", .2 + i * .01, DIM)).toList();
        assertEquals(ScribePolicy.MAX_MARKS_PER_LABEL, ScribeRecordWriter.marks(many, DIM).size(), "Bounded per label");
    }
}
