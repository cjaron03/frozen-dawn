package com.frozendawn.entity.architect;

import com.frozendawn.entity.ai.ArchitectBreakPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Observation/awareness spatial helper routines extracted from ArchitectEntity.
 */
public final class ArchitectObservationSupport {

    private ArchitectObservationSupport() {
    }

    public static void scanEntrances(ServerLevel level, BlockPos center, List<BlockPos> outEntrances) {
        outEntrances.clear();
        int radius = 16;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (Math.abs(dx) < radius - 2 && Math.abs(dz) < radius - 2) {
                    continue;
                }
                BlockPos pos = center.offset(dx, 0, dz);
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos check = pos.offset(0, dy, 0);
                    if (isDryPassage(level, check)
                            && isDryPassage(level, check.above())
                            && level.getBlockState(check.below()).isSolid()) {
                        boolean nearWall = false;
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            if (level.getBlockState(check.relative(dir)).isSolid()) {
                                nearWall = true;
                                break;
                            }
                        }
                        if (nearWall) {
                            outEntrances.add(check.immutable());
                        }
                        break;
                    }
                }
            }
        }
    }

    private static boolean isDryPassage(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return ArchitectBreakPolicy.isDryPassableForArchitect(state, level, pos);
    }

    public static boolean shouldMarkObserveDirty(
            BlockPos lastObservedPos,
            BlockPos changedPos,
            int changeCount
    ) {
        return lastObservedPos != null
                && changeCount >= 5
                && changedPos.closerToCenterThan(lastObservedPos.getCenter(), 16.0);
    }

    public static boolean isPlayerFacing(Vec3 playerLook, Vec3 playerPos, Vec3 architectPos) {
        Vec3 lookVec = playerLook.normalize();
        Vec3 toArchitect = architectPos.subtract(playerPos).normalize();
        return lookVec.dot(toArchitect) > 0.5;
    }

    public static boolean isPlayerInsideBase(Level level, LivingEntity player) {
        BlockPos pos = player.blockPosition();
        int solidSides = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(dir)).isSolid()) {
                solidSides++;
            }
        }
        return solidSides >= 3;
    }

    public static boolean isNearCorner(Level level, BlockPos pos) {
        boolean n = level.getBlockState(pos.north()).isSolid();
        boolean s = level.getBlockState(pos.south()).isSolid();
        boolean e = level.getBlockState(pos.east()).isSolid();
        boolean w = level.getBlockState(pos.west()).isSolid();
        return (n && e) || (n && w) || (s && e) || (s && w);
    }
}
