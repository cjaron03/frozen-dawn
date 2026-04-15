package com.frozendawn.block;

import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class FuelProcessingSiloMultiblock {
    private static final int[][] ADJACENT = {
            { 1, 0, 0 }, { -1, 0, 0 },
            { 0, 1, 0 }, { 0, -1, 0 },
            { 0, 0, 1 }, { 0, 0, -1 }
    };

    private FuelProcessingSiloMultiblock() {
    }

    public static boolean isValid(Level level, BlockPos controllerPos, Direction facing) {
        return resolve(level, controllerPos, facing) != null;
    }

    public static Diagnostic diagnose(Level level, BlockPos controllerPos, Direction facing) {
        StructureMatch match = resolve(level, controllerPos, facing);
        if (match != null) {
            return Diagnostic.success();
        }

        CandidateProbe bestProbe = null;
        for (Direction inward : inwardOrder(facing)) {
            Direction right = inward.getClockWise();
            for (int controllerLocalY = 0; controllerLocalY < 4; controllerLocalY++) {
                BlockPos anchorPos = controllerPos.below(controllerLocalY);
                CandidateProbe probe = probeCandidate(level, anchorPos, inward, right, 0, controllerLocalY, 0);
                if (probe.valid()) {
                    return Diagnostic.success();
                }
                if (bestProbe == null || probe.matchedBlocks() > bestProbe.matchedBlocks()) {
                    bestProbe = probe;
                }
            }
        }

        if (bestProbe != null) {
            return new Diagnostic(false, bestProbe.problemPos(), bestProbe.message());
        }
        return new Diagnostic(false, controllerPos, "No matching silo shell found.");
    }

    public static AttachedHeater findAttachedHeater(Level level, BlockPos controllerPos, Direction facing) {
        StructureMatch match = resolve(level, controllerPos, facing);
        if (match == null) {
            return AttachedHeater.none();
        }
        BlockPos basePos = match.anchorPos();
        Direction inward = match.inward();
        Direction right = match.right();
        Set<Long> visited = new HashSet<>();
        AttachedHeater bestLit = AttachedHeater.none();
        AttachedHeater bestUnlit = AttachedHeater.none();

        for (int localY = 0; localY < 4; localY++) {
            for (int localX = -1; localX <= 1; localX++) {
                for (int localZ = 0; localZ <= 2; localZ++) {
                    if (!isSolidSiloBlock(localX, localY, localZ)) {
                        continue;
                    }
                    BlockPos shellPos = translate(basePos, right, inward, localX, localY, localZ);
                    for (int[] offset : ADJACENT) {
                        BlockPos candidatePos = shellPos.offset(offset[0], offset[1], offset[2]);
                        if (!visited.add(candidatePos.asLong())) {
                            continue;
                        }
                        BlockEntity blockEntity = level.getBlockEntity(candidatePos);
                        if (!(blockEntity instanceof ThermalHeaterBlockEntity heater)) {
                            continue;
                        }
                        AttachedHeater candidate = new AttachedHeater(
                                candidatePos.immutable(),
                                heater,
                                tierCodeFor(heater),
                                speedUnitsFor(heater),
                                heater.isLit(),
                                heater.hasCapacitor()
                        );
                        if (candidate.lit()) {
                            if (bestLit.pos() == null || candidate.speedUnits() > bestLit.speedUnits()) {
                                bestLit = candidate;
                            }
                        } else if (bestUnlit.pos() == null || candidate.speedUnits() > bestUnlit.speedUnits()) {
                            bestUnlit = candidate;
                        }
                    }
                }
            }
        }

        return bestLit.pos() != null ? bestLit : bestUnlit;
    }

    public static String tierLabel(int tierCode) {
        return switch (tierCode) {
            case 4 -> "Diamond";
            case 3 -> "Gold";
            case 2 -> "Iron";
            case 1 -> "Basic";
            default -> "None";
        };
    }

    public static String formatSpeed(int speedUnits) {
        if (speedUnits <= 0) {
            return "x0";
        }
        int hundredths = speedUnits * 100 / 20;
        if (hundredths % 100 == 0) {
            return "x" + (hundredths / 100);
        }
        if (hundredths % 10 == 0) {
            return "x" + (hundredths / 100) + "." + ((hundredths / 10) % 10);
        }
        return String.format(java.util.Locale.ROOT, "x%.2f", speedUnits / 20.0f);
    }

    public static @Nullable BlockPos getTopVentCenter(Level level, BlockPos controllerPos, Direction facing) {
        StructureMatch match = resolve(level, controllerPos, facing);
        if (match == null) {
            return null;
        }
        BlockPos basePos = match.anchorPos();
        Direction inward = match.inward();
        Direction right = match.right();
        return translate(basePos, right, inward, 0, 4, 1);
    }

    private static int tierCodeFor(ThermalHeaterBlockEntity heater) {
        var block = heater.getBlockState().getBlock();
        if (block == ModBlocks.DIAMOND_THERMAL_HEATER.get()) {
            return 4;
        }
        if (block == ModBlocks.GOLD_THERMAL_HEATER.get()) {
            return 3;
        }
        if (block == ModBlocks.IRON_THERMAL_HEATER.get()) {
            return 2;
        }
        return 1;
    }

    private static int speedUnitsFor(ThermalHeaterBlockEntity heater) {
        int speedUnits = switch (tierCodeFor(heater)) {
            case 4 -> 60;
            case 3 -> 40;
            case 2 -> 30;
            default -> 20;
        };
        if (heater.hasCapacitor()) {
            speedUnits = speedUnits * 3 / 2;
        }
        return speedUnits;
    }

    private static @Nullable StructureMatch resolve(Level level, BlockPos controllerPos, Direction facing) {
        for (Direction inward : inwardOrder(facing)) {
            Direction right = inward.getClockWise();
            for (int controllerLocalY = 0; controllerLocalY < 4; controllerLocalY++) {
                BlockPos anchorPos = controllerPos.below(controllerLocalY);
                if (matchesForDirection(level, controllerPos, anchorPos, inward, right, 0, controllerLocalY, 0)) {
                    return new StructureMatch(anchorPos, inward, right);
                }
            }
        }
        return null;
    }

    private static Direction[] inwardOrder(Direction facing) {
        if (!facing.getAxis().isHorizontal()) {
            return new Direction[] {Direction.NORTH};
        }
        return new Direction[] {facing.getOpposite(), facing};
    }

    private static boolean matchesForDirection(Level level, BlockPos controllerPos, BlockPos anchorPos,
                                               Direction inward, Direction right,
                                               int controllerLocalX, int controllerLocalY, int controllerLocalZ) {
        return probeCandidate(level, anchorPos, inward, right, controllerLocalX, controllerLocalY, controllerLocalZ).valid();
    }

    private static CandidateProbe probeCandidate(Level level, BlockPos anchorPos,
                                                 Direction inward, Direction right,
                                                 int controllerLocalX, int controllerLocalY, int controllerLocalZ) {
        int matchedBlocks = 0;
        for (int localY = 0; localY < 4; localY++) {
            for (int localX = -1; localX <= 1; localX++) {
                for (int localZ = 0; localZ <= 2; localZ++) {
                    BlockPos worldPos = translate(anchorPos, right, inward, localX, localY, localZ);
                    if (localX == controllerLocalX && localY == controllerLocalY && localZ == controllerLocalZ) {
                        if (!level.getBlockState(worldPos).is(ModBlocks.FUEL_PROCESSING_SILO_CONTROLLER.get())) {
                            return CandidateProbe.invalid(matchedBlocks, worldPos,
                                    "Expected silo controller at " + shortPos(worldPos) + ".");
                        }
                        matchedBlocks++;
                        continue;
                    }

                    if (isSolidSiloBlock(localX, localY, localZ)) {
                        if (!level.getBlockState(worldPos).is(Blocks.IRON_BLOCK)) {
                            return CandidateProbe.invalid(matchedBlocks, worldPos,
                                    "Expected iron block at " + shortPos(worldPos)
                                            + ", found " + blockName(level, worldPos) + ".");
                        }
                        matchedBlocks++;
                    } else if (!level.getBlockState(worldPos).isAir()) {
                        return CandidateProbe.invalid(matchedBlocks, worldPos,
                                "Expected hollow air at " + shortPos(worldPos)
                                        + ", found " + blockName(level, worldPos) + ".");
                    } else {
                        matchedBlocks++;
                    }
                }
            }
        }
        return CandidateProbe.valid(matchedBlocks);
    }

    private static boolean isSolidSiloBlock(int localX, int localY, int localZ) {
        if (localY == 0) {
            return true;
        }
        return !(localX == 0 && localZ == 1);
    }

    private static BlockPos translate(BlockPos origin, Direction right, Direction inward, int localX, int localY, int localZ) {
        return origin.relative(right, localX).relative(inward, localZ).above(localY);
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String blockName(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    public record AttachedHeater(
            @Nullable BlockPos pos,
            @Nullable ThermalHeaterBlockEntity heater,
            int tierCode,
            int speedUnits,
            boolean lit,
            boolean hasCapacitor
    ) {
        static AttachedHeater none() {
            return new AttachedHeater(null, null, 0, 0, false, false);
        }
    }

    public record Diagnostic(boolean valid, @Nullable BlockPos problemPos, String message) {
        static Diagnostic success() {
            return new Diagnostic(true, null, "Silo shell locked.");
        }
    }

    private record CandidateProbe(boolean valid, int matchedBlocks, @Nullable BlockPos problemPos, String message) {
        static CandidateProbe valid(int matchedBlocks) {
            return new CandidateProbe(true, matchedBlocks, null, "Silo shell locked.");
        }

        static CandidateProbe invalid(int matchedBlocks, @Nullable BlockPos problemPos, String message) {
            return new CandidateProbe(false, matchedBlocks, problemPos, message);
        }
    }

    private record StructureMatch(BlockPos anchorPos, Direction inward, Direction right) {
    }
}
