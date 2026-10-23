package com.frozendawn.world;

import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Saved regional cooldown; loading/unloading a cave never grants a new spawn attempt. */
public final class BlindSpawnMemory extends SavedData {
    private static final String NAME = "frozendawn_blind_spawns";
    private final LinkedHashMap<Long, Long> cooldowns = new LinkedHashMap<>();
    public static BlindSpawnMemory get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(BlindSpawnMemory::new, BlindSpawnMemory::load, DataFixTypes.LEVEL), NAME);
    }
    private static long key(BlockPos pos) { return new BlockPos(Math.floorDiv(pos.getX(), 128), 0, Math.floorDiv(pos.getZ(), 128)).asLong(); }
    public boolean ready(BlockPos pos, long now) {
        boolean expired = cooldowns.entrySet().removeIf(e -> e.getValue() <= now); if (expired) setDirty();
        return cooldowns.size() < 256 && cooldowns.getOrDefault(key(pos), 0L) <= now;
    }
    public void spawned(BlockPos pos, long now) {
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        if (cooldowns.size() >= 256 && !cooldowns.containsKey(key(pos))) return;
        cooldowns.put(key(pos), now + 12000); setDirty();
    }
    private static BlindSpawnMemory load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new BlindSpawnMemory();
        for (Tag raw : tag.getList("cooldowns", Tag.TAG_COMPOUND)) {
            if (data.cooldowns.size() >= 256) break;
            var row = (CompoundTag) raw; if (row.getLong("until") >= 0) data.cooldowns.put(row.getLong("region"), row.getLong("until"));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag(); cooldowns.forEach((region, until) -> {
            var row = new CompoundTag(); row.putLong("region", region); row.putLong("until", until); list.add(row);
        }); tag.put("cooldowns", list); return tag;
    }
}
