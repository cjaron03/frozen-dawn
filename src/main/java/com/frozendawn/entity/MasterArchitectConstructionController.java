package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.homo.MasterArchitectCombatPhase;
import com.frozendawn.homo.MasterArchitectConstructionPolicy;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    private final ArchitectEntity architect;
    private final List<ConstructionInstance> constructions = new ArrayList<>();

    private BuildPlan activePlan;
    private BlockPos observedCoverPos;
    private long observedCoverAt = Long.MIN_VALUE;
    private BlockPos vantageTarget;
    private long vantageSeekUntil = -1L;
    private int cooldown;
    private int staggerTicks;
    private int behaviorCursor;
    private long nextConstructionId = 1L;
    private boolean openingFortUsed;

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
        } else {
            plan = selectOngoingPlan(level, target);
        }
        if (plan == null) {
            return false;
        }

        constructions.add(plan.instance);
        activePlan = plan;
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
        if (activePlan.cursor >= activePlan.steps.size()) {
            return true;
        }

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
            level.setBlock(planned.pos, state, Block.UPDATE_ALL);
            activePlan.instance.blocks.add(planned.pos.immutable());
            if (planned.seam) {
                activePlan.instance.seams.add(planned.pos.immutable());
            }
            emitPlacedBlock(level, planned);
        }
        level.playSound(
                null,
                step.focus,
                SoundEvents.GLASS_PLACE,
                architect.getSoundSource(),
                1.15F,
                step.hasSeam() ? 1.35F : 0.62F);
        return activePlan.cursor >= activePlan.steps.size();
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

    private BuildPlan selectOngoingPlan(ServerLevel level, ServerPlayer target) {
        if (observedCoverPos != null
                && level.getGameTime() - observedCoverAt
                        <= MasterArchitectConstructionPolicy.COVER_MEMORY_TICKS) {
            BuildPlan cover = createCoverDenialWall(level, target);
            observedCoverPos = null;
            if (cover != null) {
                return cover;
            }
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            int behavior = Math.floorMod(behaviorCursor++, 3);
            BuildPlan plan = switch (behavior) {
                case 0 -> createVantagePlatform(level, target);
                case 1 -> createEnclosureTrap(level, target);
                default -> createHeaterBurial(level, target);
            };
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
        BlockPos center = architect.blockPosition();
        BlockPos rearEscape = center.offset(
                -axes.normalX() * 2, 0, -axes.normalZ() * 2);
        if (!hasTwoBlockSpace(level, rearEscape)) {
            return null;
        }
        List<BuildStep> steps = new ArrayList<>();
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
            if (base == null) {
                return null;
            }
            steps.add(verticalStep(
                    base,
                    WALL_HEIGHT,
                    MasterArchitectConstructionPolicy.isWeakSeamColumn(index)));
        }
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
        BlockPos center = cover.offset(axes.normalX() * 2, 0, axes.normalZ() * 2);
        int[] offsets = {-2, 2, -1, 1, 0};
        List<BuildStep> steps = new ArrayList<>();
        for (int index = 0; index < offsets.length; index++) {
            int offset = offsets[index];
            BlockPos column = center.offset(
                    axes.tangentX() * offset,
                    0,
                    axes.tangentZ() * offset);
            BlockPos base = findWallBase(level, column, cover.getY(), WALL_HEIGHT);
            if (base == null) {
                return null;
            }
            steps.add(verticalStep(base, WALL_HEIGHT, index == offsets.length - 1));
        }
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
                return null;
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
        PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(
                level.getServer());
        BlockPos heaterPos = HeaterRegistry.getHeaters(level).stream()
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
        if (heaterPos == null) {
            return null;
        }

        List<BuildStep> steps = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos base = heaterPos.relative(direction);
            if (!level.getBlockState(base.below())
                    .isFaceSturdy(level, base.below(), Direction.UP)) {
                return null;
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

    private BuildPlan createVantagePlatform(ServerLevel level, ServerPlayer target) {
        Vec3 towardTarget = target.position().subtract(architect.position());
        MasterArchitectConstructionPolicy.WallAxes axes =
                MasterArchitectConstructionPolicy.wallAxes(
                        towardTarget.x, towardTarget.z);
        BlockPos centerColumn = architect.blockPosition().offset(
                -axes.normalX() * 4, 0, -axes.normalZ() * 4);
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
        int plannedBlocks = steps.stream().mapToInt(step -> step.blocks.size()).sum();
        if (!MasterArchitectConstructionPolicy.canReserve(
                liveBlockCount(), plannedBlocks)) {
            return null;
        }
        for (BuildStep step : steps) {
            for (PlannedBlock planned : step.blocks) {
                if (!canPlaceAt(level, planned.pos)) {
                    return null;
                }
            }
        }
        ConstructionInstance instance = new ConstructionInstance(
                nextConstructionId++,
                kind,
                anchor.immutable(),
                vantageStand == null ? null : vantageStand.immutable(),
                level.getGameTime() + lifetimeTicks);
        return new BuildPlan(instance, List.copyOf(steps));
    }

    private void observeCover(
            ServerLevel level,
            ServerPlayer target,
            MasterArchitectCombatPhase phase) {
        if (target == null || phase != MasterArchitectCombatPhase.CONSTRUCTION) {
            return;
        }
        if (!architect.hasLineOfSight(target)) {
            observedCoverPos = target.blockPosition().immutable();
            observedCoverAt = level.getGameTime();
        }
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
    }

    private void emitPlacedBlock(ServerLevel level, PlannedBlock planned) {
        level.sendParticles(
                ParticleTypes.SNOWFLAKE,
                planned.pos.getX() + 0.5D,
                planned.pos.getY() + 0.5D,
                planned.pos.getZ() + 0.5D,
                12,
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
        private int cursor;

        private BuildPlan(
                ConstructionInstance instance,
                List<BuildStep> steps) {
            this.instance = instance;
            this.steps = steps;
        }
    }

    private record PlannedBlock(BlockPos pos, boolean seam) {
    }

    private record BuildStep(List<PlannedBlock> blocks, BlockPos focus) {
        private boolean hasSeam() {
            return blocks.stream().anyMatch(PlannedBlock::seam);
        }
    }
}
