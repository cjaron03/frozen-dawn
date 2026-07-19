package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.MasterArchitectCombatPhase;
import com.frozendawn.homo.MasterArchitectConstructionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Owns temporary, bounded Phase 2 architecture for the Master Architect only. */
final class MasterArchitectConstructionController {
    private final ArchitectEntity architect;
    private final List<PlannedColumn> wallPlan = new ArrayList<>();
    private final List<BlockPos> activeBlocks = new ArrayList<>();
    private final List<BlockPos> seamBlocks = new ArrayList<>();

    private int cooldown;
    private long expiresAt = -1L;

    MasterArchitectConstructionController(ArchitectEntity architect) {
        this.architect = architect;
    }

    void tick(ServerLevel level) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (activeBlocks.isEmpty()) {
            expiresAt = -1L;
            return;
        }
        if (expiresAt >= 0L && level.getGameTime() >= expiresAt) {
            removeOwnedBlocks(level, false);
            return;
        }
        if (!wallPlan.isEmpty()) {
            return;
        }

        int intactSeams = 0;
        for (BlockPos seam : seamBlocks) {
            if (!level.hasChunkAt(seam)) {
                intactSeams++;
            } else if (level.getBlockState(seam).is(Blocks.ICE)) {
                intactSeams++;
            }
        }
        if (seamBlocks.isEmpty()
                || MasterArchitectConstructionPolicy.shouldCollapseForMissingSeam(
                        seamBlocks.size(), intactSeams)) {
            collapse(level);
        }
    }

    boolean tryBeginWall(
            ServerLevel level,
            ServerPlayer target,
            MasterArchitectCombatPhase phase) {
        if (!MasterArchitectConstructionPolicy.canStartWall(
                phase,
                cooldown,
                !activeBlocks.isEmpty() || !wallPlan.isEmpty(),
                architect.distanceToSqr(target),
                architect.hasLineOfSight(target))) {
            return false;
        }

        buildWallPlan(level, target);
        if (wallPlan.size() != MasterArchitectConstructionPolicy.WALL_COLUMN_COUNT
                || wallPlan.stream().noneMatch(PlannedColumn::weakSeam)) {
            wallPlan.clear();
            return false;
        }
        cooldown = MasterArchitectConstructionPolicy.WALL_COOLDOWN_MIN
                + architect.nextRandomInt(
                        MasterArchitectConstructionPolicy.WALL_COOLDOWN_VARIANCE + 1);
        expiresAt = level.getGameTime()
                + MasterArchitectConstructionPolicy.WALL_LIFETIME_TICKS;
        FrozenDawn.LOGGER.info(
                "Master Architect {} began Construction War enclosure columns={}",
                shortId(architect), wallPlan.size());
        return true;
    }

    void placeColumnForTick(ServerLevel level, int actionTicks) {
        int index = MasterArchitectConstructionPolicy.columnIndexAtTick(actionTicks);
        if (index < 0 || index >= wallPlan.size()) {
            return;
        }
        PlannedColumn column = wallPlan.get(index);
        BlockState state = column.weakSeam()
                ? Blocks.ICE.defaultBlockState()
                : Blocks.PACKED_ICE.defaultBlockState();
        for (int height = 0;
                height < MasterArchitectConstructionPolicy.WALL_HEIGHT;
                height++) {
            if (activeBlocks.size()
                    >= MasterArchitectConstructionPolicy.MAX_ACTIVE_BLOCKS) {
                break;
            }
            BlockPos pos = column.base().above(height);
            if (!canPlaceAt(level, pos)) {
                continue;
            }
            level.setBlock(pos, state, Block.UPDATE_ALL);
            activeBlocks.add(pos.immutable());
            if (column.weakSeam()) {
                seamBlocks.add(pos.immutable());
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    12, 0.34D, 0.38D, 0.34D, 0.09D);
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    column.weakSeam() ? 5 : 2,
                    0.22D, 0.28D, 0.22D, 0.025D);
        }
        level.playSound(null, column.base(), SoundEvents.GLASS_PLACE,
                architect.getSoundSource(), 1.1F,
                column.weakSeam() ? 1.35F : 0.62F);
    }

    void finishWall(ServerLevel level) {
        wallPlan.clear();
        if (activeBlocks.isEmpty() || seamBlocks.isEmpty()) {
            removeOwnedBlocks(level, false);
            return;
        }
        FrozenDawn.LOGGER.info(
                "Master Architect {} completed Construction War wall blocks={} seam={}",
                shortId(architect), activeBlocks.size(), seamBlocks.size());
    }

    void cancelCast(ServerLevel level) {
        if (wallPlan.isEmpty()) {
            return;
        }
        wallPlan.clear();
        removeOwnedBlocks(level, false);
    }

    void onDeath(ServerLevel level) {
        wallPlan.clear();
        removeOwnedBlocks(level, false);
    }

    void addSaveData(CompoundTag tag) {
        tag.putInt("MasterConstructionCooldown", cooldown);
        tag.putLong("MasterConstructionExpiresAt", expiresAt);
        tag.putLongArray("MasterConstructionBlocks",
                activeBlocks.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("MasterConstructionSeams",
                seamBlocks.stream().mapToLong(BlockPos::asLong).toArray());
    }

    void readSaveData(CompoundTag tag) {
        cooldown = Math.max(0, tag.getInt("MasterConstructionCooldown"));
        expiresAt = tag.contains("MasterConstructionExpiresAt")
                ? tag.getLong("MasterConstructionExpiresAt")
                : -1L;
        wallPlan.clear();
        activeBlocks.clear();
        seamBlocks.clear();
        for (long packed : tag.getLongArray("MasterConstructionBlocks")) {
            if (activeBlocks.size()
                    >= MasterArchitectConstructionPolicy.MAX_ACTIVE_BLOCKS) {
                break;
            }
            activeBlocks.add(BlockPos.of(packed));
        }
        for (long packed : tag.getLongArray("MasterConstructionSeams")) {
            if (seamBlocks.size() >= MasterArchitectConstructionPolicy.WALL_HEIGHT) {
                break;
            }
            seamBlocks.add(BlockPos.of(packed));
        }
    }

    private void buildWallPlan(ServerLevel level, ServerPlayer target) {
        wallPlan.clear();
        Vec3 towardTarget = target.position().subtract(architect.position());
        MasterArchitectConstructionPolicy.WallAxes axes =
                MasterArchitectConstructionPolicy.wallAxes(
                        towardTarget.x, towardTarget.z);
        BlockPos center = architect.blockPosition();

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
            BlockPos base = findWallBase(level, column, architect.blockPosition().getY());
            if (base == null) {
                wallPlan.clear();
                return;
            }
            wallPlan.add(new PlannedColumn(
                    base,
                    MasterArchitectConstructionPolicy.isWeakSeamColumn(index)));
        }
    }

    private BlockPos findWallBase(ServerLevel level, BlockPos column, int targetY) {
        for (int y = targetY + 2; y >= targetY - 4; y--) {
            BlockPos floor = new BlockPos(column.getX(), y - 1, column.getZ());
            BlockPos base = floor.above();
            if (!level.hasChunkAt(base)
                    || !level.getBlockState(floor)
                            .isFaceSturdy(level, floor, Direction.UP)) {
                continue;
            }
            boolean clear = true;
            for (int height = 0;
                    height < MasterArchitectConstructionPolicy.WALL_HEIGHT;
                    height++) {
                if (!canPlaceAt(level, base.above(height))) {
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

    private void collapse(ServerLevel level) {
        FrozenDawn.LOGGER.info(
                "Master Architect {} Construction War seam failed; collapsing {} blocks",
                shortId(architect), activeBlocks.size());
        removeOwnedBlocks(level, true);
    }

    private void removeOwnedBlocks(ServerLevel level, boolean dramatic) {
        List<BlockPos> unloaded = new ArrayList<>();
        for (BlockPos pos : activeBlocks) {
            if (!level.hasChunkAt(pos)) {
                unloaded.add(pos);
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.PACKED_ICE) && !state.is(Blocks.ICE)) {
                continue;
            }
            level.removeBlock(pos, false);
            if (dramatic) {
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        7, 0.30D, 0.34D, 0.30D, 0.11D);
            }
        }
        if (dramatic && !activeBlocks.isEmpty()) {
            level.playSound(null, architect.blockPosition(), SoundEvents.GLASS_BREAK,
                    architect.getSoundSource(), 1.7F, 0.48F);
        }
        activeBlocks.clear();
        activeBlocks.addAll(unloaded);
        seamBlocks.removeIf(pos -> !unloaded.contains(pos));
        if (activeBlocks.isEmpty()) {
            expiresAt = -1L;
        }
    }

    private static String shortId(ArchitectEntity architect) {
        return architect.getUUID().toString().substring(0, 8);
    }

    private record PlannedColumn(BlockPos base, boolean weakSeam) {
    }
}
