package com.frozendawn.data;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
/** Placement order survives reloads and same-tick placement. */
public final class ThermostatOrderState extends SavedData {
    private long next=1;
    public static ThermostatOrderState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(ThermostatOrderState::new,ThermostatOrderState::load),"frozendawn_thermostat_order");
    }
    public long allocate(){long value=next++;setDirty();return value;}
    public void observe(long order){if(next<=order){next=order+1;setDirty();}}
    public static ThermostatOrderState load(CompoundTag tag,HolderLookup.Provider lookup){var state=new ThermostatOrderState();state.next=Math.max(1,tag.getLong("Next"));return state;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){tag.putLong("Next",next);return tag;}
}
