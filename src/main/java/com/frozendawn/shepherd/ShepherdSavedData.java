package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    /** She came up on the vault's dock, so the vault's lines are hers to say. */
    public static final String WOKE_IN_VAULT = "woke_in_vault";
    /** {@link #lastNear()} before anyone has been seen beside her. */
    public static final long NOT_SET = -1;

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
    private String promptNode;
    /** When each deed may next count, by name, in game time. Saved, so a relog cannot farm one. */
    private final Map<String, Long> deeds = new HashMap<>();
    private long lastNear = NOT_SET;
    /** When she last spoke up unasked, and when anyone last talked to her. */
    private long lastAmbient = NOT_SET, lastTalk = NOT_SET;

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
    public ShepherdAffinity.Tier tier() { return ShepherdAffinity.of(affinity); }
    public boolean flag(String flag) { return flags.contains(flag); }
    public void flag(String flag, boolean on) { if (on ? flags.add(flag) : flags.remove(flag)) setDirty(); }
    public String resumeNode() { return resumeNode; }
    public void resumeNode(String node) { resumeNode = node; setDirty(); }
    public boolean heavy() { return heavy; }
    public void heavy(boolean heavy) { this.heavy = heavy; setDirty(); }
    public String promptNode() { return promptNode; }
    public void promptNode(String node) { promptNode = node; setDirty(); }
    public long deedReady(String deed) { return deeds.getOrDefault(deed, 0L); }
    public void deedReady(String deed, long gameTime) { deeds.put(deed, gameTime); setDirty(); }
    public long lastNear() { return lastNear; }
    public void lastNear(long gameTime) { lastNear = gameTime; setDirty(); }
    public long lastAmbient() { return lastAmbient; }
    public void lastAmbient(long gameTime) { lastAmbient = gameTime; setDirty(); }
    public long lastTalk() { return lastTalk; }
    public void lastTalk(long gameTime) { lastTalk = gameTime; setDirty(); }

    /** Back to the moment she woke: everything the first meeting earned goes, and she starts it again. Where she woke stays true. */
    void replayFirstMeeting() {
        boolean vault = flag(WOKE_IN_VAULT);
        affinity = 0;
        flags.clear();
        if (vault) flags.add(WOKE_IN_VAULT);
        heavy = false;
        resumeNode = ShepherdTalk.FIRST_MEETING;
        promptNode = null;
        deeds.clear();
        lastNear = lastAmbient = lastTalk = NOT_SET;
        setDirty();
    }

    /** For playtests: a day has gone by her clocks. {@code seen} leaves her clock on you alone, since someone is standing there. */
    public void skipDay(boolean seen) {
        deeds.replaceAll((deed, ready) -> ready - ShepherdPolicy.DAY_TICKS);
        if (!seen && lastNear != NOT_SET) lastNear -= ShepherdPolicy.DAY_TICKS;
        if (lastTalk != NOT_SET) lastTalk -= ShepherdPolicy.DAY_TICKS;
        setDirty();
    }

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
        promptNode = null;
        deeds.clear();
        lastNear = lastAmbient = lastTalk = NOT_SET;
        setDirty();
    }

    /** Diagnostic lines for {@code /fd shepherd dump}. */
    public List<String> describe() {
        var lines = new ArrayList<String>();
        lines.add("stage=" + stage + " entity=" + entity);
        lines.add("dimension=" + (dimension == null ? null : dimension.location()) + " lastPos=" + (lastPos == null ? null : lastPos.toShortString()));
        lines.add("follower=" + follower + " waiting=" + waiting + " lamp=" + lamp);
        lines.add("affinity=" + affinity + " tier=" + tier() + " heavy=" + heavy + " resumeNode=" + resumeNode + " promptNode=" + promptNode);
        lines.add("flags=" + new TreeSet<>(flags));
        lines.add("deeds=" + new TreeMap<>(deeds) + " lastNear=" + lastNear + " lastAmbient=" + lastAmbient + " lastTalk=" + lastTalk);
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
        if (tag.contains("promptNode")) data.promptNode = tag.getString("promptNode");
        var deeds = tag.getCompound("deeds");
        for (String deed : deeds.getAllKeys()) data.deeds.put(deed, deeds.getLong(deed));
        if (tag.contains("lastNear")) data.lastNear = tag.getLong("lastNear");
        if (tag.contains("lastAmbient")) data.lastAmbient = tag.getLong("lastAmbient");
        if (tag.contains("lastTalk")) data.lastTalk = tag.getLong("lastTalk");
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
        if (promptNode != null) tag.putString("promptNode", promptNode);
        var ready = new CompoundTag();
        deeds.forEach(ready::putLong);
        tag.put("deeds", ready);
        if (lastNear != NOT_SET) tag.putLong("lastNear", lastNear);
        if (lastAmbient != NOT_SET) tag.putLong("lastAmbient", lastAmbient);
        if (lastTalk != NOT_SET) tag.putLong("lastTalk", lastTalk);
        return tag;
    }
}
