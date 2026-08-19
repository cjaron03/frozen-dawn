package com.frozendawn.bloom;

import com.frozendawn.block.BloomMassBlock;
import com.frozendawn.block.SealedLatticeBlock;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.BloomSavedData;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.RocketLaunchEntity;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModParticles;
import com.frozendawn.init.ModSounds;
import com.frozendawn.mixin.ChunkMapAccessor;
import com.frozendawn.network.BloomStatePayload;
import com.frozendawn.network.HearthBoundaryEffectPayload;
import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.world.ChunkCatchUpManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Loaded-chunk-only terrain growth with a shared hard edit/time budget. */
public final class BloomGrowthManager {
    public static final int MAX_EDITS_PER_TICK = 96;
    public static final long MAX_NANOS_PER_TICK = 1_500_000L;
    private static final int CHUNK_COLUMNS = 16 * 16;
    private static final int SCAN_INTERVAL = 20;
    private static final int MAX_DISCOVERY_CHUNKS_PER_SCAN = 96;
    private static final int MAX_COLUMN_STEPS_PER_SLICE = 32;
    private static final int MAX_BLOOM_COLUMN_HEIGHT = 30;

    private static long lastTickNanos;
    private static long maxTickNanos;
    private static int lastEdits;
    private static int lastColumns;
    private static boolean deferred;
    private static long workRotation;
    private static long discoveryRotation;

    private BloomGrowthManager() {
    }

    public static void tick(ServerLevel level, ApocalypseState apocalypse) {
        lastTickNanos = 0L;
        lastEdits = 0;
        lastColumns = 0;
        deferred = false;
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        boolean releaseElapsed = PostMaeveWorldState.isBloomReleased(level.getServer());
        if (!releaseElapsed && !bloom.firstEruptionStarted()
                && !bloom.firstEruptionComplete()) {
            if (level.getGameTime() % 20L == 0L) {
                syncEmptyBloomState(level);
            }
            return;
        }
        // Radius zero is the explicit debug-purge pause. Do not let the exact
        // Hearth-center column remain a valid zero-distance growth candidate.
        if (bloom.debugRadius() == 0) {
            if (level.getGameTime() % 20L == 0L) {
                syncEmptyBloomState(level);
            }
            return;
        }
        ReturnedHearthSavedData hearths = ReturnedHearthSavedData.get(level.getServer());
        if (!bloom.firstEruptionComplete()) {
            int eruptionEdits = tickFirstEruption(level, bloom, hearths,
                    releaseElapsed || bloom.firstEruptionStarted());
            lastEdits = eruptionEdits;
            if (level.getGameTime() % 20L == 0L) {
                syncEmptyBloomState(level);
            }
            return;
        }
        long start = System.nanoTime();
        if (level.getGameTime() % 20L == 0L) {
            syncPlayerStates(level);
            if (System.nanoTime() - start >= MAX_NANOS_PER_TICK) {
                lastTickNanos = System.nanoTime() - start;
                maxTickNanos = Math.max(maxTickNanos, lastTickNanos);
                return;
            }
        }
        if (ChunkCatchUpManager.isBloomDeferred()) {
            deferred = true;
            lastTickNanos = System.nanoTime() - start;
            maxTickNanos = Math.max(maxTickNanos, lastTickNanos);
            return;
        }

        long duration = BloomGrowthPolicy.presetDurationTicks(apocalypse.getPresetName());

        for (ReturnedHearthSavedData.HearthRecord hearth : hearths.hearths()) {
            if (loadedAreaIntersects(level, hearth.center())) {
                bloom.hearth(hearth.id()).advance(1L);
            }
        }

        if (level.getGameTime() % SCAN_INTERVAL == 0L) {
            discoverLoadedChunks(level, hearths, bloom, duration, start);
        }

        List<BloomSavedData.ChunkGrowth> work = new ArrayList<>(bloom.chunks());
        work.removeIf(record -> record.complete() || !level.hasChunk(
                new ChunkPos(record.chunkPos()).x,
                new ChunkPos(record.chunkPos()).z));
        work.sort(Comparator.comparingLong(BloomSavedData.ChunkGrowth::chunkPos)
                .thenComparing(record -> record.hearthId().toString()));
        if (!work.isEmpty()) {
            int startIndex = (int) Math.floorMod(workRotation++, work.size());
            Collections.rotate(work, -startIndex);
        }

        int edits = 0;
        int columns = 0;
        for (ReturnedHearthSavedData.HearthRecord hearth : hearths.hearths()) {
            if (edits >= MAX_EDITS_PER_TICK
                    || System.nanoTime() - start >= MAX_NANOS_PER_TICK) {
                break;
            }
            BloomSavedData.HearthGrowth growth = bloom.hearth(hearth.id());
            boolean centerLoaded = level.hasChunk(
                    hearth.center().getX() >> 4, hearth.center().getZ() >> 4);
            if (hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                    && growth.originRootBase().isEmpty()) {
                BlockPos anchor = originRootAnchor(hearth);
                if (level.hasChunk(anchor.getX() >> 4, anchor.getZ() >> 4)) {
                    growth.rememberOriginRootBase(resolveOriginRootBase(level, hearth));
                }
            }
            if (!growth.seeded() && centerLoaded) {
                int seedEdits = formLivingSeed(
                        level, hearth, MAX_EDITS_PER_TICK - edits);
                edits += seedEdits;
                if (seedEdits > 0) {
                    growth.markSeeded();
                }
            }
            if (hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                    && !growth.originRootFormed()
                    && BloomOriginRootPolicy.canForm(
                    growth.activeTicks(), growth.seeded())) {
                BlockPos anchor = originRootAnchor(hearth);
                if (level.hasChunk(anchor.getX() >> 4, anchor.getZ() >> 4)) {
                    edits += formOriginRoot(level, hearth, growth,
                            MAX_EDITS_PER_TICK - edits);
                }
            }
        }
        for (BloomSavedData.ChunkGrowth record : work) {
            if (edits >= MAX_EDITS_PER_TICK || System.nanoTime() - start >= MAX_NANOS_PER_TICK) {
                break;
            }
            ReturnedHearthSavedData.HearthRecord hearth = hearths.hearth(record.hearthId())
                    .orElse(null);
            if (hearth == null) {
                record.markComplete();
                continue;
            }
            ChunkPos chunkPos = new ChunkPos(record.chunkPos());
            if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
                continue;
            }
            int radius = effectiveRadius(bloom, hearth.id(), duration);
            if (!chunkIntersectsRadius(chunkPos, hearth.center(), radius)) {
                continue;
            }

            if (!record.seedAttempted()) {
                edits += attemptChunkSeed(level, hearth, chunkPos, radius,
                        MAX_EDITS_PER_TICK - edits);
                record.markSeedAttempted();
            }

            int sliceEnd = Math.min(CHUNK_COLUMNS,
                    record.cursor() + MAX_COLUMN_STEPS_PER_SLICE);
            while (record.cursor() < sliceEnd && edits < MAX_EDITS_PER_TICK
                    && System.nanoTime() - start < MAX_NANOS_PER_TICK) {
                int cursor = record.cursor();
                int columnEdits = processColumn(level, hearths, bloom, hearth, chunkPos,
                        radius, duration, cursor, MAX_EDITS_PER_TICK - edits);
                edits += columnEdits;
                record.recordEdits(columnEdits);
                record.setCursor(cursor + 1);
                columns++;
            }
            if (record.cursor() >= CHUNK_COLUMNS) {
                BlockPos center = chunkPos.getMiddleBlockPosition(0);
                int overlaps = overlapCount(hearths, bloom, center, duration);
                int band = BloomGrowthPolicy.band(
                        horizontalDistance(center, hearth.center()), radius).ordinal();
                record.finishPass(band, overlaps);
            }
        }
        edits += BloomSporeManager.tick(level, apocalypse, bloom, hearths, duration,
                start, MAX_EDITS_PER_TICK - edits);
        lastTickNanos = System.nanoTime() - start;
        maxTickNanos = Math.max(maxTickNanos, lastTickNanos);
        lastEdits = edits;
        lastColumns = columns;
    }

    private static void syncEmptyBloomState(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, new BloomStatePayload(0.0F, 0));
        }
    }

    private static int tickFirstEruption(
            ServerLevel level, BloomSavedData bloom,
            ReturnedHearthSavedData hearths, boolean mayBegin) {
        if (!bloom.firstEruptionStarted()) {
            if (!mayBegin) {
                return 0;
            }
            ReturnedHearthSavedData.HearthRecord hearth = firstLoadedEruptionHearth(
                    level, hearths);
            if (hearth == null) {
                return 0;
            }
            BlockPos base = resolveFirstEruptionBase(level, hearth);
            if (base == null || !bloom.startFirstEruption(
                    hearth.id(), base, level.getGameTime())) {
                return 0;
            }
            sendEruptionEffect(level, base,
                    HearthBoundaryEffectPayload.bloomEruptionRumble(), 160.0D);
        }

        BlockPos base = bloom.firstEruptionBase().orElse(null);
        UUID hearthId = bloom.firstEruptionHearthId().orElse(null);
        if (base == null || hearthId == null || !level.isLoaded(base)) {
            return 0;
        }
        long elapsed = Math.max(0L,
                level.getGameTime() - bloom.firstEruptionStartGameTime());
        BloomEruptionPolicy.Stage stage = BloomEruptionPolicy.stage(elapsed);
        if (stage == BloomEruptionPolicy.Stage.RUMBLING) {
            spawnEruptionRumble(level, base, elapsed);
            return 0;
        }

        int edits = 0;
        if (!bloom.firstEruptionImpactPlayed()) {
            edits += eruptFirstCrystal(level, base);
            bloom.markFirstEruptionImpactPlayed();
            sendEruptionEffect(level, base,
                    HearthBoundaryEffectPayload.bloomEruptionImpact(), 192.0D);
            spawnEruptionImpact(level, base);
        } else if (stage == BloomEruptionPolicy.Stage.ERUPTING
                && elapsed % 3L == 0L) {
            spawnEruptionLift(level, base, 18);
        }

        if (stage != BloomEruptionPolicy.Stage.COMPLETE) {
            return edits;
        }
        ReturnedHearthSavedData.HearthRecord hearth = hearths.hearth(hearthId)
                .orElse(null);
        if (hearth != null) {
            BloomSavedData.HearthGrowth growth = bloom.hearth(hearth.id());
            if (hearth.type() == HearthSelectionPolicy.HearthType.MAJOR) {
                growth.rememberOriginRootBase(base);
            }
            edits += formLivingSeed(level, hearth,
                    Math.max(0, MAX_EDITS_PER_TICK - edits));
            growth.markSeeded();
        }
        bloom.completeFirstEruption();
        grantEruptionContact(level, base);
        return edits;
    }

    private static ReturnedHearthSavedData.HearthRecord firstLoadedEruptionHearth(
            ServerLevel level, ReturnedHearthSavedData hearths) {
        return hearths.hearths().stream()
                .filter(hearth -> {
                    BlockPos anchor = hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                            ? originRootAnchor(hearth) : hearth.center();
                    return level.hasChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
                })
                .min(Comparator.comparingInt(hearth ->
                        hearth.type() == HearthSelectionPolicy.HearthType.MAJOR ? 0 : 1))
                .orElse(null);
    }

    private static BlockPos resolveFirstEruptionBase(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth) {
        BlockPos anchor = hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                ? originRootAnchor(hearth) : hearth.center();
        long seed = BloomGrowthPolicy.mix(level.getSeed() ^ hearth.layoutSeed()
                ^ 0x4552555054494F4EL);
        for (int attempt = 0; attempt < 65; attempt++) {
            int radius = attempt == 0 ? 0 : 1 + (attempt - 1) / 8;
            double angle = (attempt + (seed & 31L)) * 2.399963229728653D;
            int x = anchor.getX() + Mth.floor(Math.cos(angle) * radius);
            int z = anchor.getZ() + Mth.floor(Math.sin(angle) * radius);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));
            BlockState mass = ModBlocks.BLOOM_MASS.get().defaultBlockState()
                    .setValue(BloomMassBlock.BAND, BloomBand.CORE);
            if (!isProtected(level, surface) && canPlaceAt(level, surface, mass)) {
                return surface;
            }
        }
        return null;
    }

    private static int eruptFirstCrystal(ServerLevel level, BlockPos base) {
        int edits = 0;
        edits += placeOriginRootPart(level, base,
                BloomOriginRootPolicy.Material.MASS) ? 1 : 0;
        edits += placeOriginRootPart(level, base.above(),
                BloomOriginRootPolicy.Material.CORE) ? 1 : 0;
        edits += placeOriginRootPart(level, base.above(2),
                BloomOriginRootPolicy.Material.MASS) ? 1 : 0;
        edits += placeOriginRootPart(level, base.above(3),
                BloomOriginRootPolicy.Material.TIP) ? 1 : 0;
        return edits;
    }

    private static void spawnEruptionRumble(
            ServerLevel level, BlockPos base, long elapsed) {
        if (elapsed % 4L != 0L) {
            return;
        }
        float progress = BloomEruptionPolicy.rumbleProgress(elapsed);
        int count = 5 + Mth.floor(progress * 18.0F);
        level.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                base.getX() + 0.5D, base.getY() + 0.08D, base.getZ() + 0.5D,
                count, 0.8D + progress * 1.8D, 0.05D,
                0.8D + progress * 1.8D, 0.015D + progress * 0.025D);
        if (elapsed > 0L && elapsed % 20L == 0L) {
            level.playSound(null, base, ModSounds.BLOOM_CRACK.get(),
                    SoundSource.AMBIENT, 1.3F, 0.62F + progress * 0.18F);
        }
    }

    private static void spawnEruptionImpact(ServerLevel level, BlockPos base) {
        spawnEruptionLift(level, base, 120);
        BlockParticleOption debris = new BlockParticleOption(
                ParticleTypes.BLOCK,
                ModBlocks.BLOOM_MASS.get().defaultBlockState()
                        .setValue(BloomMassBlock.BAND, BloomBand.CORE));
        level.sendParticles(debris,
                base.getX() + 0.5D, base.getY() + 0.5D, base.getZ() + 0.5D,
                96, 1.4D, 0.35D, 1.4D, 0.42D);
    }

    private static void spawnEruptionLift(
            ServerLevel level, BlockPos base, int count) {
        level.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D,
                count, 2.2D, 0.45D, 2.2D, 0.18D);
    }

    private static void sendEruptionEffect(
            ServerLevel level, BlockPos base,
            HearthBoundaryEffectPayload payload, double radius) {
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(
                    base.getX() + 0.5D, base.getY() + 0.5D,
                    base.getZ() + 0.5D) <= radiusSqr) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void grantEruptionContact(ServerLevel level, BlockPos base) {
        double radiusSqr = 112.0D * 112.0D;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(
                    base.getX() + 0.5D, base.getY() + 0.5D,
                    base.getZ() + 0.5D) > radiusSqr) {
                continue;
            }
            if (!hasAdvancement(player, "it_kept_going")) {
                WorldTickHandler.grantAdvancement(player, "it_kept_going");
            }
            PacketDistributor.sendToPlayer(
                    player, HearthBoundaryEffectPayload.bloomContact());
        }
    }

    private static void syncPlayerStates(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            PlayerBloomSample sample = sampleAround(level, player.blockPosition(), 12);
            PacketDistributor.sendToPlayer(player, new BloomStatePayload(
                    sample.density, sample.band.ordinal()));
            boolean needsContact = !hasAdvancement(player, "it_kept_going");
            BlockPos contact = needsContact
                    ? findNearestBloom(level, player.blockPosition(), 12) : null;
            if (contact != null && hasLineOfSight(level, player, contact)) {
                WorldTickHandler.grantAdvancement(player, "it_kept_going");
                PacketDistributor.sendToPlayer(
                        player, HearthBoundaryEffectPayload.bloomContact());
            }
        }
    }

    private static PlayerBloomSample sampleAround(ServerLevel level, BlockPos center,
                                                  int radius) {
        int count = 0;
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        BloomBand strongest = BloomBand.FRONTIER;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -6; y <= 6; y += 2) {
            for (int z = -radius; z <= radius; z += 2) {
                for (int x = -radius; x <= radius; x += 2) {
                    if (x * x + y * y + z * z > radius * radius) {
                        continue;
                    }
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(cursor);
                    BloomBand band = bandForState(state);
                    if (band == null) {
                        continue;
                    }
                    count++;
                    if (band.ordinal() > strongest.ordinal()) {
                        strongest = band;
                    }
                    double distance = cursor.distSqr(center);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = cursor.immutable();
                    }
                }
            }
        }
        return new PlayerBloomSample(Math.min(1.0F, count / 64.0F), strongest, nearest);
    }

    private static BlockPos findNearestBloom(ServerLevel level, BlockPos center, int radius) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    int distanceSqr = x * x + y * y + z * z;
                    if (distanceSqr > radius * radius || distanceSqr >= nearestDistance) {
                        continue;
                    }
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(ModBlocks.BLOOM_MASS.get())
                            || state.is(ModBlocks.BLOOM_CRUST.get())
                            || state.is(ModBlocks.BLOOM_TIP.get())
                            || state.is(ModBlocks.BLOOM_CORE.get())) {
                        nearestDistance = distanceSqr;
                        nearest = cursor.immutable();
                    }
                }
            }
        }
        return nearest;
    }

    public static float pressureMultiplier(ServerLevel level, BlockPos pos) {
        return 1.0F + 1.25F * localDensity(level, pos);
    }

    public static float localDensity(ServerLevel level, BlockPos pos) {
        if (!PostMaeveWorldState.isBloomReleased(level.getServer())) {
            return 0.0F;
        }
        return sampleAround(level, pos, 12).density;
    }

    /** Returns -1 outside Bloom, otherwise the strongest local density band ordinal. */
    public static int localBandOrdinal(ServerLevel level, BlockPos pos) {
        if (!PostMaeveWorldState.isBloomReleased(level.getServer())) {
            return -1;
        }
        PlayerBloomSample sample = sampleAround(level, pos, 12);
        return sample.nearest == null ? -1 : sample.band.ordinal();
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer player,
                                          BlockPos target) {
        Vec3 start = player.getEyePosition();
        Vec3 end = target.getCenter();
        BlockHitResult hit = level.clip(new ClipContext(
                start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        return hit.getBlockPos().equals(target);
    }

    private static boolean hasAdvancement(ServerPlayer player, String name) {
        net.minecraft.advancements.AdvancementHolder holder = player.getServer()
                .getAdvancements().get(net.minecraft.resources.ResourceLocation
                        .fromNamespaceAndPath("frozendawn", name));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private record PlayerBloomSample(float density, BloomBand band, BlockPos nearest) {
    }

    private static void discoverLoadedChunks(ServerLevel level,
                                             ReturnedHearthSavedData hearths,
                                             BloomSavedData bloom, long duration,
                                             long tickStartNanos) {
        List<LevelChunk> loaded = loadedChunks(level);
        loaded.sort(Comparator.comparingLong(chunk -> chunk.getPos().toLong()));
        if (loaded.isEmpty()) {
            return;
        }
        int startIndex = (int) Math.floorMod(discoveryRotation, loaded.size());
        int processed = 0;
        int limit = Math.min(MAX_DISCOVERY_CHUNKS_PER_SCAN, loaded.size());
        while (processed < limit
                && System.nanoTime() - tickStartNanos < MAX_NANOS_PER_TICK) {
            LevelChunk loadedChunk = loaded.get((startIndex + processed) % loaded.size());
            ChunkPos chunk = loadedChunk.getPos();
            for (ReturnedHearthSavedData.HearthRecord hearth : hearths.hearths()) {
                int radius = effectiveRadius(bloom, hearth.id(), duration);
                if (!chunkIntersectsRadius(chunk, hearth.center(), radius)) {
                    continue;
                }
                BloomSavedData.ChunkGrowth record = bloom.chunk(
                        hearth.id(), chunk.toLong());
                BlockPos center = chunk.getMiddleBlockPosition(0);
                record.prepareFor(BloomGrowthPolicy.band(
                                horizontalDistance(center, hearth.center()), radius)
                        .ordinal(), overlapCount(hearths, bloom, center, duration));
            }
            processed++;
        }
        discoveryRotation += processed;
    }

    private static List<LevelChunk> loadedChunks(ServerLevel level) {
        List<LevelChunk> loaded = new ArrayList<>();
        Iterable<ChunkHolder> holders = ((ChunkMapAccessor) (Object)
                level.getChunkSource().chunkMap).frozendawn$getChunks();
        for (ChunkHolder holder : holders) {
            ChunkPos pos = holder.getPos();
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null) {
                loaded.add(chunk);
            }
        }
        return loaded;
    }

    private static int attemptChunkSeed(ServerLevel level,
                                        ReturnedHearthSavedData.HearthRecord hearth,
                                        ChunkPos chunk, int radius, int remainingEdits) {
        if (remainingEdits <= 0) {
            return 0;
        }
        long hash = BloomGrowthPolicy.chunkSeed(
                level.getSeed(), hearth.layoutSeed(), chunk.toLong());
        int attempts = BloomGrowthPolicy.initialTipAttempts(hash);
        int edits = 0;
        for (int i = 0; i < attempts && edits < remainingEdits; i++) {
            int x = chunk.getMinBlockX() + (int) Math.floorMod(hash >>> (i * 9), 16L);
            int z = chunk.getMinBlockZ() + (int) Math.floorMod(hash >>> (i * 11 + 5), 16L);
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            double distance = horizontalDistance(surface, hearth.center());
            if (distance > radius || sealedOnLine(level, hearth.center(), surface)) {
                continue;
            }
            if (!isProtected(level, surface)
                    && canPlaceAt(level, surface,
                    ModBlocks.BLOOM_TIP.get().defaultBlockState())) {
                level.setBlock(surface, ModBlocks.BLOOM_TIP.get().defaultBlockState(), 2);
                edits++;
            }
        }
        return edits;
    }

    private static int formLivingSeed(ServerLevel level,
                                      ReturnedHearthSavedData.HearthRecord hearth,
                                      int remainingEdits) {
        int edits = 0;
        long seed = BloomGrowthPolicy.mix(level.getSeed() ^ hearth.layoutSeed());
        for (int i = 0; i < 24 && edits < remainingEdits; i++) {
            double angle = i * (Math.PI * 2.0D / 24.0D) + (seed & 255L) / 255.0D;
            double distance = 2.0D + Math.floorMod(seed >>> (i % 16), 11L);
            int x = Mth.floor(hearth.center().getX() + Math.cos(angle) * distance);
            int z = Mth.floor(hearth.center().getZ() + Math.sin(angle) * distance);
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!isProtected(level, surface)
                    && canPlaceAt(level, surface,
                    ModBlocks.BLOOM_TIP.get().defaultBlockState())) {
                level.setBlock(surface, ModBlocks.BLOOM_TIP.get().defaultBlockState(), 2);
                edits++;
            }
        }
        return edits;
    }

    private static int formOriginRoot(ServerLevel level,
                                      ReturnedHearthSavedData.HearthRecord hearth,
                                      BloomSavedData.HearthGrowth growth,
                                      int remainingEdits) {
        if (remainingEdits <= 0 || growth.originRootFormed()) {
            return 0;
        }
        List<BloomOriginRootPolicy.Placement> layout = BloomOriginRootPolicy.layout(
                level.getSeed() ^ hearth.layoutSeed());
        int cursor = Math.min(growth.originRootCursor(), layout.size());
        BlockPos base = growth.originRootBase().orElseGet(() -> {
            BlockPos resolved = resolveOriginRootBase(level, hearth);
            growth.rememberOriginRootBase(resolved);
            return resolved;
        });
        int edits = 0;
        int steps = 0;
        int editLimit = Math.min(remainingEdits,
                BloomOriginRootPolicy.MAX_PLACEMENTS_PER_TICK);
        while (cursor < layout.size() && edits < editLimit && steps < 12) {
            BloomOriginRootPolicy.Placement placement = layout.get(cursor);
            BlockPos target = base.offset(placement.offset());
            if (!level.isLoaded(target)) {
                break;
            }
            if (placeOriginRootPart(level, target, placement.material())) {
                edits++;
            }
            cursor++;
            steps++;
        }
        growth.setOriginRootCursor(cursor);
        if (cursor >= layout.size()) {
            growth.markOriginRootFormed();
        }
        return edits;
    }

    private static BlockPos resolveOriginRootBase(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth) {
        BlockPos resolved = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, originRootAnchor(hearth));
        for (int depth = 0; depth < MAX_BLOOM_COLUMN_HEIGHT + 4; depth++) {
            BlockState below = level.getBlockState(resolved.below());
            if (!isBloomBlock(below) && !below.is(Blocks.SNOW)
                    && !below.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
                break;
            }
            resolved = resolved.below();
        }
        return resolved;
    }

    private static BlockPos originRootAnchor(
            ReturnedHearthSavedData.HearthRecord hearth) {
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        return new BlockPos(anchor.getX(), 0, anchor.getZ());
    }

    private static boolean placeOriginRootPart(
            ServerLevel level, BlockPos pos, BloomOriginRootPolicy.Material material) {
        if (isProtected(level, pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        BlockState target = switch (material) {
            case MASS -> ModBlocks.BLOOM_MASS.get().defaultBlockState()
                    .setValue(BloomMassBlock.BAND, BloomBand.CORE);
            case CORE -> ModBlocks.BLOOM_CORE.get().defaultBlockState();
            case TIP -> ModBlocks.BLOOM_TIP.get().defaultBlockState();
        };
        if (current == target || current.equals(target)) {
            return false;
        }
        if (current.is(ModBlocks.BLOOM_CORE.get())) {
            return false;
        }
        if (isBloomBlock(current)) {
            if (material == BloomOriginRootPolicy.Material.TIP
                    && !current.is(ModBlocks.BLOOM_TIP.get())) {
                return false;
            }
            level.setBlock(pos, target, 2);
            return true;
        }
        if (!canPlaceAtIgnoringOriginCrown(level, pos, target)) {
            return false;
        }
        level.setBlock(pos, target, 2);
        return true;
    }

    private static int processColumn(ServerLevel level,
                                     ReturnedHearthSavedData hearths,
                                     BloomSavedData bloom,
                                     ReturnedHearthSavedData.HearthRecord hearth,
                                     ChunkPos chunk, int radius, long duration, int cursor,
                                     int remainingEdits) {
        if (remainingEdits <= 0) {
            return 0;
        }
        int x = chunk.getMinBlockX() + (cursor & 15);
        int z = chunk.getMinBlockZ() + ((cursor >>> 4) & 15);
        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        BlockPos rawTop = surface.below();
        BlockPos coveredBloomTop = findBloomTopBelowSurfaceCover(level, rawTop);
        BlockPos top = coveredBloomTop == null ? rawTop : coveredBloomTop;
        int existingHeight = 0;
        while (existingHeight <= MAX_BLOOM_COLUMN_HEIGHT
                && isBloomBlock(level.getBlockState(top))) {
            existingHeight++;
            top = top.below();
        }
        // The old 64-block scan could mistake a tall Bloom column for terrain and
        // stack another column above it. Legacy columns beyond the cap stay inert.
        if (existingHeight > MAX_BLOOM_COLUMN_HEIGHT) {
            return 0;
        }
        int existingAboveBase = Math.max(0, existingHeight - 1);
        BlockPos base = existingHeight > 0 ? top.above() : top;
        if (isOriginCrownClearance(level, base)) {
            return 0;
        }
        int maxGrowthY = base.getY() + MAX_BLOOM_COLUMN_HEIGHT - 1;
        BlockPos openSurface = base.above(existingAboveBase + 1);
        double distance = horizontalDistance(base, hearth.center());
        if (distance > radius) {
            return 0;
        }

        long hash = BloomGrowthPolicy.mix(level.getSeed() ^ hearth.layoutSeed()
                ^ base.asLong() ^ 0x47524F5754484C4FL);
        int overlaps = overlapCount(hearths, bloom, base, duration);
        BloomBand band = BloomGrowthPolicy.band(distance, radius);
        double coverage = BloomGrowthPolicy.coverage(band, hash, overlaps);
        if (((hash >>> 24) & 0xFFFFFFL) / (double) 0xFFFFFFL > coverage) {
            return 0;
        }
        boolean adjacent = hasBloomNeighbor(level, base, 2);
        if (!adjacent && !bloom.chunk(hearth.id(), chunk.toLong()).seedAttempted()) {
            return 0;
        }
        if (!adjacent && distance > BloomGrowthPolicy.INITIAL_RADIUS + 2) {
            return 0;
        }
        if (!isExposed(level, base) || isProtected(level, base)) {
            return 0;
        }

        int edits = 0;
        if (band == BloomBand.FRONTIER) {
            if (existingHeight > 0) {
                return 0;
            }
            if (isBloomBlock(level.getBlockState(openSurface))) {
                return 0;
            }
            if (level.getBlockState(openSurface).isAir()
                    && canPlaceAt(level, openSurface,
                    ModBlocks.BLOOM_CRUST.get().defaultBlockState())) {
                int layers = 1 + (int) Math.floorMod(hash, 3L);
                BlockState crust = ModBlocks.BLOOM_CRUST.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, layers);
                level.setBlock(openSurface, crust, 2);
                return 1;
            }
            return convert(level, base, BloomBand.FRONTIER) ? 1 : 0;
        }

        if (convert(level, base, band)) {
            edits++;
        }
        int height = BloomGrowthPolicy.maxHeight(band, hash, overlaps);
        int targetAboveBase = Math.max(0, height - 1);
        // Each pass adds only a short visible segment; repeated loaded passes build height.
        int segment = Math.min(Math.max(0, targetAboveBase - existingAboveBase),
                Math.min(4, remainingEdits - edits));
        segment = Math.min(segment, Math.max(0,
                maxGrowthY - base.above(existingAboveBase).getY()));
        for (int y = existingAboveBase + 1; y <= existingAboveBase + segment; y++) {
            BlockPos place = base.above(y);
            if (place.getY() > maxGrowthY || !canPlaceAt(level, place,
                    ModBlocks.BLOOM_MASS.get().defaultBlockState().setValue(
                            BloomMassBlock.BAND, band)) || isProtected(level, place)) {
                break;
            }
            level.setBlock(place, ModBlocks.BLOOM_MASS.get().defaultBlockState()
                    .setValue(BloomMassBlock.BAND, band), 2);
            edits++;
        }
        int visibleHeight = existingAboveBase + segment;
        BlockPos crown = base.above(Math.max(1, visibleHeight));
        boolean atMatureHeight = visibleHeight >= height - 1;
        if (band == BloomBand.MID && Math.floorMod(hash, 37L) == 0L
                && edits < remainingEdits) {
            edits += placeMalformedDoorframe(level, base.above(), hash,
                    remainingEdits - edits, maxGrowthY);
        } else if (band == BloomBand.CORE && Math.floorMod(hash, 29L) == 0L
                && edits < remainingEdits) {
            edits += placeFusedCore(level, base.above(), hash,
                    remainingEdits - edits, maxGrowthY);
        } else if (Math.floorMod(hash >>> 6, 5L) == 0L && edits < remainingEdits) {
            int motif = (int) Math.floorMod(hash >>> 13, 4L);
            if (motif == 0) {
                edits += placeRootFan(level, base, hash, band,
                        remainingEdits - edits, maxGrowthY);
            } else if (motif == 1 && atMatureHeight) {
                edits += placeBranchingCrown(level, crown, hash, band,
                        remainingEdits - edits, maxGrowthY);
            } else if (motif == 2 && band == BloomBand.CORE && atMatureHeight) {
                edits += placeHollowKnot(level, crown.below(1), hash,
                        remainingEdits - edits, maxGrowthY);
            } else if (motif == 3 && atMatureHeight) {
                edits += placeCrossLink(level, crown, hash, band,
                        remainingEdits - edits, maxGrowthY);
            }
        }
        return edits;
    }

    private static BlockPos findBloomTopBelowSurfaceCover(ServerLevel level,
                                                           BlockPos surfaceTop) {
        BlockPos probe = surfaceTop;
        for (int depth = 0; depth < 4; depth++) {
            BlockState state = level.getBlockState(probe);
            if (isBloomBlock(state)) {
                return probe;
            }
            if (!state.is(Blocks.SNOW)
                    && !state.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
                return null;
            }
            probe = probe.below();
        }
        return isBloomBlock(level.getBlockState(probe)) ? probe : null;
    }

    private static int placeMalformedDoorframe(ServerLevel level, BlockPos origin,
                                               long hash, int remainingEdits,
                                               int maxGrowthY) {
        Direction axis = (hash & 1L) == 0L ? Direction.EAST : Direction.SOUTH;
        int height = 4 + (int) Math.floorMod(hash >>> 8, 5L);
        int edits = 0;
        for (int y = 0; y < height && edits < remainingEdits; y++) {
            edits += placeMassIfClear(level, origin.relative(axis).above(y),
                    BloomBand.MID, maxGrowthY) ? 1 : 0;
            if (edits >= remainingEdits) {
                break;
            }
            edits += placeMassIfClear(level, origin.relative(axis.getOpposite()).above(y),
                    BloomBand.MID, maxGrowthY) ? 1 : 0;
        }
        BlockPos crown = origin.above(height - 1);
        for (int offset = -1; offset <= 1 && edits < remainingEdits; offset++) {
            edits += placeMassIfClear(level, crown.relative(axis, offset),
                    BloomBand.MID, maxGrowthY) ? 1 : 0;
        }
        return edits;
    }

    private static int placeFusedCore(ServerLevel level, BlockPos origin,
                                      long hash, int remainingEdits, int maxGrowthY) {
        int edits = 0;
        int height = 5 + (int) Math.floorMod(hash >>> 10, 8L);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (((hash >>> direction.get2DDataValue()) & 1L) == 0L) {
                continue;
            }
            int sideHeight = Math.max(2, height - Math.floorMod(
                    direction.get2DDataValue() * 3 + (int) hash, 4));
            for (int y = 0; y < sideHeight && edits < remainingEdits; y++) {
                edits += placeMassIfClear(level, origin.relative(direction).above(y),
                        BloomBand.CORE, maxGrowthY) ? 1 : 0;
            }
        }
        if (edits < remainingEdits && Math.floorMod(hash >>> 17, 4L) == 0L
                && placeCore(level, origin.above(Math.min(4, height - 1)), maxGrowthY)) {
            edits++;
        }
        return edits;
    }

    private static int placeRootFan(ServerLevel level, BlockPos origin, long hash,
                                    BloomBand band, int remainingEdits, int maxGrowthY) {
        int edits = 0;
        Direction first = Direction.from2DDataValue((int) Math.floorMod(hash, 4L));
        int arms = band == BloomBand.CORE ? 2 : 1;
        for (int arm = 0; arm < arms && edits < remainingEdits; arm++) {
            Direction direction = arm == 0 ? first : first.getClockWise();
            int length = 3 + (int) Math.floorMod(hash >>> (8 + arm * 4), 4L);
            for (int step = 1; step <= length && edits < remainingEdits; step++) {
                BlockPos sample = origin.relative(direction, step);
                BlockPos surface = level.getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample);
                if (band == BloomBand.CORE && step % 3 == 0) {
                    edits += placeMassIfClear(level, surface, BloomBand.CORE,
                            maxGrowthY) ? 1 : 0;
                } else {
                    edits += placeCrustIfClear(level, surface,
                            2 + (int) Math.floorMod(hash + step, 3L), maxGrowthY) ? 1 : 0;
                }
            }
        }
        return edits;
    }

    private static int placeBranchingCrown(ServerLevel level, BlockPos origin, long hash,
                                           BloomBand band, int remainingEdits,
                                           int maxGrowthY) {
        int edits = 0;
        Direction first = Direction.from2DDataValue((int) Math.floorMod(hash >>> 3, 4L));
        int arms = band == BloomBand.CORE ? 3 : 2;
        for (int arm = 0; arm < arms && edits < remainingEdits; arm++) {
            Direction direction = arm == 0 ? first
                    : arm == 1 ? first.getOpposite() : first.getClockWise();
            int length = 2 + (int) Math.floorMod(hash >>> (11 + arm * 3), 3L);
            for (int step = 1; step <= length && edits < remainingEdits; step++) {
                int rise = step >= 3 ? 1 : 0;
                edits += placeMassIfClear(level,
                        origin.relative(direction, step).above(rise), band,
                        maxGrowthY) ? 1 : 0;
            }
            if (edits < remainingEdits) {
                BlockPos tip = origin.relative(direction, length).above(length >= 3 ? 2 : 1);
                edits += placeTipIfClear(level, tip, maxGrowthY) ? 1 : 0;
            }
        }
        return edits;
    }

    private static int placeHollowKnot(ServerLevel level, BlockPos center, long hash,
                                       int remainingEdits, int maxGrowthY) {
        int edits = 0;
        for (int dy = -1; dy <= 1 && edits < remainingEdits; dy++) {
            for (int dz = -1; dz <= 1 && edits < remainingEdits; dz++) {
                for (int dx = -1; dx <= 1 && edits < remainingEdits; dx++) {
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (distance < 2 || Math.floorMod(
                            hash + dx * 17L + dy * 31L + dz * 47L, 5L) == 0L) {
                        continue;
                    }
                    edits += placeMassIfClear(level, center.offset(dx, dy, dz),
                            BloomBand.CORE, maxGrowthY) ? 1 : 0;
                }
            }
        }
        if (edits < remainingEdits && placeCore(level, center, maxGrowthY)) {
            edits++;
        }
        return edits;
    }

    private static int placeCrossLink(ServerLevel level, BlockPos origin, long hash,
                                      BloomBand band, int remainingEdits, int maxGrowthY) {
        Direction direction = Direction.from2DDataValue((int) Math.floorMod(hash >>> 4, 4L));
        BlockPos target = null;
        int distance = 0;
        int targetDy = 0;
        for (int step = 4; step <= 8 && target == null; step++) {
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos candidate = origin.relative(direction, step).above(dy);
                if (level.isLoaded(candidate) && isBloomBlock(level.getBlockState(candidate))) {
                    target = candidate;
                    distance = step;
                    targetDy = dy;
                    break;
                }
            }
        }
        if (target == null) {
            return 0;
        }
        int edits = 0;
        for (int step = 1; step < distance && edits < remainingEdits; step++) {
            int rise = Mth.floor(Mth.lerp(step / (double) distance, 0.0D, targetDy));
            edits += placeMassIfClear(level,
                    origin.relative(direction, step).above(rise), band,
                    maxGrowthY) ? 1 : 0;
        }
        return edits;
    }

    private static boolean placeCrustIfClear(ServerLevel level, BlockPos pos, int layers,
                                             int maxGrowthY) {
        BlockState state = ModBlocks.BLOOM_CRUST.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS,
                        Mth.clamp(layers, 1, 8));
        if (pos.getY() > maxGrowthY || isProtected(level, pos)
                || !canPlaceAt(level, pos, state)) {
            return false;
        }
        level.setBlock(pos, state, 2);
        return true;
    }

    private static boolean placeTipIfClear(ServerLevel level, BlockPos pos, int maxGrowthY) {
        BlockState state = ModBlocks.BLOOM_TIP.get().defaultBlockState();
        if (pos.getY() > maxGrowthY || isProtected(level, pos)
                || !canPlaceAt(level, pos, state)) {
            return false;
        }
        level.setBlock(pos, state, 2);
        return true;
    }

    private static boolean placeCore(ServerLevel level, BlockPos pos, int maxGrowthY) {
        BlockState current = level.getBlockState(pos);
        BlockState core = ModBlocks.BLOOM_CORE.get().defaultBlockState();
        if (pos.getY() > maxGrowthY || isProtected(level, pos)
                || (!current.is(ModBlocks.BLOOM_MASS.get()) && !canPlaceAt(level, pos, core))) {
            return false;
        }
        level.setBlock(pos, core, 2);
        return true;
    }

    private static boolean placeMassIfClear(ServerLevel level, BlockPos pos,
                                            BloomBand band, int maxGrowthY) {
        BlockState state = ModBlocks.BLOOM_MASS.get().defaultBlockState()
                .setValue(BloomMassBlock.BAND, band);
        if (pos.getY() > maxGrowthY || isProtected(level, pos)
                || !canPlaceAt(level, pos, state)) {
            return false;
        }
        level.setBlock(pos, state, 2);
        return true;
    }

    private static boolean convert(ServerLevel level, BlockPos pos, BloomBand band) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.SEALED_LATTICE.get())
                || state.is(ModBlocks.BLOOM_CORE.get()) || state.is(Blocks.BEDROCK)) {
            return false;
        }
        if (state.is(ModBlocks.BLOOM_MASS.get())) {
            BloomBand current = state.getValue(BloomMassBlock.BAND);
            if (current.ordinal() >= band.ordinal()) {
                return false;
            }
            level.setBlock(pos, state.setValue(BloomMassBlock.BAND, band), 2);
            return true;
        }
        if (state.is(ModBlocks.BLOOM_CRUST.get()) || state.is(ModBlocks.BLOOM_TIP.get())) {
            level.setBlock(pos, ModBlocks.BLOOM_MASS.get().defaultBlockState()
                    .setValue(BloomMassBlock.BAND, band), 2);
            return true;
        }
        if (state.is(ModBlocks.ACHERONITE_BLOCK.get())
                || state.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
            level.setBlock(pos, ModBlocks.INERT_ACHERONITE.get().defaultBlockState(), 2);
            PlayerPlacedBlockTracker.get(level.getServer()).markRemoved(pos);
            return true;
        }
        boolean natural = state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SNOW)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.SNOW_BLOCK);
        PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(level.getServer());
        if (!natural && !tracker.isPlayerPlaced(pos)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            Containers.dropContents(level, pos, container);
            container.clearContent();
        }
        level.setBlock(pos, ModBlocks.BLOOM_MASS.get().defaultBlockState()
                .setValue(BloomMassBlock.BAND, band), 2);
        tracker.markRemoved(pos);
        return true;
    }

    private static boolean isProtected(ServerLevel level, BlockPos pos) {
        if (StillpointPolicy.isSuppressed(level, pos)) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        if (ChunkCatchUpManager.isBloomOrsaProtected(level, pos)
                || state.is(ModBlocks.LAUNCH_PAD.get())
                || state.is(ModBlocks.TRANSPONDER.get())
                || below.is(ModBlocks.LAUNCH_PAD.get())
                || below.is(ModBlocks.TRANSPONDER.get())
                || state.is(ModBlocks.SEALED_LATTICE.get())) {
            return true;
        }
        AABB check = new AABB(pos).inflate(3.0D);
        return !level.getEntitiesOfClass(RocketLaunchEntity.class, check,
                Entity::isAlive).isEmpty();
    }

    private static boolean canPlaceAt(ServerLevel level, BlockPos pos, BlockState state) {
        if (isOriginCrownClearance(level, pos)) {
            return false;
        }
        return canPlaceAtIgnoringOriginCrown(level, pos, state);
    }

    private static boolean canPlaceAtIgnoringOriginCrown(
            ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos),
                LivingEntity::isAlive).isEmpty()) {
            return false;
        }
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() || level.getEntities(null, shape.bounds().move(pos)).isEmpty();
    }

    private static boolean isOriginCrownClearance(ServerLevel level, BlockPos pos) {
        ReturnedHearthSavedData.HearthRecord major = ReturnedHearthSavedData
                .get(level.getServer()).hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .orElse(null);
        if (major == null) {
            return false;
        }
        BlockPos base = BloomSavedData.get(level.getServer()).hearth(major.id())
                .originRootBase().orElse(null);
        return base != null && BloomOriginRootPolicy.reservesCrownClearance(
                level.getSeed() ^ major.layoutSeed(), pos.subtract(base));
    }

    private static boolean isExposed(ServerLevel level, BlockPos block) {
        ArrayDeque<AirStep> open = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos air = block.relative(direction);
            if (level.isLoaded(air) && level.getBlockState(air).isAir()
                    && visited.add(air.asLong())) {
                open.addLast(new AirStep(air, 0));
            }
        }
        int examined = 0;
        while (!open.isEmpty() && examined++ < 192) {
            AirStep step = open.removeFirst();
            if (level.canSeeSky(step.pos)) {
                return true;
            }
            if (step.depth >= 12) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = step.pos.relative(direction);
                if (level.isLoaded(next) && visited.add(next.asLong())
                        && level.getBlockState(next).isAir()) {
                    open.addLast(new AirStep(next, step.depth + 1));
                }
            }
        }
        return false;
    }

    private record AirStep(BlockPos pos, int depth) {
    }

    private static boolean hasBloomNeighbor(ServerLevel level, BlockPos pos, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > radius) {
                        continue;
                    }
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(ModBlocks.BLOOM_MASS.get())
                            || state.is(ModBlocks.BLOOM_CRUST.get())
                            || state.is(ModBlocks.BLOOM_TIP.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isBloomBlock(BlockState state) {
        return state.is(ModBlocks.BLOOM_MASS.get())
                || state.is(ModBlocks.BLOOM_CRUST.get())
                || state.is(ModBlocks.BLOOM_TIP.get())
                || state.is(ModBlocks.BLOOM_CORE.get());
    }

    public static boolean isBloomState(BlockState state) {
        return isBloomBlock(state);
    }

    private static BloomBand bandForState(BlockState state) {
        if (state.is(ModBlocks.BLOOM_MASS.get())) {
            return state.getValue(BloomMassBlock.BAND);
        }
        if (state.is(ModBlocks.BLOOM_CORE.get())) {
            return BloomBand.CORE;
        }
        if (state.is(ModBlocks.BLOOM_CRUST.get()) || state.is(ModBlocks.BLOOM_TIP.get())) {
            return BloomBand.FRONTIER;
        }
        return null;
    }

    public static boolean hasBandNear(ServerLevel level, BlockPos center,
                                      BloomBand minimum, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int vertical = Math.min(16, radius);
        for (int y = -vertical; y <= vertical; y += 2) {
            for (int z = -radius; z <= radius; z += 2) {
                for (int x = -radius; x <= radius; x += 2) {
                    if (x * x + z * z > radius * radius) {
                        continue;
                    }
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BloomBand band = bandForState(level.getBlockState(cursor));
                    if (band != null && band.ordinal() >= minimum.ordinal()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void reactivateAround(ServerLevel level, BlockPos pos) {
        BloomSavedData.get(level.getServer()).reactivateChunksNear(new ChunkPos(pos));
    }

    private static boolean sealedOnLine(ServerLevel level, BlockPos from, BlockPos to) {
        int steps = Math.max(Math.abs(to.getX() - from.getX()),
                Math.abs(to.getZ() - from.getZ()));
        if (steps <= 1) {
            return false;
        }
        for (int i = 1; i < steps; i += 2) {
            int x = Mth.floor(Mth.lerp(i / (double) steps, from.getX(), to.getX()));
            int z = Mth.floor(Mth.lerp(i / (double) steps, from.getZ(), to.getZ()));
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            for (int y = -2; y <= 3; y++) {
                if (level.getBlockState(surface.offset(0, y, 0))
                        .is(ModBlocks.SEALED_LATTICE.get())
                        && isContinuousSealedBarrier(level, surface.offset(0, y, 0))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isContinuousSealedBarrier(ServerLevel level, BlockPos origin) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        open.add(origin);
        visited.add(origin.asLong());
        int minX = origin.getX();
        int maxX = origin.getX();
        int minZ = origin.getZ();
        int maxZ = origin.getZ();
        while (!open.isEmpty() && visited.size() < 128) {
            BlockPos current = open.removeFirst();
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getY() - origin.getY()) > 2
                        || Math.abs(next.getX() - origin.getX()) > 16
                        || Math.abs(next.getZ() - origin.getZ()) > 16
                        || !level.isLoaded(next)
                        || !level.getBlockState(next).is(ModBlocks.SEALED_LATTICE.get())
                        || !visited.add(next.asLong())) {
                    continue;
                }
                open.addLast(next);
            }
        }
        return visited.size() >= 8 && (maxX - minX >= 5 || maxZ - minZ >= 5);
    }

    private static int overlapCount(ReturnedHearthSavedData hearths,
                                    BloomSavedData bloom, BlockPos pos, long duration) {
        int count = 0;
        for (ReturnedHearthSavedData.HearthRecord hearth : hearths.hearths()) {
            int radius = effectiveRadius(bloom, hearth.id(), duration);
            if (horizontalDistance(pos, hearth.center()) <= radius) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static int effectiveRadius(BloomSavedData bloom, java.util.UUID hearthId,
                                       long duration) {
        int debug = bloom.debugRadius();
        if (debug >= 0) {
            return debug;
        }
        return Mth.floor(BloomGrowthPolicy.radius(
                bloom.hearth(hearthId).activeTicks(), duration));
    }

    static int effectiveRadiusFor(BloomSavedData bloom, java.util.UUID hearthId,
                                  long duration) {
        return effectiveRadius(bloom, hearthId, duration);
    }

    static BlockPos findFrontierSporeSpawn(ServerLevel level, BlockPos center,
                                           double edgeRadius, long seed) {
        int radius = Math.max(BloomGrowthPolicy.INITIAL_RADIUS,
                Mth.floor(edgeRadius));
        for (int attempt = 0; attempt < 32; attempt++) {
            long mixed = BloomGrowthPolicy.mix(seed + attempt * 0x9E3779B97F4A7C15L);
            double angle = ((mixed >>> 11) & 0xFFFFFFL)
                    / (double) 0xFFFFFFL * Math.PI * 2.0D;
            double inward = Math.floorMod(mixed >>> 35, 13L);
            double distance = Math.max(4.0D, radius - inward);
            int x = Mth.floor(center.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(center.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos stand = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (isProtected(level, stand) || sealedOnLine(level, center, stand)
                    || !level.getBlockState(stand).isAir()
                    || !level.getBlockState(stand.above()).isAir()
                    || !level.getBlockState(stand.below()).isSolidRender(level, stand.below())
                    || !hasExactBandNear(level, stand, BloomBand.FRONTIER, 6)) {
                continue;
            }
            return stand;
        }
        return null;
    }

    private static boolean hasExactBandNear(ServerLevel level, BlockPos center,
                                            BloomBand band, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -4; y <= 5; y++) {
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    if (x * x + z * z > radius * radius) {
                        continue;
                    }
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (level.isLoaded(cursor)
                            && bandForState(level.getBlockState(cursor)) == band) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean placeSporeTrailTip(ServerLevel level, BlockPos near) {
        if (!level.hasChunkAt(near)) {
            return false;
        }
        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
        if (isProtected(level, surface) || hasSealedNearby(level, surface, 3)
                || !level.getBlockState(surface.below()).isSolidRender(level, surface.below())
                || !isExposed(level, surface.below())) {
            return false;
        }
        return placeTipIfClear(level, surface, surface.getY() + 1);
    }

    static int placeSatelliteColumn(ServerLevel level, BlockPos anchor, long layoutSeed,
                                    int cursor, int remainingEdits) {
        if (remainingEdits <= 0) {
            return 0;
        }
        int permuted = BloomSporePolicy.permutedColumn(cursor, layoutSeed);
        int dx = BloomSporePolicy.columnX(permuted);
        int dz = BloomSporePolicy.columnZ(permuted);
        if (!BloomSporePolicy.acceptsColumn(layoutSeed, dx, dz)) {
            return 0;
        }
        BlockPos sample = anchor.offset(dx, 0, dz);
        if (!level.hasChunkAt(sample)) {
            return 0;
        }
        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample);
        int bloomHeight = 0;
        BlockPos probe = surface.below();
        while (bloomHeight <= BloomSporePolicy.MAX_SATELLITE_HEIGHT
                && isBloomBlock(level.getBlockState(probe))) {
            bloomHeight++;
            probe = probe.below();
        }
        if (bloomHeight > BloomSporePolicy.MAX_SATELLITE_HEIGHT) {
            return 0;
        }
        BlockPos base = bloomHeight > 0 ? probe.above() : probe;
        if (isProtected(level, base) || sealedOnLine(level, anchor, base)
                || !isExposed(level, base)) {
            return 0;
        }
        int edits = 0;
        if (bloomHeight == 0 && convert(level, base, BloomBand.FRONTIER)) {
            edits++;
            bloomHeight = 1;
        }
        int targetHeight = BloomSporePolicy.columnHeight(layoutSeed, dx, dz);
        while (bloomHeight < targetHeight && edits < remainingEdits) {
            BlockPos place = base.above(bloomHeight);
            if (!placeMassIfClear(level, place, BloomBand.FRONTIER,
                    base.getY() + BloomSporePolicy.MAX_SATELLITE_HEIGHT)) {
                break;
            }
            edits++;
            bloomHeight++;
        }
        if (edits == 0 && bloomHeight == 0 && placeSporeTrailTip(level, surface)) {
            return 1;
        }
        return edits;
    }

    private static boolean hasSealedNearby(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius),
                center.offset(radius, 3, radius))) {
            if (level.getBlockState(pos).is(ModBlocks.SEALED_LATTICE.get())) {
                return true;
            }
        }
        return false;
    }

    private static boolean loadedAreaIntersects(ServerLevel level, BlockPos center) {
        double extra = level.getServer().getPlayerList().getViewDistance() * 16.0D;
        double range = BloomGrowthPolicy.MAX_RADIUS + extra;
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

    private static boolean chunkIntersectsRadius(ChunkPos chunk, BlockPos center, int radius) {
        int nearestX = Mth.clamp(center.getX(), chunk.getMinBlockX(), chunk.getMaxBlockX());
        int nearestZ = Mth.clamp(center.getZ(), chunk.getMinBlockZ(), chunk.getMaxBlockZ());
        long dx = (long) nearestX - center.getX();
        long dz = (long) nearestZ - center.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static void tickSealedLattice(ServerLevel level, BlockPos pos, BlockState state) {
        BloomBand strongest = null;
        for (Direction direction : Direction.values()) {
            BlockState adjacent = level.getBlockState(pos.relative(direction));
            if (adjacent.is(ModBlocks.BLOOM_MASS.get())) {
                BloomBand band = adjacent.getValue(BloomMassBlock.BAND);
                if (strongest == null || band.ordinal() > strongest.ordinal()) {
                    strongest = band;
                }
            } else if (adjacent.is(ModBlocks.BLOOM_CRUST.get())
                    || adjacent.is(ModBlocks.BLOOM_TIP.get())) {
                strongest = strongest == null ? BloomBand.FRONTIER : strongest;
            } else if (adjacent.is(ModBlocks.BLOOM_CORE.get())) {
                strongest = BloomBand.CORE;
            }
        }
        BloomSavedData data = BloomSavedData.get(level.getServer());
        if (strongest == null) {
            return;
        }
        long ticks = data.addSealedContact(pos, 20L);
        int wear = BloomGrowthPolicy.sealedWearStage(ticks, strongest);
        if (wear >= 4) {
            level.setBlock(pos, ModBlocks.BLOOM_MASS.get().defaultBlockState()
                    .setValue(BloomMassBlock.BAND, strongest), 2);
            data.removeSealedContact(pos);
        } else if (state.getValue(SealedLatticeBlock.WEAR) != wear) {
            level.setBlock(pos, state.setValue(SealedLatticeBlock.WEAR, wear), 2);
        }
    }

    public static String statusLine(ServerLevel level, ApocalypseState apocalypse) {
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        ReturnedHearthSavedData hearths = ReturnedHearthSavedData.get(level.getServer());
        long duration = BloomGrowthPolicy.presetDurationTicks(apocalypse.getPresetName());
        StringBuilder radii = new StringBuilder();
        String originRoot = "none";
        for (ReturnedHearthSavedData.HearthRecord hearth : hearths.hearths()) {
            if (!radii.isEmpty()) {
                radii.append(',');
            }
            radii.append(hearth.type().name().toLowerCase())
                    .append('=')
                    .append(effectiveRadius(bloom, hearth.id(), duration))
                    .append("@")
                    .append(String.format(java.util.Locale.ROOT, "%.2fd",
                            bloom.hearth(hearth.id()).activeTicks()
                                    / (double) BloomGrowthPolicy.DAY_TICKS));
            if (hearth.type() == HearthSelectionPolicy.HearthType.MAJOR) {
                BloomSavedData.HearthGrowth growth = bloom.hearth(hearth.id());
                originRoot = growth.originRootFormed() ? "formed"
                        : growth.originRootCursor() + "/"
                        + BloomOriginRootPolicy.layout(
                        level.getSeed() ^ hearth.layoutSeed()).size();
            }
        }
        return "radii=" + (radii.isEmpty() ? "none" : radii)
                + " releaseIn=" + String.format(java.util.Locale.ROOT, "%.1fs",
                PostMaeveWorldState.bloomReleaseRemainingTicks(level.getServer()) / 20.0D)
                + " eruption=" + firstEruptionStatus(bloom, level.getGameTime())
                + " originRoot=" + originRoot
                + " records=" + bloom.recordCount()
                + " pending=" + bloom.pendingCount()
                + " sealed=" + bloom.sealedRecordCount()
                + " edits=" + lastEdits
                + " columns=" + lastColumns
                + " lastMs=" + String.format(java.util.Locale.ROOT, "%.3f",
                lastTickNanos / 1_000_000.0D)
                + " maxMs=" + String.format(java.util.Locale.ROOT, "%.3f",
                maxTickNanos / 1_000_000.0D)
                + " deferred=" + deferred;
    }

    private static String firstEruptionStatus(BloomSavedData bloom, long gameTime) {
        if (bloom.firstEruptionComplete()) {
            return "complete";
        }
        if (!bloom.firstEruptionStarted()) {
            return bloom.debugRadius() == 0 ? "paused" : "waiting";
        }
        long elapsed = Math.max(0L, gameTime - bloom.firstEruptionStartGameTime());
        return BloomEruptionPolicy.stage(elapsed).name().toLowerCase()
                + "@" + String.format(java.util.Locale.ROOT, "%.1fs", elapsed / 20.0D);
    }

    public static void resetProfile() {
        lastTickNanos = 0L;
        maxTickNanos = 0L;
        lastEdits = 0;
        lastColumns = 0;
        workRotation = 0L;
        discoveryRotation = 0L;
    }

    public static int debugSeed(ServerLevel level) {
        ReturnedHearthSavedData hearths = ReturnedHearthSavedData.get(level.getServer());
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        int edits = 0;
        for (ReturnedHearthSavedData.HearthRecord hearth : hearths.hearths()) {
            BlockPos seedAnchor = hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                    ? originRootAnchor(hearth) : hearth.center();
            if (!level.isLoaded(seedAnchor)) {
                continue;
            }
            BloomSavedData.HearthGrowth growth = bloom.hearth(hearth.id());
            int seedEdits = formLivingSeed(level, hearth, MAX_EDITS_PER_TICK - edits);
            edits += seedEdits;
            if (seedEdits > 0) {
                growth.markSeeded();
            }
            if (edits >= MAX_EDITS_PER_TICK) {
                break;
            }
            if (hearth.type() == HearthSelectionPolicy.HearthType.MAJOR) {
                if (growth.originRootFormed()) {
                    growth.resetOriginRootForDebug();
                }
                growth.rememberOriginRootBase(resolveOriginRootBase(level, hearth));
                edits += formOriginRoot(level, hearth, growth,
                        MAX_EDITS_PER_TICK - edits);
            }
            if (edits >= MAX_EDITS_PER_TICK) {
                break;
            }
        }
        if (edits > 0) {
            bloom.completeFirstEruption();
        }
        return edits;
    }

    public static void debugAdvance(ServerLevel level, long ticks) {
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        if (bloom.debugRadius() == 0) {
            bloom.restartGrowthAuthorityForDebug();
        } else {
            bloom.setDebugRadius(-1);
        }
        if (!bloom.firstEruptionComplete()) {
            debugSeed(level);
        }
        for (ReturnedHearthSavedData.HearthRecord hearth
                : ReturnedHearthSavedData.get(level.getServer()).hearths()) {
            bloom.hearth(hearth.id()).advance(ticks);
        }
        for (BloomSavedData.ChunkGrowth chunk : bloom.chunks()) {
            chunk.resetPass();
        }
    }

    public static int debugStartNormalProgression(ServerLevel level) {
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        bloom.restartGrowthAuthorityForDebug();
        resetProfile();
        ReturnedHearthSavedData hearths = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = hearths
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return 0;
        }
        hearths.replayHeartMaeveBiologicalWarningForDebug(
                hearth.id(), level.getGameTime());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player,
                    HearthBoundaryEffectPayload.worldEventBiologicalWarning());
        }
        return 0;
    }

    public static void debugResetEruptionForMaeveReplay(ServerLevel level) {
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        bloom.resetFirstEruption();
        bloom.setDebugRadius(-1);
        resetProfile();
    }

    public static void resumePurgedGrowthForMaeveSequence(ServerLevel level) {
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        if (bloom.resumePurgedGrowthForMaeveSequence()) {
            resetProfile();
        }
    }

    public static void debugSetRadius(ServerLevel level, int radius) {
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        bloom.setDebugRadius(radius);
        for (BloomSavedData.ChunkGrowth chunk : bloom.chunks()) {
            chunk.resetPass();
        }
    }

    public static int debugPurgeLoaded(ServerLevel level) {
        BloomSavedData bloom = BloomSavedData.get(level.getServer());
        bloom.setDebugRadius(0);
        List<LevelChunk> loadedChunks = loadedChunks(level);

        Set<Long> bloomPositions = new HashSet<>();
        Set<Long> atmospherePositions = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (LevelChunk chunk : loadedChunks) {
            LevelChunkSection[] sections = chunk.getSections();
            ChunkPos chunkPos = chunk.getPos();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (!section.maybeHas(state -> isDebugPurgeBlock(state)
                        || state.is(ModBlocks.FROZEN_ATMOSPHERE.get()))) {
                    continue;
                }
                int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int localX = 0; localX < 16; localX++) {
                            BlockState state = section.getBlockState(localX, localY, localZ);
                            if (!isDebugPurgeBlock(state)
                                    && !state.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
                                continue;
                            }
                            cursor.set(chunkPos.getMinBlockX() + localX,
                                    sectionMinY + localY,
                                    chunkPos.getMinBlockZ() + localZ);
                            if (isDebugPurgeBlock(state)) {
                                bloomPositions.add(cursor.asLong());
                            } else {
                                atmospherePositions.add(cursor.asLong());
                            }
                        }
                    }
                }
            }
        }
        int removed = 0;
        for (long packed : atmospherePositions) {
            BlockPos atmosphere = BlockPos.of(packed);
            if (isAtmosphereOnBloom(atmosphere, bloomPositions, atmospherePositions)) {
                level.setBlock(atmosphere, Blocks.AIR.defaultBlockState(), 2);
                removed++;
            }
        }
        for (long packed : bloomPositions) {
            level.setBlock(BlockPos.of(packed), Blocks.AIR.defaultBlockState(), 2);
            removed++;
        }
        BloomSporeManager.debugPurgeLoaded(level);
        bloom.resetAuthority();
        bloom.setDebugRadius(0);
        for (ReturnedHearthSavedData.HearthRecord hearth
                : ReturnedHearthSavedData.get(level.getServer()).hearths()) {
            BloomSavedData.HearthGrowth growth = bloom.hearth(hearth.id());
            growth.markSeeded();
            growth.markOriginRootFormed();
        }
        resetProfile();
        return removed;
    }

    private static boolean isAtmosphereOnBloom(BlockPos atmosphere,
                                                Set<Long> bloomPositions,
                                                Set<Long> atmospherePositions) {
        BlockPos.MutableBlockPos below = atmosphere.mutable().move(Direction.DOWN);
        for (int depth = 0; depth < 4; depth++) {
            long packed = below.asLong();
            if (bloomPositions.contains(packed)) {
                return true;
            }
            if (!atmospherePositions.contains(packed)) {
                return false;
            }
            below.move(Direction.DOWN);
        }
        return false;
    }

    private static boolean isDebugPurgeBlock(BlockState state) {
        return state.is(ModBlocks.BLOOM_MASS.get())
                || state.is(ModBlocks.BLOOM_CRUST.get())
                || state.is(ModBlocks.BLOOM_TIP.get())
                || state.is(ModBlocks.BLOOM_CORE.get())
                || state.is(ModBlocks.INERT_ACHERONITE.get())
                || state.is(ModBlocks.SEALED_LATTICE.get());
    }
}
