package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Which ruined reactor site chunks have been evaluated, and where the built ones stand. */
public final class ReactorSiteState extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_reactor_sites";
    private final Set<Long> evaluated = new HashSet<>();
    private final List<BlockPos> built = new ArrayList<>();

    public static ReactorSiteState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ReactorSiteState::new, ReactorSiteState::load, DataFixTypes.LEVEL), DATA_NAME);
    }
    private static ReactorSiteState load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new ReactorSiteState();
        for (long packed : tag.getLongArray("evaluated")) state.evaluated.add(packed);
        var list = tag.getList("built", Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) state.built.add(BlockPos.of(((net.minecraft.nbt.LongTag) list.get(i)).getAsLong()));
        return state;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("evaluated", evaluated.stream().mapToLong(Long::longValue).toArray());
        var list = new ListTag(); for (var pos : built) list.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        tag.put("built", list);
        return tag;
    }
    private static long key(int chunkX, int chunkZ) { return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL); }
    public boolean isEvaluated(int chunkX, int chunkZ) { return evaluated.contains(key(chunkX, chunkZ)); }
    public void markEvaluated(int chunkX, int chunkZ) { if (evaluated.add(key(chunkX, chunkZ))) setDirty(); }
    public void markBuilt(int chunkX, int chunkZ, BlockPos center) { evaluated.add(key(chunkX, chunkZ)); built.add(center.immutable()); setDirty(); }
    public List<BlockPos> built() { return List.copyOf(built); }
}
