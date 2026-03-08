package com.frozendawn.entity.ai;

import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class ReturnedBreakLightGoal extends Goal {

    private final Monster mob;
    private final int searchRange;
    private final double speed;
    @Nullable
    private BlockPos targetPos;
    private int cooldown = 0;
    private int scanCooldown = 0;
    private int ticksStuck = 0;
    private double lastDistSq = Double.MAX_VALUE;

    public ReturnedBreakLightGoal(Monster mob) {
        this.mob = mob;
        this.searchRange = 16;
        this.speed = 1.0;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        targetPos = findNearestLight();
        if (targetPos == null) {
            scanCooldown = 40; // Don't re-scan for 2 seconds if nothing found
        }
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPos == null) return false;
        BlockState state = mob.level().getBlockState(targetPos);
        if (!isBreakableLightSource(state)) return false;
        if (ticksStuck > 100) return false; // Give up after 5 seconds of no progress
        return true;
    }

    @Override
    public void start() {
        ticksStuck = 0;
        lastDistSq = Double.MAX_VALUE;
        navigateToward();
    }

    @Override
    public void stop() {
        targetPos = null;
        ticksStuck = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetPos == null) return;

        double distSq = mob.position().distanceToSqr(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        // Close enough to break — 2.5 block reach + line of sight check
        if (distSq < 6.25 && hasLineOfSight(targetPos)) {
            BlockState state = mob.level().getBlockState(targetPos);
            if (isBreakableLightSource(state)) {
                mob.level().destroyBlock(targetPos, true);
                cooldown = 60; // 3-second cooldown
                targetPos = null;
            }
            return;
        }

        // Track progress — reset stuck counter if we're getting closer
        if (distSq < lastDistSq - 0.25) {
            ticksStuck = 0;
            lastDistSq = distSq;
        } else {
            ticksStuck++;
        }

        // Re-navigate periodically if stuck or path ended
        if (mob.getNavigation().isDone() || ticksStuck % 20 == 0) {
            navigateToward();
        }

        mob.getLookControl().setLookAt(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
    }

    private void navigateToward() {
        if (targetPos == null) return;
        PathNavigation nav = mob.getNavigation();

        // Try to pathfind to the block below the target first (handles wall torches)
        BlockPos below = targetPos.below();
        if (mob.level().getBlockState(below).isSolid()) {
            // Target is on a solid block — pathfind to adjacent open space
            for (BlockPos adj : BlockPos.betweenClosed(targetPos.offset(-1, -1, -1), targetPos.offset(1, 0, 1))) {
                if (mob.level().getBlockState(adj).isAir() && mob.level().getBlockState(adj.below()).isSolid()) {
                    if (nav.moveTo(adj.getX() + 0.5, adj.getY(), adj.getZ() + 0.5, speed)) {
                        return;
                    }
                }
            }
        }

        // Fallback: pathfind directly toward target position
        nav.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, speed);
    }

    @Nullable
    private BlockPos findNearestLight() {
        BlockPos mobPos = mob.blockPosition();
        Level level = mob.level();
        BlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                mobPos.offset(-searchRange, -2, -searchRange),
                mobPos.offset(searchRange, 4, searchRange))) {
            BlockState state = level.getBlockState(pos);
            if (isBreakableLightSource(state) && hasLineOfSight(pos)) {
                double distSq = mobPos.distSqr(pos);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = pos.immutable();
                }
            }
        }
        return closest;
    }

    private boolean hasLineOfSight(BlockPos pos) {
        net.minecraft.world.phys.Vec3 eyePos = mob.getEyePosition();
        net.minecraft.world.phys.Vec3 targetCenter = new net.minecraft.world.phys.Vec3(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
                eyePos, targetCenter,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mob);
        net.minecraft.world.phys.BlockHitResult hit = mob.level().clip(ctx);
        // Hit the target block itself or missed entirely = clear line of sight
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || hit.getBlockPos().equals(pos);
    }

    private boolean isBreakableLightSource(BlockState state) {
        // Skip Frost Ward Torches
        if (state.is(ModBlocks.FROST_WARD_TORCH.get()) || state.is(ModBlocks.FROST_WARD_WALL_TORCH.get())) {
            return false;
        }
        return state.getBlock() instanceof BaseTorchBlock
                || state.getBlock() instanceof LanternBlock
                || state.getBlock() instanceof CampfireBlock
                || state.is(Blocks.GLOWSTONE)
                || state.is(Blocks.SHROOMLIGHT)
                || state.is(Blocks.SEA_LANTERN)
                || state.is(Blocks.JACK_O_LANTERN);
    }
}
