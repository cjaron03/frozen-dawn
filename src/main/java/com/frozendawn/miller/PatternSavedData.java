package com.frozendawn.miller;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Her inner state in the overworld. The file's name is the one thing here a player can see, so it does not say what it is. */
public final class PatternSavedData extends SavedData {
    static final String NAME = "frozendawn_shepherd_state";
    private PatternState state = new PatternState();

    public static PatternSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PatternSavedData::new, PatternSavedData::load, DataFixTypes.LEVEL), NAME);
    }

    PatternState state() { return state; }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return tag.merge(state.save());
    }

    public static PatternSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PatternSavedData();
        data.state = PatternState.load(tag);
        return data;
    }
}
