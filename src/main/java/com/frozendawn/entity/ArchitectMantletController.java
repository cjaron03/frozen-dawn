package com.frozendawn.entity;

import com.frozendawn.maeve.MaeveDirector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One physical, slow, fixed-front bet. A damaged screen is never replenished. */
final class ArchitectMantletController {
    private final ArchitectEntity actor;
    private ArchitectMantletGeometry.Plan plan;
    private ArchitectMantletGeometry.Plan proposal;
    private UUID proposedSubject;
    private long proposedAt;
    private UUID encounter;
    private final List<BlockPos> current = new ArrayList<>();
    private final List<BlockPos> building = new ArrayList<>();
    private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> displaced = new java.util.LinkedHashMap<>();
    private int screen, placed;
    private long nextBlock, nextScreen;
    private Vec3 goal;

    ArchitectMantletController(ArchitectEntity actor) { this.actor = actor; }

    MaeveDirector.PositionCandidate candidate(MaeveDirector.CommitmentHint hint, LivingEntity subject) {
        proposal = null; proposedSubject = null;
        if (!subject.isAlive() || subject.level() != actor.level() || actor.distanceToSqr(subject) > 48 * 48
                || !actor.hasLineOfSight(subject)) return null;
        // History selects the ranged counter; the local executor chooses its front
        // from a subject it can actually see now. Freeze this geometry once issued.
        proposal = ArchitectMantletGeometry.plan(actor, subject.position());
        if (proposal == null) return null;
        proposedSubject = subject.getUUID(); proposedAt = actor.getServer().overworld().getGameTime();
        return new MaeveDirector.PositionCandidate(hint.pattern(), actor.blockPosition(),
                proposal.screens().getFirst().cells().getFirst(), 3, null, true);
    }

    boolean tick(MaeveDirector.PositionDirective directive, LivingEntity target, long now) {
        if (target == null || !target.isAlive() || !target.getUUID().equals(directive.player())
                || target.level() != actor.level() || actor.distanceToSqr(target) > 48 * 48) return release("SUBJECT_UNAVAILABLE");
        if (!directive.encounter().equals(encounter)) {
            var selectedPlan = proposal;
            boolean matches = directive.player().equals(proposedSubject) && proposedAt == now;
            clear();
            plan = selectedPlan;
            if (!matches || plan == null || !plan.screens().getFirst().cells().getFirst().equals(directive.cover())) return release("LOCAL_MANTLET_UNAVAILABLE");
            encounter = directive.encounter(); nextBlock = nextScreen = now; goal = plan.screens().getFirst().stand();
            MaeveDirector.commitmentArrived(actor);
            actor.recordDecision("MANTLET_STARTED", null, "fixedFront=" + directive.cover() + " screens=" + ArchitectMantletGeometry.SCREENS + " budget=" + ArchitectMantletGeometry.PLACEMENTS);
        }
        actor.getNavigation().stop();
        actor.getMoveControl().setWantedPosition(actor.getX(), actor.getY(), actor.getZ(), 0);
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        actor.setSpeed(0); actor.setZza(0); actor.setXxa(0); actor.setSprinting(false);
        if (java.util.stream.Stream.concat(current.stream(), building.stream())
                .anyMatch(p -> !actor.level().hasChunkAt(p) || !actor.level().getBlockState(p).is(Blocks.PACKED_ICE))) {
            actor.recordDecision("MANTLET_BREACHED", null, "returning to local combat; no repairs or replacement bet");
            return interrupt("LOCAL_MANTLET_BREACHED");
        }
        if (!actor.onGround()) {
            if (Math.abs(actor.getY() - goal.y) > 1) return release("LOCAL_FALL_RISK");
            actor.setMaeveHolding(false); return true;
        }
        if (actor.position().distanceToSqr(goal) > .04) {
            if (!ArchitectMantletGeometry.walk(actor, actor.position(), goal)) return release("LOCAL_MANTLET_ROUTE_UNAVAILABLE");
            actor.setMaeveHolding(false); actor.setCommitmentAction(false);
            Vec3 direction = goal.subtract(actor.position());
            double distance = direction.horizontalDistance();
            if (distance > .01) actor.setDeltaMovement(direction.x / distance * Math.min(.09, distance),
                    actor.getDeltaMovement().y, direction.z / distance * Math.min(.09, distance));
            actor.getLookControl().setLookAt(goal.x, goal.y + 1, goal.z, 15, 15);
            return true;
        }
        boolean construction = screen < plan.screens().size() && now >= nextScreen;
        actor.setMaeveHolding(!construction, true); actor.setCommitmentAction(!construction);
        if (construction && now >= nextBlock) {
            var panel = plan.screens().get(screen);
            if (building.isEmpty() && panel.cells().stream().anyMatch(p -> !ArchitectMantletGeometry.placeable(actor, p)))
                return release("LOCAL_MANTLET_UNAVAILABLE");
            BlockPos cell = panel.cells().get(building.size());
            if (placed >= ArchitectMantletGeometry.PLACEMENTS || actor.getMantletIceCount() >= ArchitectMantletGeometry.PLACEMENTS
                    || !ArchitectMantletGeometry.placeable(actor, cell))
                return release("LOCAL_MANTLET_UNAVAILABLE");
            var previous = actor.level().getBlockState(cell);
            if (!actor.placeMantletIce(cell)) return release("LOCAL_MANTLET_UNAVAILABLE");
            displaced.put(cell, previous); building.add(cell); placed++; nextBlock = now + 10;
            actor.getLookControl().setLookAt(cell.getX() + .5, cell.getY() + .5, cell.getZ() + .5, 15, 15);
            actor.recordDecision("MANTLET_BLOCK", null, "screen=" + screen + " total=" + placed + " cell=" + cell.toShortString());
            if (building.size() == 4) {
                // Keep the new front intact before opening the old lane. Retired cells
                // remain charged to the separate mantlet pool and this bet's lifetime cap.
                if (!ArchitectMantletRetirement.releaseStoppedArrows(actor, current))
                    return release("LOCAL_MANTLET_ARROW_LIMIT");
                current.forEach(p -> actor.retireMantletIce(p, displaced.get(p)));
                current.clear(); current.addAll(building); building.clear();
                goal = panel.stand(); screen++; nextScreen = now + 50;
                actor.recordDecision("MANTLET_SCREEN", null, "completed=" + screen + " stand=" + goal);
            }
        }
        if (target != null && actor.hasLineOfSight(target) && actor.distanceToSqr(target) < 2.8 * 2.8 && actor.attackAnim == 0) {
            actor.swing(InteractionHand.MAIN_HAND); actor.doHurtTarget(target);
        }
        return true;
    }

    private boolean release(String reason) {
        actor.recordDecision("MANTLET_RELEASED", null, reason + " placed=" + placed);
        MaeveDirector.releaseCommitment(actor, reason); clear(); actor.setMaeveHolding(false); actor.triggerReeval(); return false;
    }

    void onMiningStarted(BlockPos pos) {
        if (encounter == null || (!current.contains(pos) && !building.contains(pos))) return;
        var directive = MaeveDirector.positionDirective(actor);
        if (directive == null || !directive.advancingCover() || !encounter.equals(directive.encounter())) return;
        Vec3 eyes = actor.getEyePosition(), cell = Vec3.atCenterOf(pos);
        if (eyes.distanceToSqr(cell) > 12 * 12) return;
        // See the attacked wall itself. This cue supplies no hidden miner position
        // or identity, and cannot change the executor's existing combat target.
        BlockPos from = BlockPos.containing(eyes);
        for (int x = Math.min(from.getX(), pos.getX()) >> 4; x <= Math.max(from.getX(), pos.getX()) >> 4; x++)
            for (int z = Math.min(from.getZ(), pos.getZ()) >> 4; z <= Math.max(from.getZ(), pos.getZ()) >> 4; z++)
                if (!actor.level().hasChunk(x, z)) return;
        var hit = actor.level().clip(new ClipContext(eyes, cell, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) return;
        actor.recordDecision("MANTLET_MINING_OBSERVED", null, "cell=" + pos.toShortString());
        interrupt("LOCAL_MANTLET_MINED");
    }

    private boolean interrupt(String reason) {
        release(reason);
        actor.resumeAfterMantletInterruption();
        return false;
    }

    void clear() { plan = proposal = null; proposedSubject = encounter = null; proposedAt = -1; current.clear(); building.clear(); displaced.clear(); screen = placed = 0; goal = null; }
}
