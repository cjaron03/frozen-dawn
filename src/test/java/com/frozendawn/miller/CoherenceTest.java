package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CoherenceTest {
    private static Coherence at(double value) {
        var coherence = new Coherence();
        coherence.set(value, 0);
        return coherence;
    }

    private static Coherence worn() {
        return at(Coherence.FLOOR);
    }

    @Test
    void sheStartsWhole() {
        var coherence = new Coherence();
        assertEquals(1, coherence.value(10_000_000));
        coherence.begin(100);
        assertEquals(1, coherence.value(100));
        assertEquals(1, new PatternState().coherence().value(0));
    }

    @Test
    void coherenceFallsToItsFloorAndNoFurther() {
        var coherence = new Coherence();
        coherence.begin(0);
        assertEquals(0.65, coherence.value(Coherence.HALF_LIFE), 1e-9, "Half the way down in three days");
        double floor = coherence.value(100 * Coherence.HALF_LIFE);
        assertEquals(Coherence.FLOOR, floor, 1e-9);
        assertTrue(floor >= Coherence.FLOOR);
    }

    @Test
    void talkingRestoresHer() {
        var coherence = worn();
        coherence.spoken(0);
        coherence.spoken(0);
        assertEquals(0.7, coherence.value(0), 1e-9);
        coherence.spoken(0);
        coherence.spoken(0);
        assertEquals(1, coherence.value(0), "It stops at 1");
    }

    @Test
    void aConversationCountsOnce() {
        var state = new PatternState();
        state.poll(1, PatternState.NOT_SET, false, 0);
        state.coherence().set(Coherence.FLOOR, 0);
        state.poll(1, 50, false, 100);
        double once = state.coherence().value(100);
        assertEquals(0.5, once, 1e-6);
        state.poll(1, 50, false, 100);
        state = PatternState.load(state.save());
        state.poll(1, 50, false, 100);
        assertEquals(once, state.coherence().value(100), 1e-12);
    }

    @Test
    void noDelayAtSevenTenths() {
        assertEquals(0, at(0.7).halfLaps(0));
    }

    @Test
    void halfALapBelowIt() {
        assertEquals(1, at(0.69).halfLaps(0));
        assertEquals(1, at(0.5).halfLaps(0));
    }

    @Test
    void aWholeLapBelowHalf() {
        assertEquals(2, at(0.49).halfLaps(0));
        assertEquals(2, worn().halfLaps(0));
    }

    @Test
    void varietyNeverFallsBelowOne() {
        assertEquals(1, worn().variety(3, 0));
        assertEquals(2, worn().variety(4, 0));
        assertEquals(3, at(1).variety(3, 0));
        assertEquals(1, worn().variety(1, 0));
        assertEquals(0, worn().variety(0, 0), "An empty pool stays empty");
    }

    @Test
    void settingHerCannotPassTheFloor() {
        assertEquals(Coherence.FLOOR, at(0).value(0));
        assertEquals(1, at(5).value(0));
    }

    @Test
    void aDamagedSaveLoadsInRange() {
        var tag = worn().save();
        tag.putDouble("value", 0);
        assertEquals(Coherence.FLOOR, Coherence.load(tag).value(tag.getLong("at")), 1e-9);
        assertEquals(1, Coherence.load(new CompoundTag()).value(0), "Nothing saved is whole");
    }
}
