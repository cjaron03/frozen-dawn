package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.homo.HearthMasterArchitectWeatherPolicy;
import com.frozendawn.homo.MasterArchitectAuraTier;
import com.frozendawn.homo.MasterArchitectEyeWallPolicy;
import com.frozendawn.homo.MasterArchitectStormAftermathPolicy;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.MasterArchitectAuraEventPayload;
import com.frozendawn.network.MasterArchitectWeatherPayload;
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
import net.minecraft.resources.ResourceLocation;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Client-owned presentation for the Master Architect's non-damaging aura. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectAuraClient {
    private static final int HUM_DURATION_TICKS = 150;
    private static final int SILENCE_RADIUS = 13;
    private static final int O2_FLICKER_DURATION = 50;
    private static final int EYE_WALL_PARTICLE_WARMUP_TICKS = 40;
    private static final int LIGHTNING_FLASH_DURATION_TICKS = 7;
    private static final float[] LIGHTNING_FLASH_ENVELOPE = {
            1.0F, 1.0F, 0.52F, 0.10F, 0.74F, 0.30F, 0.08F, 0.0F
    };
    private static final ResourceLocation SNOWFLAKE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/particle/generic_0.png");
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
    private static float collapseStrength;
    private static int pressureWaveTicks;
    private static int eyeWallParticleWarmupTicks;
    private static MasterArchitectStormAftermathPolicy.Stage lastAftermathStage =
            MasterArchitectStormAftermathPolicy.Stage.COMPLETE;

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
                collapseStrength = Mth.clamp(payload.intensity(), 0.0F, 1.0F);
                collapseDurationTicks = MasterArchitectStormAftermathPolicy
                        .timeline(collapseStrength).completeTick();
                contractionPulse = 2.0F;
            }
            case MasterArchitectAuraEventPayload.DEATH_PRESSURE_WAVE ->
                    triggerPressureWave(payload);
            default -> {
            }
        }
    }

    static void updateAftermath(MasterArchitectWeatherPayload payload) {
        if (payload.aftermathDurationTicks() > 0) {
            collapseCenter = payload.hearthCenter().immutable();
            collapseTicks = Math.max(1, payload.aftermathTicks());
            collapseDurationTicks = Math.max(1, payload.aftermathDurationTicks());
            collapseStrength = Mth.clamp(payload.aftermathStrength(), 0.0F, 1.0F);
            return;
        }
        if (payload.hearthStormDead()) {
            collapseTicks = 0;
            collapseCenter = BlockPos.ZERO;
            collapseStrength = 0.0F;
            pressureWaveTicks = 0;
            THUNDER.clear();
            if (hum != null) {
                hum.fadeOut();
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

        MasterArchitectStormAftermathPolicy.Stage aftermathStage = aftermathStage();
        if (aftermathStage == MasterArchitectStormAftermathPolicy.Stage.STILLNESS
                && lastAftermathStage != MasterArchitectStormAftermathPolicy.Stage.STILLNESS) {
            THUNDER.clear();
            if (hum != null) {
                minecraft.getSoundManager().stop(hum);
                hum = null;
            }
        }
        lastAftermathStage = aftermathStage;
        boolean activeStormAftermath = collapseTicks > 0
                && aftermathStage != MasterArchitectStormAftermathPolicy.Stage.STILLNESS
                && aftermathStage != MasterArchitectStormAftermathPolicy.Stage.COMPLETE;
        int tier = activeStormAftermath
                ? MasterArchitectAuraTier.FIGHT
                : MasterArchitectWeather.getAuraTier();
        float proximity = activeStormAftermath
                ? aftermathProximity() * aftermathStormScale(aftermathStage)
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
        if (pressureWaveTicks > 0) {
            pressureWaveTicks--;
        }
        if (collapseTicks > 0 && collapseTicks < collapseDurationTicks) {
            collapseTicks++;
        } else if (collapseTicks >= collapseDurationTicks) {
            collapseTicks = 0;
            collapseCenter = BlockPos.ZERO;
        }

        tickThunder(level);
        tickHum(tier, proximity, aftermathStage);
        tickAmbientParticles(level, tier, proximity);
        tickAftermathParticles(level, aftermathStage);
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
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (pressureWaveTicks <= 0 || minecraft.level == null) {
            return;
        }
        float strength = pressureWaveTicks / 28.0F * collapseStrength;
        double time = minecraft.level.getGameTime() * 2.7D + pressureWaveTicks;
        event.setPitch(event.getPitch()
                + (float) Math.sin(time * 1.7D) * 1.15F * strength);
        event.setYaw(event.getYaw()
                + (float) Math.cos(time * 2.1D) * 1.55F * strength);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            renderEyeWallVolume(event);
            return;
        }
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
                    collapseTicks,
                    collapseStrength);
        } else if (MasterArchitectWeather.hasAuraAnchor()) {
            float visualTier = Math.max(
                    MasterArchitectAuraTier.PASSIVE,
                    MasterArchitectWeather.getVisualAuraTier());
            renderStormColumn(
                    event,
                    MasterArchitectWeather.getHearthCenter(),
                    visualTier,
                    0,
                    0.0F);
        }
    }

    private static void renderEyeWallVolume(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || ThaeIvenMindDimension.isMindLevel(minecraft.level)) {
            return;
        }
        if (collapseTicks > 0) {
            MasterArchitectEyeWallRenderer.render(
                    event,
                    collapseCenter,
                    MasterArchitectAuraTier.FIGHT,
                    collapseTicks,
                    collapseStrength,
                    1.0F);
        } else if (MasterArchitectWeather.hasAuraAnchor()) {
            MasterArchitectEyeWallRenderer.render(
                    event,
                    MasterArchitectWeather.getHearthCenter(),
                    MasterArchitectWeather.getVisualAuraTier(),
                    0,
                    0.0F,
                    MasterArchitectWeather.getStrength());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static float silenceFactor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && aftermathStage() == MasterArchitectStormAftermathPolicy.Stage.STILLNESS) {
            double distance = minecraft.player.position().distanceTo(
                    collapseCenter.getCenter());
            return distance < HearthMasterArchitectWeatherPolicy.OUTER_RADIUS
                    ? 1.0F : 0.0F;
        }
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

    /** Contains the hostile Master's local snow and fog inside its eye wall. */
    public static float localStormFactor(Vec3 position) {
        BlockPos centerPos = collapseTicks > 0
                ? collapseCenter : MasterArchitectWeather.getHearthCenter();
        float visualTier = collapseTicks > 0
                ? MasterArchitectAuraTier.FIGHT
                : MasterArchitectWeather.getVisualAuraTier();
        if (centerPos.equals(BlockPos.ZERO)) {
            return 1.0F;
        }
        Vec3 center = centerPos.getCenter();
        double dx = position.x - center.x;
        double dz = position.z - center.z;
        return MasterArchitectEyeWallPolicy.localStormFactor(
                Math.sqrt(dx * dx + dz * dz),
                visualTier,
                collapseTicks,
                collapseStrength);
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

    private static void triggerPressureWave(MasterArchitectAuraEventPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        collapseStrength = Math.max(
                collapseStrength, Mth.clamp(payload.intensity(), 0.0F, 1.0F));
        pressureWaveTicks = 28;
        Vec3 center = payload.target().getCenter();
        int particles = Math.max(48, Mth.floor(
                140.0D * FrozenDawnConfig.MASTER_AURA_PARTICLE_DENSITY.get()));
        for (int index = 0; index < particles; index++) {
            double angle = index * Math.PI * 2.0D / particles;
            double speed = 0.45D + level.random.nextDouble() * 0.65D;
            level.addParticle(
                    index % 5 == 0
                            ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SNOWFLAKE,
                    center.x + Math.cos(angle) * 2.0D,
                    center.y + 0.15D + level.random.nextDouble() * 0.8D,
                    center.z + Math.sin(angle) * 2.0D,
                    Math.cos(angle) * speed,
                    0.04D + level.random.nextDouble() * 0.08D,
                    Math.sin(angle) * speed);
        }
        playAtListener(
                ModSounds.MASTER_ARCHITECT_THUNDERSNOW_CLOSE.get(),
                3.8F * Math.max(0.35F, collapseStrength),
                0.68F,
                SoundSource.WEATHER);
    }

    private static MasterArchitectStormAftermathPolicy.Stage aftermathStage() {
        if (collapseTicks <= 0) {
            return MasterArchitectStormAftermathPolicy.Stage.COMPLETE;
        }
        return MasterArchitectStormAftermathPolicy.stage(
                collapseTicks, collapseStrength);
    }

    private static float aftermathProximity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || collapseTicks <= 0) {
            return 0.0F;
        }
        double distance = minecraft.player.position().distanceTo(
                collapseCenter.getCenter());
        return Mth.clamp((float) (1.0D - distance
                / HearthMasterArchitectWeatherPolicy.OUTER_RADIUS), 0.0F, 1.0F);
    }

    private static float aftermathStormScale(
            MasterArchitectStormAftermathPolicy.Stage stage) {
        MasterArchitectStormAftermathPolicy.Timeline timeline =
                MasterArchitectStormAftermathPolicy.timeline(collapseStrength);
        return switch (stage) {
            case CORE, EYE -> 1.0F;
            case RUPTURE -> Mth.lerp(
                    Mth.clamp((collapseTicks - timeline.eyeEndTick())
                            / (float) Math.max(1,
                            timeline.ruptureEndTick() - timeline.eyeEndTick()),
                            0.0F, 1.0F),
                    1.0F, 0.55F);
            case BASE_COLLAPSE -> Mth.lerp(
                    Mth.clamp((collapseTicks - timeline.ruptureEndTick())
                            / (float) Math.max(1,
                            timeline.collapseEndTick() - timeline.ruptureEndTick()),
                            0.0F, 1.0F),
                    0.55F, 0.0F);
            case FADE -> 1.0F - Mth.clamp(
                    collapseTicks / (float) Math.max(1, timeline.collapseEndTick()),
                    0.0F, 1.0F);
            case STILLNESS, COMPLETE -> 0.0F;
        };
    }

    private static void tickAftermathParticles(
            ClientLevel level,
            MasterArchitectStormAftermathPolicy.Stage stage) {
        if (collapseTicks <= 0 || collapseStrength <= 0.001F
                || stage == MasterArchitectStormAftermathPolicy.Stage.CORE
                || stage == MasterArchitectStormAftermathPolicy.Stage.STILLNESS
                || stage == MasterArchitectStormAftermathPolicy.Stage.COMPLETE) {
            return;
        }
        double density = FrozenDawnConfig.MASTER_AURA_PARTICLE_DENSITY.get();
        if (density <= 0.0D) {
            return;
        }
        Vec3 center = collapseCenter.getCenter();
        MasterArchitectStormAftermathPolicy.Timeline timeline =
                MasterArchitectStormAftermathPolicy.timeline(collapseStrength);
        int requested;
        if (stage == MasterArchitectStormAftermathPolicy.Stage.EYE) {
            float progress = Mth.clamp((collapseTicks - timeline.coreEndTick())
                    / (float) Math.max(1,
                    timeline.eyeEndTick() - timeline.coreEndTick()), 0.0F, 1.0F);
            requested = Math.max(4, Mth.floor(14.0D * density * collapseStrength));
            double radius = Mth.lerp(progress, 31.0D, 6.0D);
            for (int index = 0; index < requested; index++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0D;
                Vec3 position = center.add(
                        Math.cos(angle) * radius,
                        level.random.nextDouble() * 18.0D,
                        Math.sin(angle) * radius);
                Vec3 inward = center.add(0.0D, 5.0D, 0.0D)
                        .subtract(position).normalize().scale(0.32D + progress * 0.48D);
                level.addParticle(
                        ParticleTypes.SNOWFLAKE,
                        position.x, position.y, position.z,
                        inward.x, inward.y, inward.z);
            }
            return;
        }

        requested = Math.max(3, Mth.floor((stage
                == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE
                ? 18.0D : 10.0D) * density * collapseStrength));
        for (int index = 0; index < requested; index++) {
            double radius = 5.0D + level.random.nextDouble() * 38.0D;
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            level.addParticle(
                    index % 6 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SNOWFLAKE,
                    center.x + Math.cos(angle) * radius,
                    center.y + 14.0D + level.random.nextDouble() * 35.0D,
                    center.z + Math.sin(angle) * radius,
                    (level.random.nextDouble() - 0.5D) * 0.55D,
                    -0.55D - level.random.nextDouble() * 0.75D,
                    (level.random.nextDouble() - 0.5D) * 0.55D);
        }
    }

    private static void tickHum(
            int tier,
            float proximity,
            MasterArchitectStormAftermathPolicy.Stage aftermathStage) {
        Minecraft minecraft = Minecraft.getInstance();
        float target = tier <= MasterArchitectAuraTier.PASSIVE
                ? 0.0F
                : Mth.clamp((tier - 1) * 0.34F + proximity * 0.44F, 0.0F, 1.0F)
                * FrozenDawnConfig.MASTER_AURA_INFRASOUND_GAIN.get().floatValue();
        if (aftermathStage == MasterArchitectStormAftermathPolicy.Stage.EYE) {
            target = Math.max(target, 0.82F + collapseStrength * 0.25F);
        } else if (aftermathStage == MasterArchitectStormAftermathPolicy.Stage.RUPTURE
                || aftermathStage == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE) {
            target = Math.max(target, 0.92F + collapseStrength * 0.32F);
        } else if (aftermathStage == MasterArchitectStormAftermathPolicy.Stage.STILLNESS
                || aftermathStage == MasterArchitectStormAftermathPolicy.Stage.COMPLETE) {
            target = 0.0F;
        }
        if (hum != null && !hum.isStopped()) {
            hum.setTargetVolume(target, 0.012F);
            hum.setTargetPitch(aftermathStage == MasterArchitectStormAftermathPolicy.Stage.EYE
                    || aftermathStage == MasterArchitectStormAftermathPolicy.Stage.RUPTURE
                    || aftermathStage == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE
                    ? 0.72F : 1.0F);
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
                    aftermathStage == MasterArchitectStormAftermathPolicy.Stage.EYE
                            || aftermathStage == MasterArchitectStormAftermathPolicy.Stage.RUPTURE
                            || aftermathStage == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE
                            ? 0.72F : 1.0F,
                    HUM_DURATION_TICKS);
            minecraft.getSoundManager().play(hum);
            humRestartTicks = HUM_DURATION_TICKS - 12;
        }
    }

    private static void tickAmbientParticles(
            ClientLevel level, int tier, float proximity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (tier < MasterArchitectAuraTier.PASSIVE || proximity <= 0.0F) {
            eyeWallParticleWarmupTicks = 0;
            return;
        }
        double density = FrozenDawnConfig.MASTER_AURA_PARTICLE_DENSITY.get();
        if (density <= 0.0D) {
            eyeWallParticleWarmupTicks = 0;
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

        Vec3 center = (collapseTicks > 0
                ? collapseCenter : MasterArchitectWeather.getHearthCenter()).getCenter();
        Vec3 player = minecraft.player.position();
        float visualTier = collapseTicks > 0
                ? MasterArchitectAuraTier.FIGHT
                : MasterArchitectWeather.getVisualAuraTier();
        if (!MasterArchitectEyeWallPolicy.isVisible(
                visualTier, collapseTicks, collapseStrength)) {
            eyeWallParticleWarmupTicks = 0;
            return;
        }
        if (collapseTicks <= 0
                && Math.abs(visualTier - MasterArchitectWeather.getAuraTier()) > 0.025F) {
            // The batched wall covers tier transitions. Existing live particles retain
            // their old radius, so emitting during a transition produces competing rings.
            eyeWallParticleWarmupTicks = 0;
            return;
        }
        float tierProgress = Mth.clamp(
                visualTier - MasterArchitectAuraTier.NOTICED,
                0.0F,
                1.0F);
        float collapseProgress = MasterArchitectEyeWallPolicy.collapseProgress(
                collapseTicks, collapseStrength);
        float fade = MasterArchitectEyeWallPolicy.emptyFade(
                collapseTicks, collapseStrength);
        double wallRadius = MasterArchitectEyeWallPolicy.radius(
                visualTier, collapseTicks, collapseStrength)
                * (1.0D + columnPulse * 0.16D);
        double columnHeight = 154.0D
                * Mth.lerp(collapseProgress, 1.0F, 0.38F);
        double eyeWallHeight = Math.min(
                columnHeight * 0.42D,
                MasterArchitectEyeWallPolicy.height(visualTier) * 1.08D);
        double windSpeed = Mth.lerp(tierProgress, 0.24D, 0.72D)
                + collapseProgress * 0.42D;
        double centerDx = player.x - center.x;
        double centerDz = player.z - center.z;
        float nearWeight = MasterArchitectEyeWallPolicy.nearParticleWeight(
                Math.sqrt(centerDx * centerDx + centerDz * centerDz));
        if (nearWeight <= 0.001F) {
            eyeWallParticleWarmupTicks = 0;
            return;
        }
        eyeWallParticleWarmupTicks = Math.min(
                EYE_WALL_PARTICLE_WARMUP_TICKS,
                eyeWallParticleWarmupTicks + 1);
        int requested = Math.max(1, Mth.floor(
                Mth.lerp(tierProgress, 11.0F, 24.0F)
                        * density * Mth.lerp(proximity, 0.58F, 1.0F)
                        * fade * nearWeight));
        int spawned = 0;
        int attempts = requested * 4;
        long time = level.getGameTime();

        for (int attempt = 0; attempt < attempts && spawned < requested; attempt++) {
            int sequence = (int) (time * Math.max(1, requested) + attempt);
            boolean eyeWall = hash01(sequence * 31 + 11) < 0.72F;
            double vertical = hash01(sequence * 47 + 23);
            double y;
            double radius;
            if (eyeWall) {
                y = center.y - 1.0D + vertical * eyeWallHeight;
                radius = wallRadius * Mth.lerp(
                        hash01(sequence * 59 + 29), 0.88D, 1.18D);
            } else {
                double upper = vertical * vertical;
                y = center.y - 1.0D + eyeWallHeight
                        + upper * Math.max(1.0D, columnHeight - eyeWallHeight);
                double taper = Mth.lerp(upper, 1.0D, 0.52D);
                radius = wallRadius * taper * Mth.lerp(
                        hash01(sequence * 59 + 29), 0.74D, 1.22D);
            }
            double angle = hash01(sequence * 73 + 17) * Math.PI * 2.0D
                    + time * Mth.lerp(tierProgress, 0.018D, 0.052D)
                    + (y - center.y) * Mth.lerp(tierProgress, 0.025D, 0.052D);
            radius += Math.sin(time * 0.045D + sequence * 1.37D
                    + y * 0.12D) * Mth.lerp(tierProgress, 1.1D, 2.4D);
            Vec3 position = new Vec3(
                    center.x + Math.cos(angle) * radius,
                    y,
                    center.z + Math.sin(angle) * radius);
            BlockPos particlePos = BlockPos.containing(position);
            if (!level.getBlockState(particlePos)
                    .getCollisionShape(level, particlePos).isEmpty()) {
                continue;
            }

            double gust = Mth.lerp(
                    hash01(sequence * 67 + 7), 0.72D, 1.42D);
            double turbulence = Math.sin(time * 0.09D + angle * 7.0D) * 0.055D;
            double tangentX = -Math.sin(angle);
            double tangentZ = Math.cos(angle);
            double radialX = Math.cos(angle);
            double radialZ = Math.sin(angle);
            double radialDrift = -0.025D - collapseProgress * 0.78D;
            double velocityX = tangentX * windSpeed * gust
                    + radialX * radialDrift;
            double velocityY = (eyeWall ? -0.018D : -0.008D) + turbulence;
            double velocityZ = tangentZ * windSpeed * gust
                    + radialZ * radialDrift;
            level.addParticle(
                    ParticleTypes.SNOWFLAKE,
                    true,
                    position.x,
                    position.y,
                    position.z,
                    velocityX,
                    velocityY,
                    velocityZ);
            spawned++;
        }
    }

    private static void renderEyeWallLod(
            RenderLevelStageEvent event,
            BlockPos centerPos,
            float visualTier,
            int aftermathTicks,
            float aftermathStrength) {
        if (!MasterArchitectEyeWallPolicy.isVisible(
                visualTier, aftermathTicks, aftermathStrength)) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Vec3 center = centerPos.getCenter();
        double dx = center.x - camera.x;
        double dz = center.z - camera.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float particleWarmupProgress = eyeWallParticleWarmupTicks
                / (float) EYE_WALL_PARTICLE_WARMUP_TICKS;
        float lodWeight = MasterArchitectEyeWallPolicy.batchedRenderWeight(
                horizontalDistance, particleWarmupProgress);
        if (lodWeight <= 0.001F) {
            return;
        }

        double configuredDensity = FrozenDawnConfig.MASTER_AURA_PARTICLE_DENSITY.get();
        if (configuredDensity <= 0.0D) {
            return;
        }
        float collapseProgress = MasterArchitectEyeWallPolicy.collapseProgress(
                aftermathTicks, aftermathStrength);
        float fade = MasterArchitectEyeWallPolicy.emptyFade(
                aftermathTicks, aftermathStrength);
        float alphaScale = lodWeight * fade;
        float width = MasterArchitectEyeWallPolicy.radius(
                visualTier, aftermathTicks, aftermathStrength)
                * (1.0F + columnPulse * 0.16F);
        float height = 154.0F
                * Mth.lerp(collapseProgress, 1.0F, 0.38F);
        double time = Minecraft.getInstance().level.getGameTime()
                + event.getPartialTick().getGameTimeDeltaPartialTick(false);

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SNOWFLAKE_TEXTURE);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(dx, center.y - camera.y - 1.0D, dz);
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        addStormSnowVolume(
                buffer,
                poses.last().pose(),
                event.getCamera().getLeftVector(),
                event.getCamera().getUpVector(),
                visualTier,
                width,
                height,
                time,
                alphaScale,
                Mth.lerp(collapseProgress, 1.0F, 1.15F));
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        poses.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void addSnowflakeQuad(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vector3f cameraLeft,
            Vector3f cameraUp,
            float x,
            float y,
            float z,
            float size,
            int alpha) {
        addSnowflakeVertex(buffer, matrix, cameraLeft, cameraUp, x, y, z,
                1.0F, -1.0F, size, 1.0F, 1.0F, alpha);
        addSnowflakeVertex(buffer, matrix, cameraLeft, cameraUp, x, y, z,
                1.0F, 1.0F, size, 1.0F, 0.0F, alpha);
        addSnowflakeVertex(buffer, matrix, cameraLeft, cameraUp, x, y, z,
                -1.0F, 1.0F, size, 0.0F, 0.0F, alpha);
        addSnowflakeVertex(buffer, matrix, cameraLeft, cameraUp, x, y, z,
                -1.0F, -1.0F, size, 0.0F, 1.0F, alpha);
    }

    private static void addSnowflakeVertex(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vector3f cameraLeft,
            Vector3f cameraUp,
            float x,
            float y,
            float z,
            float xOffset,
            float yOffset,
            float size,
            float u,
            float v,
            int alpha) {
        float vertexX = x + (cameraLeft.x * xOffset + cameraUp.x * yOffset) * size;
        float vertexY = y + (cameraLeft.y * xOffset + cameraUp.y * yOffset) * size;
        float vertexZ = z + (cameraLeft.z * xOffset + cameraUp.z * yOffset) * size;
        buffer.addVertex(matrix, vertexX, vertexY, vertexZ)
                .setUv(u, v)
                .setColor(0.923F, 0.964F, 0.999F, alpha / 255.0F);
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
            int aftermathTicks,
            float aftermathStrength) {
        Vec3 camera = event.getCamera().getPosition();
        Vec3 toCenter = center.getCenter().subtract(camera);
        double horizontalDistance = Math.sqrt(
                toCenter.x * toCenter.x + toCenter.z * toCenter.z);
        float distantLodWeight = MasterArchitectEyeWallPolicy
                .distantRenderWeight(horizontalDistance);
        if (distantLodWeight <= 0.001F) {
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
        boolean aftermath = aftermathTicks > 0;
        MasterArchitectStormAftermathPolicy.Stage stage = aftermath
                ? MasterArchitectStormAftermathPolicy.stage(
                aftermathTicks, aftermathStrength)
                : MasterArchitectStormAftermathPolicy.Stage.CORE;
        MasterArchitectStormAftermathPolicy.Timeline timeline =
                MasterArchitectStormAftermathPolicy.timeline(aftermathStrength);
        float eyeProgress = stage == MasterArchitectStormAftermathPolicy.Stage.EYE
                ? Mth.clamp((aftermathTicks - timeline.coreEndTick())
                / (float) Math.max(1, timeline.eyeEndTick() - timeline.coreEndTick()),
                0.0F, 1.0F) : stage.ordinal()
                > MasterArchitectStormAftermathPolicy.Stage.EYE.ordinal() ? 1.0F : 0.0F;
        float ruptureProgress = stage == MasterArchitectStormAftermathPolicy.Stage.RUPTURE
                ? Mth.clamp((aftermathTicks - timeline.eyeEndTick())
                / (float) Math.max(1,
                timeline.ruptureEndTick() - timeline.eyeEndTick()),
                0.0F, 1.0F) : stage.ordinal()
                > MasterArchitectStormAftermathPolicy.Stage.RUPTURE.ordinal() ? 1.0F : 0.0F;
        float baseProgress = stage == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE
                ? Mth.clamp((aftermathTicks - timeline.ruptureEndTick())
                / (float) Math.max(1,
                timeline.collapseEndTick() - timeline.ruptureEndTick()),
                0.0F, 1.0F) : 0.0F;
        float fadeProgress = stage == MasterArchitectStormAftermathPolicy.Stage.FADE
                ? Mth.clamp(aftermathTicks
                / (float) Math.max(1, timeline.collapseEndTick()), 0.0F, 1.0F)
                : 0.0F;
        float collapseScale = !aftermath ? 1.0F
                : stage == MasterArchitectStormAftermathPolicy.Stage.EYE
                ? Mth.lerp(eyeProgress, 1.0F, 0.42F)
                : stage == MasterArchitectStormAftermathPolicy.Stage.RUPTURE
                ? Mth.lerp(ruptureProgress, 0.42F, 0.82F)
                : stage == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE
                ? Mth.lerp(baseProgress, 0.82F, 1.55F)
                : stage == MasterArchitectStormAftermathPolicy.Stage.FADE
                ? Mth.lerp(fadeProgress, 1.0F, 2.8F) : 1.0F;
        float pulse = 1.0F + columnPulse * 0.16F;
        float activeWidth = MasterArchitectEyeWallPolicy.radius(
                visualTier, aftermathTicks, aftermathStrength);
        float rupturingWidth = Mth.lerp(
                (visualTier - 1.0F) / 2.0F, 39.0F, 26.0F) * collapseScale;
        float width = (stage == MasterArchitectStormAftermathPolicy.Stage.CORE
                || stage == MasterArchitectStormAftermathPolicy.Stage.EYE
                ? activeWidth : rupturingWidth) * distanceScale * pulse;
        float heightScale = stage == MasterArchitectStormAftermathPolicy.Stage.EYE
                ? Mth.lerp(eyeProgress, 1.0F, 0.38F)
                : stage == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE
                ? 1.0F - baseProgress * 0.92F : 1.0F;
        float height = 154.0F * distanceScale * heightScale;
        float alphaScale = (!aftermath ? 1.0F
                : stage == MasterArchitectStormAftermathPolicy.Stage.RUPTURE
                ? Mth.lerp(ruptureProgress, 1.0F, 0.52F)
                : stage == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE
                ? Mth.lerp(baseProgress, 0.52F, 0.0F)
                : stage == MasterArchitectStormAftermathPolicy.Stage.FADE
                ? 1.0F - fadeProgress
                : stage == MasterArchitectStormAftermathPolicy.Stage.STILLNESS
                || stage == MasterArchitectStormAftermathPolicy.Stage.COMPLETE
                ? 0.0F : 1.0F) * distantLodWeight;
        if (alphaScale <= 0.01F) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SNOWFLAKE_TEXTURE);
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
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        double time = Minecraft.getInstance().level.getGameTime()
                + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        addStormSnowVolume(
                buffer,
                matrix,
                event.getCamera().getLeftVector(),
                event.getCamera().getUpVector(),
                visualTier,
                width,
                height,
                time,
                alphaScale,
                aftermath ? Mth.lerp(ruptureProgress, 1.15F, 0.18F) : 1.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        poses.popPose();
        if (aftermath && aftermathStrength > 0.001F
                && ruptureProgress > 0.0F) {
            renderDetachedStormChunks(
                    event, anchor, baseY, distanceScale, visualTier,
                    time, ruptureProgress, baseProgress, aftermathStrength);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void addStormSnowVolume(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vector3f cameraLeft,
            Vector3f cameraUp,
            float tier,
            float width,
            float height,
            double time,
            float alphaScale,
            float rotationScale) {
        float tierProgress = Mth.clamp((tier - 1.0F) / 2.0F, 0.0F, 1.0F);
        double density = FrozenDawnConfig.MASTER_AURA_PARTICLE_DENSITY.get();
        double optionScale = switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> 1.0D;
            case DECREASED -> 0.68D;
            case MINIMAL -> 0.38D;
        };
        int flakeCount = Math.max(48, Mth.floor(
                Mth.lerp(tierProgress, 176.0F, 288.0F)
                        * density * optionScale));
        float eyeWallHeight = Math.min(
                height * 0.42F,
                MasterArchitectEyeWallPolicy.height(tier) * 1.08F);
        float speed = Mth.lerp(tierProgress, 0.010F, 0.034F) * rotationScale;
        for (int index = 0; index < flakeCount; index++) {
            float seed = hash01(index * 47 + 23);
            boolean eyeWall = hash01(index * 31 + 11) < 0.72F;
            float verticalSpeed = Mth.lerp(
                    hash01(index * 19 + 3), 0.0009F, 0.0038F);
            float verticalPhase = Mth.frac(seed - (float) time * verticalSpeed);
            float y;
            float radius;
            if (eyeWall) {
                y = verticalPhase * eyeWallHeight;
                radius = width * Mth.lerp(
                        hash01(index * 59 + 29), 0.88F, 1.18F);
            } else {
                float upper = verticalPhase * verticalPhase;
                y = eyeWallHeight + upper * Math.max(1.0F, height - eyeWallHeight);
                float taper = Mth.lerp(upper, 1.0F, 0.52F);
                radius = width * taper * Mth.lerp(
                        hash01(index * 59 + 29), 0.74F, 1.22F);
            }
            float direction = (index & 15) == 0 ? -0.38F : 1.0F;
            float angle = hash01(index * 73 + 17) * Mth.TWO_PI
                    + (float) time * speed * direction
                    + y * Mth.lerp(tierProgress, 0.025F, 0.052F);
            float turbulence = Mth.sin(
                    (float) time * 0.045F + index * 1.37F + y * 0.12F);
            radius += turbulence * Mth.lerp(tierProgress, 1.1F, 2.4F);
            float x = Mth.cos(angle) * radius;
            float z = Mth.sin(angle) * radius;
            float size = Mth.lerp(
                    hash01(index * 37 + 13), 0.72F, 1.85F)
                    * Mth.lerp(tierProgress, 0.92F, 1.20F);
            int alpha = Mth.clamp(Mth.floor(
                    Mth.lerp(hash01(index * 41 + 7), 74.0F, 164.0F)
                            * alphaScale), 0, 210);
            addSnowflakeQuad(
                    buffer,
                    matrix,
                    cameraLeft,
                    cameraUp,
                    x,
                    y,
                    z,
                    size,
                    alpha);
        }
    }

    private static void renderDetachedStormChunks(
            RenderLevelStageEvent event,
            Vec3 anchor,
            float baseY,
            float distanceScale,
            float visualTier,
            double time,
            float ruptureProgress,
            float baseProgress,
            float fieldStrength) {
        int chunkCount = MasterArchitectStormAftermathPolicy
                .detachedChunkCount(fieldStrength);
        for (int index = 0; index < chunkCount; index++) {
            float releaseAt = 0.12F + index * 0.17F;
            float progress = Mth.clamp(
                    (ruptureProgress - releaseAt) / Math.max(0.1F, 1.0F - releaseAt),
                    0.0F, 1.0F);
            if (progress <= 0.0F) {
                continue;
            }
            float driftAngle = hash01(index * 71 + 19) * Mth.TWO_PI;
            float drift = Mth.lerp(progress, 3.0F, 48.0F) * distanceScale;
            float vertical = (44.0F + index * 23.0F) * distanceScale
                    + progress * (7.0F + index * 2.0F);
            float alpha = (1.0F - progress * 0.82F)
                    * (1.0F - baseProgress * 0.55F);
            if (alpha <= 0.02F) {
                continue;
            }

            PoseStack poses = event.getPoseStack();
            poses.pushPose();
            poses.translate(
                    anchor.x + Math.cos(driftAngle) * drift,
                    anchor.y + baseY + vertical,
                    anchor.z + Math.sin(driftAngle) * drift);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            addStormWisps(
                    buffer,
                    poses.last().pose(),
                    visualTier,
                    (7.0F + index * 1.3F) * distanceScale,
                    (18.0F + index * 3.0F) * distanceScale,
                    time - progress * 14.0D,
                    alpha * 0.58F,
                    Mth.lerp(progress, 0.82F, 0.08F));
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            poses.popPose();
        }
    }

    private static void addStormWisps(
            BufferBuilder buffer,
            Matrix4f matrix,
            float tier,
            float width,
            float height,
            double time,
            float alphaScale,
            float rotationScale) {
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
                    * direction * rotationScale;
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
        collapseStrength = 0.0F;
        pressureWaveTicks = 0;
        eyeWallParticleWarmupTicks = 0;
        lastAftermathStage = MasterArchitectStormAftermathPolicy.Stage.COMPLETE;
        MasterArchitectEyeWallRenderer.clear();
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
