package com.frozendawn.maeve;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Separate from the permanent ReturnedHearthSavedData violation ledger (§4). */
final class MaeveSavedData extends SavedData {
    static final String NAME = "frozendawn_maeve";
    private static final int VERSION = 7;
    private boolean activated;
    private boolean erased;
    private BeliefStore store;

    static MaeveSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(MaeveSavedData::new, MaeveSavedData::load, DataFixTypes.LEVEL), NAME);
    }

    void synchronize(boolean authoritativeErased, boolean latePhase) {
        if (authoritativeErased) {
            erase();
            return;
        }
        if (erased) {
            erased = false;
            setDirty();
        }
        if (latePhase && !activated) {
            activated = true;
            setDirty();
        }
        if (activated && store == null) store = new BeliefStore();
    }

    void erase() {
        if (store != null) store.clear();
        if (!erased || store != null) setDirty();
        store = null;
        erased = true;
    }

    String lifecycle() { return erased ? "ERASED" : activated ? "ACTIVE" : "DORMANT"; }
    boolean activated() { return activated; }
    BeliefStore store() { return store; }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", VERSION);
        tag.putBoolean("activated", activated);
        tag.putBoolean("erased", erased);
        tag.remove("beliefs");
        if (!erased && store != null) tag.put("beliefs", store.save());
        return tag;
    }

    static MaeveSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("dataVersion") > VERSION) throw new IllegalStateException("Unsupported future Maeve save version");
        MaeveSavedData data = new MaeveSavedData();
        data.activated = tag.getBoolean("activated");
        data.erased = tag.getBoolean("erased");
        if (data.activated && !data.erased) data.store = BeliefStore.load(tag.getCompound("beliefs"));
        return data;
    }
}
