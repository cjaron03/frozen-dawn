package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world's truth about her, kept in the overworld. The entity is only a body: everything here outlives it,
 * which is what lets {@link ShepherdWatch} rebuild her from nothing.
 */
public final class ShepherdSavedData extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_shepherd";
    private static final int MAX_AFFINITY = 1000;

    public enum Stage {
        DORMANT,
        AWAKE
    }

    private Stage stage = Stage.DORMANT;
    private UUID entity;
    private ResourceKey<Level> dimension;
    private BlockPos lastPos;
    private UUID follower;
    private boolean waiting;
    private boolean lamp = true;
    private int affinity;
    private final Set<String> flags = new HashSet<>();
    private String resumeNode;
    private boolean heavy;

    public static ShepherdSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShepherdSavedData::new, ShepherdSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    public Stage stage() { return stage; }
    public void stage(Stage stage) { this.stage = stage; setDirty(); }
    public UUID entity() { return entity; }
    public void entity(UUID entity) { this.entity = entity; setDirty(); }
    public ResourceKey<Level> dimension() { return dimension; }
    public void dimension(ResourceKey<Level> dimension) { this.dimension = dimension; setDirty(); }
    public BlockPos lastPos() { return lastPos; }
    public void lastPos(BlockPos pos) { lastPos = pos == null ? null : pos.immutable(); setDirty(); }
    public UUID follower() { return follower; }
    public void follower(UUID follower) { this.follower = follower; setDirty(); }
    public boolean waiting() { return waiting; }
    public void waiting(boolean waiting) { this.waiting = waiting; setDirty(); }
    public boolean lamp() { return lamp; }
    public void lamp(boolean lamp) { this.lamp = lamp; setDirty(); }
    public int affinity() { return affinity; }
    public void affinity(int affinity) { this.affinity = Mth.clamp(affinity, -MAX_AFFINITY, MAX_AFFINITY); setDirty(); }
    public boolean flag(String flag) { return flags.contains(flag); }
    public void flag(String flag, boolean on) { if (on ? flags.add(flag) : flags.remove(flag)) setDirty(); }
    public String resumeNode() { return resumeNode; }
    public void resumeNode(String node) { resumeNode = node; setDirty(); }
    public boolean heavy() { return heavy; }
    public void heavy(boolean heavy) { this.heavy = heavy; setDirty(); }

    /** GameTest isolation only: a shard may be of any age, so each test starts from a fresh state. */
    void clearForTests() {
        stage = Stage.DORMANT;
        entity = null;
        dimension = null;
        lastPos = null;
        follower = null;
        waiting = false;
        lamp = true;
        affinity = 0;
        flags.clear();
        resumeNode = null;
        heavy = false;
        setDirty();
    }

    /** Diagnostic lines for {@code /fd shepherd dump}. */
    public List<String> describe() {
        var lines = new ArrayList<String>();
        lines.add("stage=" + stage + " entity=" + entity);
        lines.add("dimension=" + (dimension == null ? null : dimension.location()) + " lastPos=" + (lastPos == null ? null : lastPos.toShortString()));
        lines.add("follower=" + follower + " waiting=" + waiting + " lamp=" + lamp);
        lines.add("affinity=" + affinity + " heavy=" + heavy + " resumeNode=" + resumeNode);
        lines.add("flags=" + new TreeSet<>(flags));
        return lines;
    }

    static ShepherdSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ShepherdSavedData();
        try { data.stage = Stage.valueOf(tag.getString("stage")); }
        catch (IllegalArgumentException ignored) { data.stage = Stage.DORMANT; }
        if (tag.hasUUID("entity")) data.entity = tag.getUUID("entity");
        var dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (tag.contains("dimension") && dimension != null) data.dimension = ResourceKey.create(Registries.DIMENSION, dimension);
        if (tag.contains("lastPos")) data.lastPos = BlockPos.of(tag.getLong("lastPos"));
        if (tag.hasUUID("follower")) data.follower = tag.getUUID("follower");
        data.waiting = tag.getBoolean("waiting");
        data.lamp = tag.getBoolean("lamp");
        data.affinity = Mth.clamp(tag.getInt("affinity"), -MAX_AFFINITY, MAX_AFFINITY);
        for (Tag flag : tag.getList("flags", Tag.TAG_STRING)) data.flags.add(flag.getAsString());
        if (tag.contains("resumeNode")) data.resumeNode = tag.getString("resumeNode");
        data.heavy = tag.getBoolean("heavy");
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("stage", stage.name());
        if (entity != null) tag.putUUID("entity", entity);
        if (dimension != null) tag.putString("dimension", dimension.location().toString());
        if (lastPos != null) tag.putLong("lastPos", lastPos.asLong());
        if (follower != null) tag.putUUID("follower", follower);
        tag.putBoolean("waiting", waiting);
        tag.putBoolean("lamp", lamp);
        tag.putInt("affinity", affinity);
        var list = new ListTag();
        for (String flag : flags) list.add(StringTag.valueOf(flag));
        tag.put("flags", list);
        if (resumeNode != null) tag.putString("resumeNode", resumeNode);
        tag.putBoolean("heavy", heavy);
        return tag;
    }
}
