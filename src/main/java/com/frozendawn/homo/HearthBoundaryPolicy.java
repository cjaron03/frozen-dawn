package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** Pure geometry for readable warning and protected Hearth threshold zones. */
public final class HearthBoundaryPolicy {
    private HearthBoundaryPolicy() {
    }

    public static Optional<BoundaryContact> contactAt(
            ReturnedHearthSavedData data, BlockPos worldPos) {
        return data.hearths().stream()
                .filter(HearthBoundaryPolicy::hasPhysicalBoundary)
                .map(hearth -> new BoundaryContact(
                        hearth.id(), zoneFor(hearth, worldPos),
                        hearth.center().distSqr(worldPos)))
                .filter(contact -> contact.zone() != Zone.OUTSIDE)
                .min(Comparator
                        .comparingInt((BoundaryContact contact) -> contact.zone().priority())
                        .reversed()
                        .thenComparingDouble(BoundaryContact::distanceSquared));
    }

    public static Zone zoneFor(
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos worldPos) {
        if (!hasPhysicalBoundary(hearth)) {
            return Zone.OUTSIDE;
        }

        BlockPos relative = worldPos.subtract(hearth.center());
        boolean intactMajor = hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                && hearth.structureStageApplied() == ReturnedHearthSavedData.HearthStage.INTACT;
        if (intactMajor) {
            if (IntactHearthLayout.isInsideMarkedBoundary(hearth.layoutSeed(), relative)
                    || IntactHearthLayout.isInsideProtectedInterior(
                            hearth.layoutSeed(), relative)) {
                return Zone.PROTECTED;
            }
            if (IntactHearthLayout.isInsideBoundaryWarningBand(
                    hearth.layoutSeed(), relative)) {
                return Zone.WARNING;
            }
            return Zone.OUTSIDE;
        }

        if (FormedHearthLayout.isInsideMarkedBoundary(hearth.layoutSeed(), relative)
                || FormedHearthLayout.isInsideProtectedInterior(
                        hearth.layoutSeed(), relative)) {
            return Zone.PROTECTED;
        }
        return FormedHearthLayout.isInsideBoundaryWarningBand(
                hearth.layoutSeed(), relative) ? Zone.WARNING : Zone.OUTSIDE;
    }

    private static boolean hasPhysicalBoundary(
            ReturnedHearthSavedData.HearthRecord hearth) {
        return hearth.surfaceResolved()
                && hearth.structurePlaced()
                && hearth.structureStageApplied().ordinal()
                >= ReturnedHearthSavedData.HearthStage.FORMED.ordinal();
    }

    public enum Zone {
        OUTSIDE(0),
        WARNING(1),
        PROTECTED(2);

        private final int priority;

        Zone(int priority) {
            this.priority = priority;
        }

        private int priority() {
            return priority;
        }
    }

    public record BoundaryContact(UUID hearthId, Zone zone, double distanceSquared) {
    }
}
