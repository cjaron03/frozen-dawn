package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThaeIvenMindDimensionTest {
    @Test
    void arenaSelectionIsDeterministicAndGridAligned() {
        UUID master = UUID.fromString("8d312aae-04d1-49a6-a208-e480a07c22a0");
        BlockPos first = ThaeIvenMindDimension.arenaCenter(master);
        BlockPos second = ThaeIvenMindDimension.arenaCenter(master);

        assertEquals(first, second);
        assertEquals(0, Math.floorMod(first.getX(), 192));
        assertEquals(0, Math.floorMod(first.getZ(), 192));
        assertEquals(ThaeIvenMindDimension.ARENA_Y, first.getY());
    }

    @Test
    void multiplayerEntriesShareTheStageWithoutOverlapping() {
        BlockPos center = BlockPos.ZERO.atY(ThaeIvenMindDimension.ARENA_Y);
        var left = ThaeIvenMindDimension.playerEntry(center, 0, 3);
        var middle = ThaeIvenMindDimension.playerEntry(center, 1, 3);
        var right = ThaeIvenMindDimension.playerEntry(center, 2, 3);

        assertTrue(left.x < middle.x);
        assertTrue(middle.x < right.x);
        assertEquals(left.z, right.z, 0.0001D);
        assertTrue(left.distanceToSqr(center.getCenter())
                < ThaeIvenMindDimension.BARRIER_RADIUS
                        * ThaeIvenMindDimension.BARRIER_RADIUS);
    }

    @Test
    void sanctuaryIsDeterministicAndInsideTheBarrier() {
        UUID master = UUID.fromString("8d312aae-04d1-49a6-a208-e480a07c22a0");
        BlockPos center = ThaeIvenMindDimension.arenaCenter(master);
        BlockPos sanctuary = ThaeIvenMindDimension.sanctuaryPosition(master);

        assertEquals(center.offset(
                0, 1, ThaeIvenMindDimension.SANCTUARY_Z_OFFSET), sanctuary);
        assertTrue(sanctuary.distSqr(center)
                < ThaeIvenMindDimension.BARRIER_RADIUS
                        * ThaeIvenMindDimension.BARRIER_RADIUS);
    }
}
