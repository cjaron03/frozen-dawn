package com.frozendawn.homo;

/** Pure limits and geometry rules for bounded Master Architect constructions. */
public final class MasterArchitectConstructionPolicy {
    public static final int WALL_COLUMN_COUNT = 11;
    public static final int WALL_HEIGHT = 3;
    public static final int MAX_ACTIVE_BLOCKS = WALL_COLUMN_COUNT * WALL_HEIGHT;
    public static final int WALL_CAST_TICKS = WALL_COLUMN_COUNT + 1;
    public static final int LIVE_BLOCK_BUDGET = 64;
    public static final int OPENING_LIFETIME_TICKS = 1_200;
    public static final int STRUCTURE_LIFETIME_TICKS = 900;
    public static final int OPENING_COOLDOWN_TICKS = 120;
    public static final int ONGOING_COOLDOWN_MIN = 160;
    public static final int ONGOING_COOLDOWN_VARIANCE = 80;
    public static final int COVER_MEMORY_TICKS = 120;
    public static final int VANTAGE_SEEK_TICKS = 100;
    public static final int SEAM_STAGGER_TICKS = 30;
    public static final double SEAM_STAGGER_RANGE = 5.0D;
    public static final double MAX_CAST_RANGE = 24.0D;

    // A three-sided U centered on the Master, open behind it for counterplay.
    private static final int[] NORMAL_OFFSETS = {-1, -1, 0, 0, 1, 1, 2, 2, 2, 2, 2};
    private static final int[] TANGENT_OFFSETS = {-2, 2, -2, 2, -2, 2, -2, 2, -1, 1, 0};

    private MasterArchitectConstructionPolicy() {
    }

    public static boolean canStartConstruction(
            MasterArchitectCombatPhase phase,
            int cooldown,
            boolean buildActive,
            double distanceSquared) {
        return phase == MasterArchitectCombatPhase.CONSTRUCTION
                && cooldown <= 0
                && !buildActive
                && distanceSquared <= MAX_CAST_RANGE * MAX_CAST_RANGE;
    }

    public static boolean canReserve(int liveBlocks, int plannedBlocks) {
        return liveBlocks >= 0
                && plannedBlocks > 0
                && liveBlocks + plannedBlocks <= LIVE_BLOCK_BUDGET;
    }

    public static boolean shouldStaggerMaster(double distanceSquared) {
        return distanceSquared
                <= SEAM_STAGGER_RANGE * SEAM_STAGGER_RANGE;
    }

    public static boolean shouldLeaveRubble(
            int blockIndex, int blockY, int minimumY, boolean seam) {
        return !seam && blockY == minimumY && Math.floorMod(blockIndex, 4) == 0;
    }

    public static int columnIndexAtTick(int actionTicks) {
        int index = actionTicks - 1;
        return index >= 0 && index < WALL_COLUMN_COUNT ? index : -1;
    }

    public static int columnNormalOffset(int index) {
        return index >= 0 && index < NORMAL_OFFSETS.length
                ? NORMAL_OFFSETS[index]
                : 0;
    }

    public static int columnTangentOffset(int index) {
        return index >= 0 && index < TANGENT_OFFSETS.length
                ? TANGENT_OFFSETS[index]
                : 0;
    }

    public static boolean isWeakSeamColumn(int index) {
        return index == WALL_COLUMN_COUNT - 1;
    }

    public static boolean shouldCollapseForMissingSeam(
            int trackedSeamBlocks, int intactSeamBlocks) {
        return trackedSeamBlocks > 0 && intactSeamBlocks < trackedSeamBlocks;
    }

    public static WallAxes wallAxes(double towardMasterX, double towardMasterZ) {
        if (Math.abs(towardMasterX) >= Math.abs(towardMasterZ)) {
            int normalX = towardMasterX >= 0.0D ? 1 : -1;
            return new WallAxes(normalX, 0, 0, 1);
        }
        int normalZ = towardMasterZ >= 0.0D ? 1 : -1;
        return new WallAxes(0, normalZ, 1, 0);
    }

    public record WallAxes(int normalX, int normalZ, int tangentX, int tangentZ) {
    }
}
