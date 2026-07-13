package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves exact protected Hearth positions without broad proximity guesses.
 */
public final class HearthProtectionPolicy {
    private static final int ENVIRONMENT_MIN_Y_OFFSET = -5;
    private static final int ENVIRONMENT_MAX_Y_OFFSET = 4;

    private HearthProtectionPolicy() {
    }

    public static Optional<ProtectedTarget> protectedTargetAt(
            ReturnedHearthSavedData data, BlockPos worldPos) {
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            if (!hearth.surfaceResolved() || !hearth.structurePlaced()) {
                continue;
            }
            BlockPos relative = worldPos.subtract(hearth.center());
            HearthStructurePlacement.Protection protection = protectionAt(hearth, relative);
            if (protection != HearthStructurePlacement.Protection.NONE) {
                return Optional.of(new ProtectedTarget(hearth.id(), protection));
            }
        }
        return Optional.empty();
    }

    public static Optional<UUID> protectedInteriorAt(
            ReturnedHearthSavedData data, BlockPos worldPos) {
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            if (!hearth.surfaceResolved() || !hearth.structurePlaced()
                    || hearth.structureStageApplied().ordinal()
                    < ReturnedHearthSavedData.HearthStage.FORMED.ordinal()) {
                continue;
            }
            BlockPos relative = worldPos.subtract(hearth.center());
            if (FormedHearthLayout.isInsideProtectedInterior(
                    hearth.layoutSeed(), relative)) {
                return Optional.of(hearth.id());
            }
        }
        return Optional.empty();
    }

    /**
     * Keeps transient apocalypse deposits from undermining a reconciled Hearth.
     */
    public static boolean isEnvironmentalMutationProtected(
            ReturnedHearthSavedData data, BlockPos worldPos) {
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            if (!hearth.surfaceResolved()
                    || hearth.stage().ordinal()
                    < ReturnedHearthSavedData.HearthStage.TRACE.ordinal()) {
                continue;
            }

            BlockPos center = hearth.center();
            int radius = hearth.stage().ordinal()
                    >= ReturnedHearthSavedData.HearthStage.FORMED.ordinal()
                    ? HearthReconciliationPolicy.FORMED_FOOTPRINT_RADIUS
                    : HearthReconciliationPolicy.TRACE_FOOTPRINT_RADIUS;
            int dx = Math.abs(worldPos.getX() - center.getX());
            int dz = Math.abs(worldPos.getZ() - center.getZ());
            int dy = worldPos.getY() - center.getY();
            if (dx <= radius && dz <= radius
                    && dy >= ENVIRONMENT_MIN_Y_OFFSET
                    && dy <= ENVIRONMENT_MAX_Y_OFFSET) {
                return true;
            }
        }
        return false;
    }

    private static HearthStructurePlacement.Protection protectionAt(
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos relative) {
        List<HearthStructurePlacement> layout = hearth.structureStageApplied().ordinal()
                >= ReturnedHearthSavedData.HearthStage.FORMED.ordinal()
                ? FormedHearthLayout.create(hearth.layoutSeed(), hearth.type())
                : TraceHearthLayout.create(hearth.layoutSeed(), hearth.type());
        for (HearthStructurePlacement placement : layout) {
            if (placement.offset().equals(relative)) {
                return placement.protection();
            }
        }
        return HearthStructurePlacement.Protection.NONE;
    }

    public record ProtectedTarget(UUID hearthId,
                                  HearthStructurePlacement.Protection protection) {
    }
}
