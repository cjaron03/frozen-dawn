package com.frozendawn.entity.architect;

import com.frozendawn.entity.ai.ArchitectBreakPolicy;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Collection;

/**
 * Encapsulates dynamic door + obstruction + breakability checks for the Architect.
 */
public final class ArchitectBlockEnvironment {

    private ArchitectBlockEnvironment() {
    }

    public static void keepNearbyWoodenDoorsOpen(LivingEntity actor) {
        keepDoorOpenNear(actor, actor.blockPosition());
        keepDoorOpenNear(actor, actor.blockPosition().above());
    }

    public static void keepDoorOpenNear(LivingEntity actor, BlockPos center) {
        openWoodenDoorAt(actor, center);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            openWoodenDoorAt(actor, center.relative(dir));
        }
    }

    public static boolean isPathObstructingState(Level level, BlockState state, BlockPos pos) {
        if (state.is(BlockTags.WOODEN_DOORS)) {
            return false;
        }
        return ArchitectBreakPolicy.isObstructiveForArchitect(state, level, pos);
    }

    public static boolean isBreakableBlock(Level level, BlockPos pos, Collection<BlockPos> scaffoldIce) {
        return breakRejection(level, pos, scaffoldIce) == null;
    }

    /** Null means eligible. Ordering matches the production break policy. */
    @javax.annotation.Nullable
    public static String breakRejection(Level level, BlockPos pos, Collection<BlockPos> scaffoldIce) {
        if (scaffoldIce.contains(pos)) return "OWN_SCAFFOLD";
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.WOODEN_DOORS)) return "OPEN_DOOR_INSTEAD";
        if (!ArchitectBreakPolicy.isObstructiveForArchitect(state, level, pos)) return "NOT_OBSTRUCTING";
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return "UNBREAKABLE";
        if (hardness >= 25.0f) return "TOO_HARD";
        if (state.is(ModBlocks.ACHERONITE_BLOCK.get()) || state.is(ModBlocks.TRANSPONDER.get())) return "PROTECTED";
        if (wouldExposeHazard(level, pos)) return "EXPOSES_HAZARD";
        return null;
    }

    private static void openWoodenDoorAt(LivingEntity actor, BlockPos pos) {
        Level level = actor.level();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock) || !state.is(BlockTags.WOODEN_DOORS)) {
            return;
        }

        // Normalize to lower half so both halves stay in sync.
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
            state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) || !state.is(BlockTags.WOODEN_DOORS)) {
                return;
            }
        }

        if (!state.getValue(DoorBlock.OPEN)) {
            ((DoorBlock) state.getBlock()).setOpen(actor, level, state, pos, true);
        }
    }

    private static boolean wouldExposeHazard(Level level, BlockPos breakPos) {
        for (Direction dir : Direction.values()) {
            BlockState adjacent = level.getBlockState(breakPos.relative(dir));
            if (isHazardousState(adjacent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHazardousState(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.getFluidState().is(FluidTags.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE);
    }
}
