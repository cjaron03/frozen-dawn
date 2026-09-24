package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

/** Twelve local candidates at most. A physical wall must screen both torso rays. */
public final class ArchitectCoverGeometry {
    private ArchitectCoverGeometry() { }

    @Nullable
    public static BlockPos find(Entity actor, Vec3 feet, Vec3 firingPoint) {
        Vec3 toward = firingPoint.subtract(feet).multiply(1, 0, 1);
        if (toward.horizontalDistanceSqr() < 4) return null;
        toward = toward.normalize();
        var level = actor.level();
        for (double distance : new double[]{1.5, 2, 2.5, 3}) {
            BlockPos projected = BlockPos.containing(feet.add(toward.scale(distance)));
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos base = projected.offset(0, dy, 0);
                boolean loaded = true;
                for (int x = Math.min(actor.blockPosition().getX(), base.getX()) >> 4;
                     x <= (Math.max(actor.blockPosition().getX(), base.getX()) >> 4); x++)
                    for (int z = Math.min(actor.blockPosition().getZ(), base.getZ()) >> 4;
                         z <= (Math.max(actor.blockPosition().getZ(), base.getZ()) >> 4); z++)
                        if (!level.hasChunk(x, z)) loaded = false;
                if (!loaded) continue;
                AABB wall = new AABB(base).expandTowards(0, 1, 0);
                AABB body = actor.getBoundingBox().move(feet.subtract(actor.position()));
                if (wall.intersects(body.inflate(.15))
                        || !level.getBlockState(base.below()).isFaceSturdy(level, base.below(), Direction.UP)
                        || !level.getBlockState(base).isAir() || !level.getBlockState(base.above()).isAir()) continue;
                // Check the actual lane, not rounded offsets on the X/Z axes. This
                // knows no projectile, held item, draw state, or future player input.
                boolean screens = true;
                for (double height : new double[]{.9, 1.6}) {
                    Vec3 torso = feet.add(0, height, 0);
                    if (wall.deflate(.04).clip(firingPoint, torso).isEmpty()) { screens = false; break; }
                    Vec3 nearFace = wall.clip(torso, firingPoint).orElse(torso);
                    if (level.clip(new ClipContext(torso, nearFace, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, actor)).getType() != HitResult.Type.MISS) { screens = false; break; }
                }
                if (!screens) continue;
                var occupants = new java.util.ArrayList<Entity>();
                ((net.minecraft.server.level.ServerLevel) level).getEntities(
                        net.minecraft.world.level.entity.EntityTypeTest.forClass(Entity.class), wall,
                        entity -> entity != actor, occupants, 1);
                if (occupants.isEmpty()) return base;
            }
        }
        return null;
    }
}
