package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure walk-break planning helpers kept out of ArchitectEntity so movement
 * orchestration and break-target selection stay separated.
 */
public final class ArchitectWalkBreakPlanner {

    private ArchitectWalkBreakPlanner() {
    }

    public static Set<BlockPos> collectUnstickBreakCandidates(
            BlockPos from,
            BlockPos stepPos,
            @Nullable Direction toward
    ) {
        boolean steppingUp = stepPos.getY() > from.getY();
        boolean steppingDown = stepPos.getY() < from.getY();

        Set<BlockPos> candidates = new LinkedHashSet<>(10);
        candidates.add(from.above());
        if (steppingUp) {
            // Standing fits in two clear cells; jumping onto a ledge also needs
            // clearance above the current head cell. Never mine the ledge itself.
            candidates.add(from.above(2));
        }
        if (toward != null) {
            BlockPos front = from.relative(toward);
            // During a step-up, front is the ledge supporting the destination.
            // Mining it converts a valid jump into a trench and repeats forever.
            if (!steppingUp) {
                candidates.add(front);
            }
            candidates.add(front.above());
            if (steppingDown) {
                candidates.add(front.above().above());
            }
        }
        if (Math.abs(stepPos.getX() - from.getX()) <= 1
                && Math.abs(stepPos.getY() - from.getY()) <= 1
                && Math.abs(stepPos.getZ() - from.getZ()) <= 1) {
            candidates.add(stepPos);
            candidates.add(stepPos.above());
        }

        // Nothing below stepPos is ever a candidate: that column is the surface the
        // destination stands on, so it is solid on any walkable path and would always
        // win selection once the real obstructions turn out to be air. Mining it drops
        // the Architect into a pit, which re-triggers the stuck check one block lower
        // and digs a trench. A slab or snow layer at the destination is stepPos itself,
        // which is already covered above.
        return candidates;
    }

    public record CandidateDecision(BreakChoice candidate, String outcome) { }

    public static List<BreakChoice> unstickChoices(BlockPos from, BlockPos step, @Nullable Direction toward) {
        return collectUnstickBreakCandidates(from, step, toward).stream().map(pos -> new BreakChoice(pos,
                pos.equals(from.above(2)) && step.getY() > from.getY() ? BreakReason.STEP_UP_CEILING
                : pos.equals(from.above()) ? BreakReason.HEAD_CLEARANCE
                : toward != null && step.getY() < from.getY() && pos.equals(from.relative(toward).above(2))
                    ? BreakReason.STEP_DOWN_CLEARANCE : BreakReason.IMMEDIATE_CANDIDATE)).toList();
    }

    public static List<BreakChoice> corridorChoices(List<BlockPos> nodes) {
        return nodes.stream().flatMap(node -> java.util.stream.Stream.of(
                new BreakChoice(node, BreakReason.CORRIDOR_NODE),
                new BreakChoice(node.above(), BreakReason.HEAD_CLEARANCE))).toList();
    }

    @Nullable
    public static BreakChoice selectChoice(Iterable<BreakChoice> candidates, Set<BlockPos> blocked,
            java.util.function.Function<BlockPos, String> rejection,
            Predicate<BlockPos> lastResort, java.util.function.Consumer<CandidateDecision> trace) {
        BreakChoice fallback = null;
        BreakChoice selected = null;
        for (BreakChoice candidate : candidates) {
            String outcome;
            if (selected != null) outcome = "NOT_EVALUATED_AFTER_SELECTION";
            else if (blocked.contains(candidate.pos())) outcome = "BLACKLISTED";
            else {
                outcome = rejection.apply(candidate.pos());
                if (outcome == null) {
                    if (lastResort.test(candidate.pos())) {
                        outcome = "LAST_RESORT_DEFERRED";
                        if (fallback == null) fallback = new BreakChoice(candidate.pos(), BreakReason.LAST_RESORT);
                    } else {
                        selected = candidate;
                        outcome = "SELECTED";
                    }
                }
            }
            trace.accept(new CandidateDecision(candidate, outcome));
        }
        if (selected == null && fallback != null) {
            trace.accept(new CandidateDecision(fallback, "SELECTED_FALLBACK"));
        }
        return selected != null ? selected : fallback;
    }

    @Nullable
    public static BlockPos selectPreferredBreakCandidate(Iterable<BlockPos> candidates, Set<BlockPos> blocked,
            Predicate<BlockPos> breakable, Predicate<BlockPos> lastResort) {
        java.util.ArrayList<BreakChoice> choices = new java.util.ArrayList<>();
        candidates.forEach(p -> choices.add(new BreakChoice(p, BreakReason.IMMEDIATE_CANDIDATE)));
        BreakChoice choice = selectChoice(choices, blocked, p -> breakable.test(p) ? null : "NOT_BREAKABLE", lastResort, d -> { });
        return choice == null ? null : choice.pos();
    }

    @Nullable
    public static BlockPos findCorridorBreakTarget(List<BlockPos> nodes,
            Predicate<BlockPos> breakable, Predicate<BlockPos> lastResort) {
        return findCorridorBreakTarget(nodes, Set.of(), breakable, lastResort);
    }

    @Nullable
    public static BlockPos findCorridorBreakTarget(List<BlockPos> nodes, Set<BlockPos> blocked,
            Predicate<BlockPos> breakable, Predicate<BlockPos> lastResort) {
        BreakChoice choice = selectChoice(corridorChoices(nodes), blocked,
                p -> breakable.test(p) ? null : "NOT_BREAKABLE", lastResort, d -> { });
        return choice == null ? null : choice.pos();
    }
}
