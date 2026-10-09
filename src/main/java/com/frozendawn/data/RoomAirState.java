package com.frozendawn.data;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Depleted room cells persist: saving or replacing a wall cannot create air. */
public final class RoomAirState extends SavedData {
    private final Set<Long> depleted = new HashSet<>();
    public static RoomAirState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RoomAirState::new, RoomAirState::load),
                "frozendawn_room_air");
    }
    public static RoomAirState load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new RoomAirState();
        for (long cell : tag.getLongArray("depleted")) result.depleted.add(cell);
        return result;
    }
    public boolean isDepleted(Set<BlockPos> cells) {
        return cells.stream().anyMatch(pos -> depleted.contains(pos.asLong()));
    }
    public void evacuate(Set<BlockPos> cells) {
        boolean changed = false;
        for (var cell : cells) changed |= depleted.add(cell.asLong());
        if (changed) setDirty();
    }
    public void refill(Set<BlockPos> cells) {
        boolean changed = false;
        for (var cell : cells) changed |= depleted.remove(cell.asLong());
        if (changed) setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("depleted", depleted.stream().mapToLong(Long::longValue).sorted().toArray());
        return tag;
    }
}
