package com.frozendawn.data;

import com.frozendawn.world.PostMaeveEncounterType;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostMaeveEncounterSavedDataTest {
    @Test
    void encounterPressureAndDiagnosticsSurviveReload() {
        PostMaeveEncounterSavedData original = new PostMaeveEncounterSavedData();
        PostMaeveEncounterSavedData.OwnerRecord owner = original.owner("player:test");
        PostMaeveEncounterSavedData.Entry entry = owner.entry(
                PostMaeveEncounterType.RESONANT);
        entry.begin(120L);
        entry.recordRoll(0.24D, false);
        entry.recordBlocked("no concealed breach surface");

        CompoundTag encoded = original.save(new CompoundTag(), null);
        PostMaeveEncounterSavedData loaded =
                PostMaeveEncounterSavedData.load(encoded, null);
        PostMaeveEncounterSavedData.Entry restored = loaded.owner("player:test")
                .entry(PostMaeveEncounterType.RESONANT);

        assertEquals(120L, restored.windowStartTick());
        assertEquals(2, restored.failedAttempts());
        assertEquals(0.24D, restored.lastChance(), 0.0001D);
        assertEquals("no concealed breach surface", restored.lastReason());
    }

    @Test
    void successResetsPressureAndPersistsOwnerVarietyState() {
        PostMaeveEncounterSavedData original = new PostMaeveEncounterSavedData();
        PostMaeveEncounterSavedData.OwnerRecord owner = original.owner("region:42");
        PostMaeveEncounterSavedData.Entry entry = owner.entry(
                PostMaeveEncounterType.REMNANT);
        entry.begin(10L);
        entry.recordBlocked("terrain");
        entry.recordSuccess(800L);
        owner.recordSuccess(PostMaeveEncounterType.REMNANT, 800L);

        PostMaeveEncounterSavedData loaded = PostMaeveEncounterSavedData.load(
                original.save(new CompoundTag(), null), null);
        PostMaeveEncounterSavedData.OwnerRecord restored = loaded.owner("region:42");
        PostMaeveEncounterSavedData.Entry restoredEntry = restored.entry(
                PostMaeveEncounterType.REMNANT);

        assertEquals(0, restoredEntry.failedAttempts());
        assertEquals(800L, restoredEntry.lastSuccessTick());
        assertEquals(800L, restored.lastEncounterTick());
        assertEquals(PostMaeveEncounterType.REMNANT, restored.lastType());
    }

    @Test
    void debugReadySurvivesReloadUntilEncounterSucceeds() {
        PostMaeveEncounterSavedData original = new PostMaeveEncounterSavedData();
        PostMaeveEncounterSavedData.OwnerRecord owner = original.owner("player:test");
        PostMaeveEncounterSavedData.Entry entry = owner.entry(
                PostMaeveEncounterType.FROSTWRITHE);
        entry.markDebugReady(20_000L,
                PostMaeveEncounterType.FROSTWRITHE.guaranteedIntervalTicks());

        PostMaeveEncounterSavedData loaded = PostMaeveEncounterSavedData.load(
                original.save(new CompoundTag(), null), null);
        PostMaeveEncounterSavedData.Entry restored = loaded.owner("player:test")
                .entry(PostMaeveEncounterType.FROSTWRITHE);

        assertTrue(restored.debugReady());
        restored.recordBlocked("terrain");
        assertTrue(restored.debugReady());
        restored.recordSuccess(21_000L);
        assertEquals(false, restored.debugReady());
    }
}
