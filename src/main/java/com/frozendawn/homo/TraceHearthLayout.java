package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic Stage 1 scene: remembered domestic objects without a shelter.
 */
public final class TraceHearthLayout {
    private TraceHearthLayout() {
    }

    public static List<HearthStructurePlacement> create(
            long layoutSeed, HearthSelectionPolicy.HearthType type) {
        int turns = Math.floorMod((int) mix(layoutSeed), 4);
        Map<BlockPos, HearthStructurePlacement> placements = new LinkedHashMap<>();

        for (int axis = -4; axis <= 4; axis += 2) {
            addBoundary(placements, axis, -4, layoutSeed, turns);
            addBoundary(placements, axis, 4, layoutSeed, turns);
            if (axis > -4 && axis < 4) {
                addBoundary(placements, -4, axis, layoutSeed, turns);
                addBoundary(placements, 4, axis, layoutSeed, turns);
            }
        }

        addFoundation(placements, 0, 0, turns);
        put(placements, HearthStructurePiece.COLD_CAMPFIRE, 0, 0, 0,
                rotate(Direction.NORTH, turns), 0,
                HearthStructurePlacement.Protection.HEARTH_RING, turns);
        put(placements, HearthStructurePiece.CLEAR_LEGACY, 0, 1, 0,
                Direction.NORTH, 0, HearthStructurePlacement.Protection.NONE, turns);

        addCrate(placements, 0, -2, Direction.SOUTH, turns);
        addCrate(placements, 2, 0, Direction.WEST, turns);
        addCrate(placements, -2, 0, Direction.EAST, turns);

        int doorCount = type == HearthSelectionPolicy.HearthType.MAJOR
                || (mix(layoutSeed ^ 0x444F4F525F434E54L) & 1L) == 0L ? 3 : 2;
        addDoor(placements, 3, -2, Direction.WEST, false, turns);
        addDoor(placements, -3, 1, Direction.SOUTH, true, turns);
        if (doorCount == 3) {
            addDoor(placements, 1, 3, Direction.NORTH, false, turns);
        }

        addFoundation(placements, -1, 2, turns);
        addFoundation(placements, -1, 3, turns);
        put(placements, HearthStructurePiece.BED_FOOT, -1, 0, 2,
                rotate(Direction.SOUTH, turns), 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        put(placements, HearthStructurePiece.BED_HEAD, -1, 0, 3,
                rotate(Direction.SOUTH, turns), 0,
                HearthStructurePlacement.Protection.STRUCTURE, turns);
        put(placements, HearthStructurePiece.CLEAR_LEGACY, -1, 1, 2,
                Direction.NORTH, 0, HearthStructurePlacement.Protection.NONE, turns);
        put(placements, HearthStructurePiece.CLEAR_LEGACY, -1, 1, 3,
                Direction.NORTH, 0, HearthStructurePlacement.Protection.NONE, turns);

        return List.copyOf(new ArrayList<>(placements.values()));
    }

    private static void addBoundary(Map<BlockPos, HearthStructurePlacement> placements, int x, int z,
                                    long seed, int turns) {
        addFoundation(placements, x, z, turns);
        int layers = 2 + Math.floorMod((int) mix(seed ^ BlockPos.asLong(x, 0, z)), 4);
        put(placements, HearthStructurePiece.SNOW_MARKER, x, 0, z,
                Direction.NORTH, layers, HearthStructurePlacement.Protection.NONE, turns);
        put(placements, HearthStructurePiece.CLEAR_LEGACY, x, 1, z,
                Direction.NORTH, 0, HearthStructurePlacement.Protection.NONE, turns);
    }

    private static void addCrate(Map<BlockPos, HearthStructurePlacement> placements, int x, int z,
                                 Direction facing, int turns) {
        addFoundation(placements, x, z, turns);
        put(placements, HearthStructurePiece.ORSA_CRATE, x, 0, z,
                rotate(facing, turns), 0,
                HearthStructurePlacement.Protection.HEARTH_RING, turns);
        put(placements, HearthStructurePiece.CLEAR_LEGACY, x, 1, z,
                Direction.NORTH, 0, HearthStructurePlacement.Protection.NONE, turns);
    }

    private static void addDoor(Map<BlockPos, HearthStructurePlacement> placements, int x, int z,
                                Direction facing, boolean rightHinge, int turns) {
        addFoundation(placements, x, z, turns);
        Direction rotatedFacing = rotate(facing, turns);
        int hinge = rightHinge ? 1 : 0;
        put(placements, HearthStructurePiece.DOOR_LOWER, x, 0, z,
                rotatedFacing, hinge, HearthStructurePlacement.Protection.NONE, turns);
        put(placements, HearthStructurePiece.DOOR_UPPER, x, 1, z,
                rotatedFacing, hinge, HearthStructurePlacement.Protection.NONE, turns);
        put(placements, HearthStructurePiece.CLEAR_LEGACY, x, 2, z,
                Direction.NORTH, 0, HearthStructurePlacement.Protection.NONE, turns);
    }

    private static void addFoundation(Map<BlockPos, HearthStructurePlacement> placements,
                                      int x, int z, int turns) {
        put(placements, HearthStructurePiece.PACKED_ICE_LOWER, x, -1, z,
                Direction.NORTH, 0, HearthStructurePlacement.Protection.NONE, turns);
        put(placements, HearthStructurePiece.CLEAR_PLATFORM, x, 0, z,
                Direction.NORTH, 0, HearthStructurePlacement.Protection.NONE, turns);
    }

    private static void put(Map<BlockPos, HearthStructurePlacement> placements,
                            HearthStructurePiece piece, int x, int y, int z,
                            Direction facing, int variant,
                            HearthStructurePlacement.Protection protection, int turns) {
        BlockPos rotated = rotate(new BlockPos(x, y, z), turns);
        HearthStructurePlacement placement = new HearthStructurePlacement(
                piece, rotated, facing, variant, protection);
        HearthStructurePlacement existing = placements.get(rotated);
        if (existing != null && existing.piece() == HearthStructurePiece.CLEAR_PLATFORM
                && piece != HearthStructurePiece.CLEAR_PLATFORM) {
            placements.put(rotated, placement);
            return;
        }
        existing = placements.putIfAbsent(rotated, placement);
        if (existing != null && existing.piece() != piece) {
            throw new IllegalStateException("Trace Hearth layout collision at " + rotated
                    + ": " + existing.piece() + " vs " + piece);
        }
    }

    private static BlockPos rotate(BlockPos pos, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case 2 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case 3 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default -> pos;
        };
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
