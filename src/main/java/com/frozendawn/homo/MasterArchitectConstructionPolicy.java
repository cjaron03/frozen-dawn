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
    public static final int OPENING_COOLDOWN_TICKS = 60;
    public static final int ONGOING_COOLDOWN_MIN = 90;
    public static final int ONGOING_COOLDOWN_VARIANCE = 50;
    public static final int COVER_MEMORY_TICKS = 120;
    public static final int VANTAGE_SEEK_TICKS = 100;
    public static final int SEAM_STAGGER_TICKS = 30;
    public static final int CHOREOGRAPHY_TICKS = 8;
    public static final int MAX_TRAVELING_FRAGMENTS = 12;
    public static final int ORBITING_FRAGMENT_COUNT = 4;
    public static final int STATIONARY_TRAP_TICKS = 30;
    public static final int MIN_OPENING_COLUMNS = 7;
    public static final int MIN_WALL_COLUMNS = 3;
    public static final int MIN_ENCLOSURE_COLUMNS = 5;
    public static final int MIN_HEATER_COLUMNS = 3;
    public static final int SHELTER_HEAL_MAX_TICKS = 100;
    public static final int SHELTER_HEAL_COOLDOWN_TICKS = 160;
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

    public static boolean hasViableStructure(
            int viableSteps,
            boolean hasSeam,
            int minimumSteps) {
        return viableSteps >= minimumSteps && hasSeam;
    }

    public static int shelterHealGraceTicks(String presetName) {
        if ("brutal".equalsIgnoreCase(presetName)) {
            return 55;
        }
        if ("cinematic".equalsIgnoreCase(presetName)) {
            return 90;
        }
        return 70;
    }

    public static float shelterHealFractionPerSecond(String presetName) {
        if ("brutal".equalsIgnoreCase(presetName)) {
            return 0.03F;
        }
        if ("cinematic".equalsIgnoreCase(presetName)) {
            return 0.006F;
        }
        return 0.0225F;
    }

    public static float shelterHealPerTick(float maxHealth, String presetName) {
        return Math.max(0.0F, maxHealth)
                * shelterHealFractionPerSecond(presetName) / 20.0F;
    }

    public static float shelterHealCeiling(
            MasterArchitectCombatPhase phase,
            float maxHealth) {
        float fraction = switch (phase) {
            case KIT -> 1.0F;
            case CONSTRUCTION -> 0.75F;
            case TETHER -> 0.50F;
            case ASCENT -> 0.30F;
            case FLOOD -> 0.10F;
        };
        return Math.max(0.0F, maxHealth) * fraction;
    }

    public static boolean shouldStaggerMaster(double distanceSquared) {
        return distanceSquared
                <= SEAM_STAGGER_RANGE * SEAM_STAGGER_RANGE;
    }

    public static boolean shouldLeaveRubble(
            int blockIndex, int blockY, int minimumY, boolean seam) {
        return !seam && blockY == minimumY && Math.floorMod(blockIndex, 4) == 0;
    }

    public static ConstructionIntent chooseIntent(
            boolean recentCover,
            boolean activePlayerHeater,
            int stationaryTicks,
            double distanceSquared,
            int fallbackCursor) {
        if (recentCover) {
            return ConstructionIntent.COVER_DENIAL;
        }
        if (activePlayerHeater) {
            return ConstructionIntent.HEATER_BURIAL;
        }
        if (stationaryTicks >= STATIONARY_TRAP_TICKS) {
            return ConstructionIntent.ENCLOSURE;
        }
        if (distanceSquared >= 10.0D * 10.0D) {
            return ConstructionIntent.VANTAGE;
        }
        return switch (Math.floorMod(fallbackCursor, 3)) {
            case 0 -> ConstructionIntent.VANTAGE;
            case 1 -> ConstructionIntent.ENCLOSURE;
            default -> ConstructionIntent.HEATER_BURIAL;
        };
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

    public enum ConstructionIntent {
        COVER_DENIAL,
        VANTAGE,
        ENCLOSURE,
        HEATER_BURIAL
    }
}
