package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ArchivistSavedData;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchivistEntity;
import com.frozendawn.entity.ArchivistRelicEntity;
import com.frozendawn.homo.ArchivistPolicy;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Loaded-chunk-only authority for Archivist spawning, sites, and relic projections. */
public final class ArchivistManager {
    private ArchivistManager() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (level.getGameTime() % 40L == 0L) {
            reconcileLoadedSites(level);
        }
        if (level.getGameTime() % ArchivistPolicy.CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        reconcileBindings(level);
        if (!PostMaeveWorldState.isUndoneSpawningReleased(level.getServer())) {
            return;
        }
        Set<Long> checkedRegions = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            long regionKey = ArchivistPolicy.regionKey(player.blockPosition());
            if (!checkedRegions.add(regionKey)) {
                continue;
            }
            tryNaturalSpawn(level, player, regionKey);
        }
    }

    private static void tryNaturalSpawn(ServerLevel level, ServerPlayer player,
                                        long regionKey) {
        ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
        ArchivistSavedData.RegionRecord region = data.region(regionKey);
        boolean occupied = region.activeArchivistId().isPresent();
        boolean neighborOccupied = data.regions().stream()
                .filter(candidate -> candidate.activeArchivistId().isPresent())
                .flatMap(candidate -> candidate.lastKnownPos().stream())
                .anyMatch(pos -> pos.distSqr(player.blockPosition())
                        <= ArchivistPolicy.ADJACENT_EXCLUSION_RADIUS
                        * ArchivistPolicy.ADJACENT_EXCLUSION_RADIUS);
        if (!ArchivistPolicy.canSpawn(PostMaeveWorldState.isErased(level),
                PostMaeveWorldState.isUndoneSpawningReleased(level.getServer()),
                occupied, neighborOccupied, level.getGameTime(),
                region.nextSpawnGameTime())
                || level.random.nextDouble()
                >= FrozenDawnConfig.ARCHIVIST_SPAWN_CHANCE_PER_CHECK.get()) {
            return;
        }
        BlockPos attraction = findAttraction(level, player);
        if (attraction == null) {
            return;
        }
        BlockPos siteAnchor = data.siteForRegion(regionKey)
                .map(ArchivistSavedData.SiteRecord::anchor)
                .orElseGet(() -> findSiteAnchor(level, attraction,
                        level.random.nextLong()));
        if (siteAnchor == null) {
            return;
        }
        BlockPos spawnPos = LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                level, player, level.random, 48, 96, 36,
                LateThreatSpawnHelper.NO_LIGHT_LIMIT);
        if (spawnPos == null || !level.hasChunkAt(spawnPos)) {
            return;
        }
        ArchivistEntity spawned = spawn(level, spawnPos, siteAnchor, regionKey, true);
        if (spawned != null) {
            FrozenDawn.LOGGER.info("[Archivist] Naturally spawned near {} region={} site={}",
                    player.getName().getString(), regionKey, siteAnchor.toShortString());
        }
    }

    public static ArchivistEntity spawn(ServerLevel level, BlockPos spawnPos,
                                         BlockPos siteAnchor, long regionKey,
                                         boolean seedLoad) {
        ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
        ArchivistSavedData.SiteRecord site = data.ensureSite(regionKey,
                siteAnchor, level.getSeed() ^ regionKey);
        ArchivistEntity entity = ModEntities.ARCHIVIST.get().create(
                level, null, spawnPos, MobSpawnType.EVENT, true, false);
        if (entity == null) {
            FrozenDawn.LOGGER.warn("[Archivist] Entity factory returned null at {}",
                    spawnPos.toShortString());
            return null;
        }
        entity.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(),
                spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        entity.bind(regionKey, site.id());
        if (seedLoad) {
            entity.seedInitialLoad(site.layoutSeed());
        }
        entity.setPersistenceRequired();
        if (!level.noCollision(entity)) {
            FrozenDawn.LOGGER.debug("[Archivist] Rejected colliding spawn at {}",
                    spawnPos.toShortString());
            entity.discard();
            return null;
        }
        if (!level.addFreshEntity(entity)) {
            FrozenDawn.LOGGER.warn("[Archivist] Level rejected entity at {}",
                    spawnPos.toShortString());
            entity.discard();
            return null;
        }
        data.bindArchivist(regionKey, entity.getUUID(), spawnPos, site.id());
        return entity;
    }

    public static ArchivistEntity debugSpawn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos site = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.blockPosition().offset(10, 0, 1));
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos spawn = LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                    level, player, level.random, 3, 18, 48,
                    LateThreatSpawnHelper.NO_LIGHT_LIMIT);
            if (spawn == null) {
                continue;
            }
            long region = ArchivistPolicy.regionKey(spawn);
            ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
            data.existingRegion(region).flatMap(
                    ArchivistSavedData.RegionRecord::activeArchivistId)
                    .map(level::getEntity).ifPresent(Entity::discard);
            data.clearRegionBinding(region, 0L);
            ArchivistEntity archivist = spawn(level, spawn, site, region, true);
            if (archivist != null) {
                FrozenDawn.LOGGER.info("[Archivist] Debug-spawned at {} after {} attempt(s)",
                        spawn.toShortString(), attempt + 1);
                return archivist;
            }
        }
        FrozenDawn.LOGGER.warn("[Archivist] Debug spawn found no collision-free position near {}",
                player.blockPosition().toShortString());
        return null;
    }

    private static BlockPos findAttraction(ServerLevel level, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        double maxDistanceSqr = ArchivistPolicy.ATTRACTION_RADIUS
                * ArchivistPolicy.ATTRACTION_RADIUS;
        BlockPos hearth = ReturnedHearthSavedData.get(level.getServer()).hearths()
                .stream()
                .filter(record -> record.stage()
                        != ReturnedHearthSavedData.HearthStage.PLANNED)
                .map(ReturnedHearthSavedData.HearthRecord::center)
                .filter(level::isLoaded)
                .filter(pos -> pos.distSqr(playerPos) <= maxDistanceSqr)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)))
                .orElse(null);
        if (hearth != null) {
            return hearth;
        }
        if (BloomGrowthManager.localDensity(level, playerPos) > 0.01F) {
            return playerPos;
        }
        for (int radius : new int[]{32, 64, 96, 128}) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0D;
                BlockPos probe = playerPos.offset(
                        (int) Math.round(Math.cos(angle) * radius), 0,
                        (int) Math.round(Math.sin(angle) * radius));
                if (level.hasChunkAt(probe)
                        && BloomGrowthManager.localDensity(level, probe) > 0.01F) {
                    return probe;
                }
            }
        }
        return null;
    }

    private static BlockPos findSiteAnchor(ServerLevel level, BlockPos attraction,
                                           long seed) {
        RandomSource random = RandomSource.create(seed);
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int distance = 12 + random.nextInt(17);
            int x = attraction.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = attraction.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos sample = new BlockPos(x, attraction.getY(), z);
            if (!level.hasChunkAt(sample)) {
                continue;
            }
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));
            if (!level.getBlockState(surface).canBeReplaced()
                    || !level.getBlockState(surface.above()).canBeReplaced()
                    || !level.getBlockState(surface.below()).isCollisionShapeFullBlock(
                    level, surface.below())
                    || ChunkCatchUpManager.isBloomOrsaProtected(level, surface)) {
                continue;
            }
            return surface;
        }
        return null;
    }

    public static Optional<ArchivistSavedData.SiteRecord> site(ServerLevel level,
                                                                UUID siteId) {
        return ArchivistSavedData.get(level.getServer()).site(siteId);
    }

    public static boolean siteHasCapacity(ServerLevel level, UUID siteId) {
        return site(level, siteId)
                .map(site -> site.relics().size() < ArchivistPolicy.TOTAL_SLOTS)
                .orElse(false);
    }

    public static boolean deposit(ServerLevel level, UUID siteId, ItemStack stack) {
        boolean badge = stack.is(ModItems.ORSA_ID_BADGE.get());
        Optional<ArchivistSavedData.RelicRecord> relic = ArchivistSavedData
                .get(level.getServer()).addRelic(siteId, stack, badge);
        relic.ifPresent(record -> spawnRelicProjection(level, siteId, record));
        return relic.isPresent();
    }

    public static boolean depositAt(ServerLevel level, UUID siteId,
                                    ItemStack stack, int slot) {
        Optional<ArchivistSavedData.RelicRecord> relic = ArchivistSavedData
                .get(level.getServer()).addRelicAtSlot(siteId, stack, slot);
        relic.ifPresent(record -> spawnRelicProjection(level, siteId, record));
        return relic.isPresent();
    }

    public static SortTask takeRelicForSorting(ServerLevel level, UUID siteId,
                                               RandomSource random) {
        ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
        ArchivistSavedData.SiteRecord site = data.site(siteId).orElse(null);
        if (site == null || site.relics().isEmpty()) {
            return null;
        }
        List<ArchivistSavedData.RelicRecord> relics = List.copyOf(site.relics());
        ArchivistSavedData.RelicRecord selected = relics.get(random.nextInt(relics.size()));
        boolean badge = selected.stack().is(ModItems.ORSA_ID_BADGE.get());
        Optional<ItemStack> claimed = data.claimRelic(siteId, selected.id());
        selected.entityId().map(level::getEntity).ifPresent(Entity::discard);
        if (claimed.isEmpty()) {
            return null;
        }
        Set<Integer> occupied = site.occupiedSlots();
        int start = badge ? ArchivistPolicy.GENERAL_SLOTS : 0;
        int span = badge ? ArchivistPolicy.BADGE_SLOTS : ArchivistPolicy.GENERAL_SLOTS;
        int destination = -1;
        for (int offset = 1; offset <= span; offset++) {
            int candidate = start + Math.floorMod(selected.slot() - start + offset * 7, span);
            if (!occupied.contains(candidate)) {
                destination = candidate;
                break;
            }
        }
        if (destination < 0) {
            destination = selected.slot();
        }
        return new SortTask(claimed.get(), destination);
    }

    public static boolean rearrangeOneRelic(ServerLevel level, UUID siteId,
                                            RandomSource random) {
        SortTask task = takeRelicForSorting(level, siteId, random);
        return task != null && depositAt(level, siteId,
                task.stack(), task.destinationSlot());
    }

    public static boolean claimRelic(ServerPlayer player,
                                     ArchivistRelicEntity entity) {
        UUID siteId = entity.siteId().orElse(null);
        UUID relicId = entity.relicId().orElse(null);
        if (siteId == null || relicId == null) {
            return false;
        }
        Optional<ItemStack> claimed = ArchivistSavedData.get(
                player.getServer()).claimRelic(siteId, relicId);
        if (claimed.isEmpty()) {
            entity.discard();
            return false;
        }
        ItemStack stack = claimed.get();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        entity.discard();
        return true;
    }

    public static void onArchivistDeath(ServerLevel level,
                                        ArchivistEntity archivist) {
        ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
        UUID siteId = archivist.siteId().orElse(null);
        ArchivistSavedData.SiteRecord site = siteId == null
                ? null : data.site(siteId).orElse(null);
        if (site != null && archivist.distanceToSqr(site.anchor().getCenter())
                <= 16.0D * 16.0D) {
            scatterSite(level, site);
        }
        data.releaseArchivist(archivist.regionKey(), archivist.getUUID(),
                level.getGameTime());
    }

    private static void scatterSite(ServerLevel level,
                                    ArchivistSavedData.SiteRecord site) {
        ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
        List<ArchivistSavedData.RelicRecord> relics = data.clearSiteRelics(site.id());
        for (ArchivistSavedData.RelicRecord relic : relics) {
            Entity projection = relic.entityId().map(level::getEntity).orElse(null);
            Vec3 position = projection == null
                    ? resolveRelicPosition(level, site, relic.slot())
                    : projection.position();
            if (projection != null) {
                projection.discard();
            }
            ItemEntity item = new ItemEntity(level, position.x, position.y + 0.2D,
                    position.z, relic.stack());
            item.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.35D,
                    0.20D + level.random.nextDouble() * 0.18D,
                    (level.random.nextDouble() - 0.5D) * 0.35D);
            level.addFreshEntity(item);
        }
    }

    private static void reconcileBindings(ServerLevel level) {
        ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
        for (ArchivistSavedData.RegionRecord region : data.regions()) {
            UUID active = region.activeArchivistId().orElse(null);
            BlockPos lastPos = region.lastKnownPos().orElse(null);
            if (active != null && lastPos != null && level.hasChunkAt(lastPos)
                    && level.getEntity(active) == null) {
                data.releaseArchivist(region.regionKey(), active, level.getGameTime());
            }
        }
    }

    private static void reconcileLoadedSites(ServerLevel level) {
        ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
        for (ArchivistSavedData.RegionRecord region : data.regions()) {
            ArchivistSavedData.SiteRecord site = region.siteId()
                    .flatMap(data::site).orElse(null);
            if (site == null || !level.hasChunkAt(site.anchor())) {
                continue;
            }
            for (ArchivistSavedData.RelicRecord relic : site.relics()) {
                Entity existing = relic.entityId().map(level::getEntity).orElse(null);
                if (existing instanceof ArchivistRelicEntity projection
                        && projection.isAlive()) {
                    BlockPos occupied = projection.blockPosition();
                    if (level.getBlockState(occupied).getCollisionShape(
                            level, occupied).isEmpty()) {
                        continue;
                    }
                    projection.discard();
                }
                spawnRelicProjection(level, site.id(), relic);
            }
        }
    }

    private static void spawnRelicProjection(ServerLevel level, UUID siteId,
                                             ArchivistSavedData.RelicRecord relic) {
        ArchivistSavedData.SiteRecord site = ArchivistSavedData.get(level.getServer())
                .site(siteId).orElse(null);
        if (site == null) {
            return;
        }
        Vec3 pos = resolveRelicPosition(level, site, relic.slot());
        BlockPos block = BlockPos.containing(pos);
        if (!level.hasChunkAt(block)) {
            return;
        }
        ArchivistRelicEntity projection = ModEntities.ARCHIVIST_RELIC.get().create(level);
        if (projection == null) {
            return;
        }
        projection.bind(siteId, relic.id(), relic.slot(), relic.stack());
        projection.moveTo(pos.x, pos.y, pos.z,
                ArchivistPolicy.slotYaw(site.layoutSeed(), relic.slot()), 0.0F);
        if (level.addFreshEntity(projection)) {
            ArchivistSavedData.get(level.getServer()).bindRelicEntity(
                    siteId, relic.id(), projection.getUUID());
        }
    }

    private static Vec3 resolveRelicPosition(ServerLevel level,
                                             ArchivistSavedData.SiteRecord site,
                                             int slot) {
        Vec3 planned = ArchivistPolicy.slotPosition(site.anchor(), slot);
        BlockPos sample = BlockPos.containing(planned);
        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(sample.getX(), 0, sample.getZ()));
        return new Vec3(planned.x, surface.getY() + 0.10D, planned.z);
    }

    public static int debugCreateSite(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos anchor = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.blockPosition().offset(8, 0, 0));
        long region = ArchivistPolicy.regionKey(anchor);
        ArchivistSavedData.get(level.getServer()).ensureSite(region, anchor,
                level.getSeed() ^ region);
        return 1;
    }

    public static int debugFillNearest(ServerPlayer player) {
        ArchivistSavedData.SiteRecord site = nearestSite(player, 96.0D).orElse(null);
        if (site == null) {
            return 0;
        }
        int added = 0;
        for (int i = 0; i < 10; i++) {
            ItemStack stack = i % 4 == 0
                    ? com.frozendawn.item.OrsaIdBadgeItem.createNamed(site.layoutSeed(), i)
                    : i % 2 == 0
                    ? new ItemStack(ModItems.TATTERED_CLOTHING_SCRAP.get())
                    : new ItemStack(ModItems.ICE_SHARD.get(), 1 + i % 3);
            if (deposit(player.serverLevel(), site.id(), stack)) {
                added++;
            }
        }
        return added;
    }

    public static boolean forceSortNearest(ServerPlayer player) {
        ArchivistEntity archivist = player.serverLevel().getEntitiesOfClass(
                        ArchivistEntity.class, player.getBoundingBox().inflate(128.0D))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
        if (archivist == null || archivist.siteId().isEmpty()) {
            return false;
        }
        return rearrangeOneRelic(player.serverLevel(),
                archivist.siteId().get(), player.serverLevel().random);
    }

    public static int purgeLoaded(ServerLevel level, BlockPos center, double radius) {
        ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
        AABB area = new AABB(center).inflate(radius);
        int removed = 0;
        for (ArchivistEntity entity : level.getEntitiesOfClass(
                ArchivistEntity.class, area)) {
            data.clearRegionBinding(entity.regionKey(), 0L);
            entity.discard();
            removed++;
        }
        for (ArchivistRelicEntity relic : level.getEntitiesOfClass(
                ArchivistRelicEntity.class, area)) {
            relic.discard();
            removed++;
        }
        for (ArchivistSavedData.RegionRecord region : data.regions()) {
            ArchivistSavedData.SiteRecord site = region.siteId()
                    .flatMap(data::site).orElse(null);
            if (site != null && area.contains(site.anchor().getCenter())
                    && level.hasChunkAt(site.anchor())) {
                data.removeSite(site.id());
            }
        }
        return removed;
    }

    public static Optional<ArchivistSavedData.SiteRecord> nearestSite(
            ServerPlayer player, double radius) {
        Vec3 center = player.position();
        return ArchivistSavedData.get(player.getServer()).regions().stream()
                .flatMap(region -> region.siteId().stream())
                .map(id -> ArchivistSavedData.get(player.getServer()).site(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(site -> site.anchor().getCenter().distanceToSqr(center)
                        <= radius * radius)
                .min(Comparator.comparingDouble(
                        site -> site.anchor().getCenter().distanceToSqr(center)));
    }

    public static String statusLine(ServerLevel level) {
        ArchivistSavedData data = ArchivistSavedData.get(level.getServer());
        long active = data.regions().stream()
                .filter(region -> region.activeArchivistId().isPresent()).count();
        return "active=" + active + " regions=" + data.recordCount()
                + " sites=" + data.siteCount() + " relics=" + data.relicCount();
    }

    public record SortTask(ItemStack stack, int destinationSlot) {
    }
}
