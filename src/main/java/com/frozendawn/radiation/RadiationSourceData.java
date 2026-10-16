package com.frozendawn.radiation;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent block sources (Corium, molten Corium, fallout), indexed by chunk for bounded nearby lookups. */
public final class RadiationSourceData extends SavedData {
    public static final String NAME = "frozendawn_radiation_sources";
    public static final int MAX_SOURCES = 50_000;
    /** Appended only: ordinals are saved. */
    public enum Kind { CORIUM, MOLTEN_CORIUM, FALLOUT, DEBRIS, FALLOUT_SEVERE }
    private final Map<Long, Kind> sources = new HashMap<>();
    private final Map<Long, Set<Long>> chunks = new HashMap<>();

    public static RadiationSourceData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RadiationSourceData::new, RadiationSourceData::load), NAME);
    }
    public void put(BlockPos pos, Kind kind) {
        long key = pos.asLong();
        if (kind.equals(sources.get(key)) || sources.size() >= MAX_SOURCES && !sources.containsKey(key)) return;
        sources.put(key, kind); chunks.computeIfAbsent(ChunkPos.asLong(pos), c -> new HashSet<>()).add(key); setDirty();
    }
    public void remove(BlockPos pos) {
        long key = pos.asLong();
        if (sources.remove(key) == null) return;
        var set = chunks.get(ChunkPos.asLong(pos)); if (set != null) { set.remove(key); if (set.isEmpty()) chunks.remove(ChunkPos.asLong(pos)); }
        setDirty();
    }
    public Kind kind(BlockPos pos) { return sources.get(pos.asLong()); }
    public int size() { return sources.size(); }
    /** Sources within radius of a point, visiting only nearby chunks. */
    public void near(BlockPos center, int radius, java.util.function.BiConsumer<BlockPos, Kind> visit) {
        int r2 = radius * radius;
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++)
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                var set = chunks.get(ChunkPos.asLong(cx, cz)); if (set == null) continue;
                for (long key : set) { var p = BlockPos.of(key); if (p.distSqr(center) <= r2) visit.accept(p, sources.get(key)); }
            }
    }
    public static RadiationSourceData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new RadiationSourceData();
        for (var item : tag.getList("sources", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) item; int kind = entry.getByte("k");
            if (kind >= 0 && kind < Kind.values().length) data.put(BlockPos.of(entry.getLong("p")), Kind.values()[kind]);
        }
        data.setDirty(false); return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        sources.forEach((key, kind) -> { var e = new CompoundTag(); e.putLong("p", key); e.putByte("k", (byte) kind.ordinal()); list.add(e); });
        tag.putInt("version", 1); tag.put("sources", list); return tag;
    }
}
