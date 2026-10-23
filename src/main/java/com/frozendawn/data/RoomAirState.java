package com.frozendawn.data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    /** Rebase cell history after a validated topology edit, not a gas transition.
     * Snapshot parent pressure first so split/merge iteration order cannot change the result. */
    public void reconcileMemberships(Collection<RoomIdentityState.Membership> parents, List<Set<BlockPos>> volumes) {
        var emptyParents = parents.stream().filter(parent -> isDepleted(parent.cells())).toList();
        var mixed=owner==null?java.util.Map.<Set<BlockPos>,Integer>of():com.frozendawn.airlock.AirlockSavedState.get(owner).reconcileRoomGas(owner,parents,volumes);
        boolean changed = false;
        for (var volume : volumes) {
            boolean inherited = parents.stream().anyMatch(parent -> !Collections.disjoint(parent.cells(), volume));
            boolean empty = inherited ? emptyParents.stream().anyMatch(parent -> !Collections.disjoint(parent.cells(), volume)) : isDepleted(volume);
            if(mixed.containsKey(volume)) {
                boolean retainedAir=parents.stream().anyMatch(parent->!emptyParents.contains(parent)&&!Collections.disjoint(parent.cells(),volume));
                int gas=mixed.get(volume);
                empty=gas==0||!retainedAir&&gas<(long)volume.size()*com.frozendawn.airlock.AirlockCycle.UNITS_PER_CELL;
            }
            for (var cell : volume)
                changed |= empty ? depleted.add(cell.asLong()) : depleted.remove(cell.asLong());
        }
        if (changed) setDirty(); // The geometry event carries this remap; no fake air/refill notification.
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
