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

/** Which Helion site chunks have been evaluated, where the built halls stand, and which have lost their core. */
public final class HelionSiteState extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_helion_sites";
    private final Set<Long> evaluated = new HashSet<>();
    private final List<BlockPos> built = new ArrayList<>();
    private final Set<Long> looted = new HashSet<>();

    public static HelionSiteState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HelionSiteState::new, HelionSiteState::load, DataFixTypes.LEVEL), DATA_NAME);
    }
    private static HelionSiteState load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new HelionSiteState();
        for (long packed : tag.getLongArray("evaluated")) state.evaluated.add(packed);
        var list = tag.getList("built", Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) state.built.add(BlockPos.of(((net.minecraft.nbt.LongTag) list.get(i)).getAsLong()));
        for (long packed : tag.getLongArray("looted")) state.looted.add(packed);
        return state;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("evaluated", evaluated.stream().mapToLong(Long::longValue).toArray());
        var list = new ListTag(); for (var pos : built) list.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        tag.put("built", list);
        tag.putLongArray("looted", looted.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
    private static long key(int chunkX, int chunkZ) { return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL); }
    public boolean isEvaluated(int chunkX, int chunkZ) { return evaluated.contains(key(chunkX, chunkZ)); }
    public void markEvaluated(int chunkX, int chunkZ) { if (evaluated.add(key(chunkX, chunkZ))) setDirty(); }
    public void markBuilt(int chunkX, int chunkZ, BlockPos hall) { evaluated.add(key(chunkX, chunkZ)); built.add(hall.immutable()); setDirty(); }
    public void markLooted(BlockPos hall) { if (looted.add(hall.asLong())) setDirty(); }
    public List<BlockPos> built() { return List.copyOf(built); }
    /** Built halls whose Confinement Core is still in place. */
    public List<BlockPos> unlooted() { return built.stream().filter(p -> !looted.contains(p.asLong())).toList(); }
}
