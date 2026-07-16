package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic Stage 3 scene: a sparse village-scale memory built around the
 * existing Formed Hearth core.
 */
public final class IntactHearthLayout {
    private static final int DEEP_SUPPORT_DEPTH = 10;
    private static final int PATH_SUPPORT_DEPTH = 6;
    private static final int CORE_RADIUS = HearthReconciliationPolicy.FORMED_FOOTPRINT_RADIUS;
    private static final int BOUNDARY_MIN_X = -8;
    private static final int BOUNDARY_MAX_X = 10;
    private static final int BOUNDARY_MIN_Z = -8;
    private static final int BOUNDARY_MAX_Z = 10;
    private static final int WARNING_WIDTH = 3;

    private static final List<InteriorBox> OUTER_INTERIORS = List.of(
            new InteriorBox(-3, 3, -21, -14, 0, 3),
            new InteriorBox(-19, -15, -6, -2, 0, 2),
            new InteriorBox(14, 18, -16, -12, 0, 2),
            new InteriorBox(-17, -13, 12, 16, 0, 2));

    private IntactHearthLayout() {
    }

    public static List<HearthStructurePlacement> create(
            long layoutSeed, HearthSelectionPolicy.HearthType type) {
        if (type != HearthSelectionPolicy.HearthType.MAJOR) {
            return FormedHearthLayout.create(layoutSeed, type);
        }

        int turns = turns(layoutSeed);
        Map<BlockPos, HearthStructurePlacement> placements = new LinkedHashMap<>();

        clearDistricts(placements, turns);
        addPaths(placements, turns);
        addCentralPlaza(placements, layoutSeed, turns);
        addSacredHall(placements, turns);
        addReturnedQuarter(placements, turns);
        addMimicHouse(placements, turns);
        addWatcherHouse(placements, turns);
        addArchitectWorkshop(placements, turns);
        addAtmosphereField(placements, layoutSeed, turns);
        addDeadEnds(placements, turns);
        addSnowBoundary(placements, layoutSeed, turns);
        clearLegacyBoundaryMarkers(placements, turns);
        preserveFormedCore(placements, layoutSeed, type);

        return List.copyOf(new ArrayList<>(placements.values()));
    }

    public static boolean isInsideProtectedInterior(long layoutSeed, BlockPos relativePos) {
        if (FormedHearthLayout.isInsideProtectedInterior(layoutSeed, relativePos)) {
            return true;
        }
        BlockPos canonical = inverseRotate(relativePos, turns(layoutSeed));
        return OUTER_INTERIORS.stream().anyMatch(box -> box.contains(canonical));
    }

    public static boolean isInsideMarkedBoundary(long layoutSeed, BlockPos relativePos) {
        BlockPos canonical = inverseRotate(relativePos, turns(layoutSeed));
        return canonical.getX() > BOUNDARY_MIN_X && canonical.getX() < BOUNDARY_MAX_X
                && canonical.getZ() > BOUNDARY_MIN_Z && canonical.getZ() < BOUNDARY_MAX_Z
                && canonical.getY() >= -1 && canonical.getY() <= 4;
    }

    public static boolean isInsideBoundaryWarningBand(long layoutSeed, BlockPos relativePos) {
        BlockPos canonical = inverseRotate(relativePos, turns(layoutSeed));
        if (canonical.getY() < -2 || canonical.getY() > 5
                || isInsideMarkedBoundary(layoutSeed, relativePos)) {
            return false;
        }
        boolean centralWarning = canonical.getX() >= BOUNDARY_MIN_X - WARNING_WIDTH
                && canonical.getX() <= BOUNDARY_MAX_X + WARNING_WIDTH
                && canonical.getZ() >= BOUNDARY_MIN_Z - WARNING_WIDTH
                && canonical.getZ() <= BOUNDARY_MAX_Z + WARNING_WIDTH;
        if (centralWarning) {
            return true;
        }
        return OUTER_INTERIORS.stream().anyMatch(
                box -> box.containsExpanded(canonical, WARNING_WIDTH)
                        && !box.contains(canonical));
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

    public static List<BlockPos> returnedAnchors(long layoutSeed) {
        int turns = turns(layoutSeed);
        return List.of(
                rotate(new BlockPos(-16, 0, -3), turns),
                rotate(new BlockPos(-15, 0, 14), turns));
    }

    public static BlockPos mimicAnchor(long layoutSeed) {
        return rotate(new BlockPos(16, 0, -14), turns(layoutSeed));
    }

    public static BlockPos architectAnchor(long layoutSeed) {
        return rotate(new BlockPos(16, 0, 13), turns(layoutSeed));
    }

    public static BlockPos masterArchitectAnchor(long layoutSeed) {
        return rotate(new BlockPos(0, 0, -10), turns(layoutSeed));
    }

    private static void clearDistricts(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        clearBox(placements, -5, 5, -22, -12, 0, 5, turns);
        clearBox(placements, -21, -13, -8, 0, 0, 4, turns);
        clearBox(placements, 12, 20, -18, -10, 0, 4, turns);
        clearBox(placements, -19, -11, 10, 18, 0, 4, turns);
        clearBox(placements, 11, 21, 9, 18, 0, 4, turns);
        clearBox(placements, 11, 21, -6, 5, 0, 2, turns);
        clearBox(placements, -7, 9, -7, 9, 0, 2, turns);
    }

    private static void addCentralPlaza(
            Map<BlockPos, HearthStructurePlacement> placements, long seed, int turns) {
        for (int x = -6; x <= 8; x++) {
            for (int z = -6; z <= 8; z++) {
                int dx = x - 1;
                int dz = z - 1;
                int distance = Math.abs(dx) + Math.abs(dz);
                if (distance < 7 || distance > 9 || insideFormedCore(x, z)) {
                    continue;
                }
                addFloor(placements, x, z, PATH_SUPPORT_DEPTH,
                        ((x + z) & 1) == 0,
                        HearthStructurePiece.PACKED_ICE_LOWER,
                        HearthStructurePlacement.Protection.HEARTH_RING, turns);
                if ((mix(seed ^ BlockPos.asLong(x, 0, z)) & 3L) == 0L) {
                    putRotated(placements, HearthStructurePiece.SNOW_MARKER,
                            new BlockPos(x, 0, z), Direction.NORTH, 2,
                            HearthStructurePlacement.Protection.HEARTH_RING, turns);
                }
            }
        }

        addRingObject(placements, -6, 1, HearthStructurePiece.COLD_FURNACE,
                Direction.EAST, turns);
        addRingObject(placements, 8, 1, HearthStructurePiece.COLD_FURNACE,
                Direction.WEST, turns);
        addRingObject(placements, 1, -6, HearthStructurePiece.ORSA_CRATE,
                Direction.SOUTH, turns);
        addRingObject(placements, 1, 8, HearthStructurePiece.COLD_CAMPFIRE,
                Direction.NORTH, turns);
    }

    private static void addSacredHall(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        addShelterShell(placements, -4, 4, -22, -13,
                0, -13, Direction.SOUTH, 4, 0x534143524544L, turns);
        putRotated(placements, HearthStructurePiece.SACRED_CHEST,
                new BlockPos(0, 0, -20), Direction.SOUTH, 0,
                HearthStructurePlacement.Protection.CONTAINER, turns);
        putRotated(placements, HearthStructurePiece.COLD_FURNACE,
                new BlockPos(-2, 0, -18), Direction.EAST, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        putRotated(placements, HearthStructurePiece.ORSA_CRATE,
                new BlockPos(2, 0, -18), Direction.WEST, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        addBed(placements, -2, -15, Direction.NORTH, turns);
        addDoor(placements, 0, -21, Direction.EAST, true,
                HearthStructurePlacement.Protection.HEARTH_RING, turns);
    }

    private static void addReturnedQuarter(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        addShelterShell(placements, -20, -14, -7, -1,
                -14, -4, Direction.EAST, 3, 0x52455455524E4544L, turns);
        addBed(placements, -18, -5, Direction.SOUTH, turns);
        putRotated(placements, HearthStructurePiece.COLD_FURNACE,
                new BlockPos(-16, 0, -2), Direction.NORTH, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
    }

    private static void addMimicHouse(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        addShelterShell(placements, 13, 19, -17, -11,
                15, -11, Direction.SOUTH, 3, 0x4D494D49435F484FL, turns);
        addBed(placements, 14, -15, Direction.WEST, turns);
        putRotated(placements, HearthStructurePiece.COLD_FURNACE,
                new BlockPos(18, 0, -15), Direction.WEST, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        addDoor(placements, 17, -14, Direction.NORTH, false,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
    }

    private static void addWatcherHouse(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        addShelterShell(placements, -18, -12, 11, 17,
                -12, 15, Direction.EAST, 3, 0x57415443484552L, turns);
        addBed(placements, -16, 13, Direction.EAST, turns);
        putRotated(placements, HearthStructurePiece.ORSA_CRATE,
                new BlockPos(-14, 0, 12), Direction.SOUTH, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
    }

    private static void addArchitectWorkshop(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        for (int x = 12; x <= 20; x++) {
            for (int z = 10; z <= 17; z++) {
                boolean support = x == 12 || x == 20 || z == 10 || z == 17
                        || ((x + z) & 3) == 0;
                addFloor(placements, x, z, DEEP_SUPPORT_DEPTH, support,
                        HearthStructurePiece.FROZEN_STONE_BRICKS,
                        HearthStructurePlacement.Protection.STRUCTURE, turns);

                boolean rearWall = z == 17;
                boolean sideWall = x == 20 && z >= 12;
                boolean post = (x == 12 || x == 20) && (z == 10 || z == 17);
                if (rearWall || sideWall || post) {
                    for (int y = 0; y <= 2; y++) {
                        HearthStructurePiece piece = ((x + z + y) & 1) == 0
                                ? HearthStructurePiece.FROZEN_STONE_BRICKS
                                : HearthStructurePiece.FROZEN_PLANKS;
                        putRotated(placements, piece, new BlockPos(x, y, z),
                                Direction.NORTH, 0,
                                HearthStructurePlacement.Protection.STRUCTURE, turns);
                    }
                }
                if (z >= 14 && (x + z) % 3 != 0) {
                    putRotated(placements, HearthStructurePiece.FROZEN_PLANKS,
                            new BlockPos(x, 3, z), Direction.NORTH, 0,
                            HearthStructurePlacement.Protection.STRUCTURE, turns);
                }
            }
        }
        putRotated(placements, HearthStructurePiece.COLD_FURNACE,
                new BlockPos(18, 0, 15), Direction.WEST, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        putRotated(placements, HearthStructurePiece.ORSA_CRATE,
                new BlockPos(15, 0, 16), Direction.SOUTH, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        putRotated(placements, HearthStructurePiece.ORSA_CRATE,
                new BlockPos(13, 0, 12), Direction.EAST, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
    }

    private static void addAtmosphereField(
            Map<BlockPos, HearthStructurePlacement> placements, long seed, int turns) {
        for (int x = 13; x <= 20; x += 2) {
            for (int z = -5; z <= 4; z += 3) {
                addFloor(placements, x, z, PATH_SUPPORT_DEPTH, true,
                        HearthStructurePiece.PACKED_ICE_LOWER,
                        HearthStructurePlacement.Protection.STRUCTURE, turns);
                if ((mix(seed ^ BlockPos.asLong(x, 0, z)) & 3L) != 0L) {
                    putRotated(placements, HearthStructurePiece.FROZEN_ATMOSPHERE,
                            new BlockPos(x, 0, z), Direction.NORTH, 0,
                            HearthStructurePlacement.Protection.STRUCTURE, turns);
                } else {
                    putRotated(placements, HearthStructurePiece.SNOW_MARKER,
                            new BlockPos(x, 0, z), Direction.NORTH, 3,
                            HearthStructurePlacement.Protection.STRUCTURE, turns);
                }
            }
        }
    }

    private static void addDeadEnds(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        addDoorWithFoundation(placements, -2, 21, Direction.NORTH, false,
                HearthStructurePlacement.Protection.HEARTH_RING, turns);
        addDoorWithFoundation(placements, 21, 7, Direction.WEST, true,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        addDoorWithFoundation(placements, -21, 6, Direction.SOUTH, false,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
    }

    private static void addPaths(
            Map<BlockPos, HearthStructurePlacement> placements, int turns) {
        addPath(placements, 0, -5, 0, -13, turns);
        addPath(placements, -5, 1, -14, -4, turns);
        addPath(placements, 6, -3, 15, -11, turns);
        addPath(placements, -5, 6, -12, 15, turns);
        addPath(placements, 7, 6, 13, 12, turns);
        addPath(placements, -2, 6, -2, 21, turns);
        addPath(placements, 7, 4, 21, 7, turns);
        addPath(placements, -6, 4, -21, 6, turns);
    }

    private static void addPath(
            Map<BlockPos, HearthStructurePlacement> placements,
            int startX, int startZ, int endX, int endZ, int turns) {
        int x = startX;
        int z = startZ;
        while (x != endX) {
            addPathCell(placements, x, z, turns);
            x += Integer.compare(endX, x);
        }
        while (z != endZ) {
            addPathCell(placements, x, z, turns);
            z += Integer.compare(endZ, z);
        }
        addPathCell(placements, endX, endZ, turns);
    }

    private static void addPathCell(
            Map<BlockPos, HearthStructurePlacement> placements, int x, int z, int turns) {
        if (insideFormedCore(x, z)) {
            return;
        }
        for (int width = 0; width <= 1; width++) {
            int pathX = x + width;
            if (Math.abs(pathX) > HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS
                    || Math.abs(z) > HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS) {
                continue;
            }
            putRotated(placements, HearthStructurePiece.CLEAR_SETTLEMENT,
                    new BlockPos(pathX, 0, z), Direction.NORTH, 0,
                    HearthStructurePlacement.Protection.NONE, turns);
            putRotated(placements, HearthStructurePiece.CLEAR_SETTLEMENT,
                    new BlockPos(pathX, 1, z), Direction.NORTH, 0,
                    HearthStructurePlacement.Protection.NONE, turns);
            addFloor(placements, pathX, z, PATH_SUPPORT_DEPTH,
                    Math.floorMod(pathX + z, 3) == 0,
                    HearthStructurePiece.PACKED_ICE_LOWER,
                    HearthStructurePlacement.Protection.NONE, turns);
        }
    }

    private static void addSnowBoundary(
            Map<BlockPos, HearthStructurePlacement> placements, long seed, int turns) {
        for (int axis = -20; axis <= 20; axis += 4) {
            addSnowMarker(placements, axis, -22, seed, turns);
            addSnowMarker(placements, axis, 22, seed, turns);
            if (axis > -20 && axis < 20) {
                addSnowMarker(placements, -22, axis, seed, turns);
                addSnowMarker(placements, 22, axis, seed, turns);
            }
        }
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
        boolean northOrSouth = (z == BOUNDARY_MIN_Z || z == BOUNDARY_MAX_Z)
                && x >= 0 && x <= 2;
        boolean eastOrWest = (x == BOUNDARY_MIN_X || x == BOUNDARY_MAX_X)
                && z >= 0 && z <= 2;
        return northOrSouth || eastOrWest;
    }

    private static void addShelterShell(
            Map<BlockPos, HearthStructurePlacement> placements,
            int minX, int maxX, int minZ, int maxZ,
            int doorX, int doorZ, Direction doorFacing,
            int roofY, long styleSalt, int turns) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean perimeter = x == minX || x == maxX || z == minZ || z == maxZ;
                boolean doorway = x == doorX && z == doorZ;
                boolean support = perimeter || Math.floorMod(x + z, 2) == 0;
                addFloor(placements, x, z, DEEP_SUPPORT_DEPTH, support,
                        HearthStructurePiece.FROZEN_PLANKS,
                        HearthStructurePlacement.Protection.STRUCTURE, turns);

                if (perimeter && !doorway) {
                    for (int y = 0; y < roofY; y++) {
                        HearthStructurePiece wall = ((x + z + y) & 3) == 0
                                ? HearthStructurePiece.FROZEN_PLANKS
                                : HearthStructurePiece.FROZEN_STONE_BRICKS;
                        putRotated(placements, wall, new BlockPos(x, y, z),
                                Direction.NORTH, 0,
                                HearthStructurePlacement.Protection.STRUCTURE, turns);
                    }
                }

                boolean roofEdge = perimeter;
                boolean rememberedGap = (mix(styleSalt ^ BlockPos.asLong(x, roofY, z)) & 7L) == 0L;
                if (roofEdge || !rememberedGap) {
                    HearthStructurePiece roof = ((x + z) & 2) == 0
                            ? HearthStructurePiece.FROZEN_PLANKS
                            : HearthStructurePiece.FROZEN_STONE_BRICKS;
                    putRotated(placements, roof, new BlockPos(x, roofY, z),
                            Direction.NORTH, 0,
                            HearthStructurePlacement.Protection.STRUCTURE, turns);
                }
            }
        }
        addDoor(placements, doorX, doorZ, doorFacing, false,
                HearthStructurePlacement.Protection.DOOR, turns);
    }

    private static void addRingObject(
            Map<BlockPos, HearthStructurePlacement> placements,
            int x, int z, HearthStructurePiece piece, Direction facing, int turns) {
        addFloor(placements, x, z, PATH_SUPPORT_DEPTH, true,
                HearthStructurePiece.PACKED_ICE_LOWER,
                HearthStructurePlacement.Protection.HEARTH_RING, turns);
        putRotated(placements, piece, new BlockPos(x, 0, z), facing, 0,
                HearthStructurePlacement.Protection.HEARTH_RING, turns);
    }

    private static void addSnowMarker(
            Map<BlockPos, HearthStructurePlacement> placements,
            int x, int z, long seed, int turns) {
        int layers = 2 + Math.floorMod((int) mix(seed ^ BlockPos.asLong(x, 0, z)), 6);
        addFloor(placements, x, z, PATH_SUPPORT_DEPTH, true,
                HearthStructurePiece.PACKED_ICE_LOWER,
                HearthStructurePlacement.Protection.NONE, turns);
        putRotated(placements, HearthStructurePiece.SNOW_MARKER,
                new BlockPos(x, 0, z), Direction.NORTH, layers,
                HearthStructurePlacement.Protection.NONE, turns);
    }

    private static void addDoorWithFoundation(
            Map<BlockPos, HearthStructurePlacement> placements,
            int x, int z, Direction facing, boolean rightHinge,
            HearthStructurePlacement.Protection protection, int turns) {
        addFloor(placements, x, z, PATH_SUPPORT_DEPTH, true,
                HearthStructurePiece.PACKED_ICE_LOWER, protection, turns);
        addDoor(placements, x, z, facing, rightHinge, protection, turns);
    }

    private static void addDoor(
            Map<BlockPos, HearthStructurePlacement> placements,
            int x, int z, Direction facing, boolean rightHinge,
            HearthStructurePlacement.Protection protection, int turns) {
        putRotated(placements, HearthStructurePiece.DOOR_LOWER,
                new BlockPos(x, 0, z), facing, rightHinge ? 1 : 0, protection, turns);
        putRotated(placements, HearthStructurePiece.DOOR_UPPER,
                new BlockPos(x, 1, z), facing, rightHinge ? 1 : 0, protection, turns);
    }

    private static void addBed(
            Map<BlockPos, HearthStructurePlacement> placements,
            int x, int z, Direction facing, int turns) {
        putRotated(placements, HearthStructurePiece.BED_FOOT,
                new BlockPos(x, 0, z), facing, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        putRotated(placements, HearthStructurePiece.BED_HEAD,
                new BlockPos(x, 0, z).relative(facing), facing, 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
    }

    private static void addFloor(
            Map<BlockPos, HearthStructurePlacement> placements,
            int x, int z, int supportDepth, boolean deepSupport,
            HearthStructurePiece surfacePiece,
            HearthStructurePlacement.Protection protection, int turns) {
        if (deepSupport) {
            for (int depth = 2; depth <= supportDepth; depth++) {
                putRotated(placements, HearthStructurePiece.FOUNDATION_SUPPORT,
                        new BlockPos(x, -depth, z), Direction.NORTH, 0, protection, turns);
            }
        }
        putRotated(placements, surfacePiece,
                new BlockPos(x, -1, z), Direction.NORTH, 0, protection, turns);
    }

    private static void clearBox(
            Map<BlockPos, HearthStructurePlacement> placements,
            int minX, int maxX, int minZ, int maxZ,
            int minY, int maxY, int turns) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    putRotated(placements, HearthStructurePiece.CLEAR_SETTLEMENT,
                            new BlockPos(x, y, z), Direction.NORTH, 0,
                            HearthStructurePlacement.Protection.NONE, turns);
                }
            }
        }
    }

    private static void preserveFormedCore(
            Map<BlockPos, HearthStructurePlacement> placements,
            long layoutSeed, HearthSelectionPolicy.HearthType type) {
        for (HearthStructurePlacement placement : FormedHearthLayout.create(layoutSeed, type)) {
            placements.put(placement.offset(), placement);
        }
    }

    private static boolean insideFormedCore(int x, int z) {
        return Math.abs(x) <= CORE_RADIUS && Math.abs(z) <= CORE_RADIUS;
    }

    private static void putRotated(
            Map<BlockPos, HearthStructurePlacement> placements,
            HearthStructurePiece piece, BlockPos canonical,
            Direction facing, int variant,
            HearthStructurePlacement.Protection protection, int turns) {
        BlockPos rotated = rotate(canonical, turns);
        placements.put(rotated, new HearthStructurePlacement(
                piece, rotated, rotate(facing, turns), variant, protection));
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

    private record InteriorBox(
            int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ
                    && pos.getY() >= minY && pos.getY() <= maxY;
        }

        private boolean containsExpanded(BlockPos pos, int amount) {
            return pos.getX() >= minX - amount && pos.getX() <= maxX + amount
                    && pos.getZ() >= minZ - amount && pos.getZ() <= maxZ + amount
                    && pos.getY() >= minY - 1 && pos.getY() <= maxY + 1;
        }
    }
}
