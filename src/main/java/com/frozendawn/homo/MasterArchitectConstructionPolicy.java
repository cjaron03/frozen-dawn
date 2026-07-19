package com.frozendawn.homo;

/** Pure limits and geometry rules for bounded Master Architect constructions. */
public final class MasterArchitectConstructionPolicy {
    public static final int WALL_COLUMN_COUNT = 11;
    public static final int WALL_HEIGHT = 3;
    public static final int MAX_ACTIVE_BLOCKS = WALL_COLUMN_COUNT * WALL_HEIGHT;
    public static final int WALL_CAST_TICKS = WALL_COLUMN_COUNT + 1;
    public static final int WALL_LIFETIME_TICKS = 1_200;
    public static final int WALL_COOLDOWN_MIN = 240;
    public static final int WALL_COOLDOWN_VARIANCE = 100;
    public static final double MAX_CAST_RANGE = 24.0D;

    // A three-sided U centered on the Master, open behind it for counterplay.
    private static final int[] NORMAL_OFFSETS = {-1, -1, 0, 0, 1, 1, 2, 2, 2, 2, 2};
    private static final int[] TANGENT_OFFSETS = {-2, 2, -2, 2, -2, 2, -2, 2, -1, 1, 0};

    private MasterArchitectConstructionPolicy() {
    }

    public static boolean canStartWall(
            MasterArchitectCombatPhase phase,
            int cooldown,
            boolean constructionActive,
            double distanceSquared,
            boolean hasLineOfSight) {
        return phase == MasterArchitectCombatPhase.CONSTRUCTION
                && cooldown <= 0
                && !constructionActive
                && hasLineOfSight
                && distanceSquared <= MAX_CAST_RANGE * MAX_CAST_RANGE;
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
