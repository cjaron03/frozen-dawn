package com.frozendawn.data;

import com.frozendawn.lore.ThaevenLoopBeat;
import com.frozendawn.lore.ThaevenRecordId;
import com.frozendawn.lore.ThaevenSemanticKey;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThaevenLoreSavedDataTest {
    @Test
    void archivesRemainIsolatedByPlayerUuid() {
        ThaevenLoreSavedData data = new ThaevenLoreSavedData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(data.grantRecord(first, ThaevenRecordId.VEL_AN));
        assertTrue(data.hasRecord(first, ThaevenRecordId.VEL_AN));
        assertFalse(data.hasRecord(second, ThaevenRecordId.VEL_AN));
        assertFalse(data.grantRecord(first, ThaevenRecordId.VEL_AN));
    }

    @Test
    void bookTwoRevisionIsWorldGlobalButViewingIsPerPlayer() {
        ThaevenLoreSavedData data = new ThaevenLoreSavedData();
        UUID player = UUID.randomUUID();
        data.grantRecord(player, ThaevenRecordId.THE_PASSAGE);

        assertEquals(0, data.currentRevision(ThaevenRecordId.THE_PASSAGE));
        assertTrue(data.markViewed(player, ThaevenRecordId.THE_PASSAGE, 0));
        assertTrue(data.unlockSemantic(
                ThaevenSemanticKey.ARCHITECT_LID_REVEAL));
        assertEquals(1, data.currentRevision(ThaevenRecordId.THE_PASSAGE));
        assertEquals(0, data.snapshot(player).seenRevisions()[
                ThaevenRecordId.THE_PASSAGE.ordinal()]);
        assertTrue(data.markViewed(player, ThaevenRecordId.THE_PASSAGE, 1));
        assertFalse(data.markViewed(player, ThaevenRecordId.THE_PASSAGE, 1));
    }

    @Test
    void payoffSourceReInksRecordOneOnly() {
        ThaevenLoreSavedData data = new ThaevenLoreSavedData();
        UUID player = UUID.randomUUID();
        data.grantRecord(player, ThaevenRecordId.VEL_AN);

        assertEquals(0, data.currentRevision(ThaevenRecordId.VEL_AN));
        assertTrue(data.unlockSemantic(ThaevenSemanticKey.PAYOFF_SOURCE));
        assertEquals(1, data.currentRevision(ThaevenRecordId.VEL_AN));
        assertEquals(0, data.currentRevision(ThaevenRecordId.THE_PASSAGE));
        assertEquals(1, data.snapshot(player).currentRevisions()[
                ThaevenRecordId.VEL_AN.ordinal()]);
        assertEquals(0, data.snapshot(player).currentRevisions()[
                ThaevenRecordId.THE_PASSAGE.ordinal()]);

        ThaevenLoreSavedData loaded = ThaevenLoreSavedData.load(
                data.save(new CompoundTag(), null), null);
        assertEquals(1, loaded.currentRevision(ThaevenRecordId.VEL_AN));
        assertEquals(0, loaded.currentRevision(ThaevenRecordId.THE_PASSAGE));
    }

    @Test
    void loopStateSurvivesSerializationAndStartsEmpty() {
        ThaevenLoreSavedData fresh = ThaevenLoreSavedData.load(new CompoundTag(), null);
        assertEquals(0, fresh.loopShown(), "An older save has shown nothing");
        assertTrue(fresh.loopBrokenOwed().isEmpty());
        assertTrue(fresh.loopReturningOwed().isEmpty());

        ThaevenLoreSavedData data = new ThaevenLoreSavedData();
        UUID broken = UUID.randomUUID();
        UUID returning = UUID.randomUUID();
        assertTrue(data.markLoopShown(ThaevenLoopBeat.CROSSED));
        assertFalse(data.markLoopShown(ThaevenLoopBeat.CROSSED), "Once");
        data.setLoopBrokenOwed(broken);
        data.setLoopReturningOwed(returning);

        ThaevenLoreSavedData loaded = ThaevenLoreSavedData.load(
                data.save(new CompoundTag(), null), null);
        assertEquals(ThaevenLoopBeat.CROSSED.bit(), loaded.loopShown());
        assertEquals(broken, loaded.loopBrokenOwed().orElseThrow());
        assertEquals(returning, loaded.loopReturningOwed().orElseThrow());

        loaded.resetLoop();
        assertEquals(0, loaded.loopShown());
        assertTrue(loaded.loopBrokenOwed().isEmpty());
        assertTrue(loaded.loopReturningOwed().isEmpty());
    }

    @Test
    void canonicalHeartScarCanReplaceACompatibilityFallback() {
        ThaevenLoreSavedData data = new ThaevenLoreSavedData();
        BlockPos fallback = new BlockPos(10, 80, 10);
        BlockPos finalDeath = new BlockPos(24, 76, -18);

        assertTrue(data.setHeartScarAnchor(Level.OVERWORLD, fallback));
        assertTrue(data.setHeartScarAnchor(Level.OVERWORLD, finalDeath));
        assertEquals(finalDeath, data.heartScarAnchor().orElseThrow().pos());
        assertFalse(data.setHeartScarAnchor(Level.OVERWORLD, finalDeath));
    }

    @Test
    void archiveAndRevisionsSurviveSerialization() {
        ThaevenLoreSavedData data = new ThaevenLoreSavedData();
        UUID player = UUID.randomUUID();
        data.discoverRecipe(player);
        data.grantRecord(player, ThaevenRecordId.THE_PASSAGE);
        data.unlockSemantic(ThaevenSemanticKey.ARCHITECT_LID_REVEAL);
        data.markViewed(player, ThaevenRecordId.THE_PASSAGE, 1);
        data.setHeartScarAnchor(Level.OVERWORLD, new BlockPos(3, 72, 9));

        CompoundTag tag = data.save(new CompoundTag(), null);
        ThaevenLoreSavedData loaded = ThaevenLoreSavedData.load(tag, null);

        assertTrue(loaded.snapshot(player).recipeDiscovered());
        assertTrue(loaded.hasRecord(player, ThaevenRecordId.THE_PASSAGE));
        assertEquals(1, loaded.snapshot(player).seenRevisions()[
                ThaevenRecordId.THE_PASSAGE.ordinal()]);
        assertEquals(1, loaded.currentRevision(ThaevenRecordId.THE_PASSAGE));
        assertEquals(new BlockPos(3, 72, 9),
                loaded.heartScarAnchor().orElseThrow().pos());
    }
}
