package com.frozendawn.entity.blind;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.frozendawn.entity.blind.BlindSignalMemory.Signal;

class BlindSignalMemoryTest {
    private Signal signal(Sense sense, int x, double confidence, long now, UUID player) {
        return new Signal(sense, new BlockPos(x, 0, 0), 2, confidence, Math.abs(x), now, player);
    }
    @Test void decoysNeverCreatePlayerLocks() {
        var memory = new BlindSignalMemory();
        for (int t = 0; t < 100; t += 10) memory.add(signal(Sense.HEARING, 2, .9, t, null));
        assertFalse(memory.select(BlockPos.ZERO, 100).lock(100));
        assertNull(memory.select(BlockPos.ZERO, 100).subject());
    }
    @Test void freshEchoWinsThenStaleEchoCanBeLuredAway() {
        var memory = new BlindSignalMemory();
        memory.add(signal(Sense.ECHO, 6, .85, 0, UUID.randomUUID()));
        memory.add(signal(Sense.HEARING, 2, .55, 0, null));
        assertEquals(Sense.ECHO, memory.select(BlockPos.ZERO, 0).sense());
        memory.add(signal(Sense.HEARING, 2, .55, 121, null));
        assertEquals(Sense.HEARING, memory.select(BlockPos.ZERO, 121).sense());
    }
    @Test void nearestConfidentSignalWinsWithoutChangingSnapshots() {
        var memory = new BlindSignalMemory(); var first = signal(Sense.ECHO, 7, .85, 0, UUID.randomUUID());
        var near = signal(Sense.ECHO, 2, .85, 0, UUID.randomUUID());
        memory.add(first); memory.add(near);
        assertEquals(near.subject(), memory.select(BlockPos.ZERO, 0).subject());
        assertEquals(first.region(), memory.snapshot().getFirst().region());
    }
    @Test void consistencyRequiresOverlappingRegionsRatherThanAnIdentityMatch() {
        var memory = new BlindSignalMemory(); UUID same = UUID.randomUUID();
        memory.add(signal(Sense.ECHO, 12, .60, 0, same)); memory.add(signal(Sense.ECHO, 30, .40, 30, same));
        assertEquals(.40, memory.snapshot().getLast().confidence());
        memory.add(signal(Sense.ECHO, 12, .60, 40, UUID.randomUUID()));
        assertEquals(.75, memory.snapshot().getFirst().confidence(), .0001);
    }
    @Test void memoryAndSearchLifetimeAreBounded() {
        var memory = new BlindSignalMemory();
        for (int i = 0; i < 20; i++) memory.add(signal(Sense.VIBRATION, i * 8, .45, i, null));
        assertEquals(4, memory.snapshot().size());
        assertNull(memory.select(BlockPos.ZERO, 260)); assertTrue(memory.snapshot().isEmpty());
    }
    @Test void repeatedAmbiguousReturnsCannotBecomeAPreciseLock() {
        var memory=new BlindSignalMemory();UUID identity=UUID.randomUUID();
        for(int tick=0;tick<=200;tick+=20)
            memory.add(new Signal(Sense.ECHO,new BlockPos(8,0,0),3,.40,8,tick,identity,.55));
        var signal=memory.select(BlockPos.ZERO,200);
        assertEquals(.55,signal.confidence(),.0001);assertFalse(signal.lock(200));
        memory.add(new Signal(Sense.ECHO,new BlockPos(8,0,0),1,.85,8,220,identity,.90));
        assertTrue(memory.select(BlockPos.ZERO,220).lock(220));
    }
    @Test void clockRollbackCannotMakeFutureSignalsActionable() {
        var memory = new BlindSignalMemory(); memory.add(signal(Sense.ECHO, 2, .85, 100, UUID.randomUUID()));
        assertNull(memory.select(BlockPos.ZERO, 99));
    }
    @Test void UnlocalizedVibrationDoesNotDisplaceActionableHearing() {
        var memory = new BlindSignalMemory();
        memory.add(signal(Sense.HEARING, 8, .55, 0, null));
        memory.add(new Signal(Sense.VIBRATION, BlockPos.ZERO, 24, .45, 6, 0, null));
        assertEquals(Sense.HEARING, memory.select(BlockPos.ZERO, 0).sense());
    }
}
