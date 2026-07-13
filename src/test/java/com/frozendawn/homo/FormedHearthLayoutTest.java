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
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormedHearthLayoutTest {

    @Test
    void formedLayoutIsDeterministicCrampedAndExplicitlyProtected() {
        List<HearthStructurePlacement> first = FormedHearthLayout.create(
                123456789L, HearthSelectionPolicy.HearthType.MAJOR);
        List<HearthStructurePlacement> second = FormedHearthLayout.create(
                123456789L, HearthSelectionPolicy.HearthType.MAJOR);

        assertEquals(first, second);
        assertEquals(1, count(first, HearthStructurePiece.PROTECTED_CHEST));
        assertEquals(1, count(first, HearthStructurePiece.COLD_CAMPFIRE));
        assertEquals(1, count(first, HearthStructurePiece.COLD_FURNACE));
        assertEquals(4, count(first, HearthStructurePiece.ORSA_CRATE));
        assertEquals(2, count(first, HearthStructurePiece.DOOR_LOWER));
        assertEquals(2, count(first, HearthStructurePiece.DOOR_UPPER));
        assertTrue(first.stream().anyMatch(placement ->
                placement.protection() == HearthStructurePlacement.Protection.CONTAINER));
        assertTrue(first.stream().anyMatch(placement ->
                placement.protection() == HearthStructurePlacement.Protection.DOOR));
        assertTrue(first.stream().anyMatch(placement ->
                placement.protection() == HearthStructurePlacement.Protection.HEARTH_RING));
        assertTrue(first.stream().anyMatch(placement ->
                placement.piece() == HearthStructurePiece.CLEAR_TRANSIENT
                        && placement.offset().getY() == 4));
    }

    @Test
    void formedLayoutNeverCollidesOrEscapesTheProvenTraceFootprint() {
        for (long seed = 0L; seed < 100L; seed++) {
            List<HearthStructurePlacement> layout = FormedHearthLayout.create(
                    seed, HearthSelectionPolicy.HearthType.MAJOR);
            Set<BlockPos> positions = new HashSet<>();
            for (HearthStructurePlacement placement : layout) {
                assertTrue(positions.add(placement.offset()),
                        () -> "duplicate placement at " + placement.offset());
                assertTrue(Math.abs(placement.offset().getX())
                                <= HearthReconciliationPolicy.FORMED_FOOTPRINT_RADIUS,
                        () -> "x outside footprint: " + placement.offset());
                assertTrue(Math.abs(placement.offset().getZ())
                                <= HearthReconciliationPolicy.FORMED_FOOTPRINT_RADIUS,
                        () -> "z outside footprint: " + placement.offset());
            }
        }
    }

    @Test
    void protectedInteriorRotatesWithTheLayout() {
        for (long seed = 0L; seed < 16L; seed++) {
            int protectedCells = 0;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    for (int y = 0; y <= 1; y++) {
                        if (FormedHearthLayout.isInsideProtectedInterior(
                                seed, new BlockPos(x, y, z))) {
                            protectedCells++;
                        }
                    }
                }
            }
            assertEquals(18, protectedCells);
        }
    }

    @Test
    void everyFormedFoundationHasADeepSupportForUnevenTerrain() {
        for (long seed = 0L; seed < 100L; seed++) {
            List<HearthStructurePlacement> layout = FormedHearthLayout.create(
                    seed, HearthSelectionPolicy.HearthType.MAJOR);
            Map<BlockPos, HearthStructurePlacement> byPosition = layout.stream()
                    .collect(Collectors.toMap(HearthStructurePlacement::offset,
                            Function.identity()));

            for (HearthStructurePlacement placement : layout) {
                if (placement.offset().getY() != -1) {
                    continue;
                }
                for (int depth = 1; depth <= 4; depth++) {
                    BlockPos supportPos = placement.offset().below(depth);
                    assertEquals(HearthStructurePiece.FOUNDATION_SUPPORT,
                            byPosition.get(supportPos).piece(),
                            () -> "missing terrain support at " + supportPos);
                }
            }
        }
    }

    @Test
    void leanToCanopyHasFourCompleteCornerSupports() {
        Map<BlockPos, HearthStructurePlacement> byPosition = FormedHearthLayout.create(
                        0L, HearthSelectionPolicy.HearthType.MAJOR).stream()
                .collect(Collectors.toMap(HearthStructurePlacement::offset,
                        Function.identity()));

        for (int x : new int[]{2, 4}) {
            for (int z : new int[]{-3, -1}) {
                assertTrue(isStructureBlock(byPosition.get(new BlockPos(x, 0, z))));
                assertTrue(isStructureBlock(byPosition.get(new BlockPos(x, 1, z))));
                assertTrue(isStructureBlock(byPosition.get(new BlockPos(x, 2, z))));
            }
        }
    }

    private static long count(List<HearthStructurePlacement> layout,
                              HearthStructurePiece piece) {
        return layout.stream().filter(placement -> placement.piece() == piece).count();
    }

    private static boolean isStructureBlock(HearthStructurePlacement placement) {
        return placement != null
                && placement.piece() != HearthStructurePiece.CLEAR_TRANSIENT
                && placement.piece() != HearthStructurePiece.CLEAR_PLATFORM
                && placement.piece() != HearthStructurePiece.CLEAR_LEGACY;
    }
}
