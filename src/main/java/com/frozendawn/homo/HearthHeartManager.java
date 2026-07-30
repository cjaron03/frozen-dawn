package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.mixin.BlockDisplayAccessor;
import com.frozendawn.mixin.DisplayAccessor;
import com.frozendawn.network.HearthBoundaryEffectPayload;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Reconciles the one persistent post-storm Heart without force-loading chunks. */
public final class HearthHeartManager {
    private static final String FRAGMENT_TAG_PREFIX = "frozendawn_heart_fragment_";
    private static final String COLLAPSE_FRAGMENT_TAG_PREFIX =
            "frozendawn_heart_collapse_fragment_";
    private static final String NODE_DEBRIS_TAG_PREFIX =
            "frozendawn_heart_node_debris_";
    private static final Map<UUID, Map<Integer, UUID>> ACTIVE_FRAGMENTS = new HashMap<>();
    private static final Map<UUID, Map<Integer, UUID>> ACTIVE_COLLAPSE_FRAGMENTS =
            new HashMap<>();
    private static final Map<UUID, Map<Integer, UUID>> ACTIVE_NODE_DEBRIS =
            new HashMap<>();
    private static long heartsSpawned;
    private static long heartsAdopted;
    private static long formationsCompleted;
    private static String lastFailure = "none";

    private HearthHeartManager() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return;
        }
        boolean debugFormationActive = hearth.heartFormationStartGameTime() >= 0L
                || hearth.heartLive();
        if (!hearth.masterArchitectDefeated() && !debugFormationActive) {
            return;
        }

        if (hearth.hearthStormDead() && hearth.watchedStopWatchingGranted()
                && hearth.heartFormationStartGameTime() < 0L
                && !hearth.heartFormationSuppressed()) {
            data.startHeartFormation(hearth.id(), level.getGameTime());
            hearth = data.hearth(hearth.id()).orElse(hearth);
        }

        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        HeartFormationPolicy.Snapshot snapshot = snapshot(level, hearth);
        HeartCollapsePolicy.Snapshot collapse = collapseSnapshot(level, hearth);
        if (snapshot.stage() != HeartFormationStage.NONE
                && snapshot.elapsedTicks() >= HeartFormationPolicy.DEAD_AIR_TICKS
                && !hearth.heartAdvancementFired()) {
            grantFormationAdvancement(level, anchor);
            data.markHeartAdvancementFired(hearth.id());
        }
        if (snapshot.stage() == HeartFormationStage.LIVE && !hearth.heartLive()
                && collapse.stage() == HeartCollapseStage.NONE) {
            data.markHeartLive(hearth.id());
            formationsCompleted++;
        }

        boolean collapseJustCompleted = collapse.stage() == HeartCollapseStage.DORMANT
                && !hearth.heartCollapseComplete()
                && data.completeHeartCollapse(hearth.id());

        hearth = data.hearth(hearth.id()).orElse(hearth);
        long maeveElapsed = hearth.heartMaeveErasureStartGameTime() < 0L
                ? 0L : Math.max(0L,
                level.getGameTime() - hearth.heartMaeveErasureStartGameTime());
        float maeveErasureProgress = maeveErasureProgress(level, hearth);
        boolean forgeJustAnnounced = hearth.heartMaeveErasureStartGameTime() >= 0L
                && !hearth.heartMaeveForgeAnnounced()
                && maeveElapsed >= HeartMaeveErasurePolicy.UNMAKING_TICKS
                && data.markHeartMaeveForgeAnnounced(hearth.id());
        boolean erasureJustCompleted = hearth.heartMaeveErasureStartGameTime() >= 0L
                && !hearth.heartMaeveErasureComplete()
                && HeartMaeveErasurePolicy.complete(Math.max(0L,
                level.getGameTime() - hearth.heartMaeveErasureStartGameTime()))
                && data.completeHeartMaeveErasure(hearth.id());
        hearth = data.hearth(hearth.id()).orElse(hearth);

        if (hearth.heartMaeveErasureComplete()) {
            if (erasureJustCompleted) {
                CognitiveLoadManager.clearForHeartErasure(level);
                Vec3 maeve = HeartLattice.maevePosition(anchor);
                level.playSound(null, BlockPos.containing(maeve),
                        ModSounds.THAE_IVEN_HEART_MAEVE_ERASURE.get(),
                        SoundSource.AMBIENT, 4.5F, 0.82F);
                shatterHeart(level, hearth, anchor);
                releaseLastWitness(level, data, hearth, maeve);
            }
            if (level.hasChunkAt(anchor)) {
                removeHeartEntities(level, hearth.id(), anchor);
                HeartScavengerWaveManager.endEncounter(
                        level, hearth.id(), anchor);
            }
            grantFinalAdvancement(level, data, hearth);
            return;
        }
        if (hearth.heartLive() && !hearth.heartConvergenceStarted()
                && level.hasChunkAt(anchor)) {
            HearthCombatRosterManager.beginAftermathConvergence(
                    level, hearth.id(), anchor);
            data.markHeartConvergenceStarted(hearth.id());
        }

        if (!level.hasChunkAt(anchor)) {
            return;
        }
        if (collapseJustCompleted) {
            level.playSound(null, anchor, ModSounds.THAE_IVEN_HEART_COLLAPSE.get(),
                    SoundSource.AMBIENT, 2.0F, 0.58F);
            Vec3 maeve = HeartLattice.maevePosition(anchor);
            level.sendParticles(ParticleTypes.FLASH,
                    maeve.x, maeve.y, maeve.z,
                    2, 0.3D, 0.3D, 0.3D, 0.0D);
            level.sendParticles(ParticleTypes.END_ROD,
                    maeve.x, maeve.y, maeve.z,
                    42, 1.2D, 1.2D, 1.2D, 0.08D);
            grieveForMaeve(level, anchor);
        }
        if (collapse.stage() == HeartCollapseStage.SETTLE
                && level.getGameTime() % 2L == 0L) {
            drawParticlesIntoMaeveFormation(
                    level, anchor,
                    HeartCollapsePolicy.maeveFormationProgress(
                            collapse.stage(), collapse.stageProgress()));
        }
        if (forgeJustAnnounced) {
            beginLastWitnessForge(level, anchor);
        }
        if (maeveErasureProgress > 0.0F
                && level.getGameTime() % 2L == 0L) {
            Vec3 maeve = HeartLattice.maevePosition(anchor);
            level.sendParticles(
                    level.getGameTime() % 6L == 0L
                            ? ParticleTypes.END_ROD : ParticleTypes.SCULK_SOUL,
                    maeve.x, maeve.y, maeve.z,
                    4 + Mth.floor(maeveErasureProgress * 8.0F),
                    2.4D, 2.1D, 2.4D,
                    0.04D + maeveErasureProgress * 0.08D);
        }
        if (HeartMaeveErasurePolicy.forging(maeveElapsed)) {
            drawParticlesIntoMaeve(level, anchor,
                    HeartMaeveErasurePolicy.forgingProgress(maeveElapsed));
        }
        ThaeIvenHeartEntity heart = reconcileHeartEntity(level, data, hearth, anchor);
        if (heart != null) {
            heart.configure(
                    hearth.id(),
                    layoutSeed(hearth, anchor),
                    anchor.asLong(),
                    hearth.heartFieldStrength(),
                    snapshot.stage(),
                    snapshot.stageProgress(),
                    hearth.heartDestroyedNodeMask(),
                    hearth.heartActiveNodeDamage(),
                    collapse.stage(),
                    collapse.stageProgress(),
                    hearth.heartMaeveExposed(),
                    maeveErasureProgress);
        }
        reconcileFragments(level, hearth, anchor, snapshot);
        reconcileNodeDebris(level, data, hearth, anchor);
        reconcileCollapseFragments(level, hearth, anchor, collapse);
        if (collapse.stage() == HeartCollapseStage.DORMANT
                && !hearth.heartCollapseDebrisLanded()) {
            placeCollapseDebris(level, hearth, anchor);
            data.markHeartCollapseDebrisLanded(hearth.id());
        }
    }

    public static void startForDebug(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return;
        }
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        data.prepareHeartFormation(hearth.id(), anchor, List.of());
        data.startHeartFormation(hearth.id(), level.getGameTime());
        tick(level);
    }

    public static int resetForDebug(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return 0;
        }
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        int removed = removeHeartEntities(level, hearth.id(), anchor);
        removed += clearFragments(level, hearth.id(), anchor);
        removed += clearCollapseFragments(level, hearth.id(), anchor);
        removed += clearNodeDebris(level, hearth.id(), anchor);
        ACTIVE_FRAGMENTS.remove(hearth.id());
        ACTIVE_COLLAPSE_FRAGMENTS.remove(hearth.id());
        ACTIVE_NODE_DEBRIS.remove(hearth.id());
        data.resetHeartForDebug(hearth.id());
        return removed;
    }

    public static boolean setStageForDebug(
            ServerLevel level, HeartFormationStage stage) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return false;
        }
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        data.prepareHeartFormation(hearth.id(), anchor, List.of());
        boolean changed = data.setHeartStageForDebug(
                hearth.id(), level.getGameTime(), stage);
        ACTIVE_FRAGMENTS.remove(hearth.id());
        clearFragments(level, hearth.id(), anchor);
        ACTIVE_COLLAPSE_FRAGMENTS.remove(hearth.id());
        clearCollapseFragments(level, hearth.id(), anchor);
        ACTIVE_NODE_DEBRIS.remove(hearth.id());
        clearNodeDebris(level, hearth.id(), anchor);
        tick(level);
        return changed;
    }

    public static boolean startCollapseForDebug(ServerLevel level) {
        return setCollapseStageForDebug(level, HeartCollapseStage.RUPTURE);
    }

    public static boolean setCollapseStageForDebug(
            ServerLevel level, HeartCollapseStage stage) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return false;
        }
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        boolean changed = data.setHeartCollapseStageForDebug(
                hearth.id(), level.getGameTime(), stage);
        ACTIVE_COLLAPSE_FRAGMENTS.remove(hearth.id());
        clearCollapseFragments(level, hearth.id(), anchor);
        tick(level);
        return changed;
    }

    public static int resetCollapseForDebug(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return 0;
        }
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        int removed = clearCollapseFragments(level, hearth.id(), anchor);
        ACTIVE_COLLAPSE_FRAGMENTS.remove(hearth.id());
        data.resetHeartCollapseForDebug(hearth.id());
        tick(level);
        return removed;
    }

    public static String describe(ServerLevel level) {
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(level.getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return "heart=none";
        }
        HeartFormationPolicy.Snapshot snapshot = snapshot(level, hearth);
        HeartFormationPolicy.Timeline timeline = snapshot.timeline();
        HeartCollapsePolicy.Snapshot collapse = collapseSnapshot(level, hearth);
        return "stage=" + snapshot.stage().name().toLowerCase()
                + " progress=" + String.format(java.util.Locale.ROOT, "%.3f",
                snapshot.stageProgress())
                + " elapsed=" + snapshot.elapsedTicks() + "/" + timeline.liveStart()
                + " field=" + String.format(java.util.Locale.ROOT, "%.3f",
                hearth.heartFieldStrength())
                + " fragments=" + timeline.fragmentCount()
                + " anchor=" + hearth.heartAnchor().map(BlockPos::toShortString).orElse("fallback")
                + " advancement=" + yesNo(hearth.heartAdvancementFired())
                + " live=" + yesNo(hearth.heartLive())
                + " nodes=" + HeartLattice.destroyedCount(
                hearth.heartDestroyedNodeMask()) + "/" + HeartLattice.NODE_COUNT
                + " activeDamage=" + hearth.heartActiveNodeDamage()
                + " collapse=" + collapse.stage().name().toLowerCase()
                + " collapseProgress=" + String.format(java.util.Locale.ROOT, "%.3f",
                collapse.stageProgress())
                + " maeve=" + yesNo(hearth.heartMaeveExposed())
                + " erasure=" + String.format(java.util.Locale.ROOT, "%.3f",
                maeveErasureProgress(level, hearth))
                + " erased=" + yesNo(hearth.heartMaeveErasureComplete())
                + " debris=0x" + Integer.toHexString(
                hearth.heartDebrisLandedMask())
                + " entity=" + hearth.heartEntityId()
                .map(id -> id.toString().substring(0, 8)).orElse("none");
    }

    public static String statusLine() {
        return "spawned=" + heartsSpawned + " adopted=" + heartsAdopted
                + " completed=" + formationsCompleted + " failure=" + lastFailure;
    }

    public static void reset() {
        ACTIVE_FRAGMENTS.clear();
        ACTIVE_COLLAPSE_FRAGMENTS.clear();
        ACTIVE_NODE_DEBRIS.clear();
        heartsSpawned = 0L;
        heartsAdopted = 0L;
        formationsCompleted = 0L;
        lastFailure = "none";
    }

    private static HeartFormationPolicy.Snapshot snapshot(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth.heartFormationStartGameTime() < 0L) {
            return new HeartFormationPolicy.Snapshot(
                    HeartFormationStage.NONE, 0.0F, 0L,
                    HeartFormationPolicy.timeline(hearth.heartFieldStrength()));
        }
        long elapsed = Math.max(0L,
                level.getGameTime() - hearth.heartFormationStartGameTime());
        return HeartFormationPolicy.snapshot(elapsed, hearth.heartFieldStrength());
    }

    private static HeartCollapsePolicy.Snapshot collapseSnapshot(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth.heartCollapseStartGameTime() < 0L) {
            return new HeartCollapsePolicy.Snapshot(
                    HeartCollapseStage.NONE, 0.0F, 0L,
                    HeartCollapsePolicy.timeline(hearth.heartFieldStrength()));
        }
        long elapsed = Math.max(0L,
                level.getGameTime() - hearth.heartCollapseStartGameTime());
        return HeartCollapsePolicy.snapshot(elapsed, hearth.heartFieldStrength());
    }

    private static float maeveErasureProgress(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth.heartMaeveErasureComplete()) {
            return 1.0F;
        }
        if (hearth.heartMaeveErasureStartGameTime() < 0L) {
            return 0.0F;
        }
        return HeartMaeveErasurePolicy.progress(Math.max(0L,
                level.getGameTime() - hearth.heartMaeveErasureStartGameTime()));
    }

    private static ThaeIvenHeartEntity reconcileHeartEntity(
            ServerLevel level,
            ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor) {
        ThaeIvenHeartEntity bound = hearth.heartEntityId()
                .map(level::getEntity)
                .filter(ThaeIvenHeartEntity.class::isInstance)
                .map(ThaeIvenHeartEntity.class::cast)
                .filter(Entity::isAlive)
                .orElse(null);
        AABB area = new AABB(anchor).inflate(56.0D, 72.0D, 56.0D);
        List<ThaeIvenHeartEntity> candidates = level.getEntitiesOfClass(
                ThaeIvenHeartEntity.class, area,
                entity -> entity.hearthId().map(hearth.id()::equals).orElse(false));
        if (bound == null && !candidates.isEmpty()) {
            bound = candidates.getFirst();
            data.bindHeartEntity(hearth.id(), bound.getUUID());
            heartsAdopted++;
        }
        for (ThaeIvenHeartEntity candidate : candidates) {
            if (candidate != bound) {
                candidate.discard();
            }
        }
        if (bound != null) {
            bound.setPos(anchor.getX() + 0.5D, anchor.getY() + 30.0D,
                    anchor.getZ() + 0.5D);
            return bound;
        }
        ThaeIvenHeartEntity created = ModEntities.THAE_IVEN_HEART.get().create(level);
        if (created == null) {
            lastFailure = "entity-create";
            return null;
        }
        created.setPos(anchor.getX() + 0.5D, anchor.getY() + 30.0D,
                anchor.getZ() + 0.5D);
        created.configure(hearth.id(), layoutSeed(hearth, anchor), anchor.asLong(),
                hearth.heartFieldStrength(), HeartFormationStage.NONE, 0.0F,
                hearth.heartDestroyedNodeMask(), hearth.heartActiveNodeDamage(),
                HeartCollapseStage.NONE, 0.0F, hearth.heartMaeveExposed(),
                maeveErasureProgress(level, hearth));
        if (!level.addFreshEntity(created)) {
            lastFailure = "entity-add";
            created.discard();
            return null;
        }
        data.bindHeartEntity(hearth.id(), created.getUUID());
        heartsSpawned++;
        lastFailure = "none";
        return created;
    }

    private static void reconcileFragments(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            HeartFormationPolicy.Snapshot snapshot) {
        if (snapshot.stage() != HeartFormationStage.GATHER) {
            clearFragments(level, hearth.id(), anchor);
            ACTIVE_FRAGMENTS.remove(hearth.id());
            return;
        }
        int count = snapshot.timeline().fragmentCount();
        Map<Integer, UUID> bindings = ACTIVE_FRAGMENTS.computeIfAbsent(
                hearth.id(), ignored -> new HashMap<>());
        for (int index = 0; index < count; index++) {
            float launch = index / (float) Math.max(1, count) * 0.72F;
            float progress = Mth.clamp(
                    (snapshot.stageProgress() - launch) / 0.28F, 0.0F, 1.0F);
            Entity existing = bindings.containsKey(index)
                    ? level.getEntity(bindings.get(index)) : null;
            if (!(existing instanceof Display.BlockDisplay)) {
                existing = adoptFragment(level, hearth.id(), anchor, index);
            }
            if (progress >= 1.0F) {
                if (existing != null) {
                    existing.discard();
                }
                bindings.remove(index);
                continue;
            }
            Display.BlockDisplay display = existing instanceof Display.BlockDisplay blockDisplay
                    ? blockDisplay : createFragment(level, hearth, anchor, index, count);
            if (display == null) {
                continue;
            }
            bindings.put(index, display.getUUID());
            Vec3 source = sourceFor(hearth, anchor, index, count);
            Vec3 destination = destinationFor(hearth, anchor, index);
            Vec3 control = source.add(destination).scale(0.5D)
                    .add(0.0D, 9.0D + (index % 5), 0.0D);
            float eased = progress * progress * (3.0F - 2.0F * progress);
            display.setPos(quadraticBezier(source, control, destination, eased));
            display.setYRot(display.getYRot() + 7.0F + index % 7);
        }
    }

    private static void reconcileNodeDebris(
            ServerLevel level,
            ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor) {
        Map<Integer, UUID> bindings = ACTIVE_NODE_DEBRIS.computeIfAbsent(
                hearth.id(), ignored -> new HashMap<>());
        for (int node = 0; node < HeartLattice.NODE_COUNT; node++) {
            if (!HeartLattice.isDestroyed(hearth.heartDestroyedNodeMask(), node)) {
                continue;
            }
            if ((hearth.heartDebrisLandedMask() & 1 << node) != 0) {
                clearNodeDebris(level, hearth.id(), anchor, node);
                continue;
            }
            long destroyedAt = hearth.heartNodeDestroyedGameTime(node);
            long elapsed = destroyedAt < 0L ? 60L
                    : Math.max(0L, level.getGameTime() - destroyedAt);
            if (elapsed >= 60L) {
                clearNodeDebris(level, hearth.id(), anchor, node);
                placeNodeDebris(level, hearth, anchor, node, 4);
                data.markHeartNodeDebrisLanded(hearth.id(), node);
                continue;
            }
            float progress = Mth.clamp(elapsed / 60.0F, 0.0F, 1.0F);
            for (int piece = 0; piece < 4; piece++) {
                int key = node * 16 + piece;
                Entity existing = bindings.containsKey(key)
                        ? level.getEntity(bindings.get(key)) : null;
                if (!(existing instanceof Display.BlockDisplay)) {
                    existing = adoptNodeDebris(
                            level, hearth.id(), anchor, node, piece);
                }
                Display.BlockDisplay display = existing instanceof Display.BlockDisplay block
                        ? block : createNodeDebris(
                        level, hearth, anchor, node, piece);
                if (display == null) {
                    continue;
                }
                bindings.put(key, display.getUUID());
                Vec3 source = nodeDebrisSource(hearth, anchor, node, piece);
                Vec3 destination = nodeDebrisDestination(
                        level, hearth, anchor, node, piece, false);
                double arc = 5.0D + piece * 0.8D;
                Vec3 control = source.add(destination).scale(0.5D)
                        .add(0.0D, arc, 0.0D);
                float eased = progress * progress * (3.0F - 2.0F * progress);
                display.setPos(quadraticBezier(source, control, destination, eased));
                display.setYRot(node * 47.0F + piece * 71.0F + eased * 190.0F);
                display.setXRot(eased * (65.0F + piece * 18.0F));
            }
        }
        bindings.entrySet().removeIf(entry -> level.getEntity(entry.getValue()) == null);
    }

    private static Display.BlockDisplay createNodeDebris(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            int node,
            int piece) {
        Display.BlockDisplay display = new Display.BlockDisplay(
                EntityType.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) (Object) display).frozendawn$setBlockState(
                debrisState(node, piece));
        ((DisplayAccessor) (Object) display).frozendawn$setPosRotInterpolationDuration(2);
        float scale = 0.58F + piece * 0.09F;
        ((DisplayAccessor) (Object) display).frozendawn$setTransformation(
                new Transformation(
                        new Vector3f(-scale * 0.5F),
                        new Quaternionf(),
                        new Vector3f(scale),
                        new Quaternionf()));
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.addTag(nodeDebrisTag(hearth.id()));
        display.addTag(nodeDebrisNodeTag(hearth.id(), node));
        display.addTag(nodeDebrisIndexTag(hearth.id(), node, piece));
        display.setPos(nodeDebrisSource(hearth, anchor, node, piece));
        if (!level.addFreshEntity(display)) {
            display.discard();
            return null;
        }
        return display;
    }

    private static Display.BlockDisplay adoptNodeDebris(
            ServerLevel level,
            UUID hearthId,
            BlockPos anchor,
            int node,
            int piece) {
        String tag = nodeDebrisIndexTag(hearthId, node, piece);
        List<Display.BlockDisplay> matches = level.getEntitiesOfClass(
                Display.BlockDisplay.class,
                new AABB(anchor).inflate(72.0D, 96.0D, 72.0D),
                entity -> entity.getTags().contains(tag));
        if (matches.isEmpty()) {
            return null;
        }
        Display.BlockDisplay adopted = matches.getFirst();
        for (int duplicate = 1; duplicate < matches.size(); duplicate++) {
            matches.get(duplicate).discard();
        }
        return adopted;
    }

    private static Vec3 nodeDebrisSource(
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            int node,
            int piece) {
        RandomSource random = RandomSource.create(layoutSeed(hearth, anchor)
                ^ node * 0x9E3779B97F4A7C15L ^ piece * 0xC2B2AE3D27D4EB4FL);
        double angle = node * 1.31D + piece * 0.87D;
        double radius = 4.0D + random.nextDouble() * 8.0D;
        float load = HeartLattice.requiredLoad(node);
        double descent = CognitiveLoadPolicy.heartDescentBlocks(load);
        return Vec3.atCenterOf(anchor.above(30)).add(
                Math.cos(angle) * radius,
                -descent - 4.0D + random.nextDouble() * 14.0D,
                Math.sin(angle) * radius);
    }

    private static Vec3 nodeDebrisDestination(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            int node,
            int piece,
            boolean finalCollapse) {
        long salt = finalCollapse ? 0x46494E414C46414CL : 0x4E4F444546414C4CL;
        RandomSource random = RandomSource.create(layoutSeed(hearth, anchor)
                ^ salt ^ node * 0x9E3779B97F4A7C15L
                ^ piece * 0xD6E8FEB86659FD93L);
        double angle = node * 1.23D + piece * 1.71D + random.nextDouble() * 0.4D;
        double radius = (finalCollapse ? 10.0D : 6.0D + node * 2.0D)
                + random.nextDouble() * (finalCollapse ? 13.0D : 6.0D);
        int x = Mth.floor(anchor.getX() + 0.5D + Math.cos(angle) * radius);
        int z = Mth.floor(anchor.getZ() + 0.5D + Math.sin(angle) * radius);
        if (!level.hasChunkAt(new BlockPos(x, anchor.getY(), z))) {
            return Vec3.atCenterOf(anchor.above());
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(x + 0.5D, y + 0.08D, z + 0.5D);
    }

    private static void placeNodeDebris(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            int node,
            int count) {
        for (int piece = 0; piece < count; piece++) {
            Vec3 destination = nodeDebrisDestination(
                    level, hearth, anchor, node, piece, false);
            placeDebrisNear(level, Mth.floor(destination.x), Mth.floor(destination.z),
                    debrisState(node, piece));
        }
    }

    private static void placeCollapseDebris(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor) {
        int count = Math.min(12, HeartCollapsePolicy.timeline(
                hearth.heartFieldStrength()).fragmentCount());
        for (int piece = 0; piece < count; piece++) {
            Vec3 destination = nodeDebrisDestination(
                    level, hearth, anchor, HeartLattice.NODE_COUNT, piece, true);
            placeDebrisNear(level, Mth.floor(destination.x), Mth.floor(destination.z),
                    debrisState(HeartLattice.NODE_COUNT, piece));
        }
    }

    private static boolean placeDebrisNear(
            ServerLevel level, int x, int z, BlockState state) {
        int[][] offsets = {
                {0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1},
                {1, 1}, {-1, 1}, {-1, -1}, {1, -1},
                {2, 0}, {0, 2}, {-2, 0}, {0, -2}
        };
        for (int[] offset : offsets) {
            int candidateX = x + offset[0];
            int candidateZ = z + offset[1];
            if (!level.hasChunkAt(new BlockPos(
                    candidateX, level.getMinBuildHeight(), candidateZ))) {
                continue;
            }
            int surface = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    candidateX, candidateZ);
            BlockPos pos = new BlockPos(candidateX, surface, candidateZ);
            if (!level.getBlockState(pos).canBeReplaced()
                    || !level.getBlockState(pos.below()).isSolidRender(
                    level, pos.below())
                    || !level.getEntities((Entity) null, new AABB(pos)).isEmpty()) {
                continue;
            }
            return level.setBlock(pos, state, 3);
        }
        return false;
    }

    private static BlockState debrisState(int node, int piece) {
        return switch (Math.floorMod(node * 3 + piece, 5)) {
            case 0 -> Blocks.BLUE_ICE.defaultBlockState();
            case 1, 2 -> Blocks.ICE.defaultBlockState();
            default -> Blocks.PACKED_ICE.defaultBlockState();
        };
    }

    private static void reconcileCollapseFragments(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            HeartCollapsePolicy.Snapshot snapshot) {
        if (snapshot.stage() == HeartCollapseStage.NONE
                || snapshot.stage() == HeartCollapseStage.DORMANT) {
            clearCollapseFragments(level, hearth.id(), anchor);
            ACTIVE_COLLAPSE_FRAGMENTS.remove(hearth.id());
            return;
        }
        int count = snapshot.timeline().fragmentCount();
        Map<Integer, UUID> bindings = ACTIVE_COLLAPSE_FRAGMENTS.computeIfAbsent(
                hearth.id(), ignored -> new HashMap<>());
        float globalProgress = Mth.clamp(snapshot.elapsedTicks()
                / (float) HeartCollapsePolicy.DORMANT_START, 0.0F, 1.0F);
        for (int index = 0; index < count; index++) {
            float launch = (index % 7) * 0.018F;
            float progress = Mth.clamp(
                    (globalProgress - launch) / Math.max(0.01F, 0.88F - launch),
                    0.0F, 1.0F);
            Entity existing = bindings.containsKey(index)
                    ? level.getEntity(bindings.get(index)) : null;
            if (!(existing instanceof Display.BlockDisplay)) {
                existing = adoptCollapseFragment(level, hearth.id(), anchor, index);
            }
            if (progress >= 1.0F) {
                if (existing != null) {
                    existing.discard();
                }
                bindings.remove(index);
                continue;
            }
            Display.BlockDisplay display = existing instanceof Display.BlockDisplay blockDisplay
                    ? blockDisplay : createCollapseFragment(level, hearth, anchor, index);
            if (display == null) {
                continue;
            }
            bindings.put(index, display.getUUID());
            Vec3 source = collapseSourceFor(hearth, anchor, index, count);
            Vec3 destination = collapseDestinationFor(
                    level, hearth, anchor, index, count);
            Vec3 control = source.add(destination).scale(0.5D).add(
                    Math.sin(index * 2.13D) * 4.0D,
                    4.0D + index % 4,
                    Math.cos(index * 1.71D) * 4.0D);
            float eased = progress * progress * (3.0F - 2.0F * progress);
            display.setPos(quadraticBezier(source, control, destination, eased));
            display.setYRot(index * 31.0F + eased * (150.0F + index * 11.0F));
            display.setXRot(eased * (55.0F + index % 5 * 13.0F));
        }
    }

    private static Display.BlockDisplay createCollapseFragment(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            int index) {
        Display.BlockDisplay display = new Display.BlockDisplay(
                EntityType.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) (Object) display).frozendawn$setBlockState(
                blockStateFor(hearth, index));
        ((DisplayAccessor) (Object) display).frozendawn$setPosRotInterpolationDuration(2);
        float scale = 0.65F + (index % 4) * 0.11F;
        ((DisplayAccessor) (Object) display).frozendawn$setTransformation(
                new Transformation(
                        new Vector3f(-scale * 0.5F),
                        new Quaternionf(),
                        new Vector3f(scale),
                        new Quaternionf()));
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.addTag(collapseFragmentTag(hearth.id()));
        display.addTag(collapseFragmentIndexTag(hearth.id(), index));
        display.setPos(collapseSourceFor(hearth, anchor, index,
                HeartCollapsePolicy.timeline(hearth.heartFieldStrength()).fragmentCount()));
        if (!level.addFreshEntity(display)) {
            display.discard();
            return null;
        }
        return display;
    }

    private static Display.BlockDisplay adoptCollapseFragment(
            ServerLevel level, UUID hearthId, BlockPos anchor, int index) {
        String tag = collapseFragmentIndexTag(hearthId, index);
        List<Display.BlockDisplay> matches = level.getEntitiesOfClass(
                Display.BlockDisplay.class,
                new AABB(anchor).inflate(72.0D, 96.0D, 72.0D),
                entity -> entity.getTags().contains(tag));
        if (matches.isEmpty()) {
            return null;
        }
        Display.BlockDisplay adopted = matches.getFirst();
        for (int duplicate = 1; duplicate < matches.size(); duplicate++) {
            matches.get(duplicate).discard();
        }
        return adopted;
    }

    private static Vec3 collapseSourceFor(
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            int index,
            int count) {
        RandomSource random = RandomSource.create(
                layoutSeed(hearth, anchor) ^ 0x434F4C4C41505345L
                        ^ index * 0x9E3779B97F4A7C15L);
        double angle = index * Math.PI * 2.0D / Math.max(1, count)
                + random.nextDouble() * 0.45D;
        double radius = 4.0D + random.nextDouble() * 12.0D;
        return Vec3.atCenterOf(anchor.above(30)).add(
                Math.cos(angle) * radius,
                (random.nextDouble() - 0.5D) * 16.0D,
                Math.sin(angle) * radius);
    }

    private static Vec3 collapseDestinationFor(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            int index,
            int count) {
        RandomSource random = RandomSource.create(
                layoutSeed(hearth, anchor) ^ 0x52454D4E414E5453L
                        ^ index * 0xC2B2AE3D27D4EB4FL);
        double angle = index * Math.PI * 2.0D / Math.max(1, count)
                + random.nextDouble() * 0.8D;
        double radius = 3.0D + random.nextDouble() * 10.0D;
        int x = Mth.floor(anchor.getX() + 0.5D + Math.cos(angle) * radius);
        int z = Mth.floor(anchor.getZ() + 0.5D + Math.sin(angle) * radius);
        if (!level.hasChunkAt(new BlockPos(x, anchor.getY(), z))) {
            return Vec3.atCenterOf(anchor.above());
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(x + 0.5D, y + 0.08D, z + 0.5D);
    }

    private static Display.BlockDisplay createFragment(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            int index,
            int count) {
        BlockState state = blockStateFor(hearth, index);
        Display.BlockDisplay display = new Display.BlockDisplay(
                EntityType.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) (Object) display).frozendawn$setBlockState(state);
        ((DisplayAccessor) (Object) display).frozendawn$setPosRotInterpolationDuration(2);
        float scale = 0.55F + (index % 4) * 0.08F;
        ((DisplayAccessor) (Object) display).frozendawn$setTransformation(
                new Transformation(
                        new Vector3f(-scale * 0.5F),
                        new Quaternionf(),
                        new Vector3f(scale),
                        new Quaternionf()));
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.addTag(fragmentTag(hearth.id()));
        display.addTag(fragmentIndexTag(hearth.id(), index));
        display.setPos(sourceFor(hearth, anchor, index, count));
        if (!level.addFreshEntity(display)) {
            display.discard();
            return null;
        }
        return display;
    }

    private static Display.BlockDisplay adoptFragment(
            ServerLevel level, UUID hearthId, BlockPos anchor, int index) {
        String tag = fragmentIndexTag(hearthId, index);
        List<Display.BlockDisplay> matches = level.getEntitiesOfClass(
                Display.BlockDisplay.class,
                new AABB(anchor).inflate(72.0D, 96.0D, 72.0D),
                entity -> entity.getTags().contains(tag));
        if (matches.isEmpty()) {
            return null;
        }
        Display.BlockDisplay adopted = matches.getFirst();
        for (int duplicate = 1; duplicate < matches.size(); duplicate++) {
            matches.get(duplicate).discard();
        }
        return adopted;
    }

    private static BlockState blockStateFor(
            ReturnedHearthSavedData.HearthRecord hearth, int index) {
        List<ReturnedHearthSavedData.HeartFragmentSnapshot> fragments = hearth.heartFragments();
        if (fragments.isEmpty()) {
            return (index % 5 == 0 ? Blocks.ICE : Blocks.PACKED_ICE).defaultBlockState();
        }
        String id = fragments.get(index % fragments.size()).blockId();
        try {
            return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id))
                    .orElse(Blocks.PACKED_ICE).defaultBlockState();
        } catch (IllegalArgumentException ignored) {
            return Blocks.PACKED_ICE.defaultBlockState();
        }
    }

    private static Vec3 sourceFor(
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor,
            int index,
            int count) {
        List<ReturnedHearthSavedData.HeartFragmentSnapshot> fragments = hearth.heartFragments();
        if (!fragments.isEmpty()) {
            BlockPos relative = fragments.get(index % fragments.size()).relativePos();
            return Vec3.atCenterOf(anchor.offset(relative));
        }
        double angle = index * Math.PI * 2.0D / Math.max(1, count);
        double radius = 6.0D + index % 8;
        return Vec3.atCenterOf(anchor).add(
                Math.cos(angle) * radius, 0.8D, Math.sin(angle) * radius);
    }

    private static Vec3 destinationFor(
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos anchor, int index) {
        RandomSource random = RandomSource.create(layoutSeed(hearth, anchor) + index * 0x9E3779B9L);
        return Vec3.atCenterOf(anchor.above(30)).add(
                (random.nextDouble() - 0.5D) * 27.0D,
                (random.nextDouble() - 0.5D) * 18.0D,
                (random.nextDouble() - 0.5D) * 21.0D);
    }

    private static Vec3 quadraticBezier(
            Vec3 start, Vec3 control, Vec3 end, double progress) {
        double inverse = 1.0D - progress;
        return start.scale(inverse * inverse)
                .add(control.scale(2.0D * inverse * progress))
                .add(end.scale(progress * progress));
    }

    private static void grantFormationAdvancement(ServerLevel level, BlockPos anchor) {
        double radiusSqr = HeartFormationPolicy.AURA_RADIUS
                * HeartFormationPolicy.AURA_RADIUS;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - (anchor.getX() + 0.5D);
            double dz = player.getZ() - (anchor.getZ() + 0.5D);
            if (dx * dx + dz * dz <= radiusSqr) {
                WorldTickHandler.grantAdvancement(player, "the_watching_never_stopped");
            }
        }
    }

    private static void grieveForMaeve(ServerLevel level, BlockPos anchor) {
        Vec3 maeve = HeartLattice.maevePosition(anchor);
        AABB area = new AABB(maeve, maeve).inflate(25.0D);
        List<Mob> grieving = level.getEntitiesOfClass(Mob.class, area, mob -> {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            return id != null && FrozenDawn.MOD_ID.equals(id.getNamespace());
        });
        int casualtyCount = grieving.size() < 2 ? 0 : Mth.clamp(
                Math.round(grieving.size()
                        * (0.18F + level.getRandom().nextFloat() * 0.22F)),
                1, grieving.size() - 1);
        Collections.shuffle(grieving, new Random(level.getRandom().nextLong()));
        int index = 0;
        for (Mob mob : grieving) {
            mob.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS,
                    MobEffectInstance.INFINITE_DURATION, 1));
            mob.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    MobEffectInstance.INFINITE_DURATION, 1));
            level.playSound(null, mob.blockPosition(),
                    ModSounds.MASTER_ARCHITECT_TETHER_WAIL.get(),
                    SoundSource.HOSTILE, 1.1F,
                    0.70F + (index++ % 7) * 0.045F);
            Vec3 from = mob.getEyePosition();
            Vec3 toward = maeve.subtract(from).normalize().scale(0.16D);
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    from.x, from.y, from.z, 0,
                    toward.x, toward.y, toward.z, 1.0D);
        }
        for (int casualty = 0; casualty < casualtyCount; casualty++) {
            Mob mob = grieving.get(casualty);
            Vec3 position = mob.getEyePosition();
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    position.x, position.y, position.z,
                    18, 0.45D, 0.8D, 0.45D, 0.11D);
            mob.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    private static void beginLastWitnessForge(ServerLevel level, BlockPos anchor) {
        Vec3 maeve = HeartLattice.maevePosition(anchor);
        level.playSound(null, BlockPos.containing(maeve),
                ModSounds.THAE_IVEN_HEART_MAEVE_BREAK.get(),
                SoundSource.AMBIENT, 5.0F, 0.72F);
        level.playSound(null, BlockPos.containing(maeve),
                ModSounds.LAST_WITNESS_CONVERGE.get(),
                SoundSource.AMBIENT, 4.8F, 1.0F);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(maeve) <= 96.0D * 96.0D) {
                PacketDistributor.sendToPlayer(
                        player, HearthBoundaryEffectPayload.maeveBreak());
            }
        }
        level.sendParticles(ParticleTypes.SONIC_BOOM,
                maeve.x, maeve.y, maeve.z, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void drawParticlesIntoMaeve(
            ServerLevel level, BlockPos anchor, float progress) {
        Vec3 maeve = HeartLattice.maevePosition(anchor);
        RandomSource random = level.getRandom();
        int count = 7 + Mth.floor(progress * 8.0F);
        double rippleRadius = 3.0D + Mth.sin(
                level.getGameTime() * 0.38F) * (0.8D + progress * 0.9D);
        for (int ripple = 0; ripple < 12; ripple++) {
            double angle = ripple * Math.PI * 2.0D / 12.0D
                    + level.getGameTime() * 0.12D;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    maeve.x + Math.cos(angle) * rippleRadius,
                    maeve.y + Math.sin(angle * 2.0D) * 1.25D,
                    maeve.z + Math.sin(angle) * rippleRadius,
                    1, 0.04D, 0.04D, 0.04D, 0.0D);
        }
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 5.0D + random.nextDouble() * 7.0D;
            Vec3 from = new Vec3(
                    maeve.x + Math.cos(angle) * radius,
                    maeve.y + (random.nextDouble() - 0.5D) * 8.0D,
                    maeve.z + Math.sin(angle) * radius);
            Vec3 velocity = maeve.subtract(from).normalize()
                    .scale(0.11D + progress * 0.15D);
            level.sendParticles(i % 3 == 0
                            ? ParticleTypes.END_ROD : ParticleTypes.SCULK_SOUL,
                    from.x, from.y, from.z, 0,
                    velocity.x, velocity.y, velocity.z, 1.0D);
        }
    }

    private static void drawParticlesIntoMaeveFormation(
            ServerLevel level, BlockPos anchor, float progress) {
        Vec3 maeve = HeartLattice.maevePosition(anchor);
        RandomSource random = level.getRandom();
        int count = 5 + Mth.floor(progress * 7.0F);
        for (int index = 0; index < count; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = Mth.lerp(progress, 12.0D, 5.5D)
                    + random.nextDouble() * 4.0D;
            Vec3 from = new Vec3(
                    maeve.x + Math.cos(angle) * radius,
                    maeve.y + (random.nextDouble() - 0.5D) * 10.0D,
                    maeve.z + Math.sin(angle) * radius);
            Vec3 velocity = maeve.subtract(from).normalize()
                    .scale(0.16D + progress * 0.20D);
            level.sendParticles(index % 4 == 0
                            ? ParticleTypes.END_ROD : ParticleTypes.SCULK_SOUL,
                    from.x, from.y, from.z, 0,
                    velocity.x, velocity.y, velocity.z, 1.0D);
        }
        if (level.getGameTime() % 6L == 0L) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    maeve.x, maeve.y, maeve.z,
                    4 + Mth.floor(progress * 8.0F),
                    2.8D * (1.0D - progress),
                    2.2D * (1.0D - progress),
                    2.8D * (1.0D - progress), 0.04D);
        }
    }

    private static void releaseLastWitness(
            ServerLevel level,
            ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth,
            Vec3 maeve) {
        if (hearth.heartLastWitnessDropped()
                || !data.markHeartLastWitnessDropped(hearth.id())) {
            return;
        }
        level.playSound(null, BlockPos.containing(maeve),
                ModSounds.LAST_WITNESS_FORGE.get(),
                SoundSource.AMBIENT, 4.2F, 0.88F);
        level.sendParticles(ParticleTypes.FLASH,
                maeve.x, maeve.y, maeve.z, 3,
                0.25D, 0.25D, 0.25D, 0.0D);
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                maeve.x, maeve.y, maeve.z, 240,
                5.5D, 4.0D, 5.5D, 0.32D);
        level.sendParticles(ParticleTypes.END_ROD,
                maeve.x, maeve.y, maeve.z, 120,
                3.5D, 2.6D, 3.5D, 0.24D);
        ItemEntity relic = new ItemEntity(level,
                maeve.x, maeve.y, maeve.z,
                new ItemStack(ModItems.THE_LAST_WITNESS.get()));
        relic.setDeltaMovement(0.0D, 0.48D, 0.0D);
        relic.setPickUpDelay(30);
        relic.setGlowingTag(true);
        level.addFreshEntity(relic);
    }

    private static void shatterHeart(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            BlockPos anchor) {
        HeartLattice.Lattice lattice = HeartLattice.create(layoutSeed(hearth, anchor));
        Vec3 origin = HeartLattice.heartOrigin(
                anchor, HeartLattice.requiredLoad(HeartLattice.NODE_COUNT - 1));
        RandomSource random = RandomSource.create(
                layoutSeed(hearth, anchor) ^ 0x4D41455645534841L);
        for (int index = 0; index < lattice.segments().size(); index += 2) {
            HeartLattice.Segment segment = lattice.segments().get(index);
            double along = 0.2D + random.nextDouble() * 0.6D;
            double x = origin.x + Mth.lerp(along, segment.x0(), segment.x1());
            double y = origin.y + Mth.lerp(along, segment.y0(), segment.y1());
            double z = origin.z + Mth.lerp(along, segment.z0(), segment.z1());
            BlockState fragment = (index & 2) == 0
                    ? Blocks.BLUE_ICE.defaultBlockState()
                    : Blocks.PACKED_ICE.defaultBlockState();
            level.sendParticles(new BlockParticleOption(
                            ParticleTypes.BLOCK, fragment),
                    x, y, z, 5,
                    0.55D, 0.55D, 0.55D, 0.14D);
            if (index % 6 == 0) {
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                        x, y, z, 3,
                        0.35D, 0.35D, 0.35D, 0.08D);
            }
        }
        for (HeartLattice.Node node : lattice.nodes()) {
            level.sendParticles(ParticleTypes.END_ROD,
                    origin.x + node.x(), origin.y + node.y(), origin.z + node.z(),
                    28, 1.2D, 1.2D, 1.2D, 0.18D);
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                origin.x, origin.y - 4.0D, origin.z,
                180, 8.0D, 7.0D, 8.0D, 0.18D);
    }

    private static void grantFinalAdvancement(
            ServerLevel level,
            ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth.heartFinalAdvancementGranted()) {
            return;
        }
        ServerPlayer recipient = hearth.heartMaeveEraserId()
                .map(level.getServer().getPlayerList()::getPlayer)
                .orElse(null);
        if (recipient == null) {
            return;
        }
        WorldTickHandler.grantAdvancement(
                recipient, "no_one_else_remembers_now");
        data.markHeartFinalAdvancementGranted(hearth.id());
    }

    private static int removeHeartEntities(
            ServerLevel level, UUID hearthId, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) {
            return 0;
        }
        List<ThaeIvenHeartEntity> hearts = level.getEntitiesOfClass(
                ThaeIvenHeartEntity.class,
                new AABB(anchor).inflate(64.0D, 96.0D, 64.0D),
                entity -> entity.hearthId().map(hearthId::equals).orElse(false));
        hearts.forEach(Entity::discard);
        return hearts.size();
    }

    private static int clearFragments(
            ServerLevel level, UUID hearthId, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) {
            return 0;
        }
        List<Display.BlockDisplay> displays = level.getEntitiesOfClass(
                Display.BlockDisplay.class,
                new AABB(anchor).inflate(72.0D, 96.0D, 72.0D),
                entity -> entity.getTags().contains(fragmentTag(hearthId)));
        displays.forEach(Entity::discard);
        return displays.size();
    }

    private static int clearCollapseFragments(
            ServerLevel level, UUID hearthId, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) {
            return 0;
        }
        List<Display.BlockDisplay> displays = level.getEntitiesOfClass(
                Display.BlockDisplay.class,
                new AABB(anchor).inflate(72.0D, 96.0D, 72.0D),
                entity -> entity.getTags().contains(collapseFragmentTag(hearthId)));
        displays.forEach(Entity::discard);
        return displays.size();
    }

    private static int clearNodeDebris(
            ServerLevel level, UUID hearthId, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) {
            return 0;
        }
        List<Display.BlockDisplay> displays = level.getEntitiesOfClass(
                Display.BlockDisplay.class,
                new AABB(anchor).inflate(72.0D, 96.0D, 72.0D),
                entity -> entity.getTags().contains(nodeDebrisTag(hearthId)));
        displays.forEach(Entity::discard);
        return displays.size();
    }

    private static int clearNodeDebris(
            ServerLevel level, UUID hearthId, BlockPos anchor, int node) {
        if (!level.hasChunkAt(anchor)) {
            return 0;
        }
        List<Display.BlockDisplay> displays = level.getEntitiesOfClass(
                Display.BlockDisplay.class,
                new AABB(anchor).inflate(72.0D, 96.0D, 72.0D),
                entity -> entity.getTags().contains(
                        nodeDebrisNodeTag(hearthId, node)));
        displays.forEach(Entity::discard);
        Map<Integer, UUID> bindings = ACTIVE_NODE_DEBRIS.get(hearthId);
        if (bindings != null) {
            int first = node * 16;
            bindings.keySet().removeIf(index -> index >= first && index < first + 16);
            if (bindings.isEmpty()) {
                ACTIVE_NODE_DEBRIS.remove(hearthId);
            }
        }
        return displays.size();
    }

    private static long layoutSeed(
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos anchor) {
        return hearth.heartLayoutSeed() != 0L
                ? hearth.heartLayoutSeed()
                : hearth.layoutSeed() ^ anchor.asLong() ^ 0x48454152544C4154L;
    }

    private static String fragmentTag(UUID hearthId) {
        return FRAGMENT_TAG_PREFIX + hearthId.toString().replace("-", "");
    }

    private static String fragmentIndexTag(UUID hearthId, int index) {
        return fragmentTag(hearthId) + "_" + index;
    }

    private static String collapseFragmentTag(UUID hearthId) {
        return COLLAPSE_FRAGMENT_TAG_PREFIX
                + hearthId.toString().replace("-", "");
    }

    private static String collapseFragmentIndexTag(UUID hearthId, int index) {
        return collapseFragmentTag(hearthId) + "_" + index;
    }

    private static String nodeDebrisTag(UUID hearthId) {
        return NODE_DEBRIS_TAG_PREFIX
                + hearthId.toString().replace("-", "");
    }

    private static String nodeDebrisNodeTag(UUID hearthId, int node) {
        return nodeDebrisTag(hearthId) + "_node_" + node;
    }

    private static String nodeDebrisIndexTag(
            UUID hearthId, int node, int piece) {
        return nodeDebrisNodeTag(hearthId, node) + "_" + piece;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
