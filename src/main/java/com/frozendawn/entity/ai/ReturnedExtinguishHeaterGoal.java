package com.frozendawn.entity.ai;

import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.event.WorldTickHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ReturnedExtinguishHeaterGoal extends MoveToBlockGoal {

    public ReturnedExtinguishHeaterGoal(PathfinderMob mob) {
        super(mob, 1.2, 16);
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ThermalHeaterBlockEntity heater) || !heater.isLit()) return false;
        // Only target heaters the mob can actually see — don't path toward walled-off heaters
        return hasLineOfSight(pos);
    }

    @Override
    public void tick() {
        super.tick();
        if (isReachedTarget() && hasLineOfSight(blockPos)) {
            BlockEntity be = mob.level().getBlockEntity(blockPos);
            if (be instanceof ThermalHeaterBlockEntity heater && heater.isLit()) {
                heater.extinguish();
                mob.level().playSound(null, blockPos, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.HOSTILE, 1.0f, 1.0f);
                if (mob.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5,
                            10, 0.3, 0.3, 0.3, 0.02);

                    // Grant "They Remember" advancement to nearest player
                    List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
                            ServerPlayer.class, new AABB(blockPos).inflate(32.0));
                    for (ServerPlayer player : nearbyPlayers) {
                        WorldTickHandler.grantAdvancement(player, "they_remember");
                    }
                }
            }
        }
    }

    @Override
    public double acceptedDistance() {
        return 2.0;
    }

    private boolean hasLineOfSight(BlockPos pos) {
        net.minecraft.world.phys.Vec3 eyePos = mob.getEyePosition();
        net.minecraft.world.phys.Vec3 targetCenter = new net.minecraft.world.phys.Vec3(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
                eyePos, targetCenter,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mob);
        net.minecraft.world.phys.BlockHitResult hit = mob.level().clip(ctx);
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || hit.getBlockPos().equals(pos);
    }
}
