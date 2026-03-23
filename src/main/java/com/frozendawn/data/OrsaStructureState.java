package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.world.LandmarkBiomeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent world data for ORSA landmark structures.
 * Keeps guaranteed landmark coordinates stable across restarts.
 */
public final class OrsaStructureState extends SavedData {

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_orsa_structures";
    private static final int TOWER_TARGET_COUNT = 6;
    private static final int TOWER_MIN_DISTANCE = 1800;
    private static final int TOWER_MAX_DISTANCE = 6200;
    private static final int TOWER_MIN_SEPARATION = 1400;
    private static final int BLAST_PIT_BUFFER = 900;
    private static final int BLAST_PIT_DISTANCE_STEP = 192;
    private static final int TOWER_DISTANCE_STEP = 192;
    private static final int TOWER_FINAL_CANDIDATE_LIMIT = 24;
    private static final int BLAST_PIT_FINAL_CANDIDATE_LIMIT = 32;
    private static final int BLAST_PIT_COARSE_BUDGET_PER_CALL = 192;
    private static final int TOWER_COARSE_BUDGET_PER_CALL = 192;
    private static final int BLAST_PIT_OUTER_RADIUS = 20;
    private static final int BLAST_PIT_DRY_BUFFER = 12;
    private static final int BLAST_PIT_MAX_HEIGHT_VARIATION = 8;
    private static final int BLAST_PIT_ALLOWED_SAMPLE_FAILURES = 2;
    private static final int TOWER_FOOTPRINT_RADIUS = 18;
    private static final int TOWER_DRY_BUFFER = 12;
    private static final int TOWER_MAX_HEIGHT_VARIATION = 8;
    private static final int TOWER_ALLOWED_SAMPLE_FAILURES = 3;
    private static final int VALIDATION_SAMPLE_STEP = 4;
    private static final long TOWER_SEED_SALT = 0x4F525341544F574EL;
    private static final long BLAST_PIT_SEED_SALT = 0x424C415354504954L;

    private BlockPos blastPitTargetPos;
    private BlockPos blastPitPos;
    private boolean blastPitPlaced;
    private int blastPitSelectionPass;
    private int towerInitPass;
    private final List<TowerRecord> towers = new ArrayList<>();
    private final Set<Long> evaluatedCamps = new HashSet<>();
    private final Set<Long> builtCamps = new HashSet<>();
    private transient BlastPitSearchState blastPitSearch;
    private transient TowerSearchState towerSearch;

    public OrsaStructureState() {
    }

    public static OrsaStructureState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OrsaStructureState::new, OrsaStructureState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static OrsaStructureState load(CompoundTag tag, HolderLookup.Provider registries) {
        OrsaStructureState state = new OrsaStructureState();
        if (tag.contains("blastPitTargetX")) {
            state.blastPitTargetPos = new BlockPos(
                    tag.getInt("blastPitTargetX"),
                    tag.getInt("blastPitTargetY"),
                    tag.getInt("blastPitTargetZ")
            );
        }
        if (tag.contains("blastPitX")) {
            state.blastPitPos = new BlockPos(
                    tag.getInt("blastPitX"),
                    tag.getInt("blastPitY"),
                    tag.getInt("blastPitZ")
            );
        }
        state.blastPitPlaced = tag.getBoolean("blastPitPlaced");
        state.blastPitSelectionPass = tag.getInt("blastPitSelectionPass");
        state.towerInitPass = tag.getInt("towerInitPass");
        if (state.blastPitTargetPos == null && state.blastPitPos != null) {
            state.blastPitTargetPos = state.blastPitPlaced ? state.blastPitPos : state.blastPitPos.immutable();
            if (!state.blastPitPlaced) {
                state.blastPitPos = null;
            }
        }
        if (state.blastPitTargetPos != null && state.blastPitPos == null && state.blastPitTargetPos.getY() <= 0) {
            state.blastPitTargetPos = null;
        }

        ListTag towerList = tag.getList("towers", Tag.TAG_COMPOUND);
        for (Tag towerTag : towerList) {
            if (towerTag instanceof CompoundTag compound) {
                TowerRecord tower = TowerRecord.load(compound);
                if (tower != null) {
                    state.towers.add(tower);
                }
            }
        }
        state.towers.sort(Comparator.comparingLong(TowerRecord::id));

        if (tag.contains("placedCamps")) {
            long[] campArray = tag.getLongArray("placedCamps");
            for (long packed : campArray) {
                state.evaluatedCamps.add(packed);
            }
        }
        if (tag.contains("builtCamps")) {
            long[] campArray = tag.getLongArray("builtCamps");
            for (long packed : campArray) {
                state.builtCamps.add(packed);
                state.evaluatedCamps.add(packed);
            }
        }

        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (blastPitTargetPos != null) {
            tag.putInt("blastPitTargetX", blastPitTargetPos.getX());
            tag.putInt("blastPitTargetY", blastPitTargetPos.getY());
            tag.putInt("blastPitTargetZ", blastPitTargetPos.getZ());
        }
        if (blastPitPos != null) {
            tag.putInt("blastPitX", blastPitPos.getX());
            tag.putInt("blastPitY", blastPitPos.getY());
            tag.putInt("blastPitZ", blastPitPos.getZ());
        }
        tag.putBoolean("blastPitPlaced", blastPitPlaced);
        tag.putInt("blastPitSelectionPass", blastPitSelectionPass);
        tag.putInt("towerInitPass", towerInitPass);

        ListTag towerList = new ListTag();
        for (TowerRecord tower : towers) {
            towerList.add(tower.save());
        }
        tag.put("towers", towerList);

        if (!evaluatedCamps.isEmpty()) {
            tag.putLongArray("placedCamps", evaluatedCamps.stream().mapToLong(Long::longValue).toArray());
        }
        if (!builtCamps.isEmpty()) {
            tag.putLongArray("builtCamps", builtCamps.stream().mapToLong(Long::longValue).toArray());
        }

        return tag;
    }

    /**
     * Pick the guaranteed Blast Pit target if it has not been chosen yet.
     * The target is 1000-3000 blocks from world spawn.
     */
    public void initBlastPitPosition(ServerLevel overworld) {
        if (blastPitTargetPos != null) {
            return;
        }

        if (blastPitSearch == null) {
            blastPitSearch = new BlastPitSearchState();
        }

        stepBlastPitSearch(overworld, BLAST_PIT_COARSE_BUDGET_PER_CALL);
    }

    private void stepBlastPitSearch(ServerLevel overworld, int coarseBudget) {
        if (blastPitSearch == null || blastPitTargetPos != null) {
            return;
        }

        while (!blastPitSearch.coarseComplete && coarseBudget-- > 0) {
            int distance = blastPitSearch.distance;
            int angleSteps = blastPitSearch.angleSteps;
            double angle = ((double) blastPitSearch.angleStep / (double) angleSteps) * (Math.PI * 2.0D);
            BlockPos spawn = overworld.getSharedSpawnPos();
            int x = spawn.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = spawn.getZ() + Mth.floor(Math.sin(angle) * distance);
            ExactTargetCandidate candidate = evaluateBlastPitCandidate(overworld, x, z, distance);
            if (candidate != null) {
                if (blastPitSearch.bestCandidate == null || candidate.compareTo(blastPitSearch.bestCandidate) < 0) {
                    blastPitSearch.bestCandidate = candidate;
                }
                long id = BlockPos.asLong(candidate.pos().getX(), candidate.pos().getY(), candidate.pos().getZ());
                if (blastPitSearch.seenCandidates.add(id)) {
                    addTopCandidate(blastPitSearch.topCandidates, candidate, BLAST_PIT_FINAL_CANDIDATE_LIMIT);
                }
            }

            blastPitSearch.angleStep++;
            if (blastPitSearch.angleStep >= angleSteps) {
                blastPitSearch.distance += BLAST_PIT_DISTANCE_STEP;
                if (blastPitSearch.distance > 3000) {
                    blastPitSearch.coarseComplete = true;
                } else {
                    blastPitSearch.angleStep = 0;
                    blastPitSearch.angleSteps = blastPitAngleSteps(blastPitSearch.distance);
                }
            }
        }

        if (blastPitSearch.coarseComplete && !blastPitSearch.topCandidates.isEmpty()) {
            ExactTargetCandidate chosen = blastPitSearch.topCandidates.get(Math.floorMod(blastPitSelectionPass, blastPitSearch.topCandidates.size()));
            blastPitTargetPos = chosen.pos();
            blastPitSearch = null;
            setDirty();
            FrozenDawn.LOGGER.info("Blast Pit final anchor chosen at ({}, {}, {}), distance {} from spawn",
                    blastPitTargetPos.getX(), blastPitTargetPos.getY(), blastPitTargetPos.getZ(),
                    (int) Math.sqrt(overworld.getSharedSpawnPos().distSqr(blastPitTargetPos)));
            return;
        }

        if (blastPitSearch.coarseComplete && blastPitSearch.topCandidates.isEmpty()) {
            FrozenDawn.LOGGER.info(
                    "Blast Pit chooser pass {} failed. best final candidate={}",
                    blastPitSelectionPass,
                    blastPitSearch.bestCandidate != null
                            ? "(" + blastPitSearch.bestCandidate.pos().getX() + ", "
                            + blastPitSearch.bestCandidate.pos().getY() + ", "
                            + blastPitSearch.bestCandidate.pos().getZ() + ")"
                            : "none");
            blastPitSelectionPass++;
            blastPitSearch = null;
        }
    }

    public void rerollBlastPitPosition(ServerLevel overworld) {
        blastPitTargetPos = null;
        blastPitPos = null;
        blastPitPlaced = false;
        blastPitSelectionPass++;
        blastPitSearch = null;
        setDirty();
    }

    public void initTowerPositions(ServerLevel overworld) {
        if (towers.size() >= TOWER_TARGET_COUNT) {
            towerSearch = null;
            return;
        }

        if (towerSearch == null) {
            int missingSector = pickNextMissingTowerSector();
            if (missingSector < 0) {
                return;
            }
            towerSearch = new TowerSearchState(missingSector);
        }

        stepTowerSearch(overworld, TOWER_COARSE_BUDGET_PER_CALL);
    }

    private void stepTowerSearch(ServerLevel overworld, int coarseBudget) {
        if (towerSearch == null) {
            return;
        }

        while (!towerSearch.coarseComplete && coarseBudget-- > 0) {
            int distance = towerSearch.distance;
            int angleSteps = towerSearch.angleSteps;
            double angleT = angleSteps == 1 ? 0.5D : (double) towerSearch.angleStep / (double) (angleSteps - 1);
            double angle = (towerSearch.sectorAngle - (towerSearch.sectorWidth * 0.48D))
                    + (towerSearch.sectorWidth * 0.96D * angleT);
            BlockPos spawn = overworld.getSharedSpawnPos();
            int x = spawn.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = spawn.getZ() + Mth.floor(Math.sin(angle) * distance);
            ExactTargetCandidate candidate = evaluateTowerCandidate(overworld, x, z, distance, angle, towerSearch.sectorAngle);
            if (candidate != null) {
                if (towerSearch.bestCandidate == null || candidate.compareTo(towerSearch.bestCandidate) < 0) {
                    towerSearch.bestCandidate = candidate;
                }
                long id = BlockPos.asLong(candidate.pos().getX(), candidate.pos().getY(), candidate.pos().getZ());
                if (towerSearch.seenCandidates.add(id)) {
                    addTopCandidate(towerSearch.topCandidates, candidate, TOWER_FINAL_CANDIDATE_LIMIT);
                }
            }

            towerSearch.angleStep++;
            if (towerSearch.angleStep >= angleSteps) {
                towerSearch.distance += TOWER_DISTANCE_STEP;
                if (towerSearch.distance > TOWER_MAX_DISTANCE) {
                    towerSearch.coarseComplete = true;
                } else {
                    towerSearch.angleStep = 0;
                    towerSearch.angleSteps = towerAngleSteps(towerSearch.distance, towerSearch.sectorWidth);
                }
            }
        }

        if (towerSearch.coarseComplete && !towerSearch.topCandidates.isEmpty()) {
            ExactTargetCandidate chosen = towerSearch.topCandidates.get(
                    Math.floorMod(towerInitPass, towerSearch.topCandidates.size()));
            BlockPos plannedAnchor = chosen.pos();
            BlockPos blastPit = blastPitTargetPos != null ? blastPitTargetPos : blastPitPos;
            if (blastPit != null && flatDistanceSq(plannedAnchor, blastPit) < (long) BLAST_PIT_BUFFER * BLAST_PIT_BUFFER) {
                FrozenDawn.LOGGER.info("Tower chooser pass {} sector {} best final candidate was too close to Blast Pit; rerolling",
                        towerInitPass, towerSearch.sectorIndex);
                towerInitPass++;
                towerSearch = null;
                return;
            }

            boolean tooClose = false;
            for (TowerRecord record : towers) {
                if (record.sectorIndex == towerSearch.sectorIndex) {
                    continue;
                }
                if (flatDistanceSq(plannedAnchor, record.anchorPos()) < (long) TOWER_MIN_SEPARATION * TOWER_MIN_SEPARATION) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                FrozenDawn.LOGGER.info("Tower chooser pass {} sector {} best final candidate violated tower separation; rerolling",
                        towerInitPass, towerSearch.sectorIndex);
                towerInitPass++;
                towerSearch = null;
                return;
            }

            long id = encodeTowerId(plannedAnchor.getX(), plannedAnchor.getZ());
            if (getTowerById(id) != null) {
                FrozenDawn.LOGGER.info("Tower chooser pass {} sector {} best final candidate duplicated existing tower id; rerolling",
                        towerInitPass, towerSearch.sectorIndex);
                towerInitPass++;
                towerSearch = null;
                return;
            }

            TowerRecord tower = new TowerRecord(id, towerSearch.sectorIndex, plannedAnchor);
            towers.add(tower);
            towers.sort(Comparator.comparingLong(TowerRecord::id));
            setDirty();
            FrozenDawn.LOGGER.info("Communication Tower final anchor chosen for sector {} at ({}, {}, {})",
                    towerSearch.sectorIndex, plannedAnchor.getX(), plannedAnchor.getY(), plannedAnchor.getZ());
            towerSearch = null;
            return;
        }

        if (towerSearch.coarseComplete && towerSearch.topCandidates.isEmpty()) {
            FrozenDawn.LOGGER.info(
                    "Tower chooser pass {} sector {} failed. best final candidate={}",
                    towerInitPass,
                    towerSearch.sectorIndex,
                    towerSearch.bestCandidate != null
                            ? "(" + towerSearch.bestCandidate.pos().getX() + ", "
                            + towerSearch.bestCandidate.pos().getY() + ", "
                            + towerSearch.bestCandidate.pos().getZ() + ")"
                            : "none");
            towerInitPass++;
            towerSearch = null;
        }
    }

    private int pickNextMissingTowerSector() {
        int startSector = Math.floorMod(towerInitPass, TOWER_TARGET_COUNT);
        for (int offset = 0; offset < TOWER_TARGET_COUNT; offset++) {
            int sector = (startSector + offset) % TOWER_TARGET_COUNT;
            if (getTowerBySectorIndex(sector) == null) {
                return sector;
            }
        }
        return -1;
    }

    private ExactTargetCandidate evaluateBlastPitCandidate(ServerLevel overworld, int centerX, int centerZ, int distance) {
        int centerY = worldgenSurfaceY(overworld, centerX, centerZ);
        if (centerY <= overworld.getMinBuildHeight() + 1 || !isEligibleLandmarkCenterBiome(overworld, centerX, centerZ)) {
            return null;
        }

        // Stage 1 is intentionally cheap: center-only biome and surface checks.
        // Loaded-terrain placement does the real footprint/water validation.
        return new ExactTargetCandidate(new BlockPos(centerX, centerY, centerZ), 0,
                Math.abs(distance - blastPitSearch.targetDistance), 0);
    }

    private ExactTargetCandidate evaluateTowerCandidate(ServerLevel overworld, int centerX, int centerZ,
                                                        int distance, double angle, double sectorAngle) {
        int centerY = worldgenSurfaceY(overworld, centerX, centerZ);
        if (centerY <= overworld.getMinBuildHeight() + 1 || !isEligibleLandmarkCenterBiome(overworld, centerX, centerZ)) {
            return null;
        }

        // Stage 1 is intentionally cheap: center-only biome and surface checks.
        // Loaded-terrain placement does the real footprint/water validation.
        return new ExactTargetCandidate(new BlockPos(centerX, centerY, centerZ), 0,
                Math.abs(distance - towerSearch.targetDistance),
                (int) Math.round(Math.abs(angle - sectorAngle) * 1000.0D));
    }

    private boolean isEligibleLandmarkCenterBiome(ServerLevel overworld, int x, int z) {
        return LandmarkBiomeRules.isEligibleLandmarkBiome(overworld, x, z);
    }

    private boolean isToleratedLandmarkFootprintBiome(ServerLevel overworld, int x, int z) {
        return LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(overworld, x, z);
    }

    private int worldgenSurfaceY(ServerLevel overworld, int x, int z) {
        var chunkSource = overworld.getChunkSource();
        return chunkSource.getGenerator().getBaseHeight(
                x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, overworld, chunkSource.randomState());
    }

    private boolean hasWorldgenWater(ServerLevel overworld, int x, int z) {
        var chunkSource = overworld.getChunkSource();
        int surfaceY = chunkSource.getGenerator().getBaseHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, overworld, chunkSource.randomState());
        int oceanFloorY = chunkSource.getGenerator().getBaseHeight(
                x, z, Heightmap.Types.OCEAN_FLOOR_WG, overworld, chunkSource.randomState());
        return surfaceY != oceanFloorY;
    }

    private static int blastPitAngleSteps(int distance) {
        return Math.max(12, Mth.ceil((float) ((Math.PI * 2.0D * distance) / 512.0D)));
    }

    private static int towerAngleSteps(int distance, double sectorWidth) {
        return Math.max(4, Mth.ceil((float) ((sectorWidth * distance) / 512.0D)));
    }

    private static void addTopCandidate(List<ExactTargetCandidate> candidates, ExactTargetCandidate candidate, int limit) {
        candidates.add(candidate);
        candidates.sort(ExactTargetCandidate::compareTo);
        if (candidates.size() > limit) {
            candidates.remove(candidates.size() - 1);
        }
    }

    public boolean rerollTowerPosition(ServerLevel overworld, long towerId) {
        TowerRecord tower = getTowerById(towerId);
        if (tower == null || tower.placed) {
            return false;
        }

        int sectorIndex = tower.sectorIndex;
        towers.remove(tower);
        if (towerSearch != null && towerSearch.sectorIndex == sectorIndex) {
            towerSearch = null;
        }
        towerInitPass++;
        setDirty();
        return true;
    }

    private static final class BlastPitSearchState {
        private final int targetDistance = 2000;
        private final List<ExactTargetCandidate> topCandidates = new ArrayList<>();
        private final Set<Long> seenCandidates = new HashSet<>();
        private int distance = 1000;
        private int angleStep;
        private int angleSteps = blastPitAngleSteps(distance);
        private boolean coarseComplete;
        private ExactTargetCandidate bestCandidate;
    }

    private static final class TowerSearchState {
        private final int sectorIndex;
        private final double sectorAngle;
        private final double sectorWidth;
        private final int targetDistance;
        private final List<ExactTargetCandidate> topCandidates = new ArrayList<>();
        private final Set<Long> seenCandidates = new HashSet<>();
        private int distance = TOWER_MIN_DISTANCE;
        private int angleStep;
        private int angleSteps;
        private boolean coarseComplete;
        private ExactTargetCandidate bestCandidate;

        private TowerSearchState(int sectorIndex) {
            this.sectorIndex = sectorIndex;
            this.sectorWidth = (Math.PI * 2.0D) / TOWER_TARGET_COUNT;
            this.sectorAngle = (Math.PI * 2.0D * sectorIndex) / TOWER_TARGET_COUNT;
            this.targetDistance = (TOWER_MIN_DISTANCE + TOWER_MAX_DISTANCE) / 2;
            this.angleSteps = towerAngleSteps(distance, sectorWidth);
        }
    }

    private record ExactTargetCandidate(BlockPos pos, int heightVariation, int distancePenalty, int anglePenalty)
            implements Comparable<ExactTargetCandidate> {

        @Override
        public int compareTo(ExactTargetCandidate other) {
            int variationOrder = Integer.compare(this.heightVariation, other.heightVariation);
            if (variationOrder != 0) {
                return variationOrder;
            }
            int distanceOrder = Integer.compare(this.distancePenalty, other.distancePenalty);
            if (distanceOrder != 0) {
                return distanceOrder;
            }
            int angleOrder = Integer.compare(this.anglePenalty, other.anglePenalty);
            if (angleOrder != 0) {
                return angleOrder;
            }
            int xOrder = Integer.compare(this.pos.getX(), other.pos.getX());
            if (xOrder != 0) {
                return xOrder;
            }
            return Integer.compare(this.pos.getZ(), other.pos.getZ());
        }
    }

    public boolean isCampEvaluated(int chunkX, int chunkZ) {
        return evaluatedCamps.contains(packCampChunkPos(chunkX, chunkZ));
    }

    public boolean isCampBuilt(int chunkX, int chunkZ) {
        return builtCamps.contains(packCampChunkPos(chunkX, chunkZ));
    }

    public void markCampEvaluated(int chunkX, int chunkZ) {
        evaluatedCamps.add(packCampChunkPos(chunkX, chunkZ));
        setDirty();
    }

    public void markCampBuilt(int chunkX, int chunkZ) {
        long key = packCampChunkPos(chunkX, chunkZ);
        evaluatedCamps.add(key);
        builtCamps.add(key);
        setDirty();
    }

    private static long packCampChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public BlockPos getBlastPitPos() {
        return blastPitPos;
    }

    public BlockPos getBlastPitTargetPos() {
        return blastPitTargetPos;
    }

    public boolean isBlastPitPlaced() {
        return blastPitPlaced;
    }

    public void setBlastPitPos(BlockPos blastPitPos) {
        this.blastPitPos = blastPitPos;
        setDirty();
    }

    public void setBlastPitPlaced(boolean blastPitPlaced) {
        this.blastPitPlaced = blastPitPlaced;
        setDirty();
    }

    public List<TowerRecord> getTowers() {
        return List.copyOf(towers);
    }

    public TowerRecord getTowerById(long id) {
        for (TowerRecord tower : towers) {
            if (tower.id == id) {
                return tower;
            }
        }
        return null;
    }

    public TowerRecord getTowerBySectorIndex(int sectorIndex) {
        for (TowerRecord tower : towers) {
            if (tower.sectorIndex == sectorIndex) {
                return tower;
            }
        }
        return null;
    }

    public TowerRecord getNearestTower(BlockPos origin) {
        TowerRecord nearest = null;
        double best = Double.MAX_VALUE;
        for (TowerRecord tower : towers) {
            BlockPos sample = tower.anchorPos();
            double dist = origin.distSqr(sample);
            if (dist < best) {
                best = dist;
                nearest = tower;
            }
        }
        return nearest;
    }

    public TowerRecord getNearestResolvedTower(BlockPos origin) {
        TowerRecord nearest = null;
        double best = Double.MAX_VALUE;
        for (TowerRecord tower : towers) {
            if (tower.pos == null) {
                continue;
            }
            double dist = origin.distSqr(tower.pos);
            if (dist < best) {
                best = dist;
                nearest = tower;
            }
        }
        return nearest;
    }

    public TowerRecord findTowerNear(BlockPos pos, int horizontalRadius) {
        long radiusSq = (long) horizontalRadius * horizontalRadius;
        for (TowerRecord tower : towers) {
            if (tower.pos == null) {
                continue;
            }
            if (flatDistanceSq(tower.anchorPos(), pos) <= radiusSq) {
                return tower;
            }
        }
        return null;
    }

    public void setTowerPlaced(long towerId, BlockPos placedPos) {
        TowerRecord tower = getTowerById(towerId);
        if (tower == null) {
            return;
        }
        tower.pos = placedPos;
        tower.placed = true;
        setDirty();
    }

    public void setTowerResolvedPos(long towerId, BlockPos resolvedPos) {
        TowerRecord tower = getTowerById(towerId);
        if (tower == null) {
            return;
        }
        tower.pos = resolvedPos;
        setDirty();
    }

    public void removeTower(long towerId) {
        if (towers.removeIf(tower -> tower.id == towerId)) {
            setDirty();
        }
    }

    public void setTowerArchitectTriggered(long towerId, boolean architectTriggered) {
        TowerRecord tower = getTowerById(towerId);
        if (tower == null || tower.architectTriggered == architectTriggered) {
            return;
        }
        tower.architectTriggered = architectTriggered;
        setDirty();
    }

    public void setTowerArchitectResolved(long towerId, boolean architectResolved) {
        TowerRecord tower = getTowerById(towerId);
        if (tower == null || tower.architectResolved == architectResolved) {
            return;
        }
        tower.architectResolved = architectResolved;
        setDirty();
    }

    public void setTowerAligned(long towerId, boolean aligned) {
        TowerRecord tower = getTowerById(towerId);
        if (tower == null || tower.aligned == aligned) {
            return;
        }
        tower.aligned = aligned;
        setDirty();
    }

    public void setTowerRewardGranted(long towerId, boolean rewardGranted) {
        TowerRecord tower = getTowerById(towerId);
        if (tower == null || tower.rewardGranted == rewardGranted) {
            return;
        }
        tower.rewardGranted = rewardGranted;
        setDirty();
    }

    private static long encodeTowerId(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long flatDistanceSq(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    public static final class TowerRecord {
        private final long id;
        private final int sectorIndex;
        private BlockPos plannedPos;
        private BlockPos pos;
        private boolean placed;
        private boolean architectTriggered;
        private boolean architectResolved;
        private boolean aligned;
        private boolean rewardGranted;
        private int rerollCount;

        private TowerRecord(long id, int sectorIndex, BlockPos plannedPos) {
            this.id = id;
            this.sectorIndex = sectorIndex;
            this.plannedPos = plannedPos;
        }

        public long id() {
            return id;
        }

        public int sectorIndex() {
            return sectorIndex;
        }

        public BlockPos pos() {
            return pos;
        }

        public BlockPos plannedPos() {
            return plannedPos;
        }

        public BlockPos anchorPos() {
            return pos != null ? pos : plannedPos;
        }

        public boolean placed() {
            return placed;
        }

        public boolean architectTriggered() {
            return architectTriggered;
        }

        public boolean architectResolved() {
            return architectResolved;
        }

        public boolean aligned() {
            return aligned;
        }

        public boolean rewardGranted() {
            return rewardGranted;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("id", id);
            tag.putInt("sectorIndex", sectorIndex);
            tag.putInt("plannedX", plannedPos.getX());
            tag.putInt("plannedY", plannedPos.getY());
            tag.putInt("plannedZ", plannedPos.getZ());
            if (pos != null) {
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
            }
            tag.putBoolean("placed", placed);
            tag.putBoolean("architectTriggered", architectTriggered);
            tag.putBoolean("architectResolved", architectResolved);
            tag.putBoolean("aligned", aligned);
            tag.putBoolean("rewardGranted", rewardGranted);
            tag.putInt("rerollCount", rerollCount);
            return tag;
        }

        private static TowerRecord load(CompoundTag tag) {
            BlockPos planned = tag.contains("plannedX")
                    ? new BlockPos(tag.getInt("plannedX"), tag.getInt("plannedY"), tag.getInt("plannedZ"))
                    : new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            if (!tag.getBoolean("placed") && planned.getY() <= 0) {
                return null;
            }
            TowerRecord tower = new TowerRecord(
                    tag.getLong("id"),
                    tag.contains("sectorIndex") ? tag.getInt("sectorIndex") : 0,
                    planned
            );
            if (tag.contains("x")) {
                tower.pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            } else if (tag.getBoolean("placed")) {
                tower.pos = planned;
            }
            tower.placed = tag.getBoolean("placed");
            tower.architectTriggered = tag.getBoolean("architectTriggered");
            tower.architectResolved = tag.getBoolean("architectResolved");
            tower.aligned = tag.getBoolean("aligned");
            tower.rewardGranted = tag.getBoolean("rewardGranted");
            tower.rerollCount = tag.getInt("rerollCount");
            if (!tag.contains("plannedX") && !tower.placed) {
                tower.pos = null;
            }
            return tower;
        }

        @Override
        public String toString() {
            return "TowerRecord{" +
                    "id=" + id +
                    ", plannedPos=" + plannedPos +
                    ", pos=" + pos +
                    ", placed=" + placed +
                    ", architectTriggered=" + architectTriggered +
                    ", architectResolved=" + architectResolved +
                    ", aligned=" + aligned +
                    ", rewardGranted=" + rewardGranted +
                    '}';
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TowerRecord tower)) {
                return false;
            }
            return id == tower.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
