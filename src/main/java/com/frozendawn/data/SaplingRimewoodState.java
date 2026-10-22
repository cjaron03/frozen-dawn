package com.frozendawn.data;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Trunk bases of Rimewoods grown from saplings, so a sighting can tell them from natural groves. */
public final class SaplingRimewoodState extends SavedData {
    private final Set<Long> trunks = new HashSet<>();
    public static SaplingRimewoodState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(SaplingRimewoodState::new, SaplingRimewoodState::load), "frozendawn_sapling_rimewood");
    }
    public void mark(BlockPos base) { if (trunks.add(base.asLong())) setDirty(); }
    public void forget(BlockPos base) { if (trunks.remove(base.asLong())) setDirty(); }
    public boolean grewFromSapling(BlockPos base) { return trunks.contains(base.asLong()); }
    public static SaplingRimewoodState load(CompoundTag tag, HolderLookup.Provider lookup) {
        var state = new SaplingRimewoodState();
        for (long packed : tag.getLongArray("Trunks")) state.trunks.add(packed);
        return state;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putLongArray("Trunks", trunks.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
