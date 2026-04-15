package com.frozendawn.block;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.BlastPitPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public final class RocketLaunchStructure {
    private RocketLaunchStructure() {
    }

    public static Diagnostic diagnose(ServerLevel level, BlockPos enginePos) {
        BlockPos expectedEngine = BlastPitPlacement.getLaunchPadCenter(level);
        if (expectedEngine == null) {
            return new Diagnostic(false, false, enginePos, "Blast pit launch pad not resolved yet.");
        }
        if (!enginePos.equals(expectedEngine)) {
            return new Diagnostic(false, false, enginePos,
                    "Rocket engine must sit on the blast pit launch mount at " + shortPos(expectedEngine) + ".");
        }
        if (!level.getBlockState(enginePos).is(ModBlocks.ROCKET_ENGINE.get())) {
            return new Diagnostic(false, true, enginePos, "Launch center must contain a Rocket Engine.");
        }
        if (!level.getBlockState(enginePos.below()).is(Blocks.BLACKSTONE)) {
            return new Diagnostic(false, true, enginePos.below(), "Engine must sit above the blast trench center.");
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (Math.abs(dx) + Math.abs(dz) == 1) {
                    continue;
                }
                BlockPos padPos = enginePos.offset(dx, 0, dz);
                if (!level.getBlockState(padPos).is(Blocks.POLISHED_DEEPSLATE)) {
                    return new Diagnostic(false, true, padPos,
                            "Rocket must stay centered on the intact blast pit pad.");
                }
            }
        }
        for (BlockPos finPos : new BlockPos[]{
                enginePos.north(), enginePos.south(), enginePos.east(), enginePos.west()
        }) {
            if (!level.getBlockState(finPos).is(ModBlocks.ROCKET_FIN.get())) {
                return new Diagnostic(false, true, finPos,
                        "Place Rocket Fins on the four cardinal sides of the engine.");
            }
        }
        for (int i = 1; i <= 4; i++) {
            BlockPos hullPos = enginePos.above(i);
            if (!level.getBlockState(hullPos).is(ModBlocks.ROCKET_HULL.get())) {
                return new Diagnostic(false, true, hullPos,
                        "Stack four Rocket Hull blocks above the engine.");
            }
        }
        BlockPos nosePos = enginePos.above(5);
        if (!level.getBlockState(nosePos).is(ModBlocks.ROCKET_NOSE_CONE.get())) {
            return new Diagnostic(false, true, nosePos,
                    "Finish the rocket with a Rocket Nose Cone.");
        }
        return new Diagnostic(true, true, enginePos, "Rocket structure locked.");
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public record Diagnostic(boolean valid, boolean atBlastPit, BlockPos problemPos, String message) {
    }
}
