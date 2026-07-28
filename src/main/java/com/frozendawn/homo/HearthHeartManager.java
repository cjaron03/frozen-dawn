package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModEntities;
import com.frozendawn.mixin.BlockDisplayAccessor;
import com.frozendawn.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reconciles the one persistent post-storm Heart without force-loading chunks. */
public final class HearthHeartManager {
    private static final String FRAGMENT_TAG_PREFIX = "frozendawn_heart_fragment_";
    private static final Map<UUID, Map<Integer, UUID>> ACTIVE_FRAGMENTS = new HashMap<>();
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
        if (snapshot.stage() != HeartFormationStage.NONE
                && snapshot.elapsedTicks() >= HeartFormationPolicy.DEAD_AIR_TICKS
                && !hearth.heartAdvancementFired()) {
            grantFormationAdvancement(level, anchor);
            data.markHeartAdvancementFired(hearth.id());
        }
        if (snapshot.stage() == HeartFormationStage.LIVE && !hearth.heartLive()) {
            data.markHeartLive(hearth.id());
            formationsCompleted++;
        }

        hearth = data.hearth(hearth.id()).orElse(hearth);
        if (hearth.heartLive() && !hearth.heartConvergenceStarted()
                && level.hasChunkAt(anchor)) {
            HearthCombatRosterManager.beginAftermathConvergence(
                    level, hearth.id(), anchor);
            data.markHeartConvergenceStarted(hearth.id());
        }

        if (!level.hasChunkAt(anchor)) {
            return;
        }
        ThaeIvenHeartEntity heart = reconcileHeartEntity(level, data, hearth, anchor);
        if (heart != null) {
            heart.configure(
                    hearth.id(),
                    layoutSeed(hearth, anchor),
                    anchor.asLong(),
                    hearth.heartFieldStrength(),
                    snapshot.stage(),
                    snapshot.stageProgress());
        }
        reconcileFragments(level, hearth, anchor, snapshot);
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
        ACTIVE_FRAGMENTS.remove(hearth.id());
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
        tick(level);
        return changed;
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
                + " entity=" + hearth.heartEntityId()
                .map(id -> id.toString().substring(0, 8)).orElse("none");
    }

    public static String statusLine() {
        return "spawned=" + heartsSpawned + " adopted=" + heartsAdopted
                + " completed=" + formationsCompleted + " failure=" + lastFailure;
    }

    public static void reset() {
        ACTIVE_FRAGMENTS.clear();
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
                hearth.heartFieldStrength(), HeartFormationStage.NONE, 0.0F);
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

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
