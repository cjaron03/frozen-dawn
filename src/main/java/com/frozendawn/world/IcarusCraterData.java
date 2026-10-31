package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.power.IcarusBlast;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Icarus craters in one level: jobs still being carved, and sites still hot. A job keeps the chunks it has not reached
 * yet, so a crater left half-done by a save or an unloaded chunk resumes where it stopped. A site is the crater's
 * afterlife: radiation, a burning floor, smoke and ash, all fading with the ticks since its blast. */
public final class IcarusCraterData extends SavedData {
    /** pending holds the chunk longs within meltRadius that are not carved yet; columns holds, for a part-carved pending
     * chunk, the index of its next column. Carving a column again would cut deeper from the lowered surface. */
    public record Job(BlockPos center, double radius, long seed, LongSet pending, Long2IntMap columns) {}

    public record Site(BlockPos center, double radius, long blastTick) {}

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_icarus_craters";
    private final List<Job> jobs = new ArrayList<>();
    private final List<Site> sites = new ArrayList<>();

    public static IcarusCraterData get(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(new Factory<>(IcarusCraterData::new, IcarusCraterData::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    public void queue(BlockPos center, double radius, long seed) {
        double reach = IcarusBlast.meltRadius(radius);
        var pending = new LongOpenHashSet();
        int minX = (int) Math.floor(center.getX() - reach) >> 4, maxX = (int) Math.floor(center.getX() + reach) >> 4;
        int minZ = (int) Math.floor(center.getZ() - reach) >> 4, maxZ = (int) Math.floor(center.getZ() + reach) >> 4;
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
            // The nearest point of the chunk to the centre decides whether any of its columns is in reach.
            double dx = Math.clamp(center.getX(), cx << 4, (cx << 4) + 15) - center.getX();
            double dz = Math.clamp(center.getZ(), cz << 4, (cz << 4) + 15) - center.getZ();
            if (dx * dx + dz * dz <= reach * reach) pending.add(ChunkPos.asLong(cx, cz));
        }
        jobs.add(new Job(center.immutable(), radius, seed, pending, new Long2IntOpenHashMap()));
        setDirty();
    }

    public List<Job> jobs() { return Collections.unmodifiableList(jobs); }

    public void addSite(BlockPos center, double radius, long blastTick) {
        sites.add(new Site(center.immutable(), radius, blastTick));
        setDirty();
    }

    public List<Site> sites() { return Collections.unmodifiableList(sites); }

    /** Forgets sites older than IcarusBlast.SITE_TICKS; returns whether any went. */
    public boolean prune(long now) {
        boolean removed = sites.removeIf(s -> now - s.blastTick() >= IcarusBlast.SITE_TICKS);
        if (removed) setDirty();
        return removed;
    }

    /** Records that chunk of job is carved up to, not including, column. */
    public void progress(Job job, long chunk, int column) {
        job.columns().put(chunk, column);
        setDirty();
    }

    /** Marks a chunk of job done, and drops the job once nothing is pending. */
    public void carved(Job job, long chunk) {
        job.pending().remove(chunk);
        job.columns().remove(chunk);
        if (job.pending().isEmpty()) jobs.removeIf(j -> j == job);
        setDirty();
    }

    public static IcarusCraterData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new IcarusCraterData();
        for (Tag t : tag.getList("Jobs", Tag.TAG_COMPOUND)) {
            var job = (CompoundTag) t;
            var center = NbtUtils.readBlockPos(job, "Center").orElse(null);
            if (center == null) continue;
            var columns = new Long2IntOpenHashMap();
            long[] partial = job.getLongArray("PartialChunks"); int[] next = job.getIntArray("PartialColumns");
            for (int i = 0; i < Math.min(partial.length, next.length); i++) columns.put(partial[i], next[i]);
            data.jobs.add(new Job(center, job.getDouble("Radius"), job.getLong("Seed"), new LongOpenHashSet(job.getLongArray("Pending")), columns));
        }
        for (Tag t : tag.getList("Sites", Tag.TAG_COMPOUND)) {
            var site = (CompoundTag) t;
            NbtUtils.readBlockPos(site, "Center").ifPresent(c -> data.sites.add(new Site(c, site.getDouble("Radius"), site.getLong("BlastTick"))));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var job : jobs) {
            var t = new CompoundTag();
            t.put("Center", NbtUtils.writeBlockPos(job.center()));
            t.putDouble("Radius", job.radius());
            t.putLong("Seed", job.seed());
            t.put("Pending", new LongArrayTag(job.pending().toLongArray()));
            long[] partial = job.columns().keySet().toLongArray(); int[] next = new int[partial.length];
            for (int i = 0; i < partial.length; i++) next[i] = job.columns().get(partial[i]);
            t.put("PartialChunks", new LongArrayTag(partial));
            t.put("PartialColumns", new IntArrayTag(next));
            list.add(t);
        }
        tag.put("Jobs", list);
        var siteList = new ListTag();
        for (var site : sites) {
            var t = new CompoundTag();
            t.put("Center", NbtUtils.writeBlockPos(site.center()));
            t.putDouble("Radius", site.radius());
            t.putLong("BlastTick", site.blastTick());
            siteList.add(t);
        }
        tag.put("Sites", siteList);
        return tag;
    }
}
