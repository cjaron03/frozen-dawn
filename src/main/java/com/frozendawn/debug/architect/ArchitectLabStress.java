package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Seeded terrain and scheduled perturbations, shared by manual runs and GameTests. */
public final class ArchitectLabStress {
    private ArchitectLabStress() { }

    /** A perfect maze: all 64 cells connect, with two-block headroom and no mineable walls. */
    public static void carveMaze(ServerLevel level, ArchitectLabFrame frame, long seed) {
        RandomSource random = RandomSource.create(seed);
        var stack = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        BlockPos start = new BlockPos(3, 1, 3);
        stack.push(start);
        visited.add(start);
        carve(level, frame, start);
        while (!stack.isEmpty()) {
            BlockPos current = stack.peek();
            var choices = new ArrayList<BlockPos>();
            for (BlockPos next : new BlockPos[]{current.north(2), current.east(2), current.south(2), current.west(2)}) {
                if (next.getX() >= 3 && next.getX() <= 17 && next.getZ() >= 3 && next.getZ() <= 17
                        && !visited.contains(next)) choices.add(next);
            }
            if (choices.isEmpty()) { stack.pop(); continue; }
            BlockPos next = choices.get(random.nextInt(choices.size()));
            carve(level, frame, new BlockPos((current.getX() + next.getX()) / 2, 1, (current.getZ() + next.getZ()) / 2));
            carve(level, frame, next);
            visited.add(next);
            stack.push(next);
        }
    }

    private static void carve(ServerLevel level, ArchitectLabFrame frame, BlockPos local) {
        level.setBlockAndUpdate(frame.block(local), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(frame.block(local.above()), Blocks.AIR.defaultBlockState());
    }

    public static void events(ArchitectLabRun run, Set<String> applied) {
        long tick = run.elapsedTicks();
        switch (run.scenario) {
            case VANISHING_WALL -> {
                if (tick >= 60 && event(run, applied, "wall_removed")) carve(run.level, run.frame, new BlockPos(4, 1, 7));
            }
            case CLOSING_PASSAGE -> {
                if (tick >= 45 && event(run, applied, "passage_closed")) {
                    for (int y = 1; y <= 2; y++) run.level.setBlockAndUpdate(run.frame.block(new BlockPos(4, y, 8)), Blocks.BEDROCK.defaultBlockState());
                }
                if (tick >= 100 && event(run, applied, "passage_reopened")) carve(run.level, run.frame, new BlockPos(4, 1, 8));
            }
            case TARGET_JUKE -> {
                if (tick >= 60 && event(run, applied, "target_north_to_south")) moveTarget(run, new Vec3(4.5, 1, 14.5));
                if (tick >= 100 && event(run, applied, "target_south_to_east")) moveTarget(run, new Vec3(14.5, 1, 14.5));
            }
            case CORRIDOR_SHUTTLE -> {
                for (int i = 1; i <= 4; i++) {
                    if (tick >= i * 120 && event(run, applied, "target_shuttle_" + i)) {
                        moveTarget(run, new Vec3(4.5, 1, i % 2 == 1 ? 4.5 : 12.5));
                    }
                }
            }
            default -> { }
        }
    }

    private static boolean event(ArchitectLabRun run, Set<String> applied, String event) {
        if (!applied.add(event)) return false;
        run.architect.recordDecision("SCENARIO_EVENT", null, event);
        return true;
    }

    private static void moveTarget(ArchitectLabRun run, Vec3 local) {
        run.target.getNavigation().stop();
        run.target.setPos(run.frame.position(local));
        run.target.setDeltaMovement(Vec3.ZERO);
    }

    public static boolean eventsComplete(ArchitectLabScenario scenario, Set<String> applied) {
        return switch (scenario) {
            case VANISHING_WALL -> applied.contains("wall_removed");
            case GATE_REOPENS -> applied.contains("fence_gate_hit_after_crossing");
            case CLOSING_PASSAGE -> applied.contains("passage_reopened");
            case TARGET_JUKE -> applied.contains("target_south_to_east");
            case CORRIDOR_SHUTTLE -> applied.contains("target_shuttle_4");
            default -> true;
        };
    }
}
