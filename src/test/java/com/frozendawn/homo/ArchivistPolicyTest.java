package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArchivistPolicyTest {
    @Test
    void regionKeysUseVanillaThirtyTwoChunkRegionsIncludingNegatives() {
        assertEquals(ArchivistPolicy.regionKey(new BlockPos(0, 64, 0)),
                ArchivistPolicy.regionKey(new BlockPos(511, 64, 511)));
        assertNotEquals(ArchivistPolicy.regionKey(new BlockPos(0, 64, 0)),
                ArchivistPolicy.regionKey(new BlockPos(512, 64, 0)));
        assertNotEquals(ArchivistPolicy.regionKey(new BlockPos(0, 64, 0)),
                ArchivistPolicy.regionKey(new BlockPos(-1, 64, 0)));
    }

    @Test
    void spawnAuthorityHonorsWorldGateCapsAndCooldown() {
        assertTrue(ArchivistPolicy.canSpawn(true, true,
                false, false, 1_000L, 999L));
        assertFalse(ArchivistPolicy.canSpawn(false, true,
                false, false, 1_000L, 0L));
        assertFalse(ArchivistPolicy.canSpawn(true, false,
                false, false, 1_000L, 0L));
        assertFalse(ArchivistPolicy.canSpawn(true, true,
                true, false, 1_000L, 0L));
        assertFalse(ArchivistPolicy.canSpawn(true, true,
                false, true, 1_000L, 0L));
        assertFalse(ArchivistPolicy.canSpawn(true, true,
                false, false, 999L, 1_000L));
    }

    @Test
    void generalAndBadgeCapacityRemainSeparated() {
        Set<Integer> occupied = new HashSet<>();
        for (int slot = 0; slot < ArchivistPolicy.GENERAL_SLOTS; slot++) {
            assertEquals(slot, ArchivistPolicy.firstSlot(false, occupied));
            occupied.add(slot);
        }
        assertEquals(-1, ArchivistPolicy.firstSlot(false, occupied));
        assertEquals(ArchivistPolicy.GENERAL_SLOTS,
                ArchivistPolicy.firstSlot(true, occupied));
        assertTrue(ArchivistPolicy.isBadgeSlot(ArchivistPolicy.GENERAL_SLOTS));
        assertFalse(ArchivistPolicy.isBadgeSlot(ArchivistPolicy.GENERAL_SLOTS - 1));
    }

    @Test
    void layoutIsDeterministicAndBadgesSitApartFromSalvage() {
        var first = ArchivistPolicy.slotPosition(BlockPos.ZERO, 0);
        assertEquals(first, ArchivistPolicy.slotPosition(BlockPos.ZERO, 0));
        var badge = ArchivistPolicy.slotPosition(
                BlockPos.ZERO, ArchivistPolicy.GENERAL_SLOTS);
        assertTrue(badge.distanceToSqr(first) > 16.0D);
        assertEquals(ArchivistPolicy.slotYaw(42L, 7),
                ArchivistPolicy.slotYaw(42L, 7));
    }
}
