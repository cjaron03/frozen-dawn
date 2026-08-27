package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/** Pure cue selection for conduct-triggering Hearth geometry. */
public final class HearthBoundaryParticlePolicy {
    private HearthBoundaryParticlePolicy() {
    }

    public static List<ParticleCue> cuesFor(ReturnedHearthSavedData.HearthRecord hearth) {
        if (!hearth.surfaceResolved() || !hearth.structurePlaced()
                || hearth.structureStageApplied().ordinal()
                < ReturnedHearthSavedData.HearthStage.FORMED.ordinal()) {
            return List.of();
        }

        List<ParticleCue> cues = new ArrayList<>();
        boundaryOffsets(hearth).forEach(offset -> cues.add(
                new ParticleCue(offset, CueType.BOUNDARY, Direction.NORTH)));
        for (HearthStructurePlacement placement : layoutFor(hearth)) {
            if (placement.protection() == HearthStructurePlacement.Protection.NONE) {
                continue;
            }
            if (placement.piece() == HearthStructurePiece.DOOR_LOWER) {
                cues.add(new ParticleCue(
                        placement.offset(), CueType.DOOR, placement.facing()));
            } else if (isInteractionCuePiece(placement.piece())) {
                cues.add(new ParticleCue(
                        placement.offset(), CueType.INTERACTION, placement.facing()));
            }
        }
        return List.copyOf(cues);
    }

    static boolean isInteractionCuePiece(HearthStructurePiece piece) {
        return switch (piece) {
            case PROTECTED_CHEST, SACRED_CHEST, ORSA_CRATE, COLD_FURNACE -> true;
            default -> false;
        };
    }

    private static List<BlockPos> boundaryOffsets(
            ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                && hearth.structureStageApplied() == ReturnedHearthSavedData.HearthStage.INTACT) {
            return IntactHearthLayout.boundaryParticleOffsets(hearth.layoutSeed());
        }
        return FormedHearthLayout.boundaryParticleOffsets(hearth.layoutSeed());
    }

    private static List<HearthStructurePlacement> layoutFor(
            ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                && hearth.structureStageApplied() == ReturnedHearthSavedData.HearthStage.INTACT) {
            return IntactHearthLayout.create(hearth.layoutSeed(), hearth.type());
        }
        return FormedHearthLayout.create(hearth.layoutSeed(), hearth.type());
    }

    public enum CueType {
        BOUNDARY,
        DOOR,
        INTERACTION
    }

    public record ParticleCue(BlockPos offset, CueType type, Direction facing) {
    }
}
