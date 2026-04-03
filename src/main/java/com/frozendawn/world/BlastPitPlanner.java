package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.OrsaStructureState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chooses the guaranteed Blast Pit anchor and keeps planning state out of SavedData.
 */
public final class BlastPitPlanner {

    private static final int MIN_DISTANCE = 1000;
    private static final int MAX_DISTANCE = 3000;
    private static final int TARGET_DISTANCE = 2000;
    private static final int DISTANCE_STEP = 192;
    private static final int FINAL_CANDIDATE_LIMIT = 32;
    private static final int COARSE_BUDGET_PER_CALL = 192;

    private static final Map<MinecraftServer, BlastPitSearchSession> SEARCH_SESSIONS = new IdentityHashMap<>();

    private BlastPitPlanner() {
    }

    public static void ensurePlanned(ServerLevel overworld) {
        OrsaStructureState state = OrsaStructureState.get(overworld.getServer());
        if (state.getBlastPitTargetPos() != null) {
            SEARCH_SESSIONS.remove(overworld.getServer());
            return;
        }

        BlastPitSearchSession search = SEARCH_SESSIONS.computeIfAbsent(
                overworld.getServer(), server -> new BlastPitSearchSession());
        stepSearch(overworld, state, search, COARSE_BUDGET_PER_CALL);
    }

    public static void reroll(ServerLevel overworld) {
        OrsaStructureState state = OrsaStructureState.get(overworld.getServer());
        state.clearBlastPitPlan();
        state.incrementBlastPitSelectionPass();
        SEARCH_SESSIONS.remove(overworld.getServer());
    }

    public static void reset() {
        SEARCH_SESSIONS.clear();
    }

    private static void stepSearch(ServerLevel overworld, OrsaStructureState state,
                                   BlastPitSearchSession search, int coarseBudget) {
        while (!search.coarseComplete && coarseBudget-- > 0) {
            int distance = search.distance;
            int angleSteps = search.angleSteps;
            double angle = ((double) search.angleStep / (double) angleSteps) * (Math.PI * 2.0D);
            BlockPos spawn = overworld.getSharedSpawnPos();
            int x = spawn.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = spawn.getZ() + Mth.floor(Math.sin(angle) * distance);
            LandmarkCandidateEvaluator.ExactTargetCandidate candidate =
                    LandmarkCandidateEvaluator.evaluateBlastPitCandidate(overworld, x, z, distance, TARGET_DISTANCE);
            if (candidate != null) {
                if (search.bestCandidate == null || candidate.compareTo(search.bestCandidate) < 0) {
                    search.bestCandidate = candidate;
                }
                long id = BlockPos.asLong(candidate.pos().getX(), candidate.pos().getY(), candidate.pos().getZ());
                if (search.seenCandidates.add(id)) {
                    LandmarkCandidateEvaluator.addTopCandidate(search.topCandidates, candidate, FINAL_CANDIDATE_LIMIT);
                }
            }

            search.angleStep++;
            if (search.angleStep >= angleSteps) {
                search.distance += DISTANCE_STEP;
                if (search.distance > MAX_DISTANCE) {
                    search.coarseComplete = true;
                } else {
                    search.angleStep = 0;
                    search.angleSteps = blastPitAngleSteps(search.distance);
                }
            }
        }

        if (search.coarseComplete && !search.topCandidates.isEmpty()) {
            LandmarkCandidateEvaluator.ExactTargetCandidate chosen = search.topCandidates.get(
                    Math.floorMod(state.getBlastPitSelectionPass(), search.topCandidates.size()));
            BlockPos targetPos = chosen.pos();
            state.setBlastPitTargetPos(targetPos);
            SEARCH_SESSIONS.remove(overworld.getServer());
            FrozenDawn.LOGGER.info("Blast Pit final anchor chosen at ({}, {}, {}), distance {} from spawn",
                    targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                    (int) Math.sqrt(overworld.getSharedSpawnPos().distSqr(targetPos)));
            return;
        }

        if (search.coarseComplete) {
            FrozenDawn.LOGGER.info(
                    "Blast Pit chooser pass {} failed. best final candidate={}",
                    state.getBlastPitSelectionPass(),
                    search.bestCandidate != null
                            ? "(" + search.bestCandidate.pos().getX() + ", "
                            + search.bestCandidate.pos().getY() + ", "
                            + search.bestCandidate.pos().getZ() + ")"
                            : "none");
            state.incrementBlastPitSelectionPass();
            SEARCH_SESSIONS.remove(overworld.getServer());
        }
    }

    private static int blastPitAngleSteps(int distance) {
        return Math.max(12, Mth.ceil((float) ((Math.PI * 2.0D * distance) / 512.0D)));
    }

    private static final class BlastPitSearchSession {
        private final List<LandmarkCandidateEvaluator.ExactTargetCandidate> topCandidates = new ArrayList<>();
        private final Set<Long> seenCandidates = new HashSet<>();
        private int distance = MIN_DISTANCE;
        private int angleStep;
        private int angleSteps = blastPitAngleSteps(distance);
        private boolean coarseComplete;
        private LandmarkCandidateEvaluator.ExactTargetCandidate bestCandidate;
    }
}
