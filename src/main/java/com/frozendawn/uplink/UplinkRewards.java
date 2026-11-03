package com.frozendawn.uplink;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.data.MonitoringStationState;
import com.frozendawn.data.ReactorSiteState;
import com.frozendawn.init.ModItems;
import com.frozendawn.maeve.MaeveDirector;
import com.frozendawn.world.HelionSitePlacement;
import com.frozendawn.world.MonitoringStationPlacement;
import com.frozendawn.world.ReactorSitePlacement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Turns one completed handshake into its reward: an archive fragment, a supply drop or a station map. Each archive
 * fragment also plots the nearest unlooted Helion facility, when one is in reach. */
public final class UplinkRewards {
    private UplinkRewards() { }

    public record Result(UplinkPolicy.Reward reward, ItemStack item, Component log, ItemStack extra, @Nullable Component extraLog) {
        public Result(UplinkPolicy.Reward reward, ItemStack item, Component log) { this(reward, item, log, ItemStack.EMPTY, null); }
    }
    /** A Helion facility plot and the chunk it promises: the built hall's, or the unbuilt candidate's. */
    public record Reveal(ItemStack plot, Component log, int chunkX, int chunkZ) { }

    public static Result grant(ServerLevel level, BlockPos console, RandomSource random) {
        var server = level.getServer();
        var data = UplinkSavedData.get(server);
        int phase = ApocalypseState.get(server).getPhase();
        boolean maeve = MaeveDirector.hasActivated(server);
        var site = findSite(level, console);
        var reward = UplinkPolicy.choose(data.archiveDelivered(), maeve, phase, data.lastReward(), site != null, random.nextDouble());
        Result result = switch (reward) {
            case ARCHIVE -> {
                int index = data.archiveDelivered();
                data.archiveDelivered(index);
                var helion = helionReveal(level, console);
                yield new Result(reward, archive(index), Component.translatable(UplinkPolicy.humanReply(index)
                        ? "uplink.frozendawn.log.reply" : "uplink.frozendawn.log.archive", index + 1, UplinkPolicy.ARCHIVE_SIZE),
                        helion == null ? ItemStack.EMPTY : helion.plot(), helion == null ? null : helion.log());
            }
            case STATION_MAP -> stationMap(level, console, site, random);
            case SUPPLY_DROP -> supplyDrop(level, console, random);
        };
        if (reward != UplinkPolicy.Reward.ARCHIVE) data.lastReward(reward);
        return result;
    }

    /** Doc type of a relay transcript; archiving it unlocks its pages in the Field Manual's Relay Archive entry. */
    public static String docType(int index) { return "relay_archive_" + (index + 1); }

    /** A printed relay transcript: an ORSA document the player archives into the Field Manual (text lives there). */
    public static ItemStack archive(int index) {
        var doc = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        doc.set(DataComponents.CUSTOM_NAME, Component.translatable("item.frozendawn.relay_transcript", index + 1,
                Component.translatable("uplink.frozendawn.archive." + (index + 1) + ".title")));
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("doc_type", docType(index));
        doc.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return doc;
    }

    static Result supplyDrop(ServerLevel level, BlockPos console, RandomSource random) {
        double angle = random.nextDouble() * 2 * Math.PI;
        int distance = UplinkPolicy.DROP_MIN_DISTANCE + random.nextInt(UplinkPolicy.DROP_MAX_DISTANCE - UplinkPolicy.DROP_MIN_DISTANCE + 1);
        var target = new BlockPos(console.getX() + (int) Math.round(Math.sin(angle) * distance), console.getY(),
                console.getZ() - (int) Math.round(Math.cos(angle) * distance));
        int radius = UplinkPolicy.DROP_CIRCLE_MIN + random.nextInt(UplinkPolicy.DROP_CIRCLE_MAX - UplinkPolicy.DROP_CIRCLE_MIN + 1);
        // The crater spans 16 blocks; keep the whole drop inside the drawn circle.
        var offset = UplinkPolicy.jitter(radius, 20, random.nextDouble() * 2 * Math.PI, random.nextDouble());
        var circle = target.offset(offset[0], 0, offset[1]);
        UplinkSavedData.get(level.getServer()).addDrop(target, level.getGameTime());
        var map = UplinkMap.create(level, console, circle, radius, "item.frozendawn.uplink_supply_map", describe(console, circle, radius, "uplink.frozendawn.map.supply"));
        return new Result(UplinkPolicy.Reward.SUPPLY_DROP, map, Component.translatable("uplink.frozendawn.log.supply",
                UplinkPolicy.sector(circle.getX() - console.getX(), circle.getZ() - console.getZ()),
                UplinkPolicy.announcedDistance(Math.sqrt(console.distSqr(circle)))));
    }

    record Site(BlockPos pos, int chunkX, int chunkZ, boolean reactor) { }

    static Result stationMap(ServerLevel level, BlockPos console, Site site, RandomSource random) {
        UplinkSavedData.get(level.getServer()).promise(site.chunkX(), site.chunkZ());
        int radius = UplinkPolicy.SITE_REGION_RADIUS;
        var offset = UplinkPolicy.jitter(UplinkPolicy.SITE_REGION_JITTER + 24, 24, random.nextDouble() * 2 * Math.PI, random.nextDouble());
        var circle = site.pos().offset(offset[0], 0, offset[1]);
        var map = UplinkMap.create(level, console, circle, radius, "item.frozendawn.uplink_station_map", describe(console, circle, radius, "uplink.frozendawn.map.station"));
        return new Result(UplinkPolicy.Reward.STATION_MAP, map, Component.translatable("uplink.frozendawn.log.station",
                UplinkPolicy.sector(circle.getX() - console.getX(), circle.getZ() - console.getZ()),
                UplinkPolicy.announcedDistance(Math.sqrt(console.distSqr(circle)))));
    }

    /** Plots and promises the nearest Helion facility no fragment has revealed: a built hall that still holds its
     * core, or a seed candidate no player has reached. Helion regions are about 2,000 blocks, so the search spans the
     * two rings of regions round the console's own and ignores the station-map range. */
    public static @Nullable Reveal helionReveal(ServerLevel level, BlockPos console) {
        if (level.dimension() != ServerLevel.OVERWORLD) return null;
        var server = level.getServer();
        var data = UplinkSavedData.get(server);
        var state = HelionSiteState.get(server);
        BlockPos best = null; int bestX = 0, bestZ = 0; double bestSq = Double.MAX_VALUE;
        for (var hall : state.unlooted()) {
            int cx = hall.getX() >> 4, cz = hall.getZ() >> 4;
            double sq = sq(console, hall.getX(), hall.getZ());
            if (!data.promised(cx, cz) && sq < bestSq) { bestSq = sq; best = hall; bestX = cx; bestZ = cz; }
        }
        int regionBlocks = HelionSitePlacement.REGION_SIZE * 16, min = HelionSitePlacement.MIN_SPAWN_DISTANCE;
        int rx0 = Math.floorDiv(console.getX(), regionBlocks), rz0 = Math.floorDiv(console.getZ(), regionBlocks);
        var spawn = level.getSharedSpawnPos();
        for (int rx = rx0 - 2; rx <= rx0 + 2; rx++) for (int rz = rz0 - 2; rz <= rz0 + 2; rz++) {
            var chunk = HelionSitePlacement.siteChunk(level.getSeed(), rx, rz);
            if (chunk == null || state.isEvaluated(chunk[0], chunk[1]) || data.promised(chunk[0], chunk[1])) continue;
            int x = (chunk[0] << 4) + 8, z = (chunk[1] << 4) + 8;
            if (sq(spawn, x, z) < (double) min * min) continue;
            double sq = sq(console, x, z);
            if (sq < bestSq) { bestSq = sq; best = new BlockPos(x, console.getY(), z); bestX = chunk[0]; bestZ = chunk[1]; }
        }
        if (best == null) return null;
        data.promise(bestX, bestZ);
        int radius = UplinkPolicy.SITE_REGION_RADIUS;
        var random = RandomSource.create(level.getSeed() ^ best.asLong());
        var offset = UplinkPolicy.jitter(UplinkPolicy.SITE_REGION_JITTER + 24, 24, random.nextDouble() * 2 * Math.PI, random.nextDouble());
        var circle = new BlockPos(best.getX() + offset[0], console.getY(), best.getZ() + offset[1]);
        var plot = UplinkMap.create(level, console, circle, radius, "item.frozendawn.uplink_helion_map", describe(console, circle, radius, "uplink.frozendawn.map.helion"));
        return new Reveal(plot, Component.translatable("uplink.frozendawn.log.helion",
                UplinkPolicy.sector(circle.getX() - console.getX(), circle.getZ() - console.getZ()),
                UplinkPolicy.announcedDistance(Math.sqrt(console.distSqr(circle)))), bestX, bestZ);
    }

    private static List<Component> describe(BlockPos console, BlockPos circle, int radius, String key) {
        return List.of(Component.translatable(key, UplinkPolicy.sector(circle.getX() - console.getX(), circle.getZ() - console.getZ()),
                UplinkPolicy.announcedDistance(Math.sqrt(console.distSqr(circle)))),
                Component.translatable("uplink.frozendawn.map.radius", radius));
    }

    /**
     * Nearest undiscovered monitoring station or ruined reactor site candidate: a deterministic worldgen position
     * whose chunk no player has loaded yet, within range of the console and not already promised to another map.
     * Positions are computed from the seed alone; nothing is loaded.
     */
    static @Nullable Site findSite(ServerLevel level, BlockPos console) {
        if (level.dimension() != ServerLevel.OVERWORLD) return null;
        var server = level.getServer();
        var data = UplinkSavedData.get(server);
        var stations = MonitoringStationState.get(server);
        var reactors = ReactorSiteState.get(server);
        long seed = level.getSeed();
        Site best = null; double bestSq = Double.MAX_VALUE;
        long min = UplinkPolicy.SITE_MIN_DISTANCE, max = UplinkPolicy.SITE_MAX_DISTANCE;
        int regionBlocks = 32 * 16, reach = UplinkPolicy.SITE_MAX_DISTANCE / regionBlocks + 1;
        int rx0 = Math.floorDiv(console.getX(), regionBlocks), rz0 = Math.floorDiv(console.getZ(), regionBlocks);
        for (int rx = rx0 - reach; rx <= rx0 + reach; rx++) for (int rz = rz0 - reach; rz <= rz0 + reach; rz++) {
            var pos = MonitoringStationPlacement.getStationBlockPos(seed, rx, rz);
            if (pos == null) continue;
            int cx = pos[0] >> 4, cz = pos[1] >> 4;
            if (stations.isStationEvaluated(cx, cz) || data.promised(cx, cz) || !MonitoringStationPlacement.isOutsideSpawnBuffer(level, pos[0], pos[1])) continue;
            double sq = sq(console, pos[0], pos[1]);
            if (sq >= min * min && sq <= max * max && sq < bestSq) { bestSq = sq; best = new Site(new BlockPos(pos[0], console.getY(), pos[1]), cx, cz, false); }
        }
        int reactorBlocks = ReactorSitePlacement.REGION_SIZE * 16;
        int qx0 = Math.floorDiv(console.getX(), reactorBlocks), qz0 = Math.floorDiv(console.getZ(), reactorBlocks);
        var spawn = level.getSharedSpawnPos();
        for (int rx = qx0 - 1; rx <= qx0 + 1; rx++) for (int rz = qz0 - 1; rz <= qz0 + 1; rz++) {
            var chunk = ReactorSitePlacement.siteChunk(seed, rx, rz);
            if (chunk == null || reactors.isEvaluated(chunk[0], chunk[1]) || data.promised(chunk[0], chunk[1])) continue;
            int x = (chunk[0] << 4) + 8, z = (chunk[1] << 4) + 8;
            if (sq(spawn, x, z) < (double) ReactorSitePlacement.MIN_SPAWN_DISTANCE * ReactorSitePlacement.MIN_SPAWN_DISTANCE) continue;
            double sq = sq(console, x, z);
            if (sq >= min * min && sq <= max * max && sq < bestSq) { bestSq = sq; best = new Site(new BlockPos(x, console.getY(), z), chunk[0], chunk[1], true); }
        }
        return best;
    }

    private static double sq(BlockPos a, int x, int z) { double dx = a.getX() - x, dz = a.getZ() - z; return dx * dx + dz * dz; }
}
