package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic Stage 2 scene: a remembered shelter with an unmistakable threshold.
 */
public final class FormedHearthLayout {
    private static final int FOUNDATION_SUPPORT_DEPTH = 8;
    private static final int SHELTER_MIN_X = -4;
    private static final int SHELTER_MAX_X = 0;
    private static final int SHELTER_MIN_Z = -2;
    private static final int SHELTER_MAX_Z = 2;
    private static final int BOUNDARY_MIN_X = -5;
    private static final int BOUNDARY_MAX_X = 1;
    private static final int BOUNDARY_MIN_Z = -3;
    private static final int BOUNDARY_MAX_Z = 3;
    private static final int WARNING_WIDTH = 3;

    private FormedHearthLayout() {
    }

    public static List<HearthStructurePlacement> create(
            long layoutSeed, HearthSelectionPolicy.HearthType type) {
        int turns = turns(layoutSeed);
        Map<BlockPos, HearthStructurePlacement> placements = new LinkedHashMap<>();

        clearTransientVolume(placements, turns);
        clearTraceScene(placements, layoutSeed, type, turns);
        addSnowBoundary(placements, layoutSeed, turns);
        addShelter(placements, turns);
        addLeanTo(placements, turns);
        addHearthRing(placements, turns);
        addWrongDoor(placements, turns);
        clearLegacyBoundaryMarkers(placements, turns);

        return List.copyOf(new ArrayList<>(placements.values()));
    }

    public static HearthStructurePlacement.Protection protectionAt(
            long layoutSeed, HearthSelectionPolicy.HearthType type, BlockPos relativePos) {
        for (HearthStructurePlacement placement : create(layoutSeed, type)) {
            if (placement.offset().equals(relativePos)) {
                return placement.protection();
            }
        }
        return HearthStructurePlacement.Protection.NONE;
    }

    private static void clearTransientVolume(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        for (int x = -HearthReconciliationPolicy.FORMED_FOOTPRINT_RADIUS;
             x <= HearthReconciliationPolicy.FORMED_FOOTPRINT_RADIUS; x++) {
            for (int z = -HearthReconciliationPolicy.FORMED_FOOTPRINT_RADIUS;
                 z <= HearthReconciliationPolicy.FORMED_FOOTPRINT_RADIUS; z++) {
                for (int y = 0; y <= 4; y++) {
                    putRotated(placements, HearthStructurePiece.CLEAR_TRANSIENT,
                            new BlockPos(x, y, z), Direction.NORTH, 0,
                            HearthStructurePlacement.Protection.NONE, turns);
                }
            }
        }
    }

    public static boolean isInsideProtectedInterior(long layoutSeed, BlockPos relativePos) {
        BlockPos canonical = inverseRotate(relativePos, turns(layoutSeed));
        return canonical.getX() >= -3 && canonical.getX() <= -1
                && canonical.getZ() >= -1 && canonical.getZ() <= 1
                && canonical.getY() >= 0 && canonical.getY() <= 1;
    }

    public static boolean isInsideMarkedBoundary(long layoutSeed, BlockPos relativePos) {
        BlockPos canonical = inverseRotate(relativePos, turns(layoutSeed));
        return canonical.getX() > BOUNDARY_MIN_X && canonical.getX() < BOUNDARY_MAX_X
                && canonical.getZ() > BOUNDARY_MIN_Z && canonical.getZ() < BOUNDARY_MAX_Z
                && canonical.getY() >= -1 && canonical.getY() <= 3;
    }

    public static boolean isInsideBoundaryWarningBand(long layoutSeed, BlockPos relativePos) {
        BlockPos canonical = inverseRotate(relativePos, turns(layoutSeed));
        if (canonical.getY() < -2 || canonical.getY() > 4
                || isInsideMarkedBoundary(layoutSeed, relativePos)) {
            return false;
        }
        return canonical.getX() >= BOUNDARY_MIN_X - WARNING_WIDTH
                && canonical.getX() <= BOUNDARY_MAX_X + WARNING_WIDTH
                && canonical.getZ() >= BOUNDARY_MIN_Z - WARNING_WIDTH
                && canonical.getZ() <= BOUNDARY_MAX_Z + WARNING_WIDTH;
    }

    public static List<BlockPos> boundaryParticleOffsets(long layoutSeed) {
        int turns = turns(layoutSeed);
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = BOUNDARY_MIN_X; x <= BOUNDARY_MAX_X; x++) {
            for (int z = BOUNDARY_MIN_Z; z <= BOUNDARY_MAX_Z; z++) {
                if (x == BOUNDARY_MIN_X || x == BOUNDARY_MAX_X
                        || z == BOUNDARY_MIN_Z || z == BOUNDARY_MAX_Z) {
                    offsets.add(rotate(new BlockPos(x, 0, z), turns));
                }
            }
        }
        return List.copyOf(offsets);
    }

    private static void clearLegacyBoundaryMarkers(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        for (int x = BOUNDARY_MIN_X; x <= BOUNDARY_MAX_X; x++) {
            for (int z = BOUNDARY_MIN_Z; z <= BOUNDARY_MAX_Z; z++) {
                boolean perimeter = x == BOUNDARY_MIN_X || x == BOUNDARY_MAX_X
                        || z == BOUNDARY_MIN_Z || z == BOUNDARY_MAX_Z;
                if (!perimeter || isBoundaryEntrance(x, z)) {
                    continue;
                }
                putRotated(placements, HearthStructurePiece.BOUNDARY_MARKER,
                        new BlockPos(x, 0, z), Direction.NORTH, 0,
                        HearthStructurePlacement.Protection.HEARTH_RING, turns);
            }
        }
    }

    private static boolean isBoundaryEntrance(int x, int z) {
        boolean shelterDoor = z == BOUNDARY_MAX_Z && x >= -3 && x <= -1;
        boolean hearthApproach = x == BOUNDARY_MAX_X && z >= 0 && z <= 1;
        return shelterDoor || hearthApproach;
    }

    private static void clearTraceScene(Map<BlockPos, HearthStructurePlacement> placements,
                                        long layoutSeed, HearthSelectionPolicy.HearthType type,
                                        int turns) {
        for (HearthStructurePlacement trace : TraceHearthLayout.create(layoutSeed, type)) {
            if (trace.offset().getY() < 0) {
                continue;
            }
            putRotated(placements, HearthStructurePiece.CLEAR_LEGACY,
                    inverseRotate(trace.offset(), turns), Direction.NORTH, 0,
                    HearthStructurePlacement.Protection.NONE, turns);
        }
    }

    private static void addSnowBoundary(Map<BlockPos, HearthStructurePlacement> placements,
                                        long seed, int turns) {
        for (int axis = -4; axis <= 4; axis += 2) {
            addSnowMarker(placements, axis, -4, seed, turns);
            addSnowMarker(placements, axis, 4, seed, turns);
            if (axis > -4 && axis < 4) {
                addSnowMarker(placements, -4, axis, seed, turns);
                addSnowMarker(placements, 4, axis, seed, turns);
            }
        }
    }

    private static void addShelter(Map<BlockPos, HearthStructurePlacement> placements,
                                   int turns) {
        for (int x = SHELTER_MIN_X; x <= SHELTER_MAX_X; x++) {
            for (int z = SHELTER_MIN_Z; z <= SHELTER_MAX_Z; z++) {
                addFoundation(placements, HearthStructurePiece.FROZEN_PLANKS,
                        x, z, HearthStructurePlacement.Protection.STRUCTURE, turns);

                boolean perimeter = x == SHELTER_MIN_X || x == SHELTER_MAX_X
                        || z == SHELTER_MIN_Z || z == SHELTER_MAX_Z;
                boolean doorway = x == -2 && z == SHELTER_MAX_Z;
                for (int y = 0; y <= 1; y++) {
                    if (perimeter && !doorway) {
                        HearthStructurePiece wall = ((x + z + y) & 3) == 0
                                ? HearthStructurePiece.FROZEN_PLANKS
                                : HearthStructurePiece.FROZEN_STONE_BRICKS;
                        putRotated(placements, wall, new BlockPos(x, y, z),
                                Direction.NORTH, 0,
                                HearthStructurePlacement.Protection.STRUCTURE, turns);
                    } else {
                        putRotated(placements, HearthStructurePiece.CLEAR_PLATFORM,
                                new BlockPos(x, y, z), Direction.NORTH, 0,
                                HearthStructurePlacement.Protection.NONE, turns);
                    }
                }

                HearthStructurePiece roof = ((x + z) & 3) == 0
                        ? HearthStructurePiece.FROZEN_STONE_BRICKS
                        : HearthStructurePiece.FROZEN_PLANKS;
                putRotated(placements, roof, new BlockPos(x, 2, z),
                        Direction.NORTH, 0,
                        HearthStructurePlacement.Protection.STRUCTURE, turns);
            }
        }

        addDoor(placements, -2, 2, Direction.SOUTH, false,
                HearthStructurePlacement.Protection.DOOR, turns);
        putRotated(placements, HearthStructurePiece.PROTECTED_CHEST,
                new BlockPos(-3, 0, -1), Direction.SOUTH, 0,
                HearthStructurePlacement.Protection.CONTAINER, turns);
        putRotated(placements, HearthStructurePiece.COLD_FURNACE,
                new BlockPos(-1, 0, -1), Direction.SOUTH, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        addBed(placements, -3, 0, Direction.EAST, turns);
    }

    private static void addLeanTo(Map<BlockPos, HearthStructurePlacement> placements,
                                  int turns) {
        for (int x = 2; x <= 4; x++) {
            for (int z = -3; z <= -1; z++) {
                addFoundation(placements, HearthStructurePiece.PACKED_ICE_LOWER,
                        x, z, HearthStructurePlacement.Protection.STRUCTURE, turns);
                boolean wall = z == -3 || x == 4;
                boolean frontPost = x == 2 && z == -1;
                if (wall || frontPost) {
                    putRotated(placements, HearthStructurePiece.FROZEN_STONE_BRICKS,
                            new BlockPos(x, 0, z), Direction.NORTH, 0,
                            HearthStructurePlacement.Protection.STRUCTURE, turns);
                    putRotated(placements, HearthStructurePiece.FROZEN_PLANKS,
                            new BlockPos(x, 1, z), Direction.NORTH, 0,
                            HearthStructurePlacement.Protection.STRUCTURE, turns);
                } else {
                    putRotated(placements, HearthStructurePiece.CLEAR_PLATFORM,
                            new BlockPos(x, 0, z), Direction.NORTH, 0,
                            HearthStructurePlacement.Protection.NONE, turns);
                    putRotated(placements, HearthStructurePiece.CLEAR_PLATFORM,
                            new BlockPos(x, 1, z), Direction.NORTH, 0,
                            HearthStructurePlacement.Protection.NONE, turns);
                }
                putRotated(placements, HearthStructurePiece.FROZEN_PLANKS,
                        new BlockPos(x, 2, z), Direction.NORTH, 0,
                        HearthStructurePlacement.Protection.STRUCTURE, turns);
            }
        }
        putRotated(placements, HearthStructurePiece.ORSA_CRATE,
                new BlockPos(3, 0, -2), Direction.SOUTH, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
    }

    private static void addHearthRing(Map<BlockPos, HearthStructurePlacement> placements,
                                      int turns) {
        addFoundation(placements, HearthStructurePiece.PACKED_ICE_LOWER,
                2, 1, HearthStructurePlacement.Protection.HEARTH_RING, turns);
        putRotated(placements, HearthStructurePiece.COLD_CAMPFIRE,
                new BlockPos(2, 0, 1), Direction.NORTH, 0,
                HearthStructurePlacement.Protection.HEARTH_RING, turns);

        addRingCrate(placements, 1, 1, Direction.EAST, turns);
        addRingCrate(placements, 3, 1, Direction.WEST, turns);
        addRingCrate(placements, 2, 0, Direction.SOUTH, turns);
    }

    private static void addWrongDoor(Map<BlockPos, HearthStructurePlacement> placements,
                                     int turns) {
        addFoundation(placements, HearthStructurePiece.PACKED_ICE_LOWER,
                4, 3, HearthStructurePlacement.Protection.HEARTH_RING, turns);
        addDoor(placements, 4, 3, Direction.WEST, true,
                HearthStructurePlacement.Protection.HEARTH_RING, turns);
    }

    private static void addRingCrate(Map<BlockPos, HearthStructurePlacement> placements,
                                     int x, int z, Direction facing, int turns) {
        addFoundation(placements, HearthStructurePiece.PACKED_ICE_LOWER,
                x, z, HearthStructurePlacement.Protection.HEARTH_RING, turns);
        putRotated(placements, HearthStructurePiece.ORSA_CRATE,
                new BlockPos(x, 0, z), facing, 0,
                HearthStructurePlacement.Protection.HEARTH_RING, turns);
    }

    private static void addSnowMarker(Map<BlockPos, HearthStructurePlacement> placements,
                                      int x, int z, long seed, int turns) {
        int layers = 2 + Math.floorMod((int) mix(seed ^ BlockPos.asLong(x, 0, z)), 5);
        addFoundation(placements, HearthStructurePiece.PACKED_ICE_LOWER,
                x, z, HearthStructurePlacement.Protection.NONE, turns);
        putRotated(placements, HearthStructurePiece.SNOW_MARKER,
                new BlockPos(x, 0, z), Direction.NORTH, layers,
                HearthStructurePlacement.Protection.NONE, turns);
    }

    private static void addDoor(Map<BlockPos, HearthStructurePlacement> placements,
                                int x, int z, Direction facing, boolean rightHinge,
                                HearthStructurePlacement.Protection protection, int turns) {
        putRotated(placements, HearthStructurePiece.DOOR_LOWER,
                new BlockPos(x, 0, z), facing, rightHinge ? 1 : 0, protection, turns);
        putRotated(placements, HearthStructurePiece.DOOR_UPPER,
                new BlockPos(x, 1, z), facing, rightHinge ? 1 : 0, protection, turns);
    }

    private static void addFoundation(
            Map<BlockPos, HearthStructurePlacement> placements,
            HearthStructurePiece surfacePiece, int x, int z,
            HearthStructurePlacement.Protection protection, int turns) {
        for (int depth = 2; depth <= FOUNDATION_SUPPORT_DEPTH; depth++) {
            putRotated(placements, HearthStructurePiece.FOUNDATION_SUPPORT,
                    new BlockPos(x, -depth, z), Direction.NORTH, 0, protection, turns);
        }
        putRotated(placements, surfacePiece,
                new BlockPos(x, -1, z), Direction.NORTH, 0, protection, turns);
    }

    private static void addBed(Map<BlockPos, HearthStructurePlacement> placements,
                               int x, int z, Direction facing, int turns) {
        putRotated(placements, HearthStructurePiece.BED_FOOT,
                new BlockPos(x, 0, z), facing, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        BlockPos head = new BlockPos(x, 0, z).relative(facing);
        putRotated(placements, HearthStructurePiece.BED_HEAD,
                head, facing, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
    }

    private static void putRotated(Map<BlockPos, HearthStructurePlacement> placements,
                                   HearthStructurePiece piece, BlockPos canonical,
                                   Direction facing, int variant,
                                   HearthStructurePlacement.Protection protection, int turns) {
        BlockPos rotated = rotate(canonical, turns);
        HearthStructurePlacement placement = new HearthStructurePlacement(
                piece, rotated, rotate(facing, turns), variant, protection);
        placements.put(rotated, placement);
    }

    private static int turns(long layoutSeed) {
        return Math.floorMod((int) mix(layoutSeed), 4);
    }

    private static BlockPos rotate(BlockPos pos, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case 2 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case 3 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default -> pos;
        };
    }

    private static BlockPos inverseRotate(BlockPos pos, int turns) {
        return rotate(pos, Math.floorMod(4 - turns, 4));
    }

    private static Direction rotate(Direction direction, int turns) {
        Direction result = direction;
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            result = result.getClockWise();
        }
        return result;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
