package com.frozendawn.data;

import net.minecraft.nbt.CompoundTag;
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
        original.hearth(hearthId).advance(42_000L);
        original.hearth(hearthId).markSeeded();
        BloomSavedData.ChunkGrowth chunk = original.chunk(hearthId, 17L);
        chunk.markSeedAttempted();
        chunk.setCursor(73);
        chunk.recordEdits(5);

        CompoundTag encoded = original.save(new CompoundTag(), null);
        BloomSavedData loaded = BloomSavedData.load(encoded, null);

        assertEquals(42_000L, loaded.hearth(hearthId).activeTicks());
        assertTrue(loaded.hearth(hearthId).seeded());
        BloomSavedData.ChunkGrowth restored = loaded.chunk(hearthId, 17L);
        assertTrue(restored.seedAttempted());
        assertEquals(73, restored.cursor());
        assertFalse(restored.complete());
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
}
