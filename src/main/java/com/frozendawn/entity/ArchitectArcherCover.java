package com.frozendawn.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded local cover preference for the existing strafe, with no construction or path search. */
final class ArchitectArcherCover {
    private int side;
    private long nextScan;
    int side() { return side; }
    void update(ArchitectEntity actor, LivingEntity target, long now) {
        if (now < nextScan) return;
        nextScan = now + 20; side = 0;
        Vec3 toward = target.position().subtract(actor.position()).multiply(1, 0, 1).normalize();
        Vec3 across = new Vec3(-toward.z, 0, toward.x);
        if (!actor.level().hasChunksAt(actor.blockPosition().offset(-5, -2, -5), actor.blockPosition().offset(5, 3, 5))) return;
        if (!actor.level().hasChunksAt(actor.blockPosition(), target.blockPosition())) return;
        // At most eight points, nearest first. Use collision geometry, including partial snow floors.
        for (int distance = 1; distance <= 4; distance++) for (int sign : new int[]{1, -1}) {
            Vec3 offset = across.scale(sign * distance);
            Vec3 eye = actor.getEyePosition().add(offset);
            if (!actor.level().noCollision(actor, actor.getBoundingBox().move(offset).deflate(.01))) continue;
            if (actor.level().clip(new ClipContext(target.getEyePosition(), eye, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, actor)).getType() == HitResult.Type.BLOCK) {
                side = sign; return;
            }
        }
    }
    void clear() { side = 0; nextScan = 0; }
}
