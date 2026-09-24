package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.architect.ArchitectWalkGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A report describes only the short crossing segment actually inspected by this observer. */
final class MissionSensing {
    static String inspect(ArchitectEntity actor, MaeveDirector.AccessHint hint) {
        if (actor.blockPosition().distSqr(hint.outside()) > 36 || hint.inside().distSqr(hint.outside()) > 16) return "UNSEEN";
        Vec3 start = Vec3.atBottomCenterOf(hint.outside()), end = Vec3.atBottomCenterOf(hint.inside());
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2));
        boolean clear = true;
        for (int i = 0; i <= steps; i++) {
            BlockPos feet = BlockPos.containing(start.lerp(end, (double) i / steps));
            if (!loadedRay(actor, feet)) return "UNSEEN";
            // A witnessed feet cell can contain the supporting path, slab or snow layer.
            // Its collision top is the floor; aim into the clearance above that surface.
            double supportTop = ArchitectWalkGeometry.partialSurfaceOffset(actor.level(), feet);
            for (int y = 0; y < 3; y++) {
                BlockPos pos = feet.above(y);
                if (!loadedRay(actor, pos)) return "UNSEEN";
                var state = actor.level().getBlockState(pos);
                boolean support = y == 0 && supportTop > 0;
                boolean obstacle = !support && !state.getCollisionShape(actor.level(), pos).isEmpty();
                Vec3 aim = support ? new Vec3(pos.getX() + .5, pos.getY() + (1 + supportTop) / 2, pos.getZ() + .5)
                        : pos.getCenter();
                boolean visible = visible(actor, pos, aim, obstacle);
                if (obstacle && visible) return "BLOCKED";
                if (!visible || obstacle || !state.getFluidState().isEmpty()) clear = false;
            }
        }
        return clear ? "OPEN" : "UNSEEN";
    }

    private static boolean loadedRay(ArchitectEntity actor, BlockPos end) {
        BlockPos start = actor.blockPosition();
        for (int x = Math.min(start.getX(), end.getX()) >> 4; x <= Math.max(start.getX(), end.getX()) >> 4; x++) {
            for (int z = Math.min(start.getZ(), end.getZ()) >> 4; z <= Math.max(start.getZ(), end.getZ()) >> 4; z++) {
                if (!actor.level().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private static boolean visible(ArchitectEntity actor, BlockPos pos, Vec3 aim, boolean surface) {
        var hit = actor.level().clip(new ClipContext(actor.getEyePosition(), aim,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
        return hit.getType() == HitResult.Type.MISS || surface && hit.getBlockPos().equals(pos);
    }
}
