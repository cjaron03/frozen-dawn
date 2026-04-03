package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
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

    private BlockPos blastPitTargetPos;
    private BlockPos blastPitPos;
    private boolean blastPitPlaced;
    private int blastPitSelectionPass;
    private int towerInitPass;
    private final List<TowerRecord> towers = new ArrayList<>();
    private final Set<Long> evaluatedCamps = new HashSet<>();
    private final Set<Long> builtCamps = new HashSet<>();

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

    public int getBlastPitSelectionPass() {
        return blastPitSelectionPass;
    }

    public boolean isBlastPitPlaced() {
        return blastPitPlaced;
    }

    public void setBlastPitTargetPos(BlockPos blastPitTargetPos) {
        BlockPos newTargetPos = blastPitTargetPos != null ? blastPitTargetPos.immutable() : null;
        if (Objects.equals(this.blastPitTargetPos, newTargetPos)) {
            return;
        }
        this.blastPitTargetPos = newTargetPos;
        setDirty();
    }

    public void clearBlastPitPlan() {
        if (blastPitTargetPos == null && blastPitPos == null && !blastPitPlaced) {
            return;
        }
        blastPitTargetPos = null;
        blastPitPos = null;
        blastPitPlaced = false;
        setDirty();
    }

    public void setBlastPitPos(BlockPos blastPitPos) {
        BlockPos newBlastPitPos = blastPitPos != null ? blastPitPos.immutable() : null;
        if (Objects.equals(this.blastPitPos, newBlastPitPos)) {
            return;
        }
        this.blastPitPos = newBlastPitPos;
        setDirty();
    }

    public void setBlastPitPlaced(boolean blastPitPlaced) {
        if (this.blastPitPlaced == blastPitPlaced) {
            return;
        }
        this.blastPitPlaced = blastPitPlaced;
        setDirty();
    }

    public void incrementBlastPitSelectionPass() {
        blastPitSelectionPass++;
        setDirty();
    }

    public int getTowerInitPass() {
        return towerInitPass;
    }

    public void incrementTowerInitPass() {
        towerInitPass++;
        setDirty();
    }

    public List<TowerRecord> getTowers() {
        return List.copyOf(towers);
    }

    public void addPlannedTower(long id, int sectorIndex, BlockPos plannedPos) {
        towers.add(new TowerRecord(id, sectorIndex, plannedPos));
        towers.sort(Comparator.comparingLong(TowerRecord::id));
        setDirty();
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

    public boolean removeUnplacedTower(long towerId) {
        TowerRecord tower = getTowerById(towerId);
        if (tower == null || tower.placed) {
            return false;
        }
        towers.remove(tower);
        setDirty();
        return true;
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
        tower.pos = placedPos != null ? placedPos.immutable() : null;
        tower.placed = true;
        setDirty();
    }

    public void setTowerResolvedPos(long towerId, BlockPos resolvedPos) {
        TowerRecord tower = getTowerById(towerId);
        if (tower == null) {
            return;
        }
        BlockPos newResolvedPos = resolvedPos != null ? resolvedPos.immutable() : null;
        if (Objects.equals(tower.pos, newResolvedPos)) {
            return;
        }
        tower.pos = newResolvedPos;
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
            this.plannedPos = plannedPos.immutable();
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
