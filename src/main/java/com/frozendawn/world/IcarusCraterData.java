package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.power.IcarusBlast;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Icarus craters still being carved in one level. A job keeps the chunks it has not reached yet, so a crater left
 * half-done by a save or an unloaded chunk resumes where it stopped. */
public final class IcarusCraterData extends SavedData {
    /** pending holds the chunk longs within meltRadius that are not carved yet. */
    public record Job(BlockPos center, double radius, long seed, LongSet pending) {}

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_icarus_craters";
    private final List<Job> jobs = new ArrayList<>();

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
        jobs.add(new Job(center.immutable(), radius, seed, pending));
        setDirty();
    }

    public List<Job> jobs() { return Collections.unmodifiableList(jobs); }

    /** Marks a chunk of job done, and drops the job once nothing is pending. */
    public void carved(Job job, long chunk) {
        job.pending().remove(chunk);
        if (job.pending().isEmpty()) jobs.removeIf(j -> j == job);
        setDirty();
    }

    public static IcarusCraterData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new IcarusCraterData();
        for (Tag t : tag.getList("Jobs", Tag.TAG_COMPOUND)) {
            var job = (CompoundTag) t;
            var center = NbtUtils.readBlockPos(job, "Center").orElse(null);
            if (center == null) continue;
            data.jobs.add(new Job(center, job.getDouble("Radius"), job.getLong("Seed"), new LongOpenHashSet(job.getLongArray("Pending"))));
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
            list.add(t);
        }
        tag.put("Jobs", list);
        return tag;
    }
}
