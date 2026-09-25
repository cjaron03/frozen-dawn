package com.frozendawn.entity;

import com.frozendawn.maeve.MaeveDirector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
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
    private boolean breached;

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
        if (!breached && java.util.stream.Stream.concat(current.stream(), building.stream())
                .anyMatch(p -> !actor.level().hasChunkAt(p) || !actor.level().getBlockState(p).is(Blocks.PACKED_ICE))) {
            breached = true;
            actor.recordDecision("MANTLET_BREACHED", null, "advance stopped; no repairs or replacement bet");
        }
        if (!actor.onGround()) {
            if (Math.abs(actor.getY() - goal.y) > 1) return release("LOCAL_FALL_RISK");
            actor.setMaeveHolding(false); return true;
        }
        if (!breached && actor.position().distanceToSqr(goal) > .04) {
            if (!ArchitectMantletGeometry.walk(actor, actor.position(), goal)) return release("LOCAL_MANTLET_ROUTE_UNAVAILABLE");
            actor.setMaeveHolding(false); actor.setCommitmentAction(false);
            Vec3 direction = goal.subtract(actor.position());
            double distance = direction.horizontalDistance();
            if (distance > .01) actor.setDeltaMovement(direction.x / distance * Math.min(.09, distance),
                    actor.getDeltaMovement().y, direction.z / distance * Math.min(.09, distance));
            actor.getLookControl().setLookAt(goal.x, goal.y + 1, goal.z, 15, 15);
            return true;
        }
        boolean construction = !breached && screen < plan.screens().size() && now >= nextScreen;
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

    void clear() { plan = proposal = null; proposedSubject = encounter = null; proposedAt = -1; current.clear(); building.clear(); displaced.clear(); screen = placed = 0; breached = false; goal = null; }
}
