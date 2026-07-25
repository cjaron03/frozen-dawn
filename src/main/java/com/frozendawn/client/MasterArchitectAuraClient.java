package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.homo.MasterArchitectAuraTier;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.MasterArchitectAuraEventPayload;
import com.frozendawn.world.ThaeIvenMindDimension;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Client-owned presentation for the Master Architect's non-damaging aura. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectAuraClient {
    private static final int HUM_DURATION_TICKS = 150;
    private static final int SILENCE_RADIUS = 13;
    private static final int O2_FLICKER_DURATION = 50;
    private static final int LIGHTNING_FLASH_DURATION_TICKS = 7;
    private static final float[] LIGHTNING_FLASH_ENVELOPE = {
            1.0F, 1.0F, 0.52F, 0.10F, 0.74F, 0.30F, 0.08F, 0.0F
    };
    private static final List<DelayedThunder> THUNDER = new ArrayList<>();

    private static TickableWindSound hum;
    private static int humRestartTicks;
    private static boolean temperatureLinePlayed;
    private static boolean fightLinePlayed;
    private static int o2FlickerTicks;
    private static float columnPulse;
    private static float contractionPulse;
    private static int lightningFlashTicks;
    private static float lightningFlashStrength;
    private static BlockPos collapseCenter = BlockPos.ZERO;
    private static int collapseTicks;
    private static int collapseDurationTicks = 1200;

    private MasterArchitectAuraClient() {
    }

    public static void handleEvent(MasterArchitectAuraEventPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        switch (payload.eventType()) {
            case MasterArchitectAuraEventPayload.BOLT -> scheduleThunder(payload);
            case MasterArchitectAuraEventPayload.ARC -> playArc(payload);
            case MasterArchitectAuraEventPayload.TETHER_SHUDDER,
                    MasterArchitectAuraEventPayload.EXPOSURE_STUTTER ->
                    columnPulse = Math.max(columnPulse, payload.intensity());
            case MasterArchitectAuraEventPayload.FOLD_CONTRACTION ->
                    contractionPulse = Math.max(contractionPulse, payload.intensity());
            case MasterArchitectAuraEventPayload.DEATH_COLLAPSE -> {
                collapseCenter = payload.target().immutable();
                collapseTicks = 1;
                collapseDurationTicks = Math.min(
                        FrozenDawnConfig.MASTER_AURA_KILL_COLLAPSE_SECONDS.get() * 20,
                        12 * 20);
                contractionPulse = 2.0F;
                MasterArchitectWeather.suppressAfterMasterDeath();
                THUNDER.clear();
                if (hum != null) {
                    hum.fadeOut();
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        int tier = collapseTicks > 0
                ? MasterArchitectAuraTier.NONE
                : MasterArchitectWeather.getAuraTier();
        float proximity = collapseTicks > 0
                ? 0.0F
                : MasterArchitectWeather.getAuraProximity();
        if (proximity > 0.01F) {
            if (tier >= MasterArchitectAuraTier.FIGHT && !fightLinePlayed) {
                playAtListener(ModSounds.MASTER_ARCHITECT_AURA_FIGHT_TTS.get(),
                        1.4F, 0.97F, SoundSource.MASTER);
                MasterArchitectFloodClient.showSuitDialogue(
                        "ui.frozendawn.master_architect.aura_fight");
                fightLinePlayed = true;
                temperatureLinePlayed = true;
                o2FlickerTicks = O2_FLICKER_DURATION;
            } else if (tier >= MasterArchitectAuraTier.NOTICED
                    && !temperatureLinePlayed) {
                playAtListener(ModSounds.MASTER_ARCHITECT_AURA_TEMPERATURE_TTS.get(),
                        1.25F, 1.0F, SoundSource.MASTER);
                MasterArchitectFloodClient.showSuitDialogue(
                        "ui.frozendawn.master_architect.aura_temperature");
                temperatureLinePlayed = true;
            }
        }
        if (o2FlickerTicks > 0) {
            o2FlickerTicks--;
        }
        columnPulse = Math.max(0.0F, columnPulse - 0.035F);
        contractionPulse = Math.max(0.0F, contractionPulse - 0.025F);
        if (lightningFlashTicks > 0) {
            lightningFlashTicks--;
        } else {
            lightningFlashStrength = 0.0F;
        }
        if (collapseTicks > 0 && collapseTicks < collapseDurationTicks) {
            collapseTicks++;
        } else if (collapseTicks >= collapseDurationTicks) {
            collapseTicks = 0;
            collapseCenter = BlockPos.ZERO;
        }

        tickThunder(level);
        tickHum(tier, proximity);
        tickAmbientParticles(level, tier, proximity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float flash = getLightningWorldFlash((float) event.getPartialTick());
        if (flash <= 0.0F) {
            return;
        }
        float blend = flash * 0.92F;
        event.setRed(Mth.lerp(blend, event.getRed(), 0.72F));
        event.setGreen(Mth.lerp(blend, event.getGreen(), 0.87F));
        event.setBlue(Mth.lerp(blend, event.getBlue(), 1.0F));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (ThaeIvenMindDimension.isMindLevel(minecraft.level)
                && MasterArchitectFloodClient.isDeathRitual()) {
            renderMindRift(event);
        }
        if (collapseTicks > 0) {
            renderStormColumn(
                    event,
                    collapseCenter,
                    (float) MasterArchitectAuraTier.FIGHT,
                    collapseTicks / (float) Math.max(1, collapseDurationTicks));
        } else if (MasterArchitectWeather.hasAuraAnchor()) {
            renderStormColumn(
                    event,
                    MasterArchitectWeather.getHearthCenter(),
                    Math.max(
                            MasterArchitectAuraTier.PASSIVE,
                            MasterArchitectWeather.getVisualAuraTier()),
                    0.0F);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static float silenceFactor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !MasterArchitectWeather.hasAuraAnchor()) {
            return 0.0F;
        }
        double distance = minecraft.player.position().distanceTo(
                MasterArchitectWeather.getHearthCenter().getCenter());
        if (distance >= SILENCE_RADIUS) {
            return 0.0F;
        }
        return Mth.clamp((float) (1.0D - distance / SILENCE_RADIUS), 0.0F, 1.0F);
    }

    public static boolean shouldFlickerO2() {
        return o2FlickerTicks > 0 && ((o2FlickerTicks / 3) & 1) == 0;
    }

    public static float getLightningWorldFlash(float partialTick) {
        if (lightningFlashTicks <= 0 || lightningFlashStrength <= 0.0F) {
            return 0.0F;
        }
        float elapsed = LIGHTNING_FLASH_DURATION_TICKS
                - lightningFlashTicks + Mth.clamp(partialTick, 0.0F, 1.0F);
        int frame = Mth.clamp(Mth.floor(elapsed), 0,
                LIGHTNING_FLASH_ENVELOPE.length - 1);
        int next = Math.min(frame + 1, LIGHTNING_FLASH_ENVELOPE.length - 1);
        float pulse = Mth.lerp(elapsed - frame,
                LIGHTNING_FLASH_ENVELOPE[frame],
                LIGHTNING_FLASH_ENVELOPE[next]);
        return Mth.clamp(pulse * lightningFlashStrength, 0.0F, 1.0F);
    }

    private static void scheduleThunder(MasterArchitectAuraEventPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        double distance = minecraft.player.position().distanceTo(
                payload.target().getCenter());
        int delay = distance < 50.0D
                ? 3
                : distance < 150.0D
                ? Math.max(7, Mth.floor(distance / 8.0D))
                : Math.max(20, Mth.floor(distance / 6.0D));
        THUNDER.add(new DelayedThunder(
                delay, payload.target(), distance, payload.intensity()));
        columnPulse = Math.max(columnPulse, payload.intensity() * 0.75F);
        if (FrozenDawnConfig.MASTER_AURA_FLASH_INTENSITY.get() > 0.0D) {
            // Keep vanilla's flash, then amplify only the world atmosphere below.
            minecraft.level.setSkyFlashTime(2);
            lightningFlashTicks = LIGHTNING_FLASH_DURATION_TICKS;
            lightningFlashStrength = Math.max(
                    lightningFlashStrength,
                    Mth.clamp(payload.intensity()
                                    * FrozenDawnConfig.MASTER_AURA_FLASH_INTENSITY.get().floatValue(),
                            0.0F, 1.0F));
        }
        if (distance < 96.0D) {
            minecraft.level.addParticle(
                    ParticleTypes.FLASH,
                    payload.target().getX() + 0.5D,
                    payload.target().getY() + 1.0D,
                    payload.target().getZ() + 0.5D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }

    private static void tickThunder(ClientLevel level) {
        Iterator<DelayedThunder> iterator = THUNDER.iterator();
        while (iterator.hasNext()) {
            DelayedThunder thunder = iterator.next();
            thunder.ticks--;
            if (thunder.ticks > 0) {
                continue;
            }
            SoundEvent sound = thunder.distance < 50.0D
                    ? ModSounds.MASTER_ARCHITECT_THUNDERSNOW_CLOSE.get()
                    : thunder.distance < 150.0D
                    ? ModSounds.MASTER_ARCHITECT_THUNDERSNOW_MID.get()
                    : ModSounds.MASTER_ARCHITECT_THUNDERSNOW_DISTANT.get();
            float volume = thunder.distance < 50.0D
                    ? 2.1F : thunder.distance < 150.0D ? 1.35F : 0.95F;
            if (thunder.distance < 50.0D) {
                level.playLocalSound(
                        thunder.position.getX() + 0.5D,
                        thunder.position.getY(),
                        thunder.position.getZ() + 0.5D,
                        sound,
                        SoundSource.WEATHER,
                        volume * thunder.intensity,
                        0.92F + level.random.nextFloat() * 0.08F,
                        false);
            } else {
                playAtListener(sound, volume * thunder.intensity, 1.0F,
                        SoundSource.WEATHER);
            }
            iterator.remove();
        }
    }

    private static void playArc(MasterArchitectAuraEventPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        Vec3 start = payload.origin().getCenter();
        Vec3 end = payload.target().getCenter();
        Vec3 delta = end.subtract(start);
        double distanceToArc = minecraft.player.position().distanceTo(start);
        if (distanceToArc < 220.0D) {
            int particles = Math.max(5, Mth.floor(22.0D
                    * FrozenDawnConfig.MASTER_AURA_PARTICLE_DENSITY.get()));
            for (int index = 0; index <= particles; index++) {
                double t = index / (double) particles;
                double jitter = Math.sin(t * 47.0D + payload.seed()) * 0.55D;
                Vec3 point = start.add(delta.scale(t)).add(
                        jitter,
                        Math.sin(t * 31.0D + payload.seed() * 0.01D) * 0.65D,
                        -jitter * 0.7D);
                level.addParticle(
                        index % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                        point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
            }
        }
        Vec3 midpoint = start.lerp(end, 0.5D);
        if (minecraft.player.position().distanceTo(midpoint) < 90.0D) {
            level.playLocalSound(
                    midpoint.x, midpoint.y, midpoint.z,
                    ModSounds.MASTER_ARCHITECT_ARC_CRACKLE.get(),
                    SoundSource.WEATHER,
                    1.25F * payload.intensity(),
                    0.88F + level.random.nextFloat() * 0.16F,
                    false);
        }
        columnPulse = Math.max(columnPulse, payload.intensity() * 0.38F);
    }

    private static void tickHum(int tier, float proximity) {
        Minecraft minecraft = Minecraft.getInstance();
        float target = tier <= MasterArchitectAuraTier.PASSIVE
                ? 0.0F
                : Mth.clamp((tier - 1) * 0.34F + proximity * 0.44F, 0.0F, 1.0F)
                * FrozenDawnConfig.MASTER_AURA_INFRASOUND_GAIN.get().floatValue();
        if (hum != null && !hum.isStopped()) {
            hum.setTargetVolume(target, 0.012F);
            humRestartTicks--;
        }
        if (target <= 0.005F) {
            if (hum != null) {
                hum.fadeOut();
            }
            return;
        }
        if (hum == null || hum.isStopped() || humRestartTicks <= 0) {
            hum = new TickableWindSound(
                    ModSounds.MASTER_ARCHITECT_INFRASOUND.get(),
                    target,
                    1.0F,
                    HUM_DURATION_TICKS);
            minecraft.getSoundManager().play(hum);
            humRestartTicks = HUM_DURATION_TICKS - 12;
        }
    }

    private static void tickAmbientParticles(
            ClientLevel level, int tier, float proximity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (tier < MasterArchitectAuraTier.PASSIVE || proximity <= 0.0F) {
            return;
        }
        double density = FrozenDawnConfig.MASTER_AURA_PARTICLE_DENSITY.get();
        if (density <= 0.0D) {
            return;
        }
        spawnStormWall(level, tier, proximity, density);
        if (tier >= MasterArchitectAuraTier.NOTICED
                && minecraft.player.tickCount % 7 == 0) {
            spawnSurfaceFrost(level, minecraft.player.blockPosition());
        }
        if (tier < MasterArchitectAuraTier.NOTICED) {
            return;
        }
        if (minecraft.player.tickCount % 8 != 0) {
            return;
        }
        BlockPos origin = minecraft.player.blockPosition();
        for (int attempt = 0; attempt < 5; attempt++) {
            BlockPos pos = origin.offset(
                    level.random.nextInt(17) - 8,
                    level.random.nextInt(7) - 3,
                    level.random.nextInt(17) - 8);
            BlockState state = level.getBlockState(pos);
            boolean torch = state.getBlock() instanceof BaseTorchBlock;
            boolean heater = state.is(ModBlocks.THERMAL_HEATER.get());
            if (!torch && !heater) {
                continue;
            }
            level.addParticle(
                    heater ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMOKE,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.8D,
                    pos.getZ() + 0.5D,
                    (level.random.nextDouble() - 0.5D) * 0.025D,
                    torch ? -0.012D : 0.012D,
                    (level.random.nextDouble() - 0.5D) * 0.025D);
            break;
        }
    }

    private static void spawnStormWall(
            ClientLevel level, int tier, float proximity, double configuredDensity) {
        Minecraft minecraft = Minecraft.getInstance();
        double optionScale = switch (minecraft.options.particles().get()) {
            case ALL -> 1.0D;
            case DECREASED -> 0.58D;
            case MINIMAL -> 0.22D;
        };
        double density = configuredDensity * optionScale;
        if (density <= 0.0D) {
            return;
        }

        Vec3 center = MasterArchitectWeather.getHearthCenter().getCenter();
        Vec3 player = minecraft.player.position();
        float tierProgress = (tier - MasterArchitectAuraTier.PASSIVE)
                / (float) (MasterArchitectAuraTier.FIGHT
                - MasterArchitectAuraTier.PASSIVE);
        double wallRadius = Mth.lerp(tierProgress, 36.0D, 22.0D);
        double wallDepth = Mth.lerp(tierProgress, 5.5D, 9.0D);
        double windSpeed = Mth.lerp(tierProgress, 0.22D, 0.82D);
        int requested = Math.max(1, Mth.floor(
                Mth.lerp(tierProgress, 4.0F, 18.0F)
                        * density * Mth.lerp(proximity, 0.45F, 1.0F)));
        int spawned = 0;
        int attempts = requested * 5;
        long time = level.getGameTime();

        for (int attempt = 0; attempt < attempts && spawned < requested; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double radius = wallRadius + level.random.nextGaussian() * wallDepth;
            double y = center.y - 1.5D + level.random.nextDouble()
                    * Mth.lerp(tierProgress, 18.0D, 31.0D);
            Vec3 position = new Vec3(
                    center.x + Math.cos(angle) * radius,
                    y,
                    center.z + Math.sin(angle) * radius);
            if (position.distanceToSqr(player) > 52.0D * 52.0D) {
                continue;
            }

            double gust = 0.72D + level.random.nextDouble() * 0.68D;
            double turbulence = Math.sin(time * 0.09D + angle * 7.0D) * 0.08D;
            double tangentX = -Math.sin(angle);
            double tangentZ = Math.cos(angle);
            double radialX = Math.cos(angle);
            double radialZ = Math.sin(angle);
            double radialDrift = -0.045D + level.random.nextGaussian() * 0.035D;
            double velocityX = tangentX * windSpeed * gust
                    + radialX * radialDrift;
            double velocityY = -0.025D + turbulence
                    + level.random.nextGaussian() * 0.035D;
            double velocityZ = tangentZ * windSpeed * gust
                    + radialZ * radialDrift;
            level.addParticle(
                    ParticleTypes.SNOWFLAKE,
                    position.x,
                    position.y,
                    position.z,
                    velocityX,
                    velocityY,
                    velocityZ);
            spawned++;
        }

        if (tier < MasterArchitectAuraTier.NOTICED) {
            return;
        }

        Vec3 fromCenter = new Vec3(
                player.x - center.x, 0.0D, player.z - center.z);
        if (fromCenter.lengthSqr() < 1.0E-4D) {
            fromCenter = new Vec3(1.0D, 0.0D, 0.0D);
        }
        double localAngle = Math.atan2(fromCenter.z, fromCenter.x);
        Vec3 localWind = new Vec3(
                -Math.sin(localAngle), 0.0D, Math.cos(localAngle))
                .scale(windSpeed * 1.15D);
        int sheetCount = Math.max(1, Mth.floor(
                Mth.lerp(tierProgress, 2.0F, 9.0F) * density * proximity));
        Vec3 crosswind = new Vec3(-localWind.z, 0.0D, localWind.x).normalize();
        Vec3 upwind = localWind.normalize().scale(-18.0D);
        for (int index = 0; index < sheetCount; index++) {
            double lateral = level.random.nextGaussian() * 13.0D;
            double forward = level.random.nextDouble() * 12.0D;
            Vec3 position = player.add(upwind)
                    .add(localWind.normalize().scale(forward))
                    .add(crosswind.scale(lateral))
                    .add(0.0D, -2.0D + level.random.nextDouble() * 10.0D, 0.0D);
            double gust = 0.85D + level.random.nextDouble() * 0.75D;
            level.addParticle(
                    ParticleTypes.SNOWFLAKE,
                    position.x,
                    position.y,
                    position.z,
                    localWind.x * gust,
                    -0.035D + level.random.nextGaussian() * 0.045D,
                    localWind.z * gust);
        }
    }

    private static void spawnSurfaceFrost(ClientLevel level, BlockPos origin) {
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos probe = origin.offset(
                    level.random.nextInt(21) - 10,
                    level.random.nextInt(5) - 2,
                    level.random.nextInt(21) - 10);
            for (int depth = 0; depth < 5; depth++) {
                BlockPos surface = probe.below(depth);
                if (level.getBlockState(surface).isAir()
                        || !level.getBlockState(surface.above()).isAir()) {
                    continue;
                }
                level.addParticle(
                        depth % 2 == 0 ? ParticleTypes.SNOWFLAKE : ParticleTypes.SCULK_SOUL,
                        surface.getX() + level.random.nextDouble(),
                        surface.getY() + 1.02D,
                        surface.getZ() + level.random.nextDouble(),
                        0.0D,
                        0.008D,
                        0.0D);
                return;
            }
        }
    }

    private static void renderStormColumn(
            RenderLevelStageEvent event,
            BlockPos center,
            float tier,
            float collapseProgress) {
        Vec3 camera = event.getCamera().getPosition();
        Vec3 toCenter = center.getCenter().subtract(camera);
        double horizontalDistance = Math.sqrt(
                toCenter.x * toCenter.x + toCenter.z * toCenter.z);
        if (horizontalDistance < 88.0D) {
            return;
        }
        Vec3 anchor;
        float baseY;
        float distanceScale;
        if (horizontalDistance > 240.0D) {
            Vec3 direction = new Vec3(toCenter.x, 0.0D, toCenter.z).normalize();
            anchor = direction.scale(220.0D);
            baseY = -36.0F;
            distanceScale = 0.72F;
        } else {
            anchor = toCenter;
            baseY = 0.0F;
            distanceScale = 1.0F;
        }

        float visualTier = Mth.clamp(
                tier,
                MasterArchitectAuraTier.PASSIVE,
                MasterArchitectAuraTier.FIGHT);
        float collapse = Mth.clamp(collapseProgress, 0.0F, 1.0F);
        float contractionPortion = Math.min(
                0.20F,
                40.0F / Math.max(1.0F, collapseDurationTicks));
        float contraction = collapse <= 0.0F
                ? 0.0F
                : Mth.clamp(collapse / contractionPortion, 0.0F, 1.0F);
        float unwind = collapse <= contractionPortion
                ? 0.0F
                : Mth.clamp(
                        (collapse - contractionPortion) / (1.0F - contractionPortion),
                        0.0F,
                        1.0F);
        float collapseScale = collapse <= contractionPortion
                ? Mth.lerp(contraction, 1.0F, 0.30F)
                : Mth.lerp(unwind, 0.30F, 3.4F);
        float pulse = 1.0F + columnPulse * 0.16F;
        float width = Mth.lerp(
                (visualTier - 1.0F) / 2.0F,
                34.0F, 19.0F)
                * distanceScale * pulse * collapseScale;
        float height = 154.0F * distanceScale;
        float alphaScale = 1.0F - unwind;
        if (alphaScale <= 0.01F) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(anchor.x, anchor.y + baseY, anchor.z);
        Matrix4f matrix = poses.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        double time = Minecraft.getInstance().level.getGameTime()
                + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        addStormWisps(
                buffer,
                matrix,
                visualTier,
                width,
                height,
                time,
                alphaScale * 0.42F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        poses.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void addStormWisps(
            BufferBuilder buffer,
            Matrix4f matrix,
            float tier,
            float width,
            float height,
            double time,
            float alphaScale) {
        float tierProgress = (tier - 1.0F) / 2.0F;
        int wispCount = Mth.floor(Mth.lerp(tierProgress, 88.0F, 158.0F));
        float speed = Mth.lerp(tierProgress, 0.006F, 0.031F);
        int red = Mth.floor(Mth.lerp(tierProgress, 116.0F, 178.0F));
        int green = Mth.floor(Mth.lerp(tierProgress, 178.0F, 230.0F));
        int blue = Mth.floor(Mth.lerp(tierProgress, 204.0F, 246.0F));
        for (int wisp = 0; wisp < wispCount; wisp++) {
            float vertical = hash01(wisp * 37 + 11);
            float y = vertical * height;
            float taper = 0.76F + 0.58F * Math.abs(vertical - 0.43F);
            float radius = width * taper * Mth.lerp(hash01(wisp * 19 + 7), 0.78F, 1.18F);
            float direction = (wisp & 7) == 0 ? -0.55F : 1.0F;
            float angularSpeed = speed
                    * Mth.lerp(hash01(wisp * 23 + 3), 0.68F, 1.34F)
                    * direction;
            float angle = hash01(wisp * 53 + 17) * Mth.TWO_PI
                    + (float) time * angularSpeed
                    + vertical * Mth.lerp(tierProgress, 3.2F, 6.8F);
            float arcLength = Mth.lerp(
                    hash01(wisp * 29 + 5),
                    Mth.lerp(tierProgress, 0.10F, 0.19F),
                    Mth.lerp(tierProgress, 0.24F, 0.46F));
            float thickness = Mth.lerp(
                    hash01(wisp * 31 + 13), 0.07F, 0.24F)
                    * (0.8F + tierProgress * 0.45F);
            float pulse = 0.78F + 0.22F * Mth.sin(
                    (float) time * 0.11F + wisp * 1.73F);
            int alpha = Mth.clamp(Mth.floor(
                    (34.0F + tier * 12.0F + columnPulse * 10.0F)
                            * alphaScale * pulse), 0, 126);
            int pieces = 4;
            for (int piece = 0; piece < pieces; piece++) {
                float p0 = piece / (float) pieces;
                float p1 = (piece + 1) / (float) pieces;
                float a0 = angle + arcLength * p0;
                float a1 = angle + arcLength * p1;
                int alpha0 = Mth.floor(alpha * Mth.sin(p0 * Mth.PI));
                int alpha1 = Mth.floor(alpha * Mth.sin(p1 * Mth.PI));
                addWispQuad(
                        buffer,
                        matrix,
                        a0,
                        a1,
                        radius,
                        y + Mth.sin(a0 * 2.0F + wisp) * 0.22F,
                        y + Mth.sin(a1 * 2.0F + wisp) * 0.22F,
                        thickness,
                        red,
                        green,
                        blue,
                        alpha0,
                        alpha1);
            }
        }
    }

    private static void renderMindRift(RenderLevelStageEvent event) {
        float deathProgress = MasterArchitectFloodClient.getDeathRitualProgress();
        float opening = 1.0F - (float) Math.pow(1.0F - deathProgress, 3.0F);
        float pulse = 0.78F + 0.22F * Mth.sin(
                Minecraft.getInstance().level.getGameTime() * 0.18F);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(0.0D, 24.0D, 0.0D);
        poses.scale(1.58F, 1.0F, 0.58F);
        Matrix4f matrix = poses.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float radius = 8.0F + opening * 8.5F + contractionPulse * 2.0F;
        int apertureSegments = 40;
        for (int segment = 0; segment < apertureSegments; segment++) {
            float a0 = segment * Mth.TWO_PI / apertureSegments;
            float a1 = (segment + 1) * Mth.TWO_PI / apertureSegments;
            float r0 = radius * (0.76F + 0.24F * Mth.sin(segment * 2.71F));
            float r1 = radius * (0.76F + 0.24F * Mth.sin((segment + 1) * 2.71F));
            addDiscSegment(
                    buffer,
                    matrix,
                    a0,
                    a1,
                    r0,
                    r1,
                    103,
                    188,
                    218,
                    Mth.floor(138.0F * pulse));
        }
        for (int ray = 0; ray < 20; ray++) {
            float angle = ray * Mth.TWO_PI / 20.0F;
            float inner = radius * (0.76F + (ray % 2) * 0.07F);
            float outer = radius * (1.38F + (ray % 5) * 0.19F + opening * 0.28F);
            float width = 0.12F + contractionPulse * 0.08F + opening * 0.10F;
            addRadialStrip(
                    buffer,
                    matrix,
                    angle,
                    inner,
                    outer,
                    width,
                    126,
                    244,
                    255,
                    220);
        }
        float crackLength = 4.0F + opening * 22.0F;
        float crackDrop = 3.0F + opening * 20.0F;
        float crackWidth = 0.14F + (float) Math.pow(opening, 1.35F) * 1.25F;
        for (int direction = 0; direction < 4; direction++) {
            addCardinalRiftCrack(
                    buffer,
                    matrix,
                    direction,
                    radius * 0.52F,
                    crackLength,
                    crackDrop,
                    crackWidth,
                    opening,
                    64,
                    164,
                    210,
                    150);
            addCardinalRiftCrack(
                    buffer,
                    matrix,
                    direction,
                    radius * 0.52F,
                    crackLength,
                    crackDrop,
                    crackWidth * 0.28F,
                    opening,
                    190,
                    247,
                    255,
                    238);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        poses.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void addCardinalRiftCrack(
            BufferBuilder buffer,
            Matrix4f matrix,
            int direction,
            float startRadius,
            float length,
            float drop,
            float halfWidth,
            float progress,
            int red,
            int green,
            int blue,
            int alpha) {
        float angle = direction * Mth.HALF_PI;
        float dx = Mth.cos(angle);
        float dz = Mth.sin(angle);
        float px = -dz;
        float pz = dx;
        int segments = 10;
        for (int segment = 0; segment < segments; segment++) {
            float t0 = segment / (float) segments;
            float t1 = (segment + 1) / (float) segments;
            float jitterScale = (0.32F + progress * 0.78F)
                    * Mth.sin(t0 * Mth.PI);
            float offset0 = (hash01(direction * 97 + segment * 31) - 0.5F)
                    * jitterScale * 2.0F;
            float offset1 = (hash01(direction * 97 + (segment + 1) * 31) - 0.5F)
                    * jitterScale * 2.0F;
            float radial0 = startRadius + length * t0;
            float radial1 = startRadius + length * t1;
            float x0 = dx * radial0 + px * offset0;
            float z0 = dz * radial0 + pz * offset0;
            float x1 = dx * radial1 + px * offset1;
            float z1 = dz * radial1 + pz * offset1;
            float y0 = -drop * (float) Math.pow(t0, 0.78F);
            float y1 = -drop * (float) Math.pow(t1, 0.78F);
            float width0 = halfWidth * (0.62F + t0 * 0.72F);
            float width1 = halfWidth * (0.62F + t1 * 0.72F);
            int alpha0 = Mth.floor(alpha * (1.0F - t0 * 0.32F));
            int alpha1 = Mth.floor(alpha * (1.0F - t1 * 0.32F));
            buffer.addVertex(matrix, x0 + px * width0, y0, z0 + pz * width0)
                    .setColor(red, green, blue, alpha0);
            buffer.addVertex(matrix, x1 + px * width1, y1, z1 + pz * width1)
                    .setColor(red, green, blue, alpha1);
            buffer.addVertex(matrix, x1 - px * width1, y1, z1 - pz * width1)
                    .setColor(red, green, blue, alpha1);
            buffer.addVertex(matrix, x0 - px * width0, y0, z0 - pz * width0)
                    .setColor(red, green, blue, alpha0);
        }
    }

    private static void addWispQuad(
            BufferBuilder buffer, Matrix4f matrix,
            float a0,
            float a1,
            float radius,
            float y0,
            float y1,
            float halfThickness,
            int red,
            int green,
            int blue,
            int alpha0,
            int alpha1) {
        float x0 = Mth.cos(a0) * radius;
        float z0 = Mth.sin(a0) * radius;
        float x1 = Mth.cos(a1) * radius;
        float z1 = Mth.sin(a1) * radius;
        buffer.addVertex(matrix, x0, y0 - halfThickness, z0)
                .setColor(red, green, blue, alpha0);
        buffer.addVertex(matrix, x1, y1 - halfThickness, z1)
                .setColor(red, green, blue, alpha1);
        buffer.addVertex(matrix, x1, y1 + halfThickness, z1)
                .setColor(red, green, blue, alpha1);
        buffer.addVertex(matrix, x0, y0 + halfThickness, z0)
                .setColor(red, green, blue, alpha0);
    }

    private static float hash01(int value) {
        int mixed = value * 0x45D9F3B;
        mixed ^= mixed >>> 16;
        mixed *= 0x45D9F3B;
        mixed ^= mixed >>> 16;
        return (mixed & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }

    private static void addDiscSegment(
            BufferBuilder buffer,
            Matrix4f matrix,
            float a0,
            float a1,
            float radius0,
            float radius1,
            int red,
            int green,
            int blue,
            int alpha) {
        buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, Mth.cos(a0) * radius0, 0.0F, Mth.sin(a0) * radius0)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, Mth.cos(a1) * radius1, 0.0F, Mth.sin(a1) * radius1)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F)
                .setColor(red, green, blue, alpha);
    }

    private static void addRadialStrip(
            BufferBuilder buffer,
            Matrix4f matrix,
            float angle,
            float inner,
            float outer,
            float halfWidth,
            int red,
            int green,
            int blue,
            int alpha) {
        float dx = Mth.cos(angle);
        float dz = Mth.sin(angle);
        float px = -dz * halfWidth;
        float pz = dx * halfWidth;
        buffer.addVertex(matrix, dx * inner + px, 0.0F, dz * inner + pz)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, dx * outer + px, 0.0F, dz * outer + pz)
                .setColor(red, green, blue, 0);
        buffer.addVertex(matrix, dx * outer - px, 0.0F, dz * outer - pz)
                .setColor(red, green, blue, 0);
        buffer.addVertex(matrix, dx * inner - px, 0.0F, dz * inner - pz)
                .setColor(red, green, blue, alpha);
    }

    private static void playAtListener(
            SoundEvent sound, float volume, float pitch, SoundSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        minecraft.level.playLocalSound(
                minecraft.player.getX(),
                minecraft.player.getY(),
                minecraft.player.getZ(),
                sound,
                source,
                volume,
                pitch,
                false);
    }

    private static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        if (hum != null) {
            minecraft.getSoundManager().stop(hum);
            hum = null;
        }
        THUNDER.clear();
        humRestartTicks = 0;
        temperatureLinePlayed = false;
        fightLinePlayed = false;
        o2FlickerTicks = 0;
        columnPulse = 0.0F;
        contractionPulse = 0.0F;
        lightningFlashTicks = 0;
        lightningFlashStrength = 0.0F;
        collapseCenter = BlockPos.ZERO;
        collapseTicks = 0;
        collapseDurationTicks = 12 * 20;
    }

    private static final class DelayedThunder {
        private int ticks;
        private final BlockPos position;
        private final double distance;
        private final float intensity;

        private DelayedThunder(
                int ticks, BlockPos position, double distance, float intensity) {
            this.ticks = ticks;
            this.position = position.immutable();
            this.distance = distance;
            this.intensity = intensity;
        }
    }
}
