package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure reconciliation rules shared by the runtime and tests.
 */
public final class HearthReconciliationPolicy {
    public static final int TRACE_PLAN_VERSION = 2;
    public static final int FORMED_PLAN_VERSION = 8;
    public static final int INTACT_PLAN_VERSION = 9;
    public static final int TRACE_FOOTPRINT_RADIUS = 4;
    public static final int FORMED_FOOTPRINT_RADIUS = 5;
    public static final int INTACT_FOOTPRINT_RADIUS = 22;
    public static final int CANDIDATE_SEARCH_RADIUS = 48;
    public static final int CANDIDATE_STEP = 8;
    public static final int MAX_SURFACE_VARIANCE = 2;
    public static final int INTACT_MAX_SURFACE_VARIANCE = 8;

    private static final long SURFACE_SEARCH_SALT = 0x54524143455F3031L;

    private HearthReconciliationPolicy() {
    }

    public static boolean needsTrace(ReturnedHearthSavedData.HearthRecord hearth) {
        return hearth.stage().ordinal() >= ReturnedHearthSavedData.HearthStage.TRACE.ordinal()
                && (hearth.structureStageApplied().ordinal()
                        < ReturnedHearthSavedData.HearthStage.TRACE.ordinal()
                    || !hearth.structurePlaced()
                    || hearth.structurePlanVersion() < TRACE_PLAN_VERSION);
    }

    public static boolean needsReconciliation(ReturnedHearthSavedData.HearthRecord hearth) {
        StructurePlan desired = desiredPlan(hearth);
        if (desired == null) {
            return false;
        }
        return hearth.structureStageApplied().ordinal() < desired.stage().ordinal()
                || !hearth.structurePlaced()
                || hearth.structurePlanVersion() < desired.version();
    }

    public static StructurePlan desiredPlan(ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                && hearth.stage() == ReturnedHearthSavedData.HearthStage.INTACT) {
            return new StructurePlan(INTACT_PLAN_VERSION,
                    ReturnedHearthSavedData.HearthStage.INTACT,
                    INTACT_FOOTPRINT_RADIUS,
                    INTACT_MAX_SURFACE_VARIANCE);
        }
        if (hearth.stage().ordinal() >= ReturnedHearthSavedData.HearthStage.FORMED.ordinal()) {
            return new StructurePlan(FORMED_PLAN_VERSION,
                    ReturnedHearthSavedData.HearthStage.FORMED,
                    FORMED_FOOTPRINT_RADIUS,
                    MAX_SURFACE_VARIANCE);
        }
        if (hearth.stage().ordinal() >= ReturnedHearthSavedData.HearthStage.TRACE.ordinal()) {
            return new StructurePlan(TRACE_PLAN_VERSION,
                    ReturnedHearthSavedData.HearthStage.TRACE,
                    TRACE_FOOTPRINT_RADIUS,
                    MAX_SURFACE_VARIANCE);
        }
        return null;
    }

    public static int resumeCursor(ReturnedHearthSavedData.HearthRecord hearth) {
        StructurePlan desired = desiredPlan(hearth);
        return desired != null && hearth.structurePlanVersion() == desired.version()
                ? Math.max(0, hearth.structureCursor())
                : 0;
    }

    public static List<BlockPos> candidateOffsets(long layoutSeed) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -CANDIDATE_SEARCH_RADIUS; x <= CANDIDATE_SEARCH_RADIUS; x += CANDIDATE_STEP) {
            for (int z = -CANDIDATE_SEARCH_RADIUS; z <= CANDIDATE_SEARCH_RADIUS; z += CANDIDATE_STEP) {
                if (x != 0 || z != 0) {
                    offsets.add(new BlockPos(x, 0, z));
                }
            }
        }

        RandomSource random = RandomSource.create(mix(layoutSeed ^ SURFACE_SEARCH_SALT));
        for (int i = offsets.size() - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            BlockPos current = offsets.get(i);
            offsets.set(i, offsets.get(swap));
            offsets.set(swap, current);
        }
        offsets.add(0, BlockPos.ZERO);
        return List.copyOf(offsets);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record StructurePlan(int version, ReturnedHearthSavedData.HearthStage stage,
                                int footprintRadius, int maxSurfaceVariance) {
    }
}
