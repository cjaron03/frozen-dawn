package com.frozendawn.data;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacifistSavedDataTest {
    @Test
    void conductSurvivesReloadAndDoesNotDisqualifyOtherPlayers() {
        var killer = UUID.randomUUID();
        var peaceful = UUID.randomUUID();
        var data = new PacifistSavedData();
        data.recordKill(killer);
        data.recordKill(killer);
        var saved = data.save(new CompoundTag(), null);
        assertEquals(1, saved.getList("disqualified", 10).size());
        var loaded = PacifistSavedData.load(saved, null);
        assertTrue(loaded.disqualified(killer));
        assertFalse(loaded.disqualified(peaceful));
        assertEquals(saved, loaded.save(new CompoundTag(), null));
    }

    @Test
    void oldWorldStartsWithoutInventedConduct() {
        var loaded = PacifistSavedData.load(new CompoundTag(), null);
        assertFalse(loaded.disqualified(UUID.randomUUID()));
    }
}
