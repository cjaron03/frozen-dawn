package com.frozendawn.entity;

import com.frozendawn.homo.HearthMasterArchitectPolicy;
import com.frozendawn.homo.HearthMemoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * Peaceful watch behavior for the apex Architect at the INTACT Major Hearth.
 */
final class ArchitectHearthMasterController {
    private static final double HOSTILE_ACQUISITION_RANGE = 112.0D;
    private static final double WALK_SPEED = 0.45D;
    private static final double RETURN_SPEED = 0.65D;
    private static final int PATROL_DELAY_MIN = 180;
    private static final int PATROL_DELAY_VARIANCE = 180;

    private final ArchitectEntity architect;
    private int patrolCooldown;

    ArchitectHearthMasterController(ArchitectEntity architect) {
        this.architect = architect;
    }

    /**
     * @return true when peaceful Master behavior handled this tick.
     */
    boolean tick(ServerLevel level) {
        if (findHostileTarget(level) != null) {
            patrolCooldown = 0;
            return false;
        }

        architect.prepareHearthAssessmentMode();
        BlockPos home = architect.getHearthMasterArchitectHome().orElse(null);
        if (home == null) {
            return true;
        }

        if (architect.position().distanceToSqr(home.getCenter())
                > (double) HearthMasterArchitectPolicy.HOME_RADIUS
                * HearthMasterArchitectPolicy.HOME_RADIUS) {
            architect.getNavigation().moveTo(
                    home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, RETURN_SPEED);
            return true;
        }

        ServerPlayer player = nearestPlayer(level);
        if (player != null) {
            architect.getLookControl().setLookAt(player, 30.0F, 30.0F);
            if (architect.distanceToSqr(player)
                    < (double) HearthMasterArchitectPolicy.RETREAT_DISTANCE
                    * HearthMasterArchitectPolicy.RETREAT_DISTANCE) {
                retreatFrom(player, home);
            } else {
                architect.getNavigation().stop();
            }
            return true;
        }

        patrol(home);
        return true;
    }

    @Nullable
    ServerPlayer findHostileTarget(ServerLevel level) {
        return level.players().stream()
                .filter(player -> player.isAlive() && !player.isCreative() && !player.isSpectator())
                .filter(player -> architect.distanceToSqr(player)
                        <= HOSTILE_ACQUISITION_RANGE * HOSTILE_ACQUISITION_RANGE)
                .filter(player -> HearthMasterArchitectPolicy.isHostileRelationship(
                        HearthMemoryManager.relationship(level, player.getUUID())))
                .min(Comparator.comparingDouble(architect::distanceToSqr))
                .orElse(null);
    }

    @Nullable
    private ServerPlayer nearestPlayer(ServerLevel level) {
        return level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .filter(player -> architect.distanceToSqr(player)
                        <= (double) HearthMasterArchitectPolicy.WATCH_DISTANCE
                        * HearthMasterArchitectPolicy.WATCH_DISTANCE)
                .min(Comparator.comparingDouble(architect::distanceToSqr))
                .orElse(null);
    }

    private void retreatFrom(ServerPlayer player, BlockPos home) {
        Vec3 away = architect.position().subtract(player.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 desired = architect.position().add(away.normalize().scale(5.0D));
        moveWithinHome(desired.x, desired.z, home, RETURN_SPEED);
    }

    private void patrol(BlockPos home) {
        if (patrolCooldown > 0) {
            patrolCooldown--;
            return;
        }
        if (architect.getNavigation().isInProgress()) {
            return;
        }
        double angle = architect.nextRandomFloat() * Math.PI * 2.0D;
        double radius = 2.0D + architect.nextRandomFloat() * 4.0D;
        architect.getNavigation().moveTo(
                home.getX() + 0.5D + Math.cos(angle) * radius,
                home.getY(),
                home.getZ() + 0.5D + Math.sin(angle) * radius,
                WALK_SPEED);
        patrolCooldown = PATROL_DELAY_MIN
                + architect.nextRandomInt(PATROL_DELAY_VARIANCE + 1);
    }

    private void moveWithinHome(double x, double z, BlockPos home, double speed) {
        Vec3 center = home.getCenter();
        Vec3 offset = new Vec3(x - center.x, 0.0D, z - center.z);
        double maxRadius = HearthMasterArchitectPolicy.HOME_RADIUS - 1.0D;
        if (offset.horizontalDistanceSqr() > maxRadius * maxRadius) {
            offset = offset.normalize().scale(maxRadius);
        }
        architect.getNavigation().moveTo(
                center.x + offset.x, home.getY(), center.z + offset.z, speed);
    }
}
