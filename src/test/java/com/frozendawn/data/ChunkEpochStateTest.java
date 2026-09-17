package com.frozendawn.data;

import com.frozendawn.world.ChunkCatchUpManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkEpochStateTest {
    @Test
    void roundTripPreservesInnerColumnProgress() {
        ChunkEpochState original = new ChunkEpochState();
        ChunkEpochState.Record record = original.getOrCreate(17, -9);
        record.begin(ChunkCatchUpManager.TRANSFORM_VERSION, 102, 6, 0.75F);
        record.advance(1, 43, 27, 118, 8_400L);

        ChunkEpochState loaded = ChunkEpochState.load(
                original.save(new CompoundTag(), null), null);
        ChunkEpochState.Record restored = loaded.get(17, -9);

        assertEquals(1, restored.passIndex());
        assertEquals(43, restored.cursor());
        assertEquals(27, restored.subCursor());
        assertEquals(118, restored.columnTopY());
        assertFalse(restored.complete());
    }

    @Test
    void legacyRecordDefaultsToStartOfCurrentColumn() {
        long key = ChunkEpochState.pack(-4, 12);
        CompoundTag record = new CompoundTag();
        record.putLong("key", key);
        record.putInt("version", ChunkCatchUpManager.TRANSFORM_VERSION);
        record.putInt("day", 102);
        record.putFloat("progress", 0.75F);
        record.putInt("phase", 6);
        record.putInt("pass", 1);
        record.putInt("cursor", 91);
        record.putBoolean("complete", false);

        ListTag records = new ListTag();
        records.add(record);
        CompoundTag encoded = new CompoundTag();
        encoded.put("chunks", records);

        ChunkEpochState.Record restored = ChunkEpochState.load(encoded, null).get(-4, 12);
        assertEquals(91, restored.cursor());
        assertEquals(0, restored.subCursor());
        assertEquals(Integer.MIN_VALUE, restored.columnTopY());
        assertFalse(restored.complete());
    }

    @Test
    void beginAndCompleteClearPartialColumnProgress() {
        ChunkEpochState state = new ChunkEpochState();
        ChunkEpochState.Record record = state.getOrCreate(3, 5);
        record.begin(ChunkCatchUpManager.TRANSFORM_VERSION, 102, 6, 0.75F);
        record.advance(0, 8, 31, 96, 400L);

        record.begin(ChunkCatchUpManager.TRANSFORM_VERSION, 103, 6, 0.80F);
        assertEquals(0, record.subCursor());
        assertEquals(Integer.MIN_VALUE, record.columnTopY());

        record.advance(0, 2, 7, 88, 500L);
        record.complete(ChunkCatchUpManager.TRANSFORM_VERSION, 103, 6, 0.80F, 600L);
        assertEquals(0, record.subCursor());
        assertEquals(Integer.MIN_VALUE, record.columnTopY());
        assertTrue(record.complete());
    }
}
