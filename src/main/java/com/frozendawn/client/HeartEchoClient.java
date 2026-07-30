package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ShadowFigureEntity;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.homo.HeartEchoPolicy;
import com.frozendawn.homo.HeartFormationStage;
import com.frozendawn.homo.HeartLattice;
import com.frozendawn.init.ModEntities;
import com.frozendawn.network.HeartEchoActionPayload;
import com.frozendawn.network.HeartEchoStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client manifestation, gaze test, and node-guide presentation for an Echo. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HeartEchoClient {
    private static final double GAZE_DOT = Math.cos(Math.toRadians(8.0D));
    private static final double ECHO_HIT_RADIUS = 0.9D;
    private static final int FILAMENT_TICKS = HeartEchoPolicy.NODE_EXPOSURE_TICKS;

    private static ShadowFigureEntity echo;
    private static Vec3 echoPosition = Vec3.ZERO;
    private static int generation;
    private static int nodeIndex = -1;
    private static int gazeTicks;
    private static boolean actionSent;
    private static int exposureTicks;
    private static int clarityTicks;
    private static int filamentTicks;

    private HeartEchoClient() {
    }

    public static void handleState(HeartEchoStatePayload payload) {
        generation = payload.generation();
        echoPosition = new Vec3(payload.x(), payload.y(), payload.z());
        nodeIndex = payload.nodeIndex();
        exposureTicks = Math.max(exposureTicks, payload.exposureTicks());
        clarityTicks = Math.max(clarityTicks, payload.clarityTicks());

        if (payload.state() == HeartEchoStatePayload.CLEAR) {
            reset();
            return;
        }
        if (payload.state() == HeartEchoStatePayload.ACTIVE) {
            spawnEcho();
            spawnArrival();
            gazeTicks = 0;
            actionSent = false;
            return;
        }

        fadeEcho(payload.state() == HeartEchoStatePayload.SCREAMED ? 4 : 12);
        if (payload.state() == HeartEchoStatePayload.ACKNOWLEDGED) {
            filamentTicks = FILAMENT_TICKS;
            spawnDissolve(72, false);
            CognitiveLoadClientState.beginClarity();
        } else if (payload.state() == HeartEchoStatePayload.SCREAMED) {
            spawnDissolve(110, true);
        } else if (payload.state() == HeartEchoStatePayload.VIOLENTLY_DISMISSED) {
            spawnDissolve(42, true);
        } else if (payload.state() == HeartEchoStatePayload.NODE_HIT) {
            CognitiveLoadClientState.beginClarity();
        }
    }

    public static boolean hasClarity() {
        return clarityTicks > 0;
    }

    public static boolean isNodeExposed(int requestedNode) {
        return exposureTicks > 0 && nodeIndex == requestedNode;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        clarityTicks = Math.max(0, clarityTicks - 1);
        exposureTicks = Math.max(0, exposureTicks - 1);
        if (filamentTicks > 0) {
            filamentTicks--;
            spawnFilament(minecraft);
        }
        if (echo == null || echo.isRemoved() || actionSent) {
            gazeTicks = 0;
            return;
        }
        echo.setPos(echoPosition.x, echoPosition.y, echoPosition.z);
        spawnEchoMotes(minecraft);
        if (isGazingAtEcho(minecraft)) {
            gazeTicks++;
            if (gazeTicks >= HeartEchoPolicy.GAZE_TICKS) {
                actionSent = true;
                PacketDistributor.sendToServer(
                        new HeartEchoActionPayload(generation, false));
            }
        } else {
            gazeTicks = Math.max(0, gazeTicks - 2);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractionInput(
            InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || event.isCanceled() || actionSent || echo == null
                || echo.isRemoved()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!rayHitsEcho(minecraft)) {
            return;
        }
        actionSent = true;
        PacketDistributor.sendToServer(new HeartEchoActionPayload(generation, true));
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void spawnEcho() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        fadeEcho(2);
        echo = ModEntities.SHADOW_FIGURE.get().create(minecraft.level);
        if (echo == null) {
            return;
        }
        echo.setEcho(true);
        echo.setPos(echoPosition.x, echoPosition.y, echoPosition.z);
        minecraft.level.addEntity(echo);
    }

    private static void fadeEcho(int ticks) {
        if (echo != null && !echo.isRemoved()) {
            echo.startFading(ticks);
        }
        echo = null;
        gazeTicks = 0;
    }

    private static boolean isGazingAtEcho(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 target = echoPosition.add(0.0D, 1.45D, 0.0D);
        Vec3 toward = target.subtract(eye);
        double distance = toward.length();
        if (distance > 24.0D || distance < 0.01D
                || minecraft.player.getViewVector(1.0F).dot(toward.scale(1.0D / distance))
                < GAZE_DOT) {
            return false;
        }
        return minecraft.level.clip(new ClipContext(
                eye, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, minecraft.player)).getType()
                == HitResult.Type.MISS;
    }

    private static boolean rayHitsEcho(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 ray = minecraft.player.getViewVector(1.0F).normalize();
        Vec3 target = echoPosition.add(0.0D, 1.0D, 0.0D);
        Vec3 relative = target.subtract(eye);
        double along = relative.dot(ray);
        if (along < 0.0D || along > 24.0D) {
            return false;
        }
        return target.distanceToSqr(eye.add(ray.scale(along)))
                <= ECHO_HIT_RADIUS * ECHO_HIT_RADIUS;
    }

    private static void spawnEchoMotes(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.level.getGameTime() % 2L != 0L) {
            return;
        }
        double angle = minecraft.level.getGameTime() * 0.19D + gazeTicks * 0.04D;
        double radius = 0.38D + gazeTicks / (double) HeartEchoPolicy.GAZE_TICKS * 0.18D;
        minecraft.level.addParticle(
                gazeTicks > 0 ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME,
                echoPosition.x + Math.cos(angle) * radius,
                echoPosition.y + 1.45D + Math.sin(angle * 0.7D) * 0.45D,
                echoPosition.z + Math.sin(angle) * radius,
                0.0D, 0.004D, 0.0D);
    }

    private static void spawnArrival() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (int index = 0; index < 64; index++) {
            double angle = index * Mth.TWO_PI / 32.0D;
            double radius = index < 32 ? 0.72D : 0.34D;
            double height = index < 32
                    ? 0.95D + Math.sin(angle * 2.0D) * 0.85D
                    : minecraft.level.random.nextDouble() * 2.1D;
            minecraft.level.addParticle(
                    index % 4 == 0 ? ParticleTypes.END_ROD
                            : ParticleTypes.SOUL_FIRE_FLAME,
                    echoPosition.x + Math.cos(angle) * radius,
                    echoPosition.y + height,
                    echoPosition.z + Math.sin(angle) * radius,
                    -Math.cos(angle) * 0.025D,
                    index < 32 ? 0.015D : 0.035D,
                    -Math.sin(angle) * 0.025D);
        }
    }

    private static void spawnFilament(Minecraft minecraft) {
        ThaeIvenHeartEntity heart = selectedHeart(minecraft);
        if (heart == null || minecraft.level == null || nodeIndex < 0) {
            return;
        }
        Vec3 node = HeartLattice.nodePosition(
                BlockPos.of(heart.anchor()), heart.layoutSeed(),
                CognitiveLoadClientState.loadPercent(), nodeIndex);
        Vec3 start = echoPosition.add(0.0D, 1.45D, 0.0D);
        int particles = 12;
        for (int index = 0; index <= particles; index++) {
            double t = index / (double) particles;
            Vec3 point = start.lerp(node, t);
            minecraft.level.addParticle(
                    index % 3 == 0 ? ParticleTypes.END_ROD
                            : ParticleTypes.SOUL_FIRE_FLAME,
                    point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void spawnDissolve(int count, boolean violent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (int index = 0; index < count; index++) {
            double angle = minecraft.level.random.nextDouble() * Math.PI * 2.0D;
            double speed = 0.025D + minecraft.level.random.nextDouble()
                    * (violent ? 0.14D : 0.07D);
            minecraft.level.addParticle(
                    violent && index % 3 == 0 ? ParticleTypes.SCULK_SOUL
                            : index % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME
                            : ParticleTypes.REVERSE_PORTAL,
                    echoPosition.x,
                    echoPosition.y + minecraft.level.random.nextDouble() * 1.9D,
                    echoPosition.z,
                    Math.cos(angle) * speed,
                    violent ? minecraft.level.random.nextDouble() * 0.12D
                            : 0.02D,
                    Math.sin(angle) * speed);
        }
    }

    private static ThaeIvenHeartEntity selectedHeart(Minecraft minecraft) {
        if (minecraft.level == null) {
            return null;
        }
        ThaeIvenHeartEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ThaeIvenHeartEntity heart)
                    || heart.formationStage() != HeartFormationStage.LIVE) {
                continue;
            }
            double distance = echoPosition.distanceToSqr(heart.position());
            if (distance < bestDistance) {
                best = heart;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void reset() {
        fadeEcho(2);
        echoPosition = Vec3.ZERO;
        generation = 0;
        nodeIndex = -1;
        actionSent = false;
        exposureTicks = 0;
        clarityTicks = 0;
        filamentTicks = 0;
    }
}
