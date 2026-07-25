package com.frozendawn.entity;

import com.mojang.math.Transformation;
import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.homo.MasterArchitectCombatPhase;
import com.frozendawn.homo.MasterArchitectConstructionPolicy;
import com.frozendawn.mixin.BlockDisplayAccessor;
import com.frozendawn.mixin.DisplayAccessor;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns temporary, bounded Phase 2 architecture for the Master Architect only. */
final class MasterArchitectConstructionController {
    private static final int TRAP_HEIGHT = 2;
    private static final int WALL_HEIGHT = 3;
    private static final double LAST_WALL_CLEAR_RADIUS_SQR = 6.0D * 6.0D;
    private static final String CONSTRUCTION_VFX_TAG =
            "frozendawn_master_construction_vfx";

    private final ArchitectEntity architect;
    private final List<ConstructionInstance> constructions = new ArrayList<>();
    private final List<Display.BlockDisplay> orbitingFragments = new ArrayList<>();

    private BuildPlan activePlan;
    private BlockPos observedCoverPos;
    private long observedCoverAt = Long.MIN_VALUE;
    private BlockPos vantageTarget;
    private long vantageSeekUntil = -1L;
    private int cooldown;
    private int staggerTicks;
    private int behaviorCursor;
    private int stationaryTicks;
    private long nextConstructionId = 1L;
    private long shelteringConstructionId = -1L;
    private boolean openingFortUsed;
    private boolean vfxInitialized;
    private Vec3 lastTargetPosition;

    MasterArchitectConstructionController(ArchitectEntity architect) {
        this.architect = architect;
    }

    void tick(
            ServerLevel level,
            ServerPlayer target,
            MasterArchitectCombatPhase phase) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (staggerTicks > 0) {
            staggerTicks--;
        }
        initializeVfx(level);
        updateTargetMotion(target, phase);
        tickAmbientFragments(level, phase);
        observeCover(level, target, phase);

        List<ConstructionInstance> expired = new ArrayList<>();
        List<ConstructionInstance> brokenSeams = new ArrayList<>();
        for (ConstructionInstance construction : List.copyOf(constructions)) {
            if (construction.expiresAt >= 0L
                    && level.getGameTime() >= construction.expiresAt) {
                expired.add(construction);
                continue;
            }
            boolean building = activePlan != null
                    && activePlan.instance.id == construction.id;
            if (building) {
                continue;
            }

            if (construction.blocks.stream().anyMatch(pos ->
                    level.hasChunkAt(pos)
                            && !isConstructionBlock(level.getBlockState(pos)))) {
                construction.breached = true;
            }

            if (construction.seams.isEmpty()) {
                brokenSeams.add(construction);
                continue;
            }

            boolean seamMissing = false;
            for (BlockPos seam : construction.seams) {
                if (level.hasChunkAt(seam)
                        && !level.getBlockState(seam).is(Blocks.ICE)) {
                    seamMissing = true;
                    break;
                }
            }
            if (!construction.seams.isEmpty() && seamMissing) {
                brokenSeams.add(construction);
                continue;
            }

            construction.blocks.removeIf(pos -> level.hasChunkAt(pos)
                    && !isConstructionBlock(level.getBlockState(pos)));
            construction.seams.removeIf(pos -> !construction.blocks.contains(pos));
            if (construction.blocks.isEmpty()) {
                expired.add(construction);
            }
        }
        expired.forEach(construction -> removeConstruction(
                level, construction, false, false));
        brokenSeams.forEach(construction -> removeConstruction(
                level, construction, true, true));

        if (vantageTarget != null
                && (level.getGameTime() > vantageSeekUntil
                        || constructions.stream().noneMatch(
                                construction -> vantageTarget.equals(
                                        construction.vantageStand)))) {
            vantageTarget = null;
            vantageSeekUntil = -1L;
        }
    }

    boolean tryBeginConstruction(
            ServerLevel level,
            ServerPlayer target,
            MasterArchitectCombatPhase phase) {
        if (!MasterArchitectConstructionPolicy.canStartConstruction(
                phase,
                cooldown,
                activePlan != null,
                architect.distanceToSqr(target))) {
            return false;
        }

        BuildPlan plan;
        if (!openingFortUsed) {
            plan = createOpeningFort(level, target);
            if (plan == null) {
                plan = createOpeningFallbackWall(level, target);
            }
        } else {
            plan = selectOngoingPlan(level, target);
        }
        if (plan == null) {
            return false;
        }
        makeRoomFor(level, plan.plannedBlockCount());
        if (!MasterArchitectConstructionPolicy.canReserve(
                liveBlockCount(), plan.plannedBlockCount())) {
            return false;
        }

        constructions.add(plan.instance);
        activePlan = plan;
        beginTravelingFragments(level, plan);
        openingFortUsed |= plan.instance.kind == ConstructionKind.OPENING_FORT;
        cooldown = plan.instance.kind == ConstructionKind.OPENING_FORT
                ? MasterArchitectConstructionPolicy.OPENING_COOLDOWN_TICKS
                : MasterArchitectConstructionPolicy.ONGOING_COOLDOWN_MIN
                        + architect.nextRandomInt(
                                MasterArchitectConstructionPolicy
                                        .ONGOING_COOLDOWN_VARIANCE + 1);
        emitBuildStart(level, plan.instance);
        FrozenDawn.LOGGER.info(
                "Master Architect {} began Construction War {} steps={} blocks={}/{}",
                shortId(architect),
                plan.instance.kind.serializedName,
                plan.steps.size(),
                liveBlockCount(),
                MasterArchitectConstructionPolicy.LIVE_BLOCK_BUDGET);
        return true;
    }

    boolean placeNextStep(ServerLevel level) {
        if (activePlan == null) {
            return true;
        }
        activePlan.choreographyTicks++;
        updateTravelingFragments(level, activePlan);
        if (activePlan.choreographyTicks
                < MasterArchitectConstructionPolicy.CHOREOGRAPHY_TICKS) {
            return false;
        }

        clearTravelingFragments(level, activePlan);
        boolean seamPlaced = false;
        int placed = 0;
        while (activePlan.cursor < activePlan.steps.size()) {
            BuildStep step = activePlan.steps.get(activePlan.cursor++);
            for (PlannedBlock planned : step.blocks) {
                if (liveBlockCount()
                                >= MasterArchitectConstructionPolicy.LIVE_BLOCK_BUDGET
                        || !canPlaceAt(level, planned.pos)) {
                    continue;
                }
                BlockState state = planned.seam
                        ? Blocks.ICE.defaultBlockState()
                        : Blocks.PACKED_ICE.defaultBlockState();
                level.setBlock(planned.pos, state, Block.UPDATE_CLIENTS);
                activePlan.instance.blocks.add(planned.pos.immutable());
                if (planned.seam) {
                    activePlan.instance.seams.add(planned.pos.immutable());
                    seamPlaced = true;
                }
                emitPlacedBlock(level, planned);
                placed++;
            }
        }
        level.playSound(
                null,
                activePlan.instance.anchor,
                SoundEvents.GENERIC_EXPLODE.value(),
                architect.getSoundSource(),
                0.72F,
                0.58F);
        level.playSound(
                null,
                activePlan.instance.anchor,
                seamPlaced
                        ? SoundEvents.AMETHYST_BLOCK_CHIME
                        : SoundEvents.GLASS_PLACE,
                architect.getSoundSource(),
                1.35F,
                seamPlaced ? 0.72F : 0.62F);
        return placed > 0 || activePlan.cursor >= activePlan.steps.size();
    }

    void finishConstruction(ServerLevel level) {
        if (activePlan == null) {
            return;
        }
        ConstructionInstance completed = activePlan.instance;
        activePlan = null;
        if (completed.blocks.isEmpty() || completed.seams.isEmpty()) {
            removeConstruction(level, completed, false, false);
            return;
        }
        if (completed.vantageStand != null) {
            vantageTarget = completed.vantageStand;
            vantageSeekUntil = level.getGameTime()
                    + MasterArchitectConstructionPolicy.VANTAGE_SEEK_TICKS;
        }
        FrozenDawn.LOGGER.info(
                "Master Architect {} completed Construction War {} blocks={} seam={} live={}/{}",
                shortId(architect),
                completed.kind.serializedName,
                completed.blocks.size(),
                completed.seams.size(),
                liveBlockCount(),
                MasterArchitectConstructionPolicy.LIVE_BLOCK_BUDGET);
    }

    void cancelCast(ServerLevel level) {
        if (activePlan == null) {
            return;
        }
        ConstructionInstance cancelled = activePlan.instance;
        clearTravelingFragments(level, activePlan);
        activePlan = null;
        removeConstruction(level, cancelled, false, false);
    }

    boolean isStaggered() {
        return staggerTicks > 0;
    }

    int staggerTicks() {
        return staggerTicks;
    }

    boolean seekVantage(ServerLevel level) {
        if (vantageTarget == null || level.getGameTime() > vantageSeekUntil) {
            return false;
        }
        Vec3 destination = Vec3.atBottomCenterOf(vantageTarget);
        if (architect.position().distanceToSqr(destination) <= 2.25D) {
            vantageTarget = null;
            vantageSeekUntil = -1L;
            architect.getNavigation().stop();
            return false;
        }
        architect.getNavigation().moveTo(
                destination.x, destination.y, destination.z, 0.92D);
        return true;
    }

    void clearLastWallFootprint(ServerLevel level) {
        List<ConstructionInstance> nearby = constructions.stream()
                .filter(construction -> construction.blocks.stream().anyMatch(pos ->
                        architect.distanceToSqr(Vec3.atCenterOf(pos))
                                <= LAST_WALL_CLEAR_RADIUS_SQR))
                .toList();
        nearby.forEach(construction -> removeConstruction(
                level, construction, false, false));
        if (activePlan != null && nearby.contains(activePlan.instance)) {
            activePlan = null;
        }
    }

    void onDeath(ServerLevel level) {
        clearAllVfx(level);
        activePlan = null;
        for (ConstructionInstance construction : List.copyOf(constructions)) {
            removeConstruction(level, construction, false, false);
        }
        constructions.clear();
        vantageTarget = null;
        vantageSeekUntil = -1L;
    }

    void addSaveData(CompoundTag tag) {
        tag.putBoolean("MasterConstructionOpeningUsed", openingFortUsed);
        tag.putInt("MasterConstructionCooldown", cooldown);
        tag.putInt("MasterConstructionBehavior", behaviorCursor);
        tag.putLong("MasterConstructionNextId", nextConstructionId);
        ListTag savedConstructions = new ListTag();
        for (ConstructionInstance construction : constructions) {
            CompoundTag saved = new CompoundTag();
            saved.putLong("Id", construction.id);
            saved.putString("Kind", construction.kind.serializedName);
            saved.putLong("ExpiresAt", construction.expiresAt);
            saved.putLong("Anchor", construction.anchor.asLong());
            saved.putBoolean("Breached", construction.breached);
            if (construction.vantageStand != null) {
                saved.putLong("VantageStand", construction.vantageStand.asLong());
            }
            saved.putLongArray("Blocks",
                    construction.blocks.stream().mapToLong(BlockPos::asLong).toArray());
            saved.putLongArray("Seams",
                    construction.seams.stream().mapToLong(BlockPos::asLong).toArray());
            savedConstructions.add(saved);
        }
        tag.put("MasterConstructions", savedConstructions);
    }

    void readSaveData(CompoundTag tag, MasterArchitectCombatPhase loadedPhase) {
        cooldown = Math.max(0, tag.getInt("MasterConstructionCooldown"));
        behaviorCursor = Math.max(0, tag.getInt("MasterConstructionBehavior"));
        nextConstructionId = Math.max(1L, tag.getLong("MasterConstructionNextId"));
        openingFortUsed = tag.contains("MasterConstructionOpeningUsed")
                ? tag.getBoolean("MasterConstructionOpeningUsed")
                : loadedPhase != MasterArchitectCombatPhase.KIT;
        activePlan = null;
        orbitingFragments.clear();
        vfxInitialized = false;
        lastTargetPosition = null;
        stationaryTicks = 0;
        shelteringConstructionId = -1L;
        constructions.clear();
        vantageTarget = null;
        vantageSeekUntil = -1L;

        if (tag.contains("MasterConstructions", Tag.TAG_LIST)) {
            ListTag savedConstructions = tag.getList(
                    "MasterConstructions", Tag.TAG_COMPOUND);
            for (Tag entry : savedConstructions) {
                if (!(entry instanceof CompoundTag saved)
                        || liveBlockCount()
                                >= MasterArchitectConstructionPolicy.LIVE_BLOCK_BUDGET) {
                    continue;
                }
                ConstructionInstance construction = readConstruction(saved);
                if (construction != null) {
                    constructions.add(construction);
                    nextConstructionId = Math.max(
                            nextConstructionId, construction.id + 1L);
                }
            }
        } else {
            migrateOpeningFort(tag);
        }
    }

    boolean hasIntactShelterBetween(
            ServerLevel level,
            ServerPlayer target) {
        shelteringConstructionId = -1L;
        if (target == null || architect.hasLineOfSight(target)) {
            return false;
        }
        BlockHitResult obstruction = level.clip(new ClipContext(
                architect.getEyePosition(),
                target.getEyePosition(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                architect));
        if (obstruction.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos hit = obstruction.getBlockPos();
        ConstructionInstance shelter = constructions.stream()
                .filter(construction -> construction.kind.isShelterWall())
                .filter(construction -> construction.blocks.contains(hit))
                .filter(construction -> isIntactShelter(level, construction))
                .findFirst()
                .orElse(null);
        if (shelter == null) {
            return false;
        }
        shelteringConstructionId = shelter.id;
        return true;
    }

    void emitShelterHealingParticles(ServerLevel level) {
        ConstructionInstance shelter = constructions.stream()
                .filter(construction -> construction.id == shelteringConstructionId)
                .filter(construction -> isIntactShelter(level, construction))
                .findFirst()
                .orElse(null);
        if (shelter == null || shelter.seams.isEmpty()) {
            return;
        }
        BlockPos seam = shelter.seams.get(
                Math.floorMod((int) (level.getGameTime() / 4L), shelter.seams.size()));
        List<BlockPos> sources = shelter.blocks.stream()
                .filter(pos -> !shelter.seams.contains(pos))
                .filter(pos -> level.hasChunkAt(pos)
                        && level.getBlockState(pos).is(Blocks.PACKED_ICE))
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(seam)))
                .limit(3)
                .toList();
        Vec3 seamCenter = Vec3.atCenterOf(seam);
        for (int index = 0; index < sources.size(); index++) {
            emitSoulStream(
                    level,
                    Vec3.atCenterOf(sources.get(index)),
                    seamCenter,
                    index);
        }
        emitSoulStream(
                level,
                seamCenter,
                architect.position().add(0.0D, architect.getBbHeight() * 0.62D, 0.0D),
                0);
    }

    private BuildPlan selectOngoingPlan(ServerLevel level, ServerPlayer target) {
        boolean recentCover = observedCoverPos != null
                && level.getGameTime() - observedCoverAt
                        <= MasterArchitectConstructionPolicy.COVER_MEMORY_TICKS;
        boolean activeHeater = findActivePlayerHeater(level, target) != null;
        MasterArchitectConstructionPolicy.ConstructionIntent preferred =
                MasterArchitectConstructionPolicy.chooseIntent(
                        recentCover,
                        activeHeater,
                        stationaryTicks,
                        architect.distanceToSqr(target),
                        behaviorCursor++);

        List<MasterArchitectConstructionPolicy.ConstructionIntent> intents =
                new ArrayList<>();
        intents.add(preferred);
        for (MasterArchitectConstructionPolicy.ConstructionIntent intent
                : MasterArchitectConstructionPolicy.ConstructionIntent.values()) {
            if (!intents.contains(intent)) {
                intents.add(intent);
            }
        }

        for (MasterArchitectConstructionPolicy.ConstructionIntent intent : intents) {
            if (intent == MasterArchitectConstructionPolicy
                            .ConstructionIntent.COVER_DENIAL
                    && !recentCover) {
                continue;
            }
            BuildPlan plan = switch (intent) {
                case COVER_DENIAL -> createCoverDenialWall(level, target);
                case VANTAGE -> createVantagePlatform(level, target);
                case ENCLOSURE -> createEnclosureTrap(level, target);
                case HEATER_BURIAL -> createHeaterBurial(level, target);
            };
            if (intent == MasterArchitectConstructionPolicy
                    .ConstructionIntent.COVER_DENIAL) {
                observedCoverPos = null;
            }
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private BuildPlan createOpeningFort(ServerLevel level, ServerPlayer target) {
        Vec3 towardTarget = target.position().subtract(architect.position());
        MasterArchitectConstructionPolicy.WallAxes axes =
                MasterArchitectConstructionPolicy.wallAxes(
                        towardTarget.x, towardTarget.z);
        List<MasterArchitectConstructionPolicy.WallAxes> orientations = List.of(
                axes,
                new MasterArchitectConstructionPolicy.WallAxes(
                        axes.tangentX(), axes.tangentZ(),
                        -axes.normalX(), -axes.normalZ()),
                new MasterArchitectConstructionPolicy.WallAxes(
                        -axes.tangentX(), -axes.tangentZ(),
                        axes.normalX(), axes.normalZ()),
                new MasterArchitectConstructionPolicy.WallAxes(
                        -axes.normalX(), -axes.normalZ(),
                        axes.tangentX(), axes.tangentZ()));
        BlockPos origin = architect.blockPosition();
        List<BlockPos> centers = List.of(
                origin,
                origin.north(),
                origin.south(),
                origin.east(),
                origin.west());
        for (BlockPos center : centers) {
            for (MasterArchitectConstructionPolicy.WallAxes orientation
                    : orientations) {
                BuildPlan plan = createOpeningFortAt(
                        level, center, orientation);
                if (plan != null) {
                    return plan;
                }
            }
        }
        return null;
    }

    private BuildPlan createOpeningFallbackWall(
            ServerLevel level,
            ServerPlayer target) {
        Vec3 towardTarget = target.position().subtract(architect.position());
        MasterArchitectConstructionPolicy.WallAxes axes =
                MasterArchitectConstructionPolicy.wallAxes(
                        towardTarget.x, towardTarget.z);
        List<MasterArchitectConstructionPolicy.WallAxes> orientations = List.of(
                axes,
                new MasterArchitectConstructionPolicy.WallAxes(
                        axes.tangentX(),
                        axes.tangentZ(),
                        -axes.normalX(),
                        -axes.normalZ()),
                new MasterArchitectConstructionPolicy.WallAxes(
                        -axes.tangentX(),
                        -axes.tangentZ(),
                        axes.normalX(),
                        axes.normalZ()));
        BlockPos master = architect.blockPosition();
        for (MasterArchitectConstructionPolicy.WallAxes orientation : orientations) {
            BlockPos preferred = master.offset(
                    orientation.normalX() * 2,
                    0,
                    orientation.normalZ() * 2);
            for (BlockPos center : orderedConstructionAnchors(
                    level, preferred, 1)) {
                BuildPlan fallback = createCoverDenialWallAt(
                        level, master, center, orientation);
                if (fallback != null) {
                    FrozenDawn.LOGGER.info(
                            "Master Architect {} adapted opening fort to crowded Hearth terrain",
                            shortId(architect));
                    return fallback;
                }
            }
        }
        return null;
    }

    private BuildPlan createOpeningFortAt(
            ServerLevel level,
            BlockPos center,
            MasterArchitectConstructionPolicy.WallAxes axes) {
        BlockPos rearEscape = center.offset(
                -axes.normalX() * 2, 0, -axes.normalZ() * 2);
        if (!hasTwoBlockSpace(level, rearEscape)) {
            return null;
        }
        List<PlannedColumn> columns = new ArrayList<>();
        for (int index = 0;
                index < MasterArchitectConstructionPolicy.WALL_COLUMN_COUNT;
                index++) {
            int normalOffset =
                    MasterArchitectConstructionPolicy.columnNormalOffset(index);
            int tangentOffset =
                    MasterArchitectConstructionPolicy.columnTangentOffset(index);
            BlockPos column = center.offset(
                    axes.normalX() * normalOffset
                            + axes.tangentX() * tangentOffset,
                    0,
                    axes.normalZ() * normalOffset
                            + axes.tangentZ() * tangentOffset);
            BlockPos base = findWallBase(level, column, center.getY(), WALL_HEIGHT);
            if (base != null) {
                columns.add(new PlannedColumn(base, index));
            }
        }
        if (columns.size() < MasterArchitectConstructionPolicy.MIN_OPENING_COLUMNS) {
            return null;
        }
        PlannedColumn seamColumn = columns.stream()
                .min(Comparator.comparingInt(column ->
                        Math.abs(MasterArchitectConstructionPolicy
                                .columnNormalOffset(column.layoutIndex) - 2) * 4
                                + Math.abs(MasterArchitectConstructionPolicy
                                        .columnTangentOffset(column.layoutIndex))))
                .orElse(null);
        if (seamColumn == null) {
            return null;
        }
        List<BuildStep> steps = new ArrayList<>();
        for (PlannedColumn column : columns) {
            steps.add(verticalStep(
                    column.base,
                    WALL_HEIGHT,
                    column == seamColumn));
        }
        moveSeamStepLast(steps);
        return createPlan(
                level,
                ConstructionKind.OPENING_FORT,
                center,
                null,
                MasterArchitectConstructionPolicy.OPENING_LIFETIME_TICKS,
                steps);
    }

    private BuildPlan createCoverDenialWall(ServerLevel level, ServerPlayer target) {
        BlockPos cover = observedCoverPos != null
                ? observedCoverPos
                : target.blockPosition();
        Vec3 awayFromMaster = Vec3.atCenterOf(cover).subtract(architect.position());
        MasterArchitectConstructionPolicy.WallAxes axes =
                MasterArchitectConstructionPolicy.wallAxes(
                        awayFromMaster.x, awayFromMaster.z);
        BlockPos preferred = cover.offset(
                axes.normalX() * 2, 0, axes.normalZ() * 2);
        for (BlockPos center : orderedConstructionAnchors(level, preferred, 2)) {
            BuildPlan plan = createCoverDenialWallAt(level, cover, center, axes);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private BuildPlan createCoverDenialWallAt(
            ServerLevel level,
            BlockPos cover,
            BlockPos center,
            MasterArchitectConstructionPolicy.WallAxes axes) {
        int[] offsets = {-2, 2, -1, 1, 0};
        List<PlannedColumn> columns = new ArrayList<>();
        for (int index = 0; index < offsets.length; index++) {
            int offset = offsets[index];
            BlockPos column = center.offset(
                    axes.tangentX() * offset,
                    0,
                    axes.tangentZ() * offset);
            BlockPos base = findWallBase(level, column, cover.getY(), WALL_HEIGHT);
            if (base != null) {
                columns.add(new PlannedColumn(base, index));
            }
        }
        if (columns.size() < MasterArchitectConstructionPolicy.MIN_WALL_COLUMNS) {
            return null;
        }
        PlannedColumn seamColumn = columns.stream()
                .min(Comparator.comparingInt(
                        column -> Math.abs(offsets[column.layoutIndex])))
                .orElse(null);
        List<BuildStep> steps = new ArrayList<>();
        for (PlannedColumn column : columns) {
            steps.add(verticalStep(
                    column.base, WALL_HEIGHT, column == seamColumn));
        }
        moveSeamStepLast(steps);
        return createPlan(
                level,
                ConstructionKind.COVER_WALL,
                center,
                null,
                MasterArchitectConstructionPolicy.STRUCTURE_LIFETIME_TICKS,
                steps);
    }

    private BuildPlan createEnclosureTrap(ServerLevel level, ServerPlayer target) {
        BlockPos center = target.blockPosition();
        Vec3 awayFromMaster = target.position().subtract(architect.position());
        MasterArchitectConstructionPolicy.WallAxes axes =
                MasterArchitectConstructionPolicy.wallAxes(
                        awayFromMaster.x, awayFromMaster.z);
        int seamX = axes.normalX();
        int seamZ = axes.normalZ();
        BlockPos exit = center.offset(seamX * 2, 0, seamZ * 2);
        if (!hasTwoBlockSpace(level, exit)) {
            return null;
        }

        int[][] ring = {
                {-1, -1}, {-1, 0}, {-1, 1},
                {0, -1}, {0, 1},
                {1, -1}, {1, 0}, {1, 1}
        };
        List<BuildStep> steps = new ArrayList<>();
        BuildStep seamStep = null;
        for (int[] offset : ring) {
            BlockPos column = center.offset(offset[0], 0, offset[1]);
            BlockPos base = findWallBase(level, column, center.getY(), TRAP_HEIGHT);
            if (base == null) {
                continue;
            }
            boolean seam = offset[0] == seamX && offset[1] == seamZ;
            BuildStep step = verticalStep(base, TRAP_HEIGHT, seam);
            if (seam) {
                seamStep = step;
            } else {
                steps.add(step);
            }
        }
        if (seamStep == null) {
            return null;
        }
        steps.add(seamStep);
        return createPlan(
                level,
                ConstructionKind.ENCLOSURE,
                center,
                null,
                MasterArchitectConstructionPolicy.STRUCTURE_LIFETIME_TICKS,
                steps);
    }

    private BuildPlan createHeaterBurial(ServerLevel level, ServerPlayer target) {
        BlockPos heaterPos = findActivePlayerHeater(level, target);
        if (heaterPos == null) {
            return null;
        }

        List<BuildStep> steps = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos base = heaterPos.relative(direction);
            if (!level.getBlockState(base.below())
                    .isFaceSturdy(level, base.below(), Direction.UP)) {
                continue;
            }
            steps.add(verticalStep(base, 2, false));
        }
        steps.add(new BuildStep(
                List.of(new PlannedBlock(heaterPos.above(), true)),
                heaterPos.above()));
        return createPlan(
                level,
                ConstructionKind.HEATER_BURIAL,
                heaterPos,
                null,
                MasterArchitectConstructionPolicy.STRUCTURE_LIFETIME_TICKS,
                steps);
    }

    private BlockPos findActivePlayerHeater(
            ServerLevel level,
            ServerPlayer target) {
        PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(
                level.getServer());
        return HeaterRegistry.getHeaters(level).stream()
                .filter(level::hasChunkAt)
                .filter(tracker::isPlayerPlaced)
                .filter(pos -> level.getBlockEntity(pos)
                        instanceof ThermalHeaterBlockEntity heater && heater.isLit())
                .filter(pos -> architect.distanceToSqr(Vec3.atCenterOf(pos))
                        <= MasterArchitectConstructionPolicy.MAX_CAST_RANGE
                                * MasterArchitectConstructionPolicy.MAX_CAST_RANGE)
                .filter(pos -> constructions.stream().noneMatch(construction ->
                        construction.kind == ConstructionKind.HEATER_BURIAL
                                && construction.anchor.equals(pos)))
                .min(Comparator.comparingDouble(
                        pos -> target.distanceToSqr(Vec3.atCenterOf(pos))))
                .orElse(null);
    }

    private BuildPlan createVantagePlatform(ServerLevel level, ServerPlayer target) {
        Vec3 towardTarget = target.position().subtract(architect.position());
        MasterArchitectConstructionPolicy.WallAxes axes =
                MasterArchitectConstructionPolicy.wallAxes(
                        towardTarget.x, towardTarget.z);
        BlockPos preferred = architect.blockPosition().offset(
                -axes.normalX() * 4, 0, -axes.normalZ() * 4);
        for (BlockPos centerColumn : orderedConstructionAnchors(
                level, preferred, 2)) {
            BuildPlan plan = createVantagePlatformAt(
                    level, centerColumn, axes);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private BuildPlan createVantagePlatformAt(
            ServerLevel level,
            BlockPos centerColumn,
            MasterArchitectConstructionPolicy.WallAxes axes) {
        Integer baseY = findGroundY(level, centerColumn, architect.blockPosition().getY());
        if (baseY == null) {
            return null;
        }
        int platformY = baseY + 2;

        Map<Long, PlannedBlock> planned = new LinkedHashMap<>();
        for (int normal = -1; normal <= 1; normal++) {
            for (int tangent = -1; tangent <= 1; tangent++) {
                BlockPos floor = new BlockPos(
                        centerColumn.getX()
                                + axes.normalX() * normal
                                + axes.tangentX() * tangent,
                        platformY,
                        centerColumn.getZ()
                                + axes.normalZ() * normal
                                + axes.tangentZ() * tangent);
                putPlanned(planned, floor, normal == 0 && tangent == 0);
                if (Math.abs(normal) == 1 && Math.abs(tangent) == 1) {
                    BlockPos supportBase = floor.atY(baseY);
                    if (!level.getBlockState(supportBase.below())
                            .isFaceSturdy(
                                    level,
                                    supportBase.below(),
                                    Direction.UP)) {
                        return null;
                    }
                    putPlanned(planned, supportBase, false);
                    putPlanned(planned, floor.atY(baseY + 1), false);
                }
            }
        }

        BlockPos lowStep = centerColumn.offset(
                axes.normalX() * 2, 0, axes.normalZ() * 2).atY(baseY);
        BlockPos highStep = centerColumn.offset(
                axes.normalX(), 0, axes.normalZ()).atY(baseY + 1);
        if (!level.getBlockState(lowStep.below())
                .isFaceSturdy(level, lowStep.below(), Direction.UP)) {
            return null;
        }
        putPlanned(planned, lowStep, false);
        putPlanned(planned, highStep.below(), false);
        putPlanned(planned, highStep, false);

        BlockPos stand = centerColumn.atY(platformY + 1);
        if (!level.hasChunkAt(stand)
                || !level.getBlockState(stand).isAir()
                || !level.getBlockState(stand.above()).isAir()) {
            return null;
        }
        List<BuildStep> steps = groupColumns(planned.values());
        moveSeamStepLast(steps);
        return createPlan(
                level,
                ConstructionKind.VANTAGE,
                centerColumn.atY(baseY),
                stand,
                MasterArchitectConstructionPolicy.STRUCTURE_LIFETIME_TICKS,
                steps);
    }

    private BuildPlan createPlan(
            ServerLevel level,
            ConstructionKind kind,
            BlockPos anchor,
            BlockPos vantageStand,
        int lifetimeTicks,
            List<BuildStep> steps) {
        List<BuildStep> viableSteps = steps.stream()
                .filter(step -> step.blocks.stream().allMatch(
                        planned -> canPlaceAt(level, planned.pos)))
                .toList();
        int minimumSteps = switch (kind) {
            case OPENING_FORT -> MasterArchitectConstructionPolicy.MIN_OPENING_COLUMNS;
            case COVER_WALL -> MasterArchitectConstructionPolicy.MIN_WALL_COLUMNS;
            case ENCLOSURE -> MasterArchitectConstructionPolicy.MIN_ENCLOSURE_COLUMNS;
            case HEATER_BURIAL -> MasterArchitectConstructionPolicy.MIN_HEATER_COLUMNS;
            case VANTAGE -> 5;
        };
        boolean hasSeam = viableSteps.stream().anyMatch(BuildStep::hasSeam);
        if (!MasterArchitectConstructionPolicy.hasViableStructure(
                viableSteps.size(), hasSeam, minimumSteps)) {
            return null;
        }
        int plannedBlocks = viableSteps.stream()
                .mapToInt(step -> step.blocks.size())
                .sum();
        if (plannedBlocks > MasterArchitectConstructionPolicy.LIVE_BLOCK_BUDGET) {
            return null;
        }
        ConstructionInstance instance = new ConstructionInstance(
                nextConstructionId++,
                kind,
                anchor.immutable(),
                vantageStand == null ? null : vantageStand.immutable(),
                level.getGameTime() + lifetimeTicks);
        return new BuildPlan(instance, viableSteps);
    }

    private void makeRoomFor(ServerLevel level, int plannedBlocks) {
        while (!MasterArchitectConstructionPolicy.canReserve(
                liveBlockCount(), plannedBlocks)) {
            ConstructionInstance oldest = constructions.stream()
                    .filter(construction -> activePlan == null
                            || construction.id != activePlan.instance.id)
                    .findFirst()
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            removeConstruction(level, oldest, false, false);
        }
    }

    private void observeCover(
            ServerLevel level,
            ServerPlayer target,
            MasterArchitectCombatPhase phase) {
        if (target == null || phase != MasterArchitectCombatPhase.CONSTRUCTION) {
            return;
        }
        if (!architect.hasLineOfSight(target)) {
            BlockHitResult obstruction = level.clip(new ClipContext(
                    architect.getEyePosition(),
                    target.getEyePosition(),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    architect));
            if (obstruction.getType() == HitResult.Type.BLOCK
                    && isTrackedConstructionBlock(obstruction.getBlockPos())) {
                return;
            }
            observedCoverPos = obstruction.getType() == HitResult.Type.BLOCK
                    ? obstruction.getBlockPos().immutable()
                    : target.blockPosition().immutable();
            observedCoverAt = level.getGameTime();
        }
    }

    private void updateTargetMotion(
            ServerPlayer target,
            MasterArchitectCombatPhase phase) {
        if (target == null || phase != MasterArchitectCombatPhase.CONSTRUCTION) {
            stationaryTicks = 0;
            lastTargetPosition = target == null ? null : target.position();
            return;
        }
        Vec3 current = target.position();
        if (lastTargetPosition != null
                && current.distanceToSqr(lastTargetPosition) <= 0.04D) {
            stationaryTicks = Math.min(stationaryTicks + 1, 200);
        } else {
            stationaryTicks = 0;
        }
        lastTargetPosition = current;
    }

    boolean isTrackedConstructionBlock(BlockPos pos) {
        return constructions.stream().anyMatch(
                construction -> construction.blocks.contains(pos));
    }

    private boolean isIntactShelter(
            ServerLevel level,
            ConstructionInstance construction) {
        if (construction.breached
                || construction.blocks.isEmpty()
                || construction.seams.isEmpty()) {
            return false;
        }
        return construction.seams.stream().allMatch(pos ->
                level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.ICE));
    }

    private static void emitSoulStream(
            ServerLevel level,
            Vec3 start,
            Vec3 end,
            int phaseOffset) {
        for (int step = 1; step <= 5; step++) {
            double progress = (step + phaseOffset * 0.35D) / 6.0D;
            progress = Math.min(0.92D, progress);
            Vec3 point = start.lerp(end, progress);
            level.sendParticles(
                    ParticleTypes.SCULK_SOUL,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.025D,
                    0.025D,
                    0.025D,
                    0.005D);
        }
    }

    private List<BlockPos> orderedConstructionAnchors(
            ServerLevel level,
            BlockPos preferred,
            int radius) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos candidate = preferred.offset(x, 0, z);
                if (level.hasChunkAt(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(
                candidate -> -constructionAnchorScore(preferred, candidate)));
        return candidates;
    }

    private double constructionAnchorScore(
            BlockPos preferred,
            BlockPos candidate) {
        double preferencePenalty = preferred.distManhattan(candidate) * 3.0D;
        int nearest = constructions.stream()
                .flatMap(construction -> construction.blocks.stream())
                .mapToInt(candidate::distManhattan)
                .min()
                .orElse(Integer.MAX_VALUE);
        double connectionBonus = nearest <= 1
                ? 18.0D
                : nearest <= 3 ? 10.0D : nearest <= 5 ? 4.0D : 0.0D;
        return connectionBonus - preferencePenalty;
    }

    private void removeConstruction(
            ServerLevel level,
            ConstructionInstance construction,
            boolean dramatic,
            boolean leaveRubble) {
        if (!constructions.contains(construction)) {
            return;
        }
        int minimumY = construction.blocks.stream()
                .mapToInt(BlockPos::getY)
                .min()
                .orElse(Integer.MIN_VALUE);
        double nearestToMaster = construction.blocks.stream()
                .mapToDouble(pos -> architect.distanceToSqr(Vec3.atCenterOf(pos)))
                .min()
                .orElse(Double.MAX_VALUE);
        List<BlockPos> retainedUnloaded = new ArrayList<>();
        List<BlockPos> retainedSeams = new ArrayList<>();
        int index = 0;
        for (BlockPos pos : construction.blocks) {
            boolean seam = construction.seams.contains(pos);
            if (!level.hasChunkAt(pos)) {
                retainedUnloaded.add(pos);
                if (seam) {
                    retainedSeams.add(pos);
                }
                index++;
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!isConstructionBlock(state)) {
                index++;
                continue;
            }
            boolean rubble = leaveRubble
                    && MasterArchitectConstructionPolicy.shouldLeaveRubble(
                            index, pos.getY(), minimumY, seam);
            if (!rubble) {
                level.removeBlock(pos, false);
            }
            if (dramatic) {
                level.sendParticles(
                        ParticleTypes.SNOWFLAKE,
                        pos.getX() + 0.5D,
                        pos.getY() + 0.5D,
                        pos.getZ() + 0.5D,
                        7,
                        0.30D,
                        0.34D,
                        0.30D,
                        0.11D);
            }
            index++;
        }

        construction.blocks.clear();
        construction.blocks.addAll(retainedUnloaded);
        construction.seams.clear();
        construction.seams.addAll(retainedSeams);
        if (construction.blocks.isEmpty()) {
            constructions.remove(construction);
        } else {
            construction.expiresAt = Math.min(
                    construction.expiresAt, level.getGameTime() + 100L);
        }
        if (activePlan != null && activePlan.instance.id == construction.id) {
            activePlan = null;
        }
        if (shelteringConstructionId == construction.id) {
            shelteringConstructionId = -1L;
        }
        if (construction.vantageStand != null
                && construction.vantageStand.equals(vantageTarget)) {
            vantageTarget = null;
            vantageSeekUntil = -1L;
        }
        if (dramatic) {
            level.playSound(
                    null,
                    construction.anchor,
                    SoundEvents.GLASS_BREAK,
                    architect.getSoundSource(),
                    1.8F,
                    0.48F);
            if (MasterArchitectConstructionPolicy.shouldStaggerMaster(
                    nearestToMaster)) {
                staggerTicks = Math.max(
                        staggerTicks,
                        MasterArchitectConstructionPolicy.SEAM_STAGGER_TICKS);
                level.sendParticles(
                        ParticleTypes.SCULK_SOUL,
                        architect.getX(),
                        architect.getY() + 1.0D,
                        architect.getZ(),
                        22,
                        0.45D,
                        0.65D,
                        0.45D,
                        0.10D);
            }
            FrozenDawn.LOGGER.info(
                    "Master Architect {} lost Construction War {} seam; rubble={} stagger={} live={}/{}",
                    shortId(architect),
                    construction.kind.serializedName,
                    leaveRubble,
                    staggerTicks > 0,
                    liveBlockCount(),
                    MasterArchitectConstructionPolicy.LIVE_BLOCK_BUDGET);
        }
    }

    private void initializeVfx(ServerLevel level) {
        if (vfxInitialized) {
            return;
        }
        String tag = vfxTag();
        level.getEntities(
                        (Entity) null,
                        architect.getBoundingBox().inflate(96.0D),
                        entity -> entity.getTags().contains(tag))
                .forEach(Entity::discard);
        vfxInitialized = true;
    }

    private void tickAmbientFragments(
            ServerLevel level,
            MasterArchitectCombatPhase phase) {
        orbitingFragments.removeIf(Entity::isRemoved);
        if (phase != MasterArchitectCombatPhase.CONSTRUCTION
                || !architect.isAlive()) {
            clearOrbitingFragments();
            return;
        }

        while (orbitingFragments.size()
                < MasterArchitectConstructionPolicy.ORBITING_FRAGMENT_COUNT) {
            int index = orbitingFragments.size();
            Display.BlockDisplay fragment = createDisplayFragment(
                    level,
                    index == MasterArchitectConstructionPolicy
                                    .ORBITING_FRAGMENT_COUNT - 1
                            ? Blocks.ICE.defaultBlockState()
                            : Blocks.PACKED_ICE.defaultBlockState(),
                    architect.position().add(0.0D, 1.2D, 0.0D),
                    0.28F,
                    index == MasterArchitectConstructionPolicy
                            .ORBITING_FRAGMENT_COUNT - 1);
            if (fragment == null) {
                break;
            }
            orbitingFragments.add(fragment);
        }

        double time = level.getGameTime() * 0.085D;
        for (int index = 0; index < orbitingFragments.size(); index++) {
            Display.BlockDisplay fragment = orbitingFragments.get(index);
            double angle = time
                    + index * (Math.PI * 2.0D / orbitingFragments.size());
            double radius = 1.30D + (index % 2) * 0.22D;
            double y = architect.getY() + 1.0D
                    + index * 0.27D
                    + Math.sin(time * 1.35D + index) * 0.18D;
            fragment.setPos(
                    architect.getX() + Math.cos(angle) * radius,
                    y,
                    architect.getZ() + Math.sin(angle) * radius);
            fragment.setYRot((float) Math.toDegrees(-angle));
        }
    }

    private void beginTravelingFragments(ServerLevel level, BuildPlan plan) {
        clearTravelingFragments(level, plan);
        List<PlannedBlock> planned = plan.steps.stream()
                .flatMap(step -> step.blocks.stream())
                .toList();
        int count = Math.min(
                MasterArchitectConstructionPolicy.MAX_TRAVELING_FRAGMENTS,
                planned.size());
        for (int index = 0; index < count; index++) {
            PlannedBlock target = planned.get(
                    Math.min(planned.size() - 1, index * planned.size() / count));
            BlockPos sourceFloor = findFragmentSource(
                    level, plan.instance.anchor, index, count);
            Vec3 source = Vec3.atCenterOf(sourceFloor).add(0.0D, 0.65D, 0.0D);
            Vec3 destination = Vec3.atCenterOf(target.pos);
            Display.BlockDisplay display = createDisplayFragment(
                    level,
                    target.seam
                            ? Blocks.ICE.defaultBlockState()
                            : Blocks.PACKED_ICE.defaultBlockState(),
                    source,
                    target.seam ? 0.46F : 0.38F,
                    target.seam);
            if (display == null) {
                continue;
            }
            plan.fragments.add(new ConstructionFragment(
                    display,
                    source,
                    destination,
                    sourceFloor,
                    Math.floorMod(index, 3),
                    -1_000_000 - architect.getId() * 32 - index));
        }
    }

    private void updateTravelingFragments(ServerLevel level, BuildPlan plan) {
        int totalTicks = MasterArchitectConstructionPolicy.CHOREOGRAPHY_TICKS;
        for (int index = 0; index < plan.fragments.size(); index++) {
            ConstructionFragment fragment = plan.fragments.get(index);
            if (fragment.display.isRemoved()) {
                continue;
            }
            int availableTicks = Math.max(1, totalTicks - fragment.delay);
            double rawProgress = Mth.clamp(
                    (plan.choreographyTicks - fragment.delay)
                            / (double) availableTicks,
                    0.0D,
                    1.0D);
            double progress = rawProgress * rawProgress
                    * (3.0D - 2.0D * rawProgress);
            Vec3 midpoint = fragment.source.add(fragment.destination).scale(0.5D)
                    .add(0.0D, 2.15D + index % 3 * 0.35D, 0.0D);
            Vec3 point = quadraticBezier(
                    fragment.source, midpoint, fragment.destination, progress);
            fragment.display.setPos(point);
            fragment.display.setYRot(
                    fragment.display.getYRot() + 24.0F + index * 1.7F);

            int crackStage = Math.min(9, plan.choreographyTicks * 2);
            level.destroyBlockProgress(
                    fragment.breakerId, fragment.sourceFloor, crackStage);
            if ((plan.choreographyTicks + index) % 2 == 0) {
                level.sendParticles(
                        new BlockParticleOption(
                                ParticleTypes.BLOCK,
                                fragment.display.blockRenderState() == null
                                        ? Blocks.PACKED_ICE.defaultBlockState()
                                        : fragment.display.blockRenderState()
                                                .blockState()),
                        point.x,
                        point.y,
                        point.z,
                        2,
                        0.08D,
                        0.08D,
                        0.08D,
                        0.025D);
            }
        }
    }

    private void clearTravelingFragments(ServerLevel level, BuildPlan plan) {
        for (ConstructionFragment fragment : plan.fragments) {
            level.destroyBlockProgress(
                    fragment.breakerId, fragment.sourceFloor, -1);
            fragment.display.discard();
        }
        plan.fragments.clear();
    }

    private void clearOrbitingFragments() {
        orbitingFragments.forEach(Entity::discard);
        orbitingFragments.clear();
    }

    private void clearAllVfx(ServerLevel level) {
        clearOrbitingFragments();
        if (activePlan != null) {
            clearTravelingFragments(level, activePlan);
        }
        String tag = vfxTag();
        level.getEntities(
                        (Entity) null,
                        architect.getBoundingBox().inflate(96.0D),
                        entity -> entity.getTags().contains(tag))
                .forEach(Entity::discard);
    }

    private Display.BlockDisplay createDisplayFragment(
            ServerLevel level,
            BlockState state,
            Vec3 position,
            float scale,
            boolean glowing) {
        Display.BlockDisplay display = new Display.BlockDisplay(
                EntityType.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) (Object) display)
                .frozendawn$setBlockState(state);
        ((DisplayAccessor) (Object) display)
                .frozendawn$setPosRotInterpolationDuration(2);
        float half = scale * 0.5F;
        ((DisplayAccessor) (Object) display).frozendawn$setTransformation(
                new Transformation(
                        new Vector3f(-half, -half, -half),
                        new Quaternionf(),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()));
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setGlowingTag(glowing);
        display.addTag(vfxTag());
        display.setPos(position);
        if (!level.addFreshEntity(display)) {
            display.discard();
            return null;
        }
        return display;
    }

    private BlockPos findFragmentSource(
            ServerLevel level,
            BlockPos anchor,
            int index,
            int count) {
        double angle = index * (Math.PI * 2.0D / Math.max(1, count))
                + architect.getId() * 0.17D;
        int radius = 4 + index % 4;
        BlockPos column = anchor.offset(
                Mth.floor(Math.cos(angle) * radius),
                0,
                Mth.floor(Math.sin(angle) * radius));
        Integer airY = findGroundY(level, column, anchor.getY());
        if (airY != null) {
            return column.atY(airY - 1);
        }
        return architect.blockPosition().below();
    }

    private static Vec3 quadraticBezier(
            Vec3 start,
            Vec3 control,
            Vec3 end,
            double progress) {
        double inverse = 1.0D - progress;
        return start.scale(inverse * inverse)
                .add(control.scale(2.0D * inverse * progress))
                .add(end.scale(progress * progress));
    }

    private String vfxTag() {
        return CONSTRUCTION_VFX_TAG + "_" + shortId(architect);
    }

    private void emitBuildStart(ServerLevel level, ConstructionInstance instance) {
        level.playSound(
                null,
                architect.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(),
                architect.getSoundSource(),
                0.85F,
                0.42F);
        Vec3 from = architect.position().add(0.0D, 0.08D, 0.0D);
        Vec3 to = Vec3.atBottomCenterOf(instance.anchor);
        Vec3 delta = to.subtract(from);
        int steps = Math.max(4, Math.min(16, (int) Math.ceil(delta.length() * 2.0D)));
        for (int step = 1; step <= steps; step++) {
            Vec3 point = from.add(delta.scale(step / (double) steps));
            level.sendParticles(
                    step % 3 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SNOWFLAKE,
                    point.x,
                    point.y,
                    point.z,
                    2,
                    0.10D,
                    0.03D,
                    0.10D,
                    0.035D);
        }
        for (int index = 0; index < 28; index++) {
            double angle = index * (Math.PI * 2.0D / 28.0D);
            double radius = 0.75D + index % 4 * 0.55D;
            level.sendParticles(
                    index % 5 == 0
                            ? ParticleTypes.SCULK_SOUL
                            : ParticleTypes.SNOWFLAKE,
                    instance.anchor.getX() + 0.5D + Math.cos(angle) * radius,
                    instance.anchor.getY() + 0.08D,
                    instance.anchor.getZ() + 0.5D + Math.sin(angle) * radius,
                    1,
                    0.03D,
                    0.01D,
                    0.03D,
                    0.018D);
        }
    }

    private void emitPlacedBlock(ServerLevel level, PlannedBlock planned) {
        level.sendParticles(
                ParticleTypes.SNOWFLAKE,
                planned.pos.getX() + 0.5D,
                planned.pos.getY() + 0.5D,
                planned.pos.getZ() + 0.5D,
                7,
                0.34D,
                0.38D,
                0.34D,
                0.09D);
        level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                planned.pos.getX() + 0.5D,
                planned.pos.getY() + 0.5D,
                planned.pos.getZ() + 0.5D,
                planned.seam ? 5 : 2,
                0.22D,
                0.28D,
                0.22D,
                0.025D);
        level.sendParticles(
                new BlockParticleOption(
                        ParticleTypes.BLOCK,
                        planned.seam
                                ? Blocks.ICE.defaultBlockState()
                                : Blocks.PACKED_ICE.defaultBlockState()),
                planned.pos.getX() + 0.5D,
                planned.pos.getY() + 0.5D,
                planned.pos.getZ() + 0.5D,
                planned.seam ? 7 : 4,
                0.24D,
                0.27D,
                0.24D,
                0.08D);
    }

    private BuildStep verticalStep(BlockPos base, int height, boolean seam) {
        List<PlannedBlock> blocks = new ArrayList<>();
        for (int offset = 0; offset < height; offset++) {
            blocks.add(new PlannedBlock(base.above(offset), seam));
        }
        return new BuildStep(List.copyOf(blocks), base);
    }

    private BlockPos findWallBase(
            ServerLevel level,
            BlockPos column,
            int referenceY,
            int height) {
        for (int y = referenceY + 2; y >= referenceY - 4; y--) {
            BlockPos base = new BlockPos(column.getX(), y, column.getZ());
            BlockPos floor = base.below();
            if (!level.hasChunkAt(base)
                    || !level.getBlockState(floor)
                            .isFaceSturdy(level, floor, Direction.UP)) {
                continue;
            }
            boolean clear = true;
            for (int offset = 0; offset < height; offset++) {
                if (!canPlaceAt(level, base.above(offset))) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return base;
            }
        }
        return null;
    }

    private Integer findGroundY(ServerLevel level, BlockPos column, int referenceY) {
        for (int y = referenceY + 2; y >= referenceY - 4; y--) {
            BlockPos base = new BlockPos(column.getX(), y, column.getZ());
            BlockPos floor = base.below();
            if (level.hasChunkAt(base)
                    && level.getBlockState(floor)
                            .isFaceSturdy(level, floor, Direction.UP)) {
                return y;
            }
        }
        return null;
    }

    private boolean canPlaceAt(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }
        return level.getEntities(null, new AABB(pos)).isEmpty();
    }

    private boolean hasTwoBlockSpace(ServerLevel level, BlockPos feet) {
        return level.hasChunkAt(feet)
                && level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && level.getBlockState(feet.below())
                        .isFaceSturdy(level, feet.below(), Direction.UP);
    }

    private int liveBlockCount() {
        return constructions.stream()
                .mapToInt(construction -> construction.blocks.size())
                .sum();
    }

    private ConstructionInstance readConstruction(CompoundTag saved) {
        long id = Math.max(1L, saved.getLong("Id"));
        ConstructionKind kind = ConstructionKind.fromSerializedName(
                saved.getString("Kind"));
        BlockPos anchor = BlockPos.of(saved.getLong("Anchor"));
        BlockPos vantage = saved.contains("VantageStand")
                ? BlockPos.of(saved.getLong("VantageStand"))
                : null;
        ConstructionInstance construction = new ConstructionInstance(
                id,
                kind,
                anchor,
                vantage,
                saved.getLong("ExpiresAt"));
        construction.breached = saved.getBoolean("Breached");
        int remainingBudget = MasterArchitectConstructionPolicy.LIVE_BLOCK_BUDGET
                - liveBlockCount();
        for (long packed : saved.getLongArray("Blocks")) {
            if (construction.blocks.size() >= remainingBudget) {
                break;
            }
            construction.blocks.add(BlockPos.of(packed));
        }
        for (long packed : saved.getLongArray("Seams")) {
            BlockPos seam = BlockPos.of(packed);
            if (construction.blocks.contains(seam)) {
                construction.seams.add(seam);
            }
        }
        return construction.blocks.isEmpty() ? null : construction;
    }

    private void migrateOpeningFort(CompoundTag tag) {
        long[] blocks = tag.getLongArray("MasterConstructionBlocks");
        if (blocks.length == 0) {
            return;
        }
        ConstructionInstance legacy = new ConstructionInstance(
                nextConstructionId++,
                ConstructionKind.OPENING_FORT,
                architect.blockPosition(),
                null,
                tag.getLong("MasterConstructionExpiresAt"));
        for (long packed : blocks) {
            if (legacy.blocks.size()
                    >= MasterArchitectConstructionPolicy.LIVE_BLOCK_BUDGET) {
                break;
            }
            legacy.blocks.add(BlockPos.of(packed));
        }
        for (long packed : tag.getLongArray("MasterConstructionSeams")) {
            BlockPos seam = BlockPos.of(packed);
            if (legacy.blocks.contains(seam)) {
                legacy.seams.add(seam);
            }
        }
        constructions.add(legacy);
        openingFortUsed = true;
    }

    private static List<BuildStep> groupColumns(Iterable<PlannedBlock> blocks) {
        Map<Long, List<PlannedBlock>> columns = new LinkedHashMap<>();
        Map<Long, BlockPos> focuses = new LinkedHashMap<>();
        for (PlannedBlock block : blocks) {
            long key = BlockPos.asLong(block.pos.getX(), 0, block.pos.getZ());
            columns.computeIfAbsent(key, ignored -> new ArrayList<>()).add(block);
            focuses.putIfAbsent(key, block.pos);
        }
        List<BuildStep> steps = new ArrayList<>();
        columns.forEach((key, column) -> {
            column.sort(Comparator.comparingInt(planned -> planned.pos.getY()));
            steps.add(new BuildStep(List.copyOf(column), focuses.get(key)));
        });
        return steps;
    }

    private static void moveSeamStepLast(List<BuildStep> steps) {
        BuildStep seam = steps.stream()
                .filter(BuildStep::hasSeam)
                .findFirst()
                .orElse(null);
        if (seam != null) {
            steps.remove(seam);
            steps.add(seam);
        }
    }

    private static void putPlanned(
            Map<Long, PlannedBlock> planned,
            BlockPos pos,
            boolean seam) {
        planned.merge(
                pos.asLong(),
                new PlannedBlock(pos.immutable(), seam),
                (existing, added) -> new PlannedBlock(
                        existing.pos, existing.seam || added.seam));
    }

    private static boolean isConstructionBlock(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.ICE);
    }

    private static String shortId(ArchitectEntity architect) {
        return architect.getUUID().toString().substring(0, 8);
    }

    private enum ConstructionKind {
        OPENING_FORT("opening_fort"),
        COVER_WALL("cover_wall"),
        VANTAGE("vantage"),
        ENCLOSURE("enclosure"),
        HEATER_BURIAL("heater_burial");

        private final String serializedName;

        ConstructionKind(String serializedName) {
            this.serializedName = serializedName;
        }

        private boolean isShelterWall() {
            return this == OPENING_FORT || this == COVER_WALL;
        }

        private static ConstructionKind fromSerializedName(String name) {
            if (name != null) {
                String normalized = name.toLowerCase(Locale.ROOT);
                for (ConstructionKind kind : values()) {
                    if (kind.serializedName.equals(normalized)) {
                        return kind;
                    }
                }
            }
            return COVER_WALL;
        }
    }

    private static final class ConstructionInstance {
        private final long id;
        private final ConstructionKind kind;
        private final BlockPos anchor;
        private final BlockPos vantageStand;
        private final List<BlockPos> blocks = new ArrayList<>();
        private final List<BlockPos> seams = new ArrayList<>();
        private long expiresAt;
        private boolean breached;

        private ConstructionInstance(
                long id,
                ConstructionKind kind,
                BlockPos anchor,
                BlockPos vantageStand,
                long expiresAt) {
            this.id = id;
            this.kind = kind;
            this.anchor = anchor;
            this.vantageStand = vantageStand;
            this.expiresAt = expiresAt;
        }
    }

    private static final class BuildPlan {
        private final ConstructionInstance instance;
        private final List<BuildStep> steps;
        private final List<ConstructionFragment> fragments = new ArrayList<>();
        private int cursor;
        private int choreographyTicks;

        private BuildPlan(
                ConstructionInstance instance,
                List<BuildStep> steps) {
            this.instance = instance;
            this.steps = steps;
        }

        private int plannedBlockCount() {
            return steps.stream().mapToInt(step -> step.blocks.size()).sum();
        }
    }

    private static final class ConstructionFragment {
        private final Display.BlockDisplay display;
        private final Vec3 source;
        private final Vec3 destination;
        private final BlockPos sourceFloor;
        private final int delay;
        private final int breakerId;

        private ConstructionFragment(
                Display.BlockDisplay display,
                Vec3 source,
                Vec3 destination,
                BlockPos sourceFloor,
                int delay,
                int breakerId) {
            this.display = display;
            this.source = source;
            this.destination = destination;
            this.sourceFloor = sourceFloor;
            this.delay = delay;
            this.breakerId = breakerId;
        }
    }

    private record PlannedBlock(BlockPos pos, boolean seam) {
    }

    private record PlannedColumn(BlockPos base, int layoutIndex) {
    }

    private record BuildStep(List<PlannedBlock> blocks, BlockPos focus) {
        private boolean hasSeam() {
            return blocks.stream().anyMatch(PlannedBlock::seam);
        }
    }
}
