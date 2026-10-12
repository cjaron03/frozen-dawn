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
    public static final String NAME = "frozendawn_room_air";
    private final Set<Long> depleted = new HashSet<>();
    private ServerLevel owner; // Runtime binding only; NBT load never emits transitions.
    public static RoomAirState get(ServerLevel level) {
        var state = level.getDataStorage().computeIfAbsent(new Factory<>(RoomAirState::new, RoomAirState::load), NAME);
        state.owner = level;
        return state;
    }
    public static RoomAirState load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new RoomAirState();
        for (long cell : tag.getLongArray("depleted")) result.depleted.add(cell);
        return result;
    }
    public boolean isDepleted(Set<BlockPos> cells) {
        return cells.stream().anyMatch(pos -> depleted.contains(pos.asLong()));
    }
    public void evacuate(Set<BlockPos> cells) { change(cells, true); }
    public void refill(Set<BlockPos> cells) { change(cells, false); }
    private void change(Set<BlockPos> cells, boolean evacuate) {
        var changed = new HashSet<BlockPos>();
        for (var cell : cells)
            if (evacuate ? depleted.add(cell.asLong()) : depleted.remove(cell.asLong())) changed.add(cell.immutable());
        if (changed.isEmpty()) return;
        setDirty();
        if (owner != null) com.frozendawn.world.RoomAtmosphere.airChanged(owner, changed, evacuate);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("depleted", depleted.stream().mapToLong(Long::longValue).sorted().toArray());
        return tag;
    }
}
