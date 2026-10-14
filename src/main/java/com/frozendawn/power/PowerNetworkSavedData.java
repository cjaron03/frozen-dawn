package com.frozendawn.power;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Saved topology is independent of loaded chunks. Runtime connectivity is rebuilt on changes. */
public final class PowerNetworkSavedData extends SavedData {
    public static final String NAME = "frozendawn_power_network";
    public static final int MAX_LINKS = 1024;
    public static final int MAX_NODE_LINKS = 16;
    private final NavigableSet<PowerLink> links = new TreeSet<>();
    private long revision;

    public static PowerNetworkSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(PowerNetworkSavedData::new, PowerNetworkSavedData::load), NAME);
    }
    public Set<PowerLink> links() { return Collections.unmodifiableSet(links); }
    public long revision() { return revision; }
    public int degree(BlockPos pos) { return (int) links.stream().filter(link -> link.touches(pos)).count(); }
    public boolean add(PowerLink link) {
        if (links.size() >= MAX_LINKS || degree(link.a()) >= MAX_NODE_LINKS
                || degree(link.b()) >= MAX_NODE_LINKS || !links.add(link)) return false;
        changed(); return true;
    }
    public int clear(BlockPos pos) {
        int before = links.size(); links.removeIf(link -> link.touches(pos));
        int removed = before - links.size(); if (removed > 0) changed(); return removed;
    }
    private void changed() { revision++; setDirty(); }
    public static PowerNetworkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PowerNetworkSavedData();
        for (var item : tag.getList("links", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) item;
            if (!entry.contains("a", Tag.TAG_LONG) || !entry.contains("b", Tag.TAG_LONG)) continue;
            var a = BlockPos.of(entry.getLong("a")); var b = BlockPos.of(entry.getLong("b"));
            if (a.equals(b) || a.distSqr(b) > 32 * 32) continue;
            data.add(new PowerLink(a, b));
        }
        data.setDirty(false); return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        for (var link : links) {
            var entry = new CompoundTag(); entry.putLong("a", link.a().asLong());
            entry.putLong("b", link.b().asLong()); entries.add(entry);
        }
        tag.putInt("version", 1); tag.put("links", entries); return tag;
    }
}
