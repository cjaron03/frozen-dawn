package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AlarmBeaconBlockEntity;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class AlarmBeaconAmbience {

    private static final double MAX_DISTANCE = 88.0;
    private static final double MAX_DISTANCE_SQR = MAX_DISTANCE * MAX_DISTANCE;

    private static TickableAlarmSound currentSound;
    private static BlockPos currentBeaconPos;

    private AlarmBeaconAmbience() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) {
            return;
        }
        if (mc.level.dimension() != Level.OVERWORLD) {
            stopAll(mc);
            return;
        }

        AlarmBeaconBlockEntity beacon = resolveCurrentBeacon(mc.level, mc.player.position());
        if (beacon == null) {
            fadeOut();
            return;
        }

        BlockPos beaconPos = beacon.getBlockPos();
        currentBeaconPos = beaconPos.immutable();

        float soundStrength = beacon.getSoundStrength(1.0f);
        float occlusion = computeOcclusion(mc.level, mc.player.getEyePosition(), beacon.getHeadWorldPos(), mc.player.blockPosition());
        double distance = mc.player.position().distanceTo(beacon.getHeadWorldPos());
        double distanceFalloff = Math.max(0.0, 1.0 - (distance / MAX_DISTANCE));
        float targetVolume = (float) ((0.12 + (distanceFalloff * 0.88)) * soundStrength * occlusion);
        float targetPitch = (0.76f + (0.28f * soundStrength)) * (0.92f + (0.08f * occlusion));

        targetVolume *= TownPABroadcastTracker.getAlarmVolumeDuck(mc.level, mc.player.getEyePosition());
        targetPitch *= TownPABroadcastTracker.getAlarmPitchDuck(mc.level, mc.player.getEyePosition());

        if (targetVolume <= 0.01f) {
            fadeOut();
            return;
        }

        if (currentSound == null || currentSound.isStopped()) {
            currentSound = new TickableAlarmSound(ModSounds.ALARM_BEACON.get(), beaconPos, targetVolume);
            currentSound.setTargetPitch(targetPitch);
            mc.getSoundManager().play(currentSound);
        } else {
            currentSound.moveTo(beaconPos);
            currentSound.setTargetVolume(targetVolume);
            currentSound.setTargetPitch(targetPitch);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll(Minecraft.getInstance());
    }

    private static AlarmBeaconBlockEntity resolveCurrentBeacon(ClientLevel level, Vec3 playerPos) {
        if (currentBeaconPos != null) {
            AlarmBeaconBlockEntity current = AlarmBeaconRegistry.getBeacon(level, currentBeaconPos);
            if (current != null
                    && current.isEffectivelyRunning(1.0f)
                    && playerPos.distanceToSqr(current.getHeadWorldPos()) <= MAX_DISTANCE_SQR) {
                return current;
            }
        }

        var beacons = AlarmBeaconRegistry.findNearestActiveBeacons(level, playerPos, 1.0f, 1, MAX_DISTANCE_SQR);
        if (beacons.isEmpty()) {
            currentBeaconPos = null;
            return null;
        }
        return beacons.getFirst();
    }

    private static float computeOcclusion(ClientLevel level, Vec3 eyePos, Vec3 targetPos, BlockPos playerPos) {
        ClipContext context = new ClipContext(
                eyePos,
                targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                Minecraft.getInstance().player
        );
        BlockHitResult hit = level.clip(context);
        boolean blocked = hit.getType() != HitResult.Type.MISS
                && targetPos.distanceToSqr(Vec3.atCenterOf(hit.getBlockPos())) > 1.75;
        float occlusion = blocked ? 0.38f : 1.0f;
        if (TemperatureManager.isSheltered(level, playerPos)) {
            occlusion *= blocked ? 0.68f : 0.84f;
        }
        return occlusion;
    }

    private static void fadeOut() {
        if (currentSound != null) {
            currentSound.setTargetVolume(0f);
            currentSound.setTargetPitch(0.72f);
            if (currentSound.isStopped()) {
                currentSound = null;
            }
        }
        currentBeaconPos = null;
    }

    private static void stopAll(Minecraft mc) {
        if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
            currentSound = null;
        }
        currentBeaconPos = null;
    }
}
