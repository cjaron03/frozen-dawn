package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Pure eligibility, anchor, identity, and replacement rules for INTACT residents.
 */
public final class HearthPopulationPolicy {
    public static final long CHECK_INTERVAL_TICKS = 40L;
    public static final long RESPAWN_DELAY_TICKS = 1_200L;
    public static final int RETURNED_HOME_RADIUS = 10;
    public static final int MIMIC_HOME_RADIUS = 8;
    public static final int ARCHITECT_HOME_RADIUS = 12;
    public static final int WATCH_DISTANCE = 28;
    public static final int RETREAT_DISTANCE = 7;

    private static final long VARIANT_SALT = 0x504F50554C415449L;
    private static final List<BlockPos> LOCAL_SPAWN_OFFSETS = List.of(
            BlockPos.ZERO,
            new BlockPos(1, 0, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 0, 1),
            new BlockPos(0, 0, -1),
            new BlockPos(1, 0, 1),
            new BlockPos(-1, 0, 1),
            new BlockPos(1, 0, -1),
            new BlockPos(-1, 0, -1),
            new BlockPos(2, 0, 0),
            new BlockPos(-2, 0, 0),
            new BlockPos(0, 0, 2),
            new BlockPos(0, 0, -2));

    private HearthPopulationPolicy() {
    }

    public static boolean canHostPopulation(ReturnedHearthSavedData.HearthRecord hearth) {
        return hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                && hearth.stage() == ReturnedHearthSavedData.HearthStage.INTACT
                && hearth.structureStageApplied() == ReturnedHearthSavedData.HearthStage.INTACT
                && hearth.surfaceResolved()
                && hearth.structurePlaced();
    }

    public static BlockPos anchorOffset(HearthPopulationRole role, long layoutSeed) {
        List<BlockPos> returned = IntactHearthLayout.returnedAnchors(layoutSeed);
        return switch (role) {
            case RETURNED -> returned.get(0);
            case HUNTER -> returned.get(1);
            case MIMIC -> IntactHearthLayout.mimicAnchor(layoutSeed);
            case ARCHITECT -> IntactHearthLayout.architectAnchor(layoutSeed);
        };
    }

    public static BlockPos anchor(ReturnedHearthSavedData.HearthRecord hearth,
                                  HearthPopulationRole role) {
        return hearth.center().offset(anchorOffset(role, hearth.layoutSeed()));
    }

    public static List<BlockPos> localSpawnOffsets() {
        return LOCAL_SPAWN_OFFSETS;
    }

    public static int textureVariant(long layoutSeed, HearthPopulationRole role) {
        return Math.floorMod((int) mix(layoutSeed ^ VARIANT_SALT ^ role.ordinal()), 5);
    }

    public static int homeRadius(HearthPopulationRole role) {
        return switch (role) {
            case RETURNED, HUNTER -> RETURNED_HOME_RADIUS;
            case MIMIC -> MIMIC_HOME_RADIUS;
            case ARCHITECT -> ARCHITECT_HOME_RADIUS;
        };
    }

    public static boolean isReplacementReady(long respawnAfterGameTime, long gameTime) {
        return respawnAfterGameTime < 0L || gameTime >= respawnAfterGameTime;
    }

    public static boolean isHostileRelationship(
            ReturnedHearthSavedData.HiveRelationship relationship) {
        return relationship == ReturnedHearthSavedData.HiveRelationship.ORSATHAE;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
