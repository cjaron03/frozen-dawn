package com.frozendawn.client;

import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.init.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

final class SurveyorLensTargetCollector {

    private SurveyorLensTargetCollector() {
    }

    static List<ColdAnchor> collectColdAnchors(Minecraft mc, int maxColdFields) {
        List<ColdAnchor> anchors = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int dy = -12; dy <= 12; dy++) {
            int y = playerPos.getY() + dy;
            if (y < mc.level.getMinBuildHeight() || y >= mc.level.getMaxBuildHeight()) {
                continue;
            }

            for (int dz = -24; dz <= 24; dz++) {
                for (int dx = -24; dx <= 24; dx++) {
                    mutablePos.set(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    if (!mc.level.hasChunkAt(mutablePos)) {
                        continue;
                    }

                    var state = mc.level.getBlockState(mutablePos);
                    if (!state.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                        continue;
                    }

                    float strength = switch (state.getValue(AcheroniteCrystalBlock.AGE)) {
                        case 0 -> 0.55F;
                        case 1 -> 0.82F;
                        case 2 -> 1.08F;
                        default -> 1.32F;
                    };
                    if (state.getValue(AcheroniteCrystalBlock.BURIED)) {
                        strength += 0.14F;
                    }
                    addColdAnchor(anchors, mutablePos, strength);
                }
            }
        }

        anchors.sort((left, right) -> Float.compare(right.strength(), left.strength()));
        if (anchors.size() > maxColdFields) {
            return new ArrayList<>(anchors.subList(0, maxColdFields));
        }
        return anchors;
    }

    private static void addColdAnchor(List<ColdAnchor> anchors, BlockPos pos, float strength) {
        int clusterRadius = 3;
        int clusterRadiusSqr = clusterRadius * clusterRadius;
        ColdAnchor candidate = new ColdAnchor(pos.immutable(), strength);

        for (int i = 0; i < anchors.size(); i++) {
            ColdAnchor existing = anchors.get(i);
            if (existing.pos().distSqr(pos) <= clusterRadiusSqr) {
                if (candidate.strength() > existing.strength()) {
                    anchors.set(i, candidate);
                }
                return;
            }
        }

        anchors.add(candidate);
    }

    record ColdAnchor(BlockPos pos, float strength) {
    }
}
