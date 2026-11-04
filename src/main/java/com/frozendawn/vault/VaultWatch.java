package com.frozendawn.vault;

import com.frozendawn.defense.Terminus;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The Unwatched Door's one rule: it moves only while no mind faces it down a clear line. The server judges the head, so
 * third-person front view watches the door while the body does not, and it needs nothing from the client. */
public final class VaultWatch {
    /** How near a player must be for a mechanism to run, and how far a face counts. */
    public static final double RANGE = 24;
    private static final double CONE_COS = Math.cos(Math.toRadians(35));
    private VaultWatch() { }

    public static boolean faces(Vec3 eye, Vec3 look, Vec3 target) {
        var to = target.subtract(eye);
        double distance = to.length();
        return distance > 1e-6 && to.dot(look) / (distance * look.length()) >= CONE_COS;
    }

    /** Whether any player or susceptible mob faces one of the targets with nothing in the way but {@code own} blocks. */
    public static boolean watched(ServerLevel level, Predicate<BlockPos> own, Vec3... targets) {
        for (var mind : level.getEntitiesOfClass(LivingEntity.class, new AABB(targets[0], targets[0]).inflate(RANGE), VaultWatch::isMind)) {
            var eye = mind.getEyePosition();
            var look = mind instanceof Player ? mind.getLookAngle() : mind.getViewVector(1);
            for (var target : targets) {
                if (!faces(eye, look, target)) continue;
                var hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mind));
                if (hit.getType() == HitResult.Type.MISS || own.test(hit.getBlockPos())) return true;
            }
        }
        return false;
    }

    private static boolean isMind(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !entity.isSleeping() && entity.getType().is(Terminus.SUSCEPTIBLE);
    }
}
