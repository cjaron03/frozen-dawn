package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ShadowFigureEntity;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.WatcherSeenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side sanity effects: ambient sounds, shadow figures, and The Watcher.
 * All effects are purely visual/audio — no gameplay impact.
 * Shadow figures are rendered as proper entities via ShadowFigureEntity + ShadowFigureRenderer.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class SanityEffects {

    // Shadow figure tracking
    private static final List<ShadowInstance> activeShadows = new ArrayList<>();
    private static int shadowSpawnCooldown = 0;

    // Watcher tracking
    private static WatcherInstance activeWatcher = null;
    private static int watcherSpawnCooldown = 0;

    // ── Tick handler: sounds + shadow/watcher spawn logic ──

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;

        int stage = SanityClientData.getStage();
        if (stage <= 0) {
            discardAllShadows();
            discardWatcher();
            shadowSpawnCooldown = 0;
            watcherSpawnCooldown = 0;
            return;
        }

        // Stage 1+: ambient sounds
        tickAmbientSounds(mc, stage);

        // Stage 2+: shadow figures
        if (stage >= 2) {
            tickShadows(mc);
        } else {
            discardAllShadows();
            shadowSpawnCooldown = 0;
        }

        // Stage 3: The Watcher
        if (stage >= 3) {
            tickWatcher(mc);
        } else {
            discardWatcher();
            watcherSpawnCooldown = 0;
        }

        // Stage 2+: peripheral camera nudge
        if (stage >= 2 && FrozenDawnConfig.ENABLE_SANITY_CAMERA.get() && !activeShadows.isEmpty()) {
            tickCameraNudge(mc);
        }
    }

    private static void tickAmbientSounds(Minecraft mc, int stage) {
        // Frequency: Stage 1 ~0.3%, Stage 2 ~0.6%, Stage 3 ~1.0%
        float chance = switch (stage) {
            case 1 -> 0.003f;
            case 2 -> 0.006f;
            default -> 0.01f;
        };

        if (mc.level.random.nextFloat() >= chance) return;

        // Pick a random sound
        SoundEvent[] sounds = {
                ModSounds.SANITY_WHISPER.get(),
                ModSounds.SANITY_FOOTSTEP.get(),
                ModSounds.SANITY_THUD.get()
        };
        SoundEvent sound = sounds[mc.level.random.nextInt(sounds.length)];

        // Spawn at random offset 8-16 blocks away
        double angle = mc.level.random.nextDouble() * Math.PI * 2;
        double dist = 8 + mc.level.random.nextDouble() * 8;
        double x = mc.player.getX() + Math.cos(angle) * dist;
        double y = mc.player.getY() + (mc.level.random.nextDouble() - 0.5) * 4;
        double z = mc.player.getZ() + Math.sin(angle) * dist;

        float pitch = 0.7f + mc.level.random.nextFloat() * 0.6f;
        float volume = 0.15f + mc.level.random.nextFloat() * 0.25f;

        mc.level.playLocalSound(x, y, z, sound, SoundSource.AMBIENT, volume, pitch, false);
    }

    // ── Shadow figure logic ──

    private static void tickShadows(Minecraft mc) {
        // Update existing shadows
        Iterator<ShadowInstance> it = activeShadows.iterator();
        while (it.hasNext()) {
            ShadowInstance shadow = it.next();

            // Entity was discarded externally or already removed
            if (shadow.entity.isRemoved()) {
                it.remove();
                continue;
            }

            // Check if player is looking at the shadow
            Vec3 playerPos = mc.player.getEyePosition();
            Vec3 shadowPos = shadow.entity.position().add(0, 0.975, 0); // eye level of shadow
            Vec3 toShadow = shadowPos.subtract(playerPos).normalize();
            Vec3 lookDir = mc.player.getLookAngle();
            double dot = lookDir.dot(toShadow);

            if (dot > 0.85) {
                // Player is looking — start fading
                shadow.entity.startFading();
            }

            // Remove after 10 seconds max
            if (shadow.entity.getTicksAlive() >= 200 && !shadow.entity.isFading()) {
                shadow.entity.startFading();
            }
        }

        // Spawn new shadows
        if (shadowSpawnCooldown > 0) {
            shadowSpawnCooldown--;
            return;
        }

        if (activeShadows.size() >= 3) return;

        // Only spawn if player is moving
        Vec3 motion = mc.player.getDeltaMovement();
        if (motion.horizontalDistanceSqr() < 0.001) return;

        // Spawn in peripheral vision (40-80 degrees from look direction)
        Vec3 lookDir = mc.player.getLookAngle();
        Vec3 playerPos = mc.player.getEyePosition();

        // Try up to 5 times to find a valid peripheral position
        for (int attempt = 0; attempt < 5; attempt++) {
            double angle = mc.level.random.nextDouble() * Math.PI * 2;
            double dist = 7 + mc.level.random.nextDouble() * 6; // 7-13 blocks
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;

            Vec3 candidateDir = new Vec3(dx, 0, dz).normalize();
            Vec3 lookHoriz = new Vec3(lookDir.x, 0, lookDir.z).normalize();
            double dot = lookHoriz.dot(candidateDir);

            // dot product for 40-80 degrees: cos(40)=0.766, cos(80)=0.174
            if (dot >= 0.174 && dot <= 0.766) {
                double x = playerPos.x + dx;
                double z = playerPos.z + dz;
                double y = mc.player.getY();

                ShadowFigureEntity entity = spawnShadow(mc, x, y, z, false);
                if (entity != null) {
                    activeShadows.add(new ShadowInstance(entity));
                    shadowSpawnCooldown = 600 + mc.level.random.nextInt(600); // 30-60s
                }
                break;
            }
        }
    }

    // ── Watcher logic ──

    private static void tickWatcher(Minecraft mc) {
        if (activeWatcher != null) {
            // Entity was discarded externally
            if (activeWatcher.entity.isRemoved()) {
                activeWatcher = null;
                watcherSpawnCooldown = 2400 + mc.level.random.nextInt(3600);
                return;
            }

            Vec3 playerPos = mc.player.getEyePosition();
            Vec3 watcherPos = activeWatcher.entity.position().add(0, 0.975, 0);
            Vec3 toWatcher = watcherPos.subtract(playerPos).normalize();
            Vec3 lookDir = mc.player.getLookAngle();
            double dot = lookDir.dot(toWatcher);

            if (dot > 0.85) {
                // Player is looking at The Watcher — hold eye contact
                if (activeWatcher.lookHeldTicks == 0) {
                    // First time looking — send advancement packet
                    PacketDistributor.sendToServer(new WatcherSeenPayload());
                }
                activeWatcher.lookHeldTicks++;
                if (activeWatcher.lookHeldTicks >= 60 + mc.level.random.nextInt(60)) {
                    // After 1-2 seconds of eye contact, start fading
                    activeWatcher.entity.startFading(40);
                }
            }

            if (activeWatcher.entity.isFading() && activeWatcher.entity.isRemoved()) {
                activeWatcher = null;
                watcherSpawnCooldown = 2400 + mc.level.random.nextInt(3600);
                return;
            }

            // Max lifetime: 15 seconds
            if (activeWatcher.entity.getTicksAlive() >= 300 && !activeWatcher.entity.isFading()) {
                activeWatcher.entity.startFading(40);
            }
            return;
        }

        // Spawn new Watcher
        if (watcherSpawnCooldown > 0) {
            watcherSpawnCooldown--;
            return;
        }

        // Spawn in line of sight, 6-11 blocks away
        Vec3 lookDir = mc.player.getLookAngle();
        Vec3 playerPos = mc.player.getEyePosition();

        double dist = 7 + mc.level.random.nextDouble() * 6; // 7-13 blocks
        // Slight offset from direct center (up to 15 degrees)
        double yawOffset = (mc.level.random.nextDouble() - 0.5) * Math.toRadians(30);
        double baseYaw = Math.atan2(lookDir.z, lookDir.x);
        double spawnYaw = baseYaw + yawOffset;

        double x = playerPos.x + Math.cos(spawnYaw) * dist;
        double z = playerPos.z + Math.sin(spawnYaw) * dist;
        double y = mc.player.getY();

        ShadowFigureEntity entity = spawnShadow(mc, x, y, z, true);
        if (entity != null) {
            activeWatcher = new WatcherInstance(entity);
            watcherSpawnCooldown = 100 + mc.level.random.nextInt(100);

            // Show ominous message
            mc.player.displayClientMessage(
                    Component.translatable("message.frozendawn.sanity.watched"), true);
        }
    }

    // ── Camera nudge ──

    private static void tickCameraNudge(Minecraft mc) {
        if (activeShadows.isEmpty()) return;

        ShadowInstance nearest = activeShadows.get(0);
        if (nearest.entity.isRemoved()) return;

        Vec3 playerPos = mc.player.getEyePosition();
        Vec3 toShadow = nearest.entity.position().subtract(playerPos).normalize();
        Vec3 lookDir = mc.player.getLookAngle();

        // Cross product Y component gives direction to nudge
        double cross = lookDir.x * toShadow.z - lookDir.z * toShadow.x;
        float nudge = (float) (Math.signum(cross) * 0.05); // max 0.05 degrees (subtle)

        mc.player.setYRot(mc.player.getYRot() + nudge);
    }

    // ── Spawn/Despawn helpers ──

    private static ShadowFigureEntity spawnShadow(Minecraft mc, double x, double y, double z, boolean isWatcher) {
        ShadowFigureEntity entity = ModEntities.SHADOW_FIGURE.get().create(mc.level);
        if (entity == null) {
            FrozenDawn.LOGGER.warn("[Sanity] Failed to create ShadowFigureEntity");
            return null;
        }

        entity.setPos(x, y, z);
        entity.setWatcher(isWatcher);

        // Face toward the player
        double dx = mc.player.getX() - x;
        double dz = mc.player.getZ() - z;
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        entity.setYRot(yaw);

        mc.level.addEntity(entity);
        return entity;
    }

    private static void discardAllShadows() {
        for (ShadowInstance shadow : activeShadows) {
            if (!shadow.entity.isRemoved()) {
                shadow.entity.discard();
            }
        }
        activeShadows.clear();
    }

    private static void discardWatcher() {
        if (activeWatcher != null) {
            if (!activeWatcher.entity.isRemoved()) {
                activeWatcher.entity.discard();
            }
            activeWatcher = null;
        }
    }

    // ── Instance classes ──

    private static class ShadowInstance {
        final ShadowFigureEntity entity;

        ShadowInstance(ShadowFigureEntity entity) {
            this.entity = entity;
        }
    }

    private static class WatcherInstance {
        final ShadowFigureEntity entity;
        int lookHeldTicks = 0;

        WatcherInstance(ShadowFigureEntity entity) {
            this.entity = entity;
        }
    }
}
