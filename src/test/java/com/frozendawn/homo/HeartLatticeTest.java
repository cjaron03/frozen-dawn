package com.frozendawn.homo;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartLatticeTest {
    @Test
    void latticeIsDeterministicForItsSavedSeed() {
        HeartLattice.Lattice first = HeartLattice.create(0x48454152544CL);
        HeartLattice.Lattice second = HeartLattice.create(0x48454152544CL);
        HeartLattice.Lattice different = HeartLattice.create(0x48454152544DL);

        assertEquals(first, second);
        assertEquals(5, first.nodes().size());
        assertNotEquals(first, different);
    }

    @Test
    void destroyedMaskSelectsNodesInCanonOrder() {
        assertEquals(0, HeartLattice.nextNode(0));
        assertEquals(1, HeartLattice.nextNode(0b00001));
        assertEquals(4, HeartLattice.nextNode(0b01111));
        assertEquals(-1, HeartLattice.nextNode(0b11111));
        assertEquals(3, HeartLattice.destroyedCount(0b10101));
    }

    @Test
    void laterMemoriesRequireIncreasingCognitiveLoad() {
        for (int index = 1; index < HeartLattice.NODE_COUNT; index++) {
            assertTrue(HeartLattice.requiredLoad(index)
                    > HeartLattice.requiredLoad(index - 1));
        }
    }

    @Test
    void raySelectionRejectsMissesAndOutOfRangeNodes() {
        Vec3 eye = Vec3.ZERO;
        Vec3 look = new Vec3(0.0D, 0.0D, 1.0D);

        assertTrue(HeartLattice.raySelectsNode(
                eye, look, new Vec3(0.4D, 0.2D, 12.0D)));
        assertFalse(HeartLattice.raySelectsNode(
                eye, look, new Vec3(4.5D, 0.0D, 12.0D)));
        assertTrue(HeartLattice.raySelectsNode(
                eye, look, new Vec3(3.5D, 0.0D, 40.0D)));
        assertFalse(HeartLattice.raySelectsNode(
                eye, look, new Vec3(0.0D, 0.0D, 41.0D)));
        assertFalse(HeartLattice.raySelectsNode(
                eye, look, new Vec3(0.0D, 0.0D, -5.0D)));
    }

    @Test
    void raySelectionAcceptsPointBlankNodeVolume() {
        assertTrue(HeartLattice.raySelectsNode(
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 1.0D),
                new Vec3(0.0D, 0.0D, -0.5D)));
    }
}
