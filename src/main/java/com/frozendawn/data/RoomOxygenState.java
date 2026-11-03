package com.frozendawn.data;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Breathable oxygen only. Never emits pressure/air/thermal events or refills room gas. */
public final class RoomOxygenState extends SavedData {
    public static final String NAME = "frozendawn_room_oxygen";
    private final Set<Long> unavailable = new HashSet<>();
    public static RoomOxygenState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RoomOxygenState::new, RoomOxygenState::load), NAME);
    }
    public static RoomOxygenState load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new RoomOxygenState();
        for (long cell : tag.getLongArray("unavailable")) result.unavailable.add(cell);
        return result;
    }
    public boolean unavailableAt(BlockPos pos) { return unavailable.contains(pos.asLong()); }
    public boolean isUnavailable(Set<BlockPos> cells) { return cells.stream().anyMatch(this::unavailableAt); }
    public void setUnavailable(Set<BlockPos> cells, boolean value) {
        boolean changed = false;
        for (var pos : cells) changed |= value ? unavailable.add(pos.asLong()) : unavailable.remove(pos.asLong());
        if (changed) setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("unavailable", unavailable.stream().mapToLong(Long::longValue).sorted().toArray());
        return tag;
    }
}
