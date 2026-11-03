package com.frozendawn.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChunkCatchUpQueueTest {
    @Test void nearestPendingChunkComesFirstWhetherFreshOrReloaded() {
        // A reloaded chunk beside the player must not wait behind freshly generated ones further out.
        Map<Long, Double> distance = Map.of(1L, 4.0, 2L, 400.0, 3L, 900.0);
        assertEquals(1L, ChunkCatchUpManager.nearestFirst(List.of(3L, 2L, 1L), Set.of(), distance::get));
    }

    @Test void skippedChunksAreNotChosen() {
        Map<Long, Double> distance = Map.of(1L, 4.0, 2L, 400.0);
        assertEquals(2L, ChunkCatchUpManager.nearestFirst(List.of(1L, 2L), Set.of(1L), distance::get),
                "A chunk still generating is skipped this tick, not waited on");
        assertEquals(Long.MIN_VALUE, ChunkCatchUpManager.nearestFirst(List.of(1L, 2L), Set.of(1L, 2L), distance::get));
        assertEquals(Long.MIN_VALUE, ChunkCatchUpManager.nearestFirst(List.of(), Set.of(), k -> 0));
    }

    @Test void tiesBreakDeterministically() {
        assertEquals(5L, ChunkCatchUpManager.nearestFirst(List.of(9L, 5L, 7L), Set.of(), k -> 16.0));
    }
}
