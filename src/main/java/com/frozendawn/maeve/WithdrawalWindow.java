package com.frozendawn.maeve;

import net.minecraft.world.phys.Vec3;

/** Pure movement classifier. Missing samples and implausible jumps invalidate the whole window. */
final class WithdrawalWindow {
    static final int DURATION = 100, SAMPLE_GAP = 20;
    enum Result { WAITING, FOLLOWED, NOT_FOLLOWED, UNKNOWN }
    final Vec3 actorStart, playerStart;
    final long started;
    private Vec3 previousActor, previousPlayer;
    private long last;

    WithdrawalWindow(Vec3 actor, Vec3 player, long now) {
        actorStart = previousActor = actor; playerStart = previousPlayer = player;
        started = last = now;
    }

    Result sample(Vec3 actor, Vec3 player, long now, boolean visible) {
        long elapsed = now - last;
        if (!visible || elapsed < 0 || elapsed > SAMPLE_GAP || actor.distanceTo(previousActor) > 4
                || player.distanceTo(previousPlayer) > 4) return Result.UNKNOWN;
        if (elapsed == 0) return Result.WAITING;
        previousActor = actor; previousPlayer = player; last = now;
        if (now - started < DURATION) return Result.WAITING;
        Vec3 movement = actor.subtract(actorStart).multiply(1, 0, 1);
        if (movement.length() < 2) return Result.UNKNOWN;
        Vec3 direction = movement.normalize();
        double followed = player.subtract(playerStart).dot(direction);
        // Both forward progress and proximity are required; running past on a parallel route is not pursuit.
        boolean pursuing = followed >= 2 && player.distanceTo(actor) <= playerStart.distanceTo(actorStart) + 3
                && player.subtract(playerStart).subtract(direction.scale(followed)).horizontalDistance() <= 3;
        return pursuing ? Result.FOLLOWED : Result.NOT_FOLLOWED;
    }
}
