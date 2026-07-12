package com.frozendawn.entity.ai;

import com.frozendawn.entity.ReturnedEntity;
import com.frozendawn.homo.HearthWatcherPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Uncanny observation without pursuit: hold the perimeter, face visitors, and
 * withdraw when they close the distance.
 */
public final class ReturnedHearthWatchGoal extends Goal {
    private static final double WATCH_SPEED = 0.55D;
    private static final double RETURN_SPEED = 0.75D;
    private static final int MIN_REPOSITION_DELAY = 80;
    private static final int REPOSITION_DELAY_RANGE = 80;

    private final ReturnedEntity returned;
    private int repositionCooldown;
    private boolean wasObserving;

    public ReturnedHearthWatchGoal(ReturnedEntity returned) {
        this.returned = returned;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return returned.isHearthBound() && returned.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repositionCooldown = 20;
        wasObserving = false;
    }

    @Override
    public void stop() {
        returned.getNavigation().stop();
        wasObserving = false;
    }

    @Override
    public void tick() {
        BlockPos hearth = returned.getHearthCenter().orElse(null);
        if (hearth == null) {
            return;
        }

        double homeDistance = returned.position().distanceToSqr(hearth.getCenter());
        if (HearthWatcherPolicy.shouldReturnHome(homeDistance)) {
            returned.getNavigation().moveTo(
                    hearth.getX() + 0.5D, hearth.getY(), hearth.getZ() + 0.5D, RETURN_SPEED);
            return;
        }

        Player player = returned.level().getNearestPlayer(
                returned.getX(), returned.getY(), returned.getZ(),
                HearthWatcherPolicy.WATCH_DISTANCE,
                entity -> entity instanceof Player candidate
                        && candidate.isAlive() && !candidate.isSpectator());
        if (player == null) {
            wasObserving = false;
            ambientDrift(hearth);
            return;
        }

        returned.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (!wasObserving) {
            returned.getNavigation().stop();
            wasObserving = true;
            repositionCooldown = 20;
        }

        double playerDistance = returned.distanceToSqr(player);
        if (HearthWatcherPolicy.shouldRetreat(playerDistance)) {
            if (returned.getNavigation().isDone() || repositionCooldown-- <= 0) {
                retreatFrom(player, hearth);
                resetCooldown();
            }
            return;
        }

        if (repositionCooldown > 0) {
            repositionCooldown--;
        } else if (returned.getNavigation().isDone()) {
            choosePerimeterPoint(hearth, player);
            resetCooldown();
        }
    }

    private void ambientDrift(BlockPos hearth) {
        if (repositionCooldown > 0) {
            repositionCooldown--;
            return;
        }
        if (returned.getNavigation().isDone()) {
            choosePerimeterPoint(hearth, null);
            resetCooldown();
        }
    }

    private void retreatFrom(Player player, BlockPos hearth) {
        Vec3 away = returned.position().subtract(player.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 desired = returned.position().add(away.normalize().scale(7.0D));
        moveWithinHearth(desired.x, desired.z, hearth, RETURN_SPEED);
    }

    private void choosePerimeterPoint(BlockPos hearth, Player player) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = returned.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 8.0D + returned.getRandom().nextDouble() * 8.0D;
            double x = hearth.getX() + 0.5D + Math.cos(angle) * radius;
            double z = hearth.getZ() + 0.5D + Math.sin(angle) * radius;
            if (player != null && player.distanceToSqr(x, hearth.getY(), z)
                    < (double) HearthWatcherPolicy.RETREAT_DISTANCE
                    * HearthWatcherPolicy.RETREAT_DISTANCE) {
                continue;
            }
            returned.getNavigation().moveTo(x, hearth.getY(), z, WATCH_SPEED);
            return;
        }
    }

    private void moveWithinHearth(double x, double z, BlockPos hearth, double speed) {
        Vec3 center = hearth.getCenter();
        Vec3 offset = new Vec3(x - center.x, 0.0D, z - center.z);
        double maxRadius = HearthWatcherPolicy.HOME_RADIUS - 2.0D;
        if (offset.horizontalDistanceSqr() > maxRadius * maxRadius) {
            offset = offset.normalize().scale(maxRadius);
        }
        returned.getNavigation().moveTo(center.x + offset.x, hearth.getY(), center.z + offset.z, speed);
    }

    private void resetCooldown() {
        repositionCooldown = MIN_REPOSITION_DELAY
                + returned.getRandom().nextInt(REPOSITION_DELAY_RANGE + 1);
    }
}
