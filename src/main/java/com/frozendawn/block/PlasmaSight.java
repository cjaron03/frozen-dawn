package com.frozendawn.block;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Line of sight into a burning SFR-1 channel, for the retinal specks. A look passes air and blocks with no visual
 * shape, such as glass; a closed Maintenance Hatch lets it through only at the porthole painted on its upper half. */
public final class PlasmaSight {
    /** The porthole's square on the upper half's face, in block units: the blue glass of sfr_maintenance_hatch_top.png. */
    public static final double PORTHOLE_MIN = 5 / 16.0, PORTHOLE_MAX = 11 / 16.0;
    private PlasmaSight() {}

    public static boolean seesPlasma(BlockGetter level, Vec3 eye, Vec3 look, double reach, Predicate<BlockPos> burningChannel) {
        var to = eye.add(look.normalize().scale(reach));
        return BlockGetter.traverseBlocks(eye, to, level, (l, pos) -> {
            if (burningChannel.test(pos)) return true;
            var state = l.getBlockState(pos);
            var hit = state.getVisualShape(l, pos, CollisionContext.empty()).clip(eye, to, pos);
            if (hit == null) return null;
            return state.getBlock() instanceof SfrHatchBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER && porthole(hit, pos) ? null : false;
        }, l -> false);
    }

    private static boolean porthole(BlockHitResult hit, BlockPos pos) {
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        var axis = hit.getDirection().getAxis();
        if (axis == Direction.Axis.Y) return false;
        double across = axis == Direction.Axis.Z ? local.x : local.z;
        return within(across) && within(local.y);
    }
    private static boolean within(double v) { return v >= PORTHOLE_MIN && v <= PORTHOLE_MAX; }
}
