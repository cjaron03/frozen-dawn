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
 * Chooses communication tower anchors and keeps planning search state out of SavedData.
 */
public final class TowerPlanner {

    private static final int TARGET_COUNT = 6;
    private static final int MIN_DISTANCE = 1800;
    private static final int MAX_DISTANCE = 6200;
    private static final int TARGET_DISTANCE = (MIN_DISTANCE + MAX_DISTANCE) / 2;
    private static final int MIN_SEPARATION = 1400;
    private static final int BLAST_PIT_BUFFER = 900;
    private static final int DISTANCE_STEP = 192;
    private static final int FINAL_CANDIDATE_LIMIT = 24;
    private static final int COARSE_BUDGET_PER_CALL = 192;

    private static final Map<MinecraftServer, TowerSearchSession> SEARCH_SESSIONS = new IdentityHashMap<>();

    private TowerPlanner() {
    }

    public static void ensurePlanned(ServerLevel overworld) {
        OrsaStructureState state = OrsaStructureState.get(overworld.getServer());
        if (state.getTowers().size() >= TARGET_COUNT) {
            SEARCH_SESSIONS.remove(overworld.getServer());
            return;
        }

        TowerSearchSession search = SEARCH_SESSIONS.get(overworld.getServer());
        if (search == null) {
            int missingSector = pickNextMissingTowerSector(state);
            if (missingSector < 0) {
                return;
            }
            search = new TowerSearchSession(missingSector);
            SEARCH_SESSIONS.put(overworld.getServer(), search);
        } else if (state.getTowerBySectorIndex(search.sectorIndex) != null) {
            SEARCH_SESSIONS.remove(overworld.getServer());
            return;
        }

        stepSearch(overworld, state, search, COARSE_BUDGET_PER_CALL);
    }

    public static boolean reroll(ServerLevel overworld, long towerId) {
        OrsaStructureState state = OrsaStructureState.get(overworld.getServer());
        OrsaStructureState.TowerRecord tower = state.getTowerById(towerId);
        if (tower == null || tower.placed()) {
            return false;
        }

        if (!state.removeUnplacedTower(towerId)) {
            return false;
        }

        TowerSearchSession search = SEARCH_SESSIONS.get(overworld.getServer());
        if (search != null && search.sectorIndex == tower.sectorIndex()) {
            SEARCH_SESSIONS.remove(overworld.getServer());
        }
        state.incrementTowerInitPass();
        return true;
    }

    public static void reset() {
        SEARCH_SESSIONS.clear();
    }

    private static void stepSearch(ServerLevel overworld, OrsaStructureState state,
                                   TowerSearchSession search, int coarseBudget) {
        while (!search.coarseComplete && coarseBudget-- > 0) {
            int distance = search.distance;
            int angleSteps = search.angleSteps;
            double angleT = angleSteps == 1 ? 0.5D : (double) search.angleStep / (double) (angleSteps - 1);
            double angle = (search.sectorAngle - (search.sectorWidth * 0.48D))
                    + (search.sectorWidth * 0.96D * angleT);
            BlockPos spawn = overworld.getSharedSpawnPos();
            int x = spawn.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = spawn.getZ() + Mth.floor(Math.sin(angle) * distance);
            LandmarkCandidateEvaluator.ExactTargetCandidate candidate =
                    LandmarkCandidateEvaluator.evaluateTowerCandidate(overworld, x, z, distance, angle,
                            search.sectorAngle, TARGET_DISTANCE);
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
                    search.angleSteps = towerAngleSteps(search.distance, search.sectorWidth);
                }
            }
        }

        if (search.coarseComplete && !search.topCandidates.isEmpty()) {
            LandmarkCandidateEvaluator.ExactTargetCandidate chosen = search.topCandidates.get(
                    Math.floorMod(state.getTowerInitPass(), search.topCandidates.size()));
            BlockPos plannedAnchor = chosen.pos();
            BlockPos blastPit = state.getBlastPitTargetPos() != null ? state.getBlastPitTargetPos() : state.getBlastPitPos();
            if (blastPit != null && LandmarkCandidateEvaluator.flatDistanceSq(plannedAnchor, blastPit)
                    < (long) BLAST_PIT_BUFFER * BLAST_PIT_BUFFER) {
                FrozenDawn.LOGGER.info("Tower chooser pass {} sector {} best final candidate was too close to Blast Pit; rerolling",
                        state.getTowerInitPass(), search.sectorIndex);
                state.incrementTowerInitPass();
                SEARCH_SESSIONS.remove(overworld.getServer());
                return;
            }

            boolean tooClose = false;
            for (OrsaStructureState.TowerRecord record : state.getTowers()) {
                if (record.sectorIndex() == search.sectorIndex) {
                    continue;
                }
                if (LandmarkCandidateEvaluator.flatDistanceSq(plannedAnchor, record.anchorPos())
                        < (long) MIN_SEPARATION * MIN_SEPARATION) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                FrozenDawn.LOGGER.info("Tower chooser pass {} sector {} best final candidate violated tower separation; rerolling",
                        state.getTowerInitPass(), search.sectorIndex);
                state.incrementTowerInitPass();
                SEARCH_SESSIONS.remove(overworld.getServer());
                return;
            }

            long id = encodeTowerId(plannedAnchor.getX(), plannedAnchor.getZ());
            if (state.getTowerById(id) != null) {
                FrozenDawn.LOGGER.info("Tower chooser pass {} sector {} best final candidate duplicated existing tower id; rerolling",
                        state.getTowerInitPass(), search.sectorIndex);
                state.incrementTowerInitPass();
                SEARCH_SESSIONS.remove(overworld.getServer());
                return;
            }

            state.addPlannedTower(id, search.sectorIndex, plannedAnchor);
            FrozenDawn.LOGGER.info("Communication Tower final anchor chosen for sector {} at ({}, {}, {})",
                    search.sectorIndex, plannedAnchor.getX(), plannedAnchor.getY(), plannedAnchor.getZ());
            SEARCH_SESSIONS.remove(overworld.getServer());
            return;
        }

        if (search.coarseComplete) {
            FrozenDawn.LOGGER.info(
                    "Tower chooser pass {} sector {} failed. best final candidate={}",
                    state.getTowerInitPass(),
                    search.sectorIndex,
                    search.bestCandidate != null
                            ? "(" + search.bestCandidate.pos().getX() + ", "
                            + search.bestCandidate.pos().getY() + ", "
                            + search.bestCandidate.pos().getZ() + ")"
                            : "none");
            state.incrementTowerInitPass();
            SEARCH_SESSIONS.remove(overworld.getServer());
        }
    }

    private static int pickNextMissingTowerSector(OrsaStructureState state) {
        int startSector = Math.floorMod(state.getTowerInitPass(), TARGET_COUNT);
        for (int offset = 0; offset < TARGET_COUNT; offset++) {
            int sector = (startSector + offset) % TARGET_COUNT;
            if (state.getTowerBySectorIndex(sector) == null) {
                return sector;
            }
        }
        return -1;
    }

    private static long encodeTowerId(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int towerAngleSteps(int distance, double sectorWidth) {
        return Math.max(4, Mth.ceil((float) ((sectorWidth * distance) / 512.0D)));
    }

    private static final class TowerSearchSession {
        private final int sectorIndex;
        private final double sectorAngle;
        private final double sectorWidth;
        private final List<LandmarkCandidateEvaluator.ExactTargetCandidate> topCandidates = new ArrayList<>();
        private final Set<Long> seenCandidates = new HashSet<>();
        private int distance = MIN_DISTANCE;
        private int angleStep;
        private int angleSteps;
        private boolean coarseComplete;
        private LandmarkCandidateEvaluator.ExactTargetCandidate bestCandidate;

        private TowerSearchSession(int sectorIndex) {
            this.sectorIndex = sectorIndex;
            this.sectorWidth = (Math.PI * 2.0D) / TARGET_COUNT;
            this.sectorAngle = (Math.PI * 2.0D * sectorIndex) / TARGET_COUNT;
            this.angleSteps = towerAngleSteps(distance, sectorWidth);
        }
    }
}
