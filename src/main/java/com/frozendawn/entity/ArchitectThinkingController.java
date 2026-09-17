package com.frozendawn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/** Visualizes real pursuit pauses without holding up movement or planner work. */
final class ArchitectThinkingController {
    private final ArchitectEntity architect;
    private Vec3 lastPosition;
    private BlockPos lastTargetPosition;
    private UUID lastTargetId;
    private int stillTicks;
    private int lastTick = Integer.MIN_VALUE;
    private int nextChinTick;
    private boolean chin;
    private String reason = "ROUTE_EXECUTION";
    private int reasonUntil;
    private BlockPos routeFocus;

    ArchitectThinkingController(ArchitectEntity architect) {
        this.architect = architect;
    }

    void noteRouteChange(String reason, @Nullable BlockPos focus) {
        this.reason = reason;
        this.routeFocus = focus;
        this.reasonUntil = architect.tickCount + 40;
    }

    int tick(@Nullable LivingEntity target, boolean eligible, boolean searching) {
        int now = architect.tickCount;
        // Early-return AI modes must not carry a previous pause into a new encounter.
        if (lastTick != now - 1) endPause();
        lastTick = now;
        if (target != null) {
            BlockPos targetPos = target.blockPosition();
            if (lastTargetId != null && (!target.getUUID().equals(lastTargetId)
                    || lastTargetPosition.distSqr(targetPos) >= 4.0)) {
                noteRouteChange("TARGET_MOVED", null);
            }
            lastTargetPosition = targetPos;
            lastTargetId = target.getUUID();
        } else {
            lastTargetPosition = null;
            lastTargetId = null;
        }
        boolean stationary = lastPosition != null
                && lastPosition.distanceToSqr(architect.position()) < 0.000625;
        lastPosition = architect.position();
        if (!eligible || target == null || !stationary) {
            endPause();
            return 0;
        }
        stillTicks++;
        // Notice a nearby obstruction immediately, before easing into the tilt.
        if (routeFocus != null && now <= reasonUntil) {
            Vec3 focus = routeFocus.getCenter();
            architect.getLookControl().setLookAt(focus.x, focus.y, focus.z, 35.0F, 25.0F);
        } else {
            architect.getLookControl().setLookAt(target, 35.0F, 25.0F);
        }
        if (stillTicks < 6) return 0;
        if (stillTicks == 6) {
            String cause = now <= reasonUntil ? reason
                    : searching ? "PATH_SEARCH"
                    : architect.horizontalCollision ? "OBSTRUCTION" : "ROUTE_EXECUTION";
            architect.recordDecision("PURSUIT_PAUSE_START", null, "cause=" + cause);
        }
        if (stillTicks == 32 && now >= nextChinTick) {
            // Deterministic per entity/pause; do not consume the AI's random stream.
            chin = Math.floorMod(architect.getUUID().hashCode() + now, 3) == 0;
            if (chin) nextChinTick = now + 240;
        }
        return chin ? 2 : 1;
    }

    private void endPause() {
        if (stillTicks >= 6) {
            architect.recordDecision("PURSUIT_PAUSE_END", null, "durationTicks=" + stillTicks);
        }
        stillTicks = 0;
        chin = false;
    }
}
