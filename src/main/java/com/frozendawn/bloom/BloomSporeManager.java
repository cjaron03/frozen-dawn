package com.frozendawn.bloom;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.BloomSavedData;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.BloomSporeCorpseEntity;
import com.frozendawn.entity.BloomSporeEntity;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Loaded-time authority for Hearth fronts, finite satellite nodes, and relays. */
public final class BloomSporeManager {
    private static final long FRONT_SALT = 0x53504F524546524FL;
    private static final int MAINTENANCE_INTERVAL = 20;

    private BloomSporeManager() {
    }

    static int tick(ServerLevel level, ApocalypseState apocalypse, BloomSavedData bloom,
                    ReturnedHearthSavedData hearths, long duration, long budgetStartNanos,
                    int remainingEdits) {
        if (level.dimension() != Level.OVERWORLD
                || !PostMaeveWorldState.isUndoneSpawningReleased(level.getServer())) {
            return 0;
        }

        ensureHearthFronts(bloom, hearths, duration);
        reconcileLoadedBindings(level, bloom);
        for (BloomSavedData.SporeFront front : List.copyOf(bloom.sporeFronts())) {
            if (front.satellite() && !front.removed() && isPlayerLoaded(level, front.anchor())) {
                front.advanceLoaded(1L);
                reconcileCorpse(level, front);
            }
        }

        if (level.getGameTime() % BloomSporePolicy.SPAWN_CHECK_INTERVAL == 0L) {
            runSpawnChecks(level, bloom);
        }

        int edits = 0;
        edits += processTrailRequests(level, remainingEdits - edits, budgetStartNanos);
        for (BloomSavedData.SporeFront front : List.copyOf(bloom.sporeFronts())) {
            if (edits >= remainingEdits
                    || System.nanoTime() - budgetStartNanos
                    >= BloomGrowthManager.MAX_NANOS_PER_TICK) {
                break;
            }
            if (!front.satellite() || front.removed() || !isPlayerLoaded(level, front.anchor())) {
                continue;
            }
            edits += processInitialPatch(level, front, remainingEdits - edits);
            edits += processSatelliteGrowth(level, front, remainingEdits - edits,
                    budgetStartNanos);
        }
        return edits;
    }

    private static void ensureHearthFronts(BloomSavedData bloom,
                                           ReturnedHearthSavedData hearths,
                                           long duration) {
        for (ReturnedHearthSavedData.HearthRecord hearth : hearths.hearths()) {
            bloom.sporeFront(hearth.id(), hearth.id(), hearth.center(), false,
                    BloomGrowthManager.effectiveRadiusFor(bloom, hearth.id(), duration));
        }
    }

    private static void reconcileLoadedBindings(ServerLevel level, BloomSavedData bloom) {
        for (BloomSavedData.SporeFront front : List.copyOf(bloom.sporeFronts())) {
            for (UUID id : List.copyOf(front.activeSporeIds())) {
                BlockPos last = front.activeSporePos(id).orElse(front.anchor());
                if (level.hasChunkAt(last) && level.getEntity(id) == null) {
                    front.clearSpore(id);
                }
            }
        }
    }

    private static void reconcileCorpse(ServerLevel level, BloomSavedData.SporeFront front) {
        if (!front.satellite() || front.removed() || !level.hasChunkAt(front.anchor())) {
            return;
        }
        Entity bound = front.corpseId().map(level::getEntity).orElse(null);
        if (bound instanceof BloomSporeCorpseEntity corpse && corpse.isAlive()) {
            return;
        }
        BloomSporeCorpseEntity recovered = level.getEntitiesOfClass(
                        BloomSporeCorpseEntity.class,
                        AABB.ofSize(Vec3.atCenterOf(front.anchor()), 5.0D, 3.0D, 5.0D),
                        candidate -> candidate.isAlive()
                                && candidate.nodeId().filter(front.id()::equals).isPresent())
                .stream().findFirst().orElse(null);
        if (recovered != null) {
            front.bindCorpse(recovered.getUUID());
            return;
        }
        BloomSporeCorpseEntity corpse = ModEntities.BLOOM_SPORE_CORPSE.get().create(level);
        if (corpse == null) {
            return;
        }
        corpse.bindNode(front.id());
        corpse.moveTo(front.anchor().getX() + 0.5D, supportTop(level, front.anchor()),
                front.anchor().getZ() + 0.5D, corpse.getYRot(), 0.0F);
        corpse.setPersistenceRequired();
        if (level.addFreshEntity(corpse)) {
            front.bindCorpse(corpse.getUUID());
        }
    }

    private static void runSpawnChecks(ServerLevel level, BloomSavedData bloom) {
        if (bloom.activeSporeCount() >= BloomSporePolicy.GLOBAL_ACTIVE_CAP) {
            return;
        }
        List<BloomSavedData.SporeFront> fronts = new ArrayList<>(bloom.sporeFronts());
        fronts.sort(Comparator.comparingDouble(front -> nearestPlayerDistanceSq(
                level, front.anchor())));
        for (BloomSavedData.SporeFront front : fronts) {
            if (bloom.activeSporeCount() >= BloomSporePolicy.GLOBAL_ACTIVE_CAP) {
                return;
            }
            if (front.removed()
                    || front.activeSporeCount()
                    >= BloomSporePolicy.sourceActiveCap(front.satellite())
                    || !isPlayerLoaded(level, front.anchor())) {
                continue;
            }
            boolean relayReady = front.satellite() && !front.relayEmitted()
                    && front.loadedTicks() >= BloomSporePolicy.RELAY_TICKS;
            boolean hearthRoll = !front.satellite()
                    && BloomSporePolicy.shouldSpawn(level.random.nextDouble());
            if (!relayReady && !hearthRoll) {
                continue;
            }
            long seed = BloomGrowthPolicy.mix(level.getSeed() ^ front.id().getMostSignificantBits()
                    ^ front.id().getLeastSignificantBits() ^ level.getGameTime() ^ FRONT_SALT);
            BlockPos spawn = BloomGrowthManager.findFrontierSporeSpawn(
                    level, front.anchor(), front.sourceEdgeRadius(), seed);
            if (spawn == null) {
                continue;
            }
            BloomSporeEntity entity = spawn(level, front, spawn, seed);
            if (entity != null && relayReady) {
                front.markRelayEmitted();
            }
        }
    }

    @Nullable
    private static BloomSporeEntity spawn(ServerLevel level,
                                          BloomSavedData.SporeFront front,
                                          BlockPos spawn, long seed) {
        return spawn(level, front, spawn, seed, front.sourceEdgeRadius());
    }

    @Nullable
    private static BloomSporeEntity spawn(ServerLevel level,
                                          BloomSavedData.SporeFront front,
                                          BlockPos spawn, long seed,
                                          double sourceEdgeRadius) {
        BloomSporeEntity spore = ModEntities.BLOOM_SPORE.get().create(
                level, null, spawn, MobSpawnType.EVENT, true, false);
        if (spore == null) {
            return null;
        }
        Vec3 heading = horizontalDirection(front.anchor(), spawn, seed);
        spore.bindSource(front.id(), front.lineageId(), front.anchor(),
                sourceEdgeRadius, heading);
        spore.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                (float) (Mth.atan2(-heading.x, heading.z) * Mth.RAD_TO_DEG), 0.0F);
        spore.setPersistenceRequired();
        if (!level.addFreshEntity(spore)) {
            return null;
        }
        front.bindSpore(spore.getUUID(), spore.blockPosition());
        return spore;
    }

    private static Vec3 horizontalDirection(BlockPos source, BlockPos spawn, long seed) {
        double dx = spawn.getX() - source.getX();
        double dz = spawn.getZ() - source.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0.01D) {
            return new Vec3(dx / length, 0.0D, dz / length);
        }
        double angle = ((seed >>> 11) & 0xFFFFFFL) / (double) 0xFFFFFFL
                * Math.PI * 2.0D;
        return new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
    }

    private static int processTrailRequests(ServerLevel level, int remainingEdits,
                                            long budgetStartNanos) {
        int edits = 0;
        for (Entity entity : level.getAllEntities()) {
            if (edits >= remainingEdits
                    || System.nanoTime() - budgetStartNanos
                    >= BloomGrowthManager.MAX_NANOS_PER_TICK) {
                break;
            }
            if (entity instanceof BloomSporeEntity spore) {
                BlockPos request = spore.pollTrailRequest();
                if (request != null && BloomGrowthManager.placeSporeTrailTip(level, request)) {
                    spore.onTrailPlaced();
                    edits++;
                }
            }
        }
        return edits;
    }

    private static int processInitialPatch(ServerLevel level,
                                           BloomSavedData.SporeFront front,
                                           int remainingEdits) {
        int edits = 0;
        int attempts = 0;
        while (front.initialPatchEdits() < 3 && edits < remainingEdits && attempts++ < 8) {
            int index = front.initialPatchEdits() + attempts - 1;
            double angle = index * (Math.PI * 2.0D / 7.0D);
            int distance = index == 0 ? 0 : 1 + index / 4;
            BlockPos target = front.anchor().offset(
                    Mth.floor(Math.cos(angle) * distance), 0,
                    Mth.floor(Math.sin(angle) * distance));
            if (BloomGrowthManager.placeSporeTrailTip(level, target)) {
                front.recordInitialPatchEdit();
                edits++;
            }
        }
        return edits;
    }

    private static int processSatelliteGrowth(ServerLevel level,
                                              BloomSavedData.SporeFront front,
                                              int remainingEdits,
                                              long budgetStartNanos) {
        int edits = 0;
        long seed = layoutSeed(level, front);
        int desired = BloomSporePolicy.desiredGrowthCursor(front.loadedTicks());
        while (front.growthCursor() < desired && edits < remainingEdits
                && System.nanoTime() - budgetStartNanos
                < BloomGrowthManager.MAX_NANOS_PER_TICK) {
            int cursor = front.growthCursor();
            if (!isColumnLoaded(level, front.anchor(), seed, cursor)) {
                break;
            }
            edits += BloomGrowthManager.placeSatelliteColumn(level, front.anchor(), seed,
                    cursor, remainingEdits - edits);
            front.setGrowthCursor(cursor + 1);
        }
        if (desired >= BloomSporePolicy.SATELLITE_COLUMNS
                && level.getGameTime() % MAINTENANCE_INTERVAL == 0L
                && edits < remainingEdits) {
            int cursor = front.maintenanceCursor();
            if (isColumnLoaded(level, front.anchor(), seed, cursor)) {
                edits += BloomGrowthManager.placeSatelliteColumn(level, front.anchor(), seed,
                        cursor, remainingEdits - edits);
                front.setMaintenanceCursor(
                        (cursor + 1) % BloomSporePolicy.SATELLITE_COLUMNS);
            }
        }
        return edits;
    }

    private static boolean isColumnLoaded(ServerLevel level, BlockPos anchor,
                                          long seed, int cursor) {
        int permuted = BloomSporePolicy.permutedColumn(cursor, seed);
        BlockPos pos = anchor.offset(BloomSporePolicy.columnX(permuted), 0,
                BloomSporePolicy.columnZ(permuted));
        return level.hasChunkAt(pos);
    }

    private static long layoutSeed(ServerLevel level, BloomSavedData.SporeFront front) {
        return BloomGrowthPolicy.mix(level.getSeed() ^ front.id().getMostSignificantBits()
                ^ front.id().getLeastSignificantBits() ^ 0x534154454C4C4954L);
    }

    public static void updateSporePosition(ServerLevel level, UUID sourceId,
                                           UUID entityId, BlockPos pos) {
        BloomSavedData.get(level.getServer()).sporeFront(sourceId)
                .ifPresent(front -> front.updateSporePos(entityId, pos));
    }

    public static void clearSporeBinding(ServerLevel level, UUID sourceId,
                                         UUID entityId) {
        BloomSavedData.get(level.getServer()).sporeFront(sourceId)
                .ifPresent(front -> front.clearSpore(entityId));
    }

    public static void completeRooting(ServerLevel level, BloomSporeEntity spore) {
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        BloomSavedData.SporeFront source = bloom.sporeFront(spore.getSourceId())
                .orElse(null);
        if (source == null) {
            spore.discard();
            return;
        }
        if (com.frozendawn.aggregate.StillpointPolicy.isSuppressed(
                level, spore.blockPosition())) {
            BlockPos field = com.frozendawn.aggregate.AggregateSavedData
                    .get(level.getServer()).stillpointPos().orElse(null);
            if (field != null) {
                Vec3 safe = com.frozendawn.aggregate.StillpointPolicy.clampOutside(
                        field, spore.position(),
                        com.frozendawn.config.FrozenDawnConfig.STILLPOINT_RADIUS.get());
                spore.teleportTo(safe.x, safe.y, safe.z);
            }
        }
        BlockPos anchor = supportBeneath(level, spore);
        UUID nodeId = UUID.randomUUID();
        BloomSavedData.SporeFront node = bloom.sporeFront(nodeId, source.lineageId(),
                anchor, true, BloomSporePolicy.SATELLITE_RADIUS);
        node.setInitialPatchEdits(spore.getCollapsePatchEdits());
        source.clearSpore(spore.getUUID());
        reconcileCorpse(level, node);
        formCorpseTendrils(level, node, spore.getOutwardHeading());
        spore.discard();
    }

    private static void formCorpseTendrils(ServerLevel level,
                                           BloomSavedData.SporeFront node,
                                           Vec3 heading) {
        Vec3 forward = heading.horizontalDistanceSqr() < 0.001D
                ? new Vec3(0.0D, 0.0D, 1.0D) : heading.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3[] branches = {
                forward.scale(2.6D),
                forward.scale(-2.0D).add(side.scale(0.7D)),
                side.scale(2.4D),
                side.scale(-2.4D)
        };
        double startX = node.anchor().getX() + 0.5D;
        double startY = supportTop(level, node.anchor()) + 0.04D;
        double startZ = node.anchor().getZ() + 0.5D;
        for (Vec3 branch : branches) {
            int segments = 7;
            for (int segment = 1; segment <= segments; segment++) {
                double t = segment / (double) segments;
                double bend = Math.sin(t * Math.PI) * 0.28D;
                double x = startX + branch.x * t + side.x * bend;
                double z = startZ + branch.z * t + side.z * bend;
                level.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                        x, startY + 0.015D * segment, z,
                        3, 0.035D, 0.025D, 0.035D, 0.012D);
            }
            BlockPos tip = node.anchor().offset(
                    Mth.floor(branch.x), 0, Mth.floor(branch.z));
            BloomGrowthManager.placeSporeTrailTip(level, tip);
        }
    }

    private static double supportTop(ServerLevel level, BlockPos support) {
        VoxelShape collision = level.getBlockState(support)
                .getCollisionShape(level, support);
        double height = collision.isEmpty() ? 1.0D : collision.max(Direction.Axis.Y);
        return support.getY() + height + 0.03D;
    }

    private static BlockPos supportBeneath(ServerLevel level, BloomSporeEntity spore) {
        int x = Mth.floor(spore.getX());
        int z = Mth.floor(spore.getZ());
        int startY = Math.min(Mth.floor(spore.getY() - 0.01D),
                level.getMaxBuildHeight() - 1);
        int stopY = Math.max(level.getMinBuildHeight(), startY - 48);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, startY, z);
        for (int y = startY; y >= stopY; y--) {
            cursor.setY(y);
            if (!level.isLoaded(cursor)) {
                break;
            }
            VoxelShape collision = level.getBlockState(cursor)
                    .getCollisionShape(level, cursor);
            if (!collision.isEmpty()
                    && y + collision.max(Direction.Axis.Y) <= spore.getY() + 0.12D) {
                return cursor.immutable();
            }
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spore.blockPosition()).below();
    }

    public static boolean removeNode(ServerLevel level, UUID nodeId) {
        BloomSavedData.SporeFront node = BloomSavedData.get(level.getServer())
                .sporeFront(nodeId).orElse(null);
        if (node == null || !node.satellite() || node.removed()) {
            return false;
        }
        node.markRemoved();
        return true;
    }

    @Nullable
    public static BloomSporeEntity debugSpawn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        BloomSavedData.SporeFront source = bloom.sporeFronts().stream()
                .filter(front -> !front.satellite() && !front.removed()
                        && front.activeSporeCount()
                        < BloomSporePolicy.sourceActiveCap(false))
                .min(Comparator.comparingDouble(front -> player.distanceToSqr(
                        Vec3.atCenterOf(front.anchor())))).orElse(null);
        if (source == null) {
            return null;
        }
        BlockPos probe = player.blockPosition().offset(5, 0, 3);
        BlockPos spawn = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
        long seed = level.random.nextLong();
        double distanceFromSource = Math.hypot(
                spawn.getX() - source.anchor().getX(),
                spawn.getZ() - source.anchor().getZ());
        double debugEdgeRadius = Math.max(
                source.sourceEdgeRadius(), distanceFromSource);
        return spawn(level, source, spawn, seed, debugEdgeRadius);
    }

    public static boolean debugRootNearest(ServerPlayer player) {
        BloomSporeEntity spore = player.serverLevel().getEntitiesOfClass(
                        BloomSporeEntity.class, player.getBoundingBox().inflate(96.0D)).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        return spore != null && spore.beginRooting();
    }

    public static void debugAdvance(ServerLevel level, long ticks) {
        for (BloomSavedData.SporeFront front
                : BloomSavedData.get(level.getServer()).sporeFronts()) {
            if (front.satellite() && !front.removed()) {
                front.advanceLoaded(ticks);
            }
        }
    }

    public static String statusLine(ServerLevel level) {
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        long hearths = bloom.sporeFronts().stream().filter(front -> !front.satellite()).count();
        long satellites = bloom.sporeFronts().stream()
                .filter(front -> front.satellite() && !front.removed()).count();
        long relays = bloom.sporeFronts().stream().filter(BloomSavedData.SporeFront::relayEmitted)
                .count();
        return "sources=" + hearths + " satellites=" + satellites
                + " active=" + bloom.activeSporeCount() + "/"
                + BloomSporePolicy.GLOBAL_ACTIVE_CAP + " relays=" + relays;
    }

    public static int debugPurgeLoaded(ServerLevel level) {
        int removed = 0;
        List<Entity> loadedEntities = new ArrayList<>();
        level.getAllEntities().forEach(loadedEntities::add);
        for (Entity entity : loadedEntities) {
            if (entity instanceof BloomSporeEntity
                    || entity instanceof BloomSporeCorpseEntity) {
                entity.discard();
                removed++;
            }
        }
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        List<BloomSavedData.SporeFront> fronts = List.copyOf(bloom.sporeFronts());
        for (BloomSavedData.SporeFront front : fronts) {
            if (front.satellite()) {
                if (level.hasChunkAt(front.anchor())) {
                    removed += removeSatelliteBlocks(level, front.anchor());
                }
                bloom.removeSporeFront(front.id());
            } else {
                for (UUID id : List.copyOf(front.activeSporeIds())) {
                    front.clearSpore(id);
                }
            }
        }
        return removed;
    }

    private static int removeSatelliteBlocks(ServerLevel level, BlockPos anchor) {
        int removed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                anchor.offset(-BloomSporePolicy.SATELLITE_RADIUS, -2,
                        -BloomSporePolicy.SATELLITE_RADIUS),
                anchor.offset(BloomSporePolicy.SATELLITE_RADIUS,
                        BloomSporePolicy.MAX_SATELLITE_HEIGHT + 3,
                        BloomSporePolicy.SATELLITE_RADIUS))) {
            int dx = pos.getX() - anchor.getX();
            int dz = pos.getZ() - anchor.getZ();
            if (dx * dx + dz * dz <= BloomSporePolicy.SATELLITE_RADIUS
                    * BloomSporePolicy.SATELLITE_RADIUS
                    && BloomGrowthManager.isBloomState(level.getBlockState(pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                removed++;
            }
        }
        return removed;
    }

    private static boolean isPlayerLoaded(ServerLevel level, BlockPos center) {
        double range = level.getServer().getPlayerList().getViewDistance() * 16.0D + 48.0D;
        double rangeSqr = range * range;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                double dx = player.getX() - center.getX();
                double dz = player.getZ() - center.getZ();
                if (dx * dx + dz * dz <= rangeSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double nearestPlayerDistanceSq(ServerLevel level, BlockPos center) {
        return level.players().stream().filter(player -> !player.isSpectator())
                .mapToDouble(player -> player.distanceToSqr(Vec3.atCenterOf(center)))
                .min().orElse(Double.MAX_VALUE);
    }
}
