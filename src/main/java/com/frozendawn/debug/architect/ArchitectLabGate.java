package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.Set;

/** Observe the gate's actual state and passage before accepting a subsequent hit. */
final class ArchitectLabGate {
    private final ArchitectLabRun run;
    private Vec3 previous;
    private long closedTick = -1, reopenedTick = -1, crossedTick = -1, hitTick = -1;
    private long hitsAtCrossing;
    private float healthAtCrossing;
    private boolean throughOpening;
    private String reopening = "pending";

    ArchitectLabGate(ArchitectLabRun run) {
        this.run = run;
        previous = run.frame.local(run.architect.position());
    }

    String tick(Set<String> applied) {
        long tick = run.elapsedTicks();
        BlockPos gate = run.frame.block(new BlockPos(10, 1, 10));
        var state = run.level.getBlockState(gate);
        if (!(state.getBlock() instanceof FenceGateBlock)) return "Replay gate was removed or replaced";
        Vec3 current = run.frame.local(run.architect.position());
        if (closedTick < 0 && tick >= 45) {
            if (current.x >= 10) return "Architect reached gate before its closure was observed";
            run.level.setBlockAndUpdate(gate, state.setValue(FenceGateBlock.OPEN, false));
            state = run.level.getBlockState(gate);
            if (state.getValue(FenceGateBlock.OPEN)) return "Replay gate did not close";
            closedTick = tick;
            event(applied, "fence_gate_closed");
        }
        if (closedTick >= 0 && tick > closedTick && reopenedTick < 0) {
            if (!state.getValue(FenceGateBlock.OPEN) && tick >= 100) {
                run.level.setBlockAndUpdate(gate, state.setValue(FenceGateBlock.OPEN, true));
                state = run.level.getBlockState(gate);
                reopening = "scheduled";
            }
            if (state.getValue(FenceGateBlock.OPEN)) {
                reopenedTick = tick;
                if (reopening.equals("pending")) reopening = "observed";
                event(applied, "fence_gate_reopened");
            }
        }
        if (reopenedTick >= 0 && state.getValue(FenceGateBlock.OPEN) && crossedTick < 0) {
            // Check passage through the opening, not merely presence on the far side.
            if (previous.x <= 10.5 && current.x > 10.5) {
                double fraction = (10.5 - previous.x) / (current.x - previous.x);
                Vec3 crossing = previous.lerp(current, fraction);
                double halfWidth = run.architect.getBbWidth() / 2;
                throughOpening = crossing.z >= 10 + halfWidth && crossing.z <= 11 - halfWidth
                        && Math.abs(crossing.y - 1) < 0.25;
            }
            if (throughOpening && current.x >= 11 + run.architect.getBbWidth() / 2) {
                crossedTick = tick;
                hitsAtCrossing = hits();
                healthAtCrossing = run.target.getHealth();
                event(applied, "fence_gate_crossed");
            }
        }
        if (crossedTick >= 0 && tick > crossedTick && hitTick < 0 && hits() > hitsAtCrossing
                && run.target.getHealth() < healthAtCrossing && run.target.getLastHurtByMob() == run.architect) {
            hitTick = tick;
            event(applied, "fence_gate_hit_after_crossing");
        }
        previous = current;
        return null;
    }

    private long hits() { return run.architect.decisionJournal().eventCounts().getOrDefault("MELEE_HIT", 0L); }
    private void event(Set<String> applied, String name) {
        if (applied.add(name)) run.architect.recordDecision("SCENARIO_EVENT", null, name);
    }
    Map<String, Object> context() {
        return Map.of("closedTick", closedTick, "reopenedTick", reopenedTick,
                "crossedTick", crossedTick, "hitTick", hitTick, "reopening", reopening);
    }
}
