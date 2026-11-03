package com.frozendawn.uplink;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-shared Uplink progress, kept in the overworld: archive order, the daily transmission window,
 * reward rotation, signal sites (the dish locations Maeve's pawns are drawn toward), pending supply
 * drops and the undiscovered sites a station map has promised. Every list is bounded.
 */
public final class UplinkSavedData extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_orsa_uplink";

    /** A transmitting dish. Pressure starts after {@code armed} and decays from {@code at}. */
    public static final class Site {
        public final String dimension; public final BlockPos pos; public double score; public long at, armed; public int transmissions;
        Site(String dimension, BlockPos pos) { this.dimension = dimension; this.pos = pos.immutable(); }
        public double pressure(long now) { return UplinkPolicy.pressure(score, at, armed, now); }
    }

    /** A supply drop announced by the console. {@code center} is null until its area loads and it is built. */
    public static final class Drop {
        public final BlockPos target; public BlockPos center; public boolean retrieved; public final long issued;
        Drop(BlockPos target, long issued) { this.target = target.immutable(); this.issued = issued; }
    }

    private int archiveDelivered;
    private long nextTransmit;
    private UplinkPolicy.Reward lastReward;
    private final List<Site> sites = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>();
    private final Set<Long> promisedSites = new HashSet<>();

    public static UplinkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(UplinkSavedData::new, UplinkSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    public int archiveDelivered() { return archiveDelivered; }
    public void archiveDelivered(int index) { archiveDelivered = Math.clamp(Math.max(archiveDelivered, index + 1), 0, UplinkPolicy.ARCHIVE_SIZE); setDirty(); }
    public long nextTransmit() { return nextTransmit; }
    public void startCooldown(long now) { nextTransmit = now + UplinkPolicy.COOLDOWN_TICKS; setDirty(); }
    public UplinkPolicy.Reward lastReward() { return lastReward; }
    public void lastReward(UplinkPolicy.Reward reward) { lastReward = reward; setDirty(); }

    /** Records a transmission at the dish. Merges with a site within the merge radius; evicts the weakest when full. */
    public Site signal(String dimension, BlockPos dish, long now, boolean boost) {
        Site site = null;
        for (var s : sites)
            if (s.dimension.equals(dimension) && s.pos.distSqr(dish) <= UplinkPolicy.SIGNAL_MERGE_RADIUS * UplinkPolicy.SIGNAL_MERGE_RADIUS) { site = s; break; }
        if (site == null) {
            if (sites.size() >= UplinkPolicy.MAX_SITES)
                sites.remove(sites.stream().min(java.util.Comparator.comparingDouble((Site s) -> UplinkPolicy.pressure(s.score, s.at, 0, now))
                        .thenComparingLong(s -> s.at)).orElseThrow());
            site = new Site(dimension, dish);
            sites.add(site);
        }
        site.score = UplinkPolicy.accumulate(site.score, site.at, now, boost);
        site.at = now;
        // A newer transmission never delays pressure the site has already armed.
        site.armed = site.armed > 0 && site.armed <= now ? site.armed : now + UplinkPolicy.SIGNAL_LAG_TICKS;
        site.transmissions = Math.min(1_000_000, site.transmissions + 1);
        setDirty();
        return site;
    }

    public List<Site> sites() { return List.copyOf(sites); }

    /** Strongest armed pressure reaching a position in a dimension. */
    public double pressure(String dimension, BlockPos pos, long now) {
        double best = 0;
        for (var s : sites)
            if (s.dimension.equals(dimension) && s.pos.distSqr(pos) <= (double) UplinkPolicy.SIGNAL_REACH * UplinkPolicy.SIGNAL_REACH)
                best = Math.max(best, s.pressure(now));
        return best;
    }

    public Drop addDrop(BlockPos target, long now) {
        if (drops.size() >= UplinkPolicy.MAX_DROPS) {
            var stale = drops.stream().filter(d -> d.retrieved).findFirst().orElse(drops.getFirst());
            drops.remove(stale);
        }
        var drop = new Drop(target, now); drops.add(drop); setDirty(); return drop;
    }
    public List<Drop> drops() { return List.copyOf(drops); }
    public void built(Drop drop, BlockPos center) { drop.center = center.immutable(); setDirty(); }
    public void retrieved(Drop drop) { drop.retrieved = true; setDirty(); }

    private static long key(int chunkX, int chunkZ) { return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL); }
    /** A station map's site will be built when its area loads, even on ground worldgen would normally refuse. */
    public boolean promised(int chunkX, int chunkZ) { return promisedSites.contains(key(chunkX, chunkZ)); }
    public void promise(int chunkX, int chunkZ) { if (promisedSites.size() < 256 && promisedSites.add(key(chunkX, chunkZ))) setDirty(); }

    /** GameTest isolation only: each Uplink batch starts from a fresh world-shared state. */
    void clearForTests() {
        archiveDelivered = 0; nextTransmit = 0; lastReward = null;
        sites.clear(); drops.clear(); promisedSites.clear(); setDirty();
    }

    /** Diagnostic lines for {@code /fd uplink dump}. */
    public List<String> describe(long now) {
        var lines = new ArrayList<String>();
        lines.add("archive=" + archiveDelivered + "/" + UplinkPolicy.ARCHIVE_SIZE + " lastReward=" + lastReward
                + " cooldownTicks=" + UplinkPolicy.cooldownRemaining(now, nextTransmit) + " promisedSites=" + promisedSites.size());
        for (var s : sites)
            lines.add("site " + s.dimension + " " + s.pos.toShortString() + " score=" + String.format(java.util.Locale.ROOT, "%.2f", s.score)
                    + " pressure=" + String.format(java.util.Locale.ROOT, "%.2f", s.pressure(now)) + " armedIn=" + Math.max(0, s.armed - now)
                    + " transmissions=" + s.transmissions);
        for (var d : drops)
            lines.add("drop target=" + d.target.toShortString() + " built=" + (d.center == null ? "pending" : d.center.toShortString())
                    + " retrieved=" + d.retrieved);
        return lines;
    }

    private static UplinkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new UplinkSavedData();
        data.archiveDelivered = Math.clamp(tag.getInt("archive"), 0, UplinkPolicy.ARCHIVE_SIZE);
        data.nextTransmit = Math.max(0, tag.getLong("nextTransmit"));
        try { data.lastReward = tag.contains("lastReward") ? UplinkPolicy.Reward.valueOf(tag.getString("lastReward")) : null; }
        catch (IllegalArgumentException ignored) { data.lastReward = null; }
        for (Tag raw : tag.getList("sites", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            if (ResourceLocation.tryParse(t.getString("dimension")) == null) continue;
            var s = new Site(t.getString("dimension"), BlockPos.of(t.getLong("pos")));
            double score = t.getDouble("score"); s.score = Double.isFinite(score) ? Math.clamp(score, 0, UplinkPolicy.MAX_SIGNAL) : 0;
            s.at = Math.max(0, t.getLong("at")); s.armed = Math.max(0, t.getLong("armed")); s.transmissions = Math.max(0, t.getInt("transmissions"));
            data.sites.add(s);
            if (data.sites.size() == UplinkPolicy.MAX_SITES) break;
        }
        for (Tag raw : tag.getList("drops", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            var d = new Drop(BlockPos.of(t.getLong("target")), Math.max(0, t.getLong("issued")));
            if (t.contains("center")) d.center = BlockPos.of(t.getLong("center"));
            d.retrieved = t.getBoolean("retrieved");
            data.drops.add(d);
            if (data.drops.size() == UplinkPolicy.MAX_DROPS) break;
        }
        for (long packed : tag.getLongArray("promised")) { data.promisedSites.add(packed); if (data.promisedSites.size() == 256) break; }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("archive", archiveDelivered);
        tag.putLong("nextTransmit", nextTransmit);
        if (lastReward != null) tag.putString("lastReward", lastReward.name());
        var siteList = new ListTag();
        for (var s : sites) {
            var t = new CompoundTag(); t.putString("dimension", s.dimension); t.putLong("pos", s.pos.asLong());
            t.putDouble("score", s.score); t.putLong("at", s.at); t.putLong("armed", s.armed); t.putInt("transmissions", s.transmissions);
            siteList.add(t);
        }
        tag.put("sites", siteList);
        var dropList = new ListTag();
        for (var d : drops) {
            var t = new CompoundTag(); t.putLong("target", d.target.asLong()); t.putLong("issued", d.issued);
            if (d.center != null) t.putLong("center", d.center.asLong());
            t.putBoolean("retrieved", d.retrieved);
            dropList.add(t);
        }
        tag.put("drops", dropList);
        tag.putLongArray("promised", promisedSites.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
