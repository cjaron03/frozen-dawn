package com.frozendawn.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A dropped concern is acted out locally: turn away, leave, then resume roaming. */
final class ArchitectAttentionController {
    private final ArchitectEntity actor;
    private UUID releasedPlayer;
    private Vec3 away, destination;
    private long walkUntil, ignoreUntil, nextStep;

    ArchitectAttentionController(ArchitectEntity actor) { this.actor = actor; }
    private long now() { return actor.getServer().overworld().getGameTime(); }

    void begin(UUID player, BlockPos lastObserved, String kind) {
        if (actor.isMasterArchitectVisual()) return;
        if (actor.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getEntity(player) instanceof net.minecraft.server.level.ServerPlayer subject)
            com.frozendawn.maeve.MaeveDirector.observeWithdrawal(actor, subject);
        releasedPlayer = player;
        away = actor.position().subtract(lastObserved.getCenter()).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < .01) away = new Vec3(1, 0, 0);
        walkUntil = now() + 200; ignoreUntil = now() + 600; nextStep = 0; destination = null;
        actor.cancelMaeveAttentionWork();
        actor.setReconnaissanceEyes(kind.startsWith("RECON"));
        actor.recordDecision(kind.startsWith("RECON_") ? "MAEVE_RECON_EXTRACTION" : "MAEVE_ATTENTION_EVICTED", null,
                "concern=" + kind + " observed=" + lastObserved + " player=" + player);
    }

    boolean tick() {
        if (releasedPlayer == null) return false;
        if (now() >= walkUntil) { actor.setReconnaissanceEyes(false); return false; }
        actor.getNavigation().stop(); actor.setTarget(null); actor.setMaeveHolding(false);
        actor.setCommitmentAction(false); actor.setSprinting(false);
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        if (now() >= nextStep || destination == null || actor.position().distanceToSqr(destination) < .09) {
            nextStep = now() + 20; destination = null;
            for (int angle : new int[]{0, 30, -30, 60, -60, 90, -90}) {
                Vec3 candidate = actor.position().add(away.yRot((float) Math.toRadians(angle)).scale(2));
                if (safe(candidate)) { destination = candidate; break; }
            }
        }
        Vec3 look = destination == null ? actor.position().add(away) : destination;
        float yaw = (float) (Math.atan2(look.z - actor.getZ(), look.x - actor.getX()) * 180 / Math.PI) - 90;
        actor.setYRot(yaw); actor.setYBodyRot(yaw); actor.setYHeadRot(yaw);
        actor.getLookControl().setLookAt(look.x, actor.getEyeY(), look.z, 15, 15);
        if (destination != null && actor.onGround() && safe(destination)) {
            Vec3 delta = destination.subtract(actor.position()).multiply(1, 0, 1);
            double speed = Math.min(.14, delta.length());
            delta = delta.normalize().scale(speed);
            actor.setDeltaMovement(delta.x, actor.getDeltaMovement().y, delta.z);
        }
        return true;
    }

    private boolean safe(Vec3 target) {
        for (int i = 0; i <= 4; i++) {
            Vec3 point = actor.position().lerp(target, i / 4.0);
            BlockPos pos = BlockPos.containing(point);
            var box = actor.getBoundingBox().move(point.subtract(actor.position())).deflate(.01);
            for (int x = ((int) Math.floor(box.minX) >> 4); x <= ((int) Math.floor(box.maxX) >> 4); x++) {
                for (int z = ((int) Math.floor(box.minZ) >> 4); z <= ((int) Math.floor(box.maxZ) >> 4); z++) {
                    if (!actor.level().hasChunk(x, z)) return false;
                }
            }
            var ground = actor.level().getBlockState(pos.below()); var feet = actor.level().getBlockState(pos);
            if (!ground.isFaceSturdy(actor.level(), pos.below(), Direction.UP) || ground.is(Blocks.MAGMA_BLOCK)
                    || ground.is(Blocks.CACTUS) || !feet.getFluidState().isEmpty() || feet.is(BlockTags.FIRE)
                    || !actor.level().noCollision(actor, box)) return false;
        }
        return true;
    }

    boolean suppresses(UUID player) { return releasedPlayer != null && releasedPlayer.equals(player) && now() < ignoreUntil; }
    boolean active() { return releasedPlayer != null && now() < ignoreUntil; }
    void clear() {
        releasedPlayer = null; away = null; destination = null; walkUntil = 0; ignoreUntil = 0;
        actor.setReconnaissanceEyes(false);
        actor.getNavigation().stop(); actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
    }
}
