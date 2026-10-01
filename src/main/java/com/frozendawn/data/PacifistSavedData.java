package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Permanent player conduct, independent of lives, Hearth mood and Maeve's erasable beliefs. */
public final class PacifistSavedData extends SavedData {
    private final Set<UUID> disqualified = new HashSet<>();

    public static PacifistSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PacifistSavedData::new, PacifistSavedData::load),
                FrozenDawn.MOD_ID + "_pacifist");
    }

    public boolean disqualified(UUID player) {
        return disqualified.contains(player);
    }

    public void recordKill(UUID player) {
        if (disqualified.add(player)) setDirty();
    }

    public static PacifistSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PacifistSavedData();
        for (Tag entry : tag.getList("disqualified", Tag.TAG_COMPOUND)) {
            var player = (CompoundTag) entry;
            if (player.hasUUID("player")) data.disqualified.add(player.getUUID("player"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var players = new ListTag();
        disqualified.stream().sorted().forEach(id -> {
            var player = new CompoundTag();
            player.putUUID("player", id);
            players.add(player);
        });
        tag.put("disqualified", players);
        return tag;
    }
}
