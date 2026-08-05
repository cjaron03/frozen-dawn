package com.frozendawn.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomSavedDataTest {
    @Test
    void roundTripPreservesPartialGrowthAndSeedAuthority() {
        BloomSavedData original = new BloomSavedData();
        UUID hearthId = UUID.fromString("c5c09f52-6d6a-4e85-8e35-45430b6b3843");
        BloomSavedData.HearthGrowth hearth = original.hearth(hearthId);
        hearth.advance(42_000L);
        hearth.markSeeded();
        hearth.setOriginRootCursor(91);
        hearth.rememberOriginRootBase(new BlockPos(8, 74, -12));
        BloomSavedData.ChunkGrowth chunk = original.chunk(hearthId, 17L);
        chunk.markSeedAttempted();
        chunk.setCursor(73);
        chunk.recordEdits(5);

        CompoundTag encoded = original.save(new CompoundTag(), null);
        BloomSavedData loaded = BloomSavedData.load(encoded, null);

        assertEquals(42_000L, loaded.hearth(hearthId).activeTicks());
        assertTrue(loaded.hearth(hearthId).seeded());
        assertEquals(91, loaded.hearth(hearthId).originRootCursor());
        assertEquals(new BlockPos(8, 74, -12),
                loaded.hearth(hearthId).originRootBase().orElseThrow());
        assertFalse(loaded.hearth(hearthId).originRootFormed());
        BloomSavedData.ChunkGrowth restored = loaded.chunk(hearthId, 17L);
        assertTrue(restored.seedAttempted());
        assertEquals(73, restored.cursor());
        assertFalse(restored.complete());
    }

    @Test
    void roundTripPreservesFirstEruptionAuthority() {
        BloomSavedData original = new BloomSavedData();
        UUID hearthId = UUID.randomUUID();
        BlockPos base = new BlockPos(42, 71, -18);

        assertTrue(original.startFirstEruption(hearthId, base, 9_000L));
        assertTrue(original.markFirstEruptionImpactPlayed());

        BloomSavedData loaded = BloomSavedData.load(
                original.save(new CompoundTag(), null), null);
        assertEquals(hearthId, loaded.firstEruptionHearthId().orElseThrow());
        assertEquals(base, loaded.firstEruptionBase().orElseThrow());
        assertEquals(9_000L, loaded.firstEruptionStartGameTime());
        assertTrue(loaded.firstEruptionImpactPlayed());
        assertFalse(loaded.firstEruptionComplete());
    }

    @Test
    void oldGrownBloomMigratesPastFirstEruption() {
        BloomSavedData original = new BloomSavedData();
        original.hearth(UUID.randomUUID()).markSeeded();
        CompoundTag legacy = original.save(new CompoundTag(), null);
        legacy.putInt("dataVersion", 4);
        legacy.remove("firstEruptionComplete");
        legacy.remove("firstEruptionImpactPlayed");

        BloomSavedData loaded = BloomSavedData.load(legacy, null);
        assertTrue(loaded.firstEruptionComplete());
        assertTrue(loaded.firstEruptionImpactPlayed());
    }

    @Test
    void productivePassRepeatsThenConvergesUntilDensityChanges() {
        BloomSavedData data = new BloomSavedData();
        BloomSavedData.ChunkGrowth chunk = data.chunk(UUID.randomUUID(), 23L);
        chunk.recordEdits(2);
        chunk.setCursor(256);
        chunk.finishPass(0, 1);
        assertFalse(chunk.complete());
        assertEquals(0, chunk.cursor());

        chunk.setCursor(256);
        chunk.finishPass(0, 1);
        assertTrue(chunk.complete());
        chunk.prepareFor(0, 1);
        assertTrue(chunk.complete());
        chunk.prepareFor(1, 1);
        assertFalse(chunk.complete());
    }

    @Test
    void preBloomSaveStartsEmptyAndChunkKeysDoNotDuplicate() {
        BloomSavedData loaded = BloomSavedData.load(new CompoundTag(), null);
        UUID hearthId = UUID.randomUUID();
        BloomSavedData.ChunkGrowth first = loaded.chunk(hearthId, 91L);
        BloomSavedData.ChunkGrowth second = loaded.chunk(hearthId, 91L);

        assertSame(first, second);
        assertEquals(1, loaded.recordCount());
        assertEquals(0L, loaded.hearth(hearthId).activeTicks());
        assertFalse(loaded.hearth(hearthId).seeded());
    }

    @Test
    void normalProgressionRestartClearsPausedGrowthButPreservesOtherAuthority() {
        BloomSavedData data = new BloomSavedData();
        UUID hearthId = UUID.randomUUID();
        data.hearth(hearthId).advance(48_000L);
        data.hearth(hearthId).markSeeded();
        data.hearth(hearthId).markOriginRootFormed();
        data.chunk(hearthId, 31L).markSeedAttempted();
        data.setDebugRadius(0);
        data.addSealedContact(new BlockPos(3, 70, 8), 40L);

        UUID frontId = UUID.randomUUID();
        data.sporeFront(frontId, UUID.randomUUID(), BlockPos.ZERO, true, 16.0D);
        data.restartGrowthAuthorityForDebug();

        assertEquals(-1, data.debugRadius());
        assertEquals(0, data.recordCount());
        assertEquals(0L, data.hearth(hearthId).activeTicks());
        assertFalse(data.hearth(hearthId).seeded());
        assertFalse(data.hearth(hearthId).originRootFormed());
        assertEquals(1, data.sealedRecordCount());
        assertTrue(data.sporeFront(frontId).isPresent());
    }

    @Test
    void maeveSequenceOnlyRestartsAuthorityPausedByDebugPurge() {
        BloomSavedData data = new BloomSavedData();
        UUID hearthId = UUID.randomUUID();
        data.hearth(hearthId).markSeeded();
        data.hearth(hearthId).markOriginRootFormed();
        data.chunk(hearthId, 31L).markSeedAttempted();

        assertFalse(data.resumePurgedGrowthForMaeveSequence());
        assertTrue(data.hearth(hearthId).seeded());

        data.setDebugRadius(0);
        assertTrue(data.resumePurgedGrowthForMaeveSequence());
        assertEquals(-1, data.debugRadius());
        assertEquals(0, data.recordCount());
        assertFalse(data.hearth(hearthId).seeded());
        assertFalse(data.firstEruptionStarted());
        assertFalse(data.firstEruptionComplete());
    }

    @Test
    void roundTripPreservesSatelliteAndActiveSporeAuthority() {
        BloomSavedData original = new BloomSavedData();
        UUID nodeId = UUID.fromString("a30a3d2d-4bc0-4c51-a106-a6cbf6c54631");
        UUID lineageId = UUID.fromString("912075a5-9aa9-4a45-9445-cf2874c20ff8");
        UUID sporeId = UUID.fromString("e9978626-ddd4-461c-b955-7a1a1fe3852d");
        UUID secondSporeId = UUID.fromString("a66ae8ee-a764-41c7-9868-c0b48a269d80");
        UUID corpseId = UUID.fromString("c1b82f69-0750-4b7e-a164-69c68313d338");
        BloomSavedData.SporeFront front = original.sporeFront(
                nodeId, lineageId, new BlockPos(44, 71, -93), true, 16.0D);
        front.bindSpore(sporeId, new BlockPos(51, 72, -86));
        front.bindSpore(secondSporeId, new BlockPos(38, 70, -101));
        front.advanceLoaded(61_000L);
        front.setGrowthCursor(417);
        front.setMaintenanceCursor(19);
        front.recordInitialPatchEdit();
        front.recordInitialPatchEdit();
        front.bindCorpse(corpseId);
        front.markRelayEmitted();

        BloomSavedData loaded = BloomSavedData.load(
                original.save(new CompoundTag(), null), null);
        BloomSavedData.SporeFront restored = loaded.sporeFront(nodeId).orElseThrow();
        assertEquals(lineageId, restored.lineageId());
        assertEquals(new BlockPos(44, 71, -93), restored.anchor());
        assertTrue(restored.satellite());
        assertEquals(16.0D, restored.sourceEdgeRadius(), 0.001D);
        assertEquals(sporeId, restored.activeSporeId().orElseThrow());
        assertEquals(new BlockPos(51, 72, -86), restored.activeSporePos().orElseThrow());
        assertEquals(2, restored.activeSporeCount());
        assertTrue(restored.activeSporeIds().contains(secondSporeId));
        assertEquals(new BlockPos(38, 70, -101),
                restored.activeSporePos(secondSporeId).orElseThrow());
        assertEquals(61_000L, restored.loadedTicks());
        assertEquals(417, restored.growthCursor());
        assertEquals(19, restored.maintenanceCursor());
        assertEquals(2, restored.initialPatchEdits());
        assertEquals(corpseId, restored.corpseId().orElseThrow());
        assertTrue(restored.relayEmitted());

        restored.clearSpore(sporeId);
        assertEquals(1, restored.activeSporeCount());
        assertEquals(secondSporeId, restored.activeSporeId().orElseThrow());
        assertEquals(new BlockPos(38, 70, -101), restored.activeSporePos().orElseThrow());
    }

    @Test
    void sporeFrontKeysDoNotDuplicateAndOldSavesStartWithoutRelays() {
        BloomSavedData migrated = BloomSavedData.load(new CompoundTag(), null);
        assertTrue(migrated.sporeFronts().isEmpty());

        UUID id = UUID.randomUUID();
        UUID lineage = UUID.randomUUID();
        BloomSavedData.SporeFront first = migrated.sporeFront(
                id, lineage, BlockPos.ZERO, false, 12.0D);
        BloomSavedData.SporeFront second = migrated.sporeFront(
                id, lineage, new BlockPos(1, 2, 3), false, 20.0D);
        assertSame(first, second);
        assertEquals(1, migrated.sporeFronts().size());
        assertEquals(new BlockPos(1, 2, 3), first.anchor());
        assertEquals(20.0D, first.sourceEdgeRadius(), 0.001D);
    }
}
