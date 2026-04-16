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
        BlockPos expectedPadCenter = getExpectedPadCenter(level);
        if (expectedPadCenter == null) {
            return new Diagnostic(false, false, false, enginePos, "Blast pit launch pad not resolved yet.");
        }

        BlockPos expectedEngine = expectedPadCenter.above();
        if (!enginePos.equals(expectedEngine)) {
            return new Diagnostic(false, false, false, enginePos,
                    "Rocket engine must sit one block above the blast pit pad center at " + shortPos(expectedEngine) + ".");
        }
        if (!level.getBlockState(enginePos).is(ModBlocks.ROCKET_ENGINE.get())) {
            return new Diagnostic(false, true, false, enginePos, "Launch center must contain a Rocket Engine.");
        }

        PadDiagnostic padDiagnostic = diagnosePad(level, expectedPadCenter);
        if (!padDiagnostic.valid()) {
            return new Diagnostic(false, padDiagnostic.onBlastPit(), false, padDiagnostic.problemPos(), padDiagnostic.message());
        }

        for (BlockPos finPos : new BlockPos[]{
                enginePos.north(), enginePos.south(), enginePos.east(), enginePos.west()
        }) {
            if (!level.getBlockState(finPos).is(ModBlocks.ROCKET_FIN.get())) {
                return new Diagnostic(false, true, true, finPos,
                        "Place Rocket Fins on the four cardinal sides of the engine.");
            }
        }
        for (int i = 1; i <= 4; i++) {
            BlockPos hullPos = enginePos.above(i);
            if (!level.getBlockState(hullPos).is(ModBlocks.ROCKET_HULL.get())) {
                return new Diagnostic(false, true, true, hullPos,
                        "Stack four Rocket Hull blocks above the engine.");
            }
        }
        BlockPos nosePos = enginePos.above(5);
        if (!level.getBlockState(nosePos).is(ModBlocks.ROCKET_NOSE_CONE.get())) {
            return new Diagnostic(false, true, true, nosePos,
                    "Finish the rocket with a Rocket Nose Cone.");
        }
        return new Diagnostic(true, true, true, enginePos, "Rocket structure locked.");
    }

    public static PadDiagnostic diagnosePad(ServerLevel level, BlockPos clickedPos) {
        BlockPos expectedPadCenter = getExpectedPadCenter(level);
        if (expectedPadCenter == null) {
            return new PadDiagnostic(false, false, clickedPos, "Blast pit launch pad not resolved yet.");
        }

        boolean onBlastPit = isWithinPadFootprint(clickedPos, expectedPadCenter);
        if (!onBlastPit) {
            return new PadDiagnostic(false, false, clickedPos,
                    "Launch pad must be centered on the ORSA blast pit at " + shortPos(expectedPadCenter) + ".");
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos padPos = expectedPadCenter.offset(dx, 0, dz);
                if (!level.getBlockState(padPos).is(ModBlocks.LAUNCH_PAD.get())) {
                    return new PadDiagnostic(false, true, padPos,
                            "Build a full 3x3 ORSA launch pad on the blast pit mount.");
                }
            }
        }

        if (!level.getBlockState(expectedPadCenter.below()).is(Blocks.BLACKSTONE)) {
            return new PadDiagnostic(false, true, expectedPadCenter.below(),
                    "Blast trench alignment under the pad center is broken.");
        }

        return new PadDiagnostic(true, true, expectedPadCenter, "Launch pad locked.");
    }

    public static BlockPos getExpectedPadCenter(ServerLevel level) {
        return BlastPitPlacement.getLaunchPadCenter(level);
    }

    public static boolean isWithinPadFootprint(BlockPos pos, BlockPos center) {
        return pos.getY() == center.getY()
                && Math.abs(pos.getX() - center.getX()) <= 1
                && Math.abs(pos.getZ() - center.getZ()) <= 1;
    }

    public static BlockPos[] rocketBlockPositions(BlockPos enginePos) {
        return new BlockPos[]{
                enginePos,
                enginePos.north(), enginePos.south(), enginePos.east(), enginePos.west(),
                enginePos.above(1), enginePos.above(2), enginePos.above(3), enginePos.above(4),
                enginePos.above(5)
        };
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public record Diagnostic(boolean valid, boolean atBlastPit, boolean padValid, BlockPos problemPos, String message) {
    }

    public record PadDiagnostic(boolean valid, boolean onBlastPit, BlockPos problemPos, String message) {
    }
}
