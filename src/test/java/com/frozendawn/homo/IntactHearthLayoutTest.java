package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntactHearthLayoutTest {

    @Test
    void intactMajorIsDeterministicVillageScaleAndPreservesTheFormedCore() {
        long seed = 123456789L;
        List<HearthStructurePlacement> formed = FormedHearthLayout.create(
                seed, HearthSelectionPolicy.HearthType.MAJOR);
        List<HearthStructurePlacement> first = IntactHearthLayout.create(
                seed, HearthSelectionPolicy.HearthType.MAJOR);
        List<HearthStructurePlacement> second = IntactHearthLayout.create(
                seed, HearthSelectionPolicy.HearthType.MAJOR);

        assertEquals(first, second);
        assertTrue(first.size() > formed.size() * 4);
        assertTrue(count(first, HearthStructurePiece.DOOR_LOWER) >= 9);
        assertEquals(1, count(first, HearthStructurePiece.SACRED_CHEST));
        assertEquals(1, count(first, HearthStructurePiece.PROTECTED_CHEST));
        assertTrue(count(first, HearthStructurePiece.FROZEN_ATMOSPHERE) >= 8);
        assertTrue(count(first, HearthStructurePiece.BOUNDARY_MARKER)
                > count(formed, HearthStructurePiece.BOUNDARY_MARKER));
        assertTrue(first.stream()
                .filter(placement -> placement.piece() == HearthStructurePiece.BOUNDARY_MARKER)
                .allMatch(placement -> placement.protection()
                        == HearthStructurePlacement.Protection.HEARTH_RING));

        Map<BlockPos, HearthStructurePlacement> intactByPosition = first.stream()
                .collect(Collectors.toMap(HearthStructurePlacement::offset,
                        Function.identity()));
        for (HearthStructurePlacement placement : formed) {
            assertEquals(placement, intactByPosition.get(placement.offset()),
                    () -> "Formed core changed at " + placement.offset());
        }
    }

    @Test
    void minorHearthStillUsesTheCompactFormedLayout() {
        for (long seed = 0L; seed < 16L; seed++) {
            assertEquals(FormedHearthLayout.create(seed, HearthSelectionPolicy.HearthType.MINOR),
                    IntactHearthLayout.create(seed, HearthSelectionPolicy.HearthType.MINOR));
        }
    }

    @Test
    void intactLayoutNeverCollidesOrEscapesItsAuthoredFootprint() {
        for (long seed = 0L; seed < 32L; seed++) {
            List<HearthStructurePlacement> layout = IntactHearthLayout.create(
                    seed, HearthSelectionPolicy.HearthType.MAJOR);
            Set<BlockPos> positions = new HashSet<>();
            boolean reachedOuterSettlement = false;
            for (HearthStructurePlacement placement : layout) {
                assertTrue(positions.add(placement.offset()),
                        () -> "duplicate placement at " + placement.offset());
                assertTrue(Math.abs(placement.offset().getX())
                                <= HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS,
                        () -> "x outside footprint: " + placement.offset());
                assertTrue(Math.abs(placement.offset().getZ())
                                <= HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS,
                        () -> "z outside footprint: " + placement.offset());
                assertTrue(placement.offset().getY() >= -10
                                && placement.offset().getY() <= 5,
                        () -> "y outside authored volume: " + placement.offset());
                reachedOuterSettlement |= Math.abs(placement.offset().getX()) >= 20
                        || Math.abs(placement.offset().getZ()) >= 20;
            }
            assertTrue(reachedOuterSettlement);
        }
    }

    @Test
    void sacredInteriorsAndFutureRoleAnchorsRotateWithTheSettlement() {
        for (long seed = 0L; seed < 16L; seed++) {
            List<HearthStructurePlacement> layout = IntactHearthLayout.create(
                    seed, HearthSelectionPolicy.HearthType.MAJOR);
            HearthStructurePlacement sacredChest = layout.stream()
                    .filter(placement -> placement.piece() == HearthStructurePiece.SACRED_CHEST)
                    .findFirst()
                    .orElseThrow();
            HearthStructurePlacement formedChest = layout.stream()
                    .filter(placement -> placement.piece()
                            == HearthStructurePiece.PROTECTED_CHEST)
                    .findFirst()
                    .orElseThrow();

            assertTrue(IntactHearthLayout.isInsideProtectedInterior(
                    seed, sacredChest.offset()));
            assertTrue(IntactHearthLayout.isInsideProtectedInterior(
                    seed, formedChest.offset()));
            assertFalse(IntactHearthLayout.isInsideProtectedInterior(
                    seed, IntactHearthLayout.masterArchitectAnchor(seed)));

            Set<BlockPos> anchors = new HashSet<>(IntactHearthLayout.returnedAnchors(seed));
            anchors.add(IntactHearthLayout.mimicAnchor(seed));
            anchors.add(IntactHearthLayout.architectAnchor(seed));
            anchors.add(IntactHearthLayout.masterArchitectAnchor(seed));
            assertEquals(5, anchors.size());
            for (BlockPos anchor : anchors) {
                assertTrue(Math.abs(anchor.getX())
                        <= HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS);
                assertTrue(Math.abs(anchor.getZ())
                        <= HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS);
            }
        }
    }

    @Test
    void layoutSeedsChangeOrientationWithoutChangingSettlementScale() {
        List<HearthStructurePlacement> first = IntactHearthLayout.create(
                1L, HearthSelectionPolicy.HearthType.MAJOR);
        List<HearthStructurePlacement> second = IntactHearthLayout.create(
                2L, HearthSelectionPolicy.HearthType.MAJOR);

        assertNotEquals(first, second);
        assertEquals(first.size(), second.size());
        assertTrue(count(first, HearthStructurePiece.FOUNDATION_SUPPORT) > 500);
        assertTrue(count(second, HearthStructurePiece.FOUNDATION_SUPPORT) > 500);
    }

    private static long count(List<HearthStructurePlacement> layout,
                              HearthStructurePiece piece) {
        return layout.stream().filter(placement -> placement.piece() == piece).count();
    }
}
