package com.frozendawn.data;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
/** Prevents duplicate transfers after cache rebuilds/reloads and never performs offline catch-up. */
public final class OxygenTransferState extends SavedData {
    public static final String NAME="frozendawn_o2_transfers";
    private long lastSecond=Long.MIN_VALUE;
    public static OxygenTransferState get(ServerLevel l){return l.getDataStorage().computeIfAbsent(new Factory<>(OxygenTransferState::new,OxygenTransferState::load),NAME);}
    public boolean claim(long ticks){long second=Math.floorDiv(ticks,20);if(second<=lastSecond)return false;lastSecond=second;setDirty();return true;}
    public static OxygenTransferState load(CompoundTag tag,HolderLookup.Provider registries){var s=new OxygenTransferState();if(tag.contains("LastSecond"))s.lastSecond=tag.getLong("LastSecond");return s;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){tag.putLong("LastSecond",lastSecond);return tag;}
}
