package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthBoundaryParticlePolicyTest {
    @Test
    void formedBoundaryParticleLineIsDeterministicAndContinuous() {
        List<BlockPos> first = FormedHearthLayout.boundaryParticleOffsets(42L);
        List<BlockPos> second = FormedHearthLayout.boundaryParticleOffsets(42L);

        assertEquals(first, second);
        assertEquals(first.size(), new HashSet<>(first).size());
        assertEquals(24, first.size());
    }

    @Test
    void intactBoundaryParticleLineCoversOnlyThePerimeter() {
        List<BlockPos> offsets = IntactHearthLayout.boundaryParticleOffsets(0L);

        assertEquals(72, offsets.size());
        assertEquals(offsets.size(), new HashSet<>(offsets).size());
        assertTrue(offsets.stream().allMatch(pos ->
                Math.abs(pos.getX()) == 8 || Math.abs(pos.getX()) == 10
                        || Math.abs(pos.getZ()) == 8 || Math.abs(pos.getZ()) == 10));
    }

    @Test
    void interactionCuesOnlyCoverBlocksWhoseUseTriggersConduct() {
        assertTrue(HearthBoundaryParticlePolicy.isInteractionCuePiece(
                HearthStructurePiece.PROTECTED_CHEST));
        assertTrue(HearthBoundaryParticlePolicy.isInteractionCuePiece(
                HearthStructurePiece.SACRED_CHEST));
        assertTrue(HearthBoundaryParticlePolicy.isInteractionCuePiece(
                HearthStructurePiece.ORSA_CRATE));
        assertTrue(HearthBoundaryParticlePolicy.isInteractionCuePiece(
                HearthStructurePiece.COLD_FURNACE));
        assertFalse(HearthBoundaryParticlePolicy.isInteractionCuePiece(
                HearthStructurePiece.FROZEN_PLANKS));
    }
}
