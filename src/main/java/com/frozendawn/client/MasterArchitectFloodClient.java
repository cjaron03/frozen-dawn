package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ShadowFigureEntity;
import com.frozendawn.homo.MasterArchitectFloodPolicy;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.MasterArchitectFloodMotePayload;
import com.frozendawn.network.MasterArchitectFloodProgressPayload;
import com.frozendawn.network.MasterArchitectFloodStatePayload;
import com.frozendawn.world.ThaeIvenMindDimension;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-client Flood presentation; mechanics remain server authoritative. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectFloodClient {
    private static final int HEARTBEAT_TIMEOUT_TICKS = 20;
    private static final int MIND_SCORE_START_DELAY_TICKS = 8;
    private static final int MIND_SCAN_DELAY_TICKS = 25;
    private static final int MIND_TELEMETRY_MISMATCH_DELAY_TICKS = 45 * 20;
    private static final int TELEMETRY_RESTORED_DELAY_TICKS = 110;
    private static final int SUIT_DIALOGUE_DURATION_TICKS = 10 * 20;
    private static final int SUIT_DIALOGUE_CHARS_PER_TICK = 2;
    private static final int MIND_WITNESS_COUNT = 9;
    private static final double MIND_WITNESS_RADIUS = 23.0D;
    private static final Set<ResourceLocation> FADING_LAYERS = Set.of(
            VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.EFFECTS,
            VanillaGuiLayers.SELECTED_ITEM_NAME);

    private static final Map<Integer, ClientMote> MOTES = new LinkedHashMap<>();
    private static final List<ShadowFigureEntity> MIND_WITNESSES = new ArrayList<>();
    private static boolean active;
    private static boolean shaderTintApplied;
    private static int masterEntityId = -1;
    private static int heartbeatTicks;
    private static int memoryPulseTicks;
    private static int memoryPulseType = -1;
    private static float floodStrength;
    private static float proximity;
    private static float immersion;
    private static float audioFade;
    private static MindScore mindScore;
    private static MindScore mindEscalationScore;
    private static int mindScoreStartTicks = MIND_SCORE_START_DELAY_TICKS;
    private static int mindSessionTicks;
    private static int pendingTelemetryTicks;
    private static boolean mindScanPlayed;
    private static boolean telemetryMismatchPlayed;
    private static String suitDialogueKey;
    private static int suitDialogueTicks;
    private static int suitDialogueAge;
    private static boolean suitDialogueWarning;
    private static String suitDialogueSpeakerKey;
    private static boolean suitDialogueCorrupted;
    private static int ivenStacks;
    private static int exposureCycle;
    private static boolean coreExposed;
    private static boolean deathRitual;
    private static int deathRitualTicks;
    private static int healingTier = 1;
    private static int corePulseTicks;
    private static int mindWitnessSoundTicks;

    private MasterArchitectFloodClient() {
    }

    public static void handleState(MasterArchitectFloodStatePayload payload) {
        if (payload.operation() == MasterArchitectFloodStatePayload.CLEAR) {
            clear();
            return;
        }
        if (payload.operation() == MasterArchitectFloodStatePayload.COMPLETE_RECEIVED
                || payload.operation()
                        == MasterArchitectFloodStatePayload.COMPLETE_REFUSED) {
            clear();
            MasterArchitectFightMusic.suppressAfterCanonicalDeath(200);
            pendingTelemetryTicks = TELEMETRY_RESTORED_DELAY_TICKS;
            return;
        }

        boolean entering = !active;
        active = true;
        masterEntityId = payload.entityId();
        floodStrength = Mth.clamp(payload.floodStrength(), 0.0F, 1.0F);
        proximity = Mth.clamp(payload.proximity(), 0.0F, 1.0F);
        immersion = Mth.clamp(payload.immersion(), 0.0F, 1.0F);
        heartbeatTicks = HEARTBEAT_TIMEOUT_TICKS;
        MasterArchitectFightMusic.setFloodIntensity(floodStrength, proximity);
        if (entering) {
            audioFade = 0.0F;
            stopMindScore();
            mindScoreStartTicks = MIND_SCORE_START_DELAY_TICKS;
            mindSessionTicks = 0;
            mindScanPlayed = false;
            telemetryMismatchPlayed = false;
            clearSuitDialogue();
            Minecraft.getInstance().getMusicManager().stopPlaying();
        }
    }

    public static void handleMote(MasterArchitectFloodMotePayload payload) {
        if (payload.operation() == MasterArchitectFloodMotePayload.CLEAR) {
            MOTES.clear();
            return;
        }
        if (payload.operation() == MasterArchitectFloodMotePayload.SPAWN) {
            MOTES.put(payload.moteId(), new ClientMote(
                    payload.memoryType(),
                    new Vec3(payload.x(), payload.y(), payload.z())));
            return;
        }
        if (payload.operation() == MasterArchitectFloodMotePayload.COLLECT) {
            MOTES.remove(payload.moteId());
            memoryPulseTicks = 18;
            memoryPulseType = payload.memoryType();
        }
    }

    public static void handleProgress(MasterArchitectFloodProgressPayload payload) {
        int previousCycle = exposureCycle;
        boolean wasExposed = coreExposed;
        boolean wasDeathRitual = deathRitual;
        ivenStacks = Mth.clamp(
                payload.stacks(), 0, MasterArchitectFloodPolicy.IVEN_STACK_CAP);
        exposureCycle = Mth.clamp(
                payload.exposureCycle(), 0, MasterArchitectFloodPolicy.REQUIRED_EXPOSURES);
        coreExposed = payload.coreExposed();
        deathRitual = payload.deathRitual();
        healingTier = Mth.clamp(payload.healingTier(), 1, 3);
        if (deathRitual) {
            MOTES.clear();
            if (!wasDeathRitual) {
                deathRitualTicks = 0;
                mindWitnessSoundTicks = 1;
                stopMindScore();
                MasterArchitectFightMusic.stopAll();
                clearSuitDialogue();
            }
        } else {
            deathRitualTicks = 0;
        }
        if ((!wasExposed && coreExposed) || exposureCycle > previousCycle) {
            corePulseTicks = 18;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isDeathRitual() {
        return deathRitual;
    }

    public static float audioDuckFactor() {
        return active && !deathRitual
                ? Mth.clamp(1.0F - audioFade * effectStrength() * 0.97F, 0.03F, 1.0F)
                : 1.0F;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        renderSuitDialogue(graphics);
        if (!active || deathRitual
                || !FrozenDawnConfig.ENABLE_FLOOD_SCREEN_EFFECTS.get()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float alpha = MasterArchitectFloodPolicy.overlayAlpha(
                proximity, floodStrength) * effectStrength();
        float time = (Minecraft.getInstance().level == null
                ? 0.0F
                : Minecraft.getInstance().level.getGameTime())
                + deltaTracker.getGameTimeDeltaPartialTick(false);
        float breath = 0.82F + 0.18F * Mth.sin(time * 0.12F);
        int wash = argb(Math.round(alpha * breath * 98.0F), 0x07141C);
        graphics.fill(0, 0, width, height, wash);

        int edge = Math.max(12, Math.round(Math.min(width, height) * 0.10F));
        int edgeAlpha = Math.round(alpha * 170.0F);
        int edgeColor = argb(edgeAlpha, 0x02080E);
        graphics.fillGradient(0, 0, width, edge, edgeColor, 0x00000000);
        graphics.fillGradient(0, height - edge, width, height, 0x00000000, edgeColor);
        graphics.fill(0, 0, edge / 2, height, edgeColor);
        graphics.fill(width - edge / 2, 0, width, height, edgeColor);

        renderDoorMemory(graphics, width, height, alpha);
        renderStarMemory(graphics, width, height, alpha, time);
        renderFrostMemory(graphics, width, height, alpha, time);
        renderIvenProgress(graphics, width, height);
        if (corePulseTicks > 0) {
            float pulse = corePulseTicks / 18.0F;
            graphics.fill(0, 0, width, height,
                    argb(Math.round(84.0F * pulse), 0x7DE9F4));
        }
        if (memoryPulseTicks > 0) {
            float pulse = memoryPulseTicks / 18.0F;
            int pulseColor = switch (memoryPulseType) {
                case 0 -> 0x17252B;
                case 1 -> 0xD9EEF2;
                case 2 -> 0x103840;
                case 3 -> 0x071017;
                default -> 0xCAEFFF;
            };
            graphics.fill(0, 0, width, height,
                    argb(Math.round(112.0F * pulse), pulseColor));
            renderMemoryPulse(
                    graphics, width, height, memoryPulseType, pulse);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            pendingTelemetryTicks = 0;
            return;
        }
        if (pendingTelemetryTicks > 0 && --pendingTelemetryTicks == 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.MASTER_ARCHITECT_TELEMETRY_RESTORED_TTS.get(),
                    1.0F,
                    1.2F));
            showSuitDialogue(
                    "ui.frozendawn.master_architect.telemetry_restored");
        }
        if (suitDialogueTicks > 0) {
            suitDialogueTicks--;
            suitDialogueAge++;
            if (suitDialogueTicks == 0) {
                clearSuitDialogue();
            }
        }
        if (active && --heartbeatTicks <= 0) {
            clear();
            return;
        }
        if (memoryPulseTicks > 0) {
            memoryPulseTicks--;
        }
        if (corePulseTicks > 0) {
            corePulseTicks--;
        }
        if (active) {
            audioFade = Mth.clamp(audioFade + 1.0F / 60.0F, 0.0F, 1.0F);
            if (ThaeIvenMindDimension.isMindLevel(minecraft.level)) {
                mindSessionTicks++;
                tickMindWitnesses(minecraft);
                if (!mindScanPlayed && mindSessionTicks >= MIND_SCAN_DELAY_TICKS) {
                    mindScanPlayed = true;
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                            ModSounds.MASTER_ARCHITECT_MIND_SCAN_TTS.get(),
                            1.0F,
                            1.25F));
                    showSuitDialogue(
                            "ui.frozendawn.master_architect.mind_scan");
                }
                if (!telemetryMismatchPlayed
                        && mindSessionTicks >= MIND_TELEMETRY_MISMATCH_DELAY_TICKS) {
                    telemetryMismatchPlayed = true;
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                            ModSounds.MASTER_ARCHITECT_TELEMETRY_MISMATCH_TTS.get(),
                            1.0F,
                            1.16F));
                    showSuitDialogue(
                            "ui.frozendawn.master_architect.telemetry_mismatch");
                }
            }
            if (deathRitual) {
                deathRitualTicks++;
                stopMindScore();
            } else {
                tickMindScore(minecraft);
            }
        } else {
            discardMindWitnesses();
        }
        if (!active || minecraft.isPaused()) {
            return;
        }
        if (deathRitual) {
            MOTES.clear();
            return;
        }
        if (minecraft.level.getEntity(masterEntityId) != null) {
            Vec3 master = minecraft.level.getEntity(masterEntityId).position()
                    .add(0.0D, 1.0D, 0.0D);
            double time = minecraft.player.tickCount * 0.17D;
            for (int index = 0; index < 3; index++) {
                double angle = time + index * (Math.PI * 2.0D / 3.0D);
                double radius = 1.7D + 0.38D * Math.sin(time * 0.7D + index);
                double y = master.y + Math.sin(angle * 1.4D + index) * 1.25D;
                minecraft.level.addParticle(
                        index % 2 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL_FIRE_FLAME,
                        master.x + Math.cos(angle) * radius,
                        y,
                        master.z + Math.sin(angle) * radius,
                        -Math.cos(angle) * 0.035D,
                        0.012D,
                        -Math.sin(angle) * 0.035D);
            }
            for (int index = 0; index < 3; index++) {
                double boundaryAngle = time * 0.12D
                        + index * (Math.PI * 2.0D / 3.0D);
                double radius = ThaeIvenMindDimension.BARRIER_RADIUS - 0.35D;
                minecraft.level.addParticle(
                        ParticleTypes.REVERSE_PORTAL,
                        master.x + Math.cos(boundaryAngle) * radius,
                        master.y - 0.75D + (index % 3) * 0.85D,
                        master.z + Math.sin(boundaryAngle) * radius,
                        -Math.cos(boundaryAngle) * 0.018D,
                        0.006D,
                        -Math.sin(boundaryAngle) * 0.018D);
            }
            if (minecraft.player.tickCount % 3 == 0) {
                int echoes = Mth.clamp(Math.round(2.0F + floodStrength * 6.0F), 2, 8);
                int echoIndex = Math.floorMod(minecraft.player.tickCount / 2, echoes);
                double echoAngle = echoIndex * (Math.PI * 2.0D / echoes) + 0.35D;
                double echoRadius = 7.2D + (echoIndex % 3) * 0.75D;
                double echoX = master.x + Math.cos(echoAngle) * echoRadius;
                double echoZ = master.z + Math.sin(echoAngle) * echoRadius;
                for (int height = 0; height < 4; height++) {
                    minecraft.level.addParticle(
                            height == 3 ? ParticleTypes.END_ROD : ParticleTypes.SCULK_SOUL,
                            echoX,
                            master.y - 0.7D + height * 0.48D,
                            echoZ,
                            0.0D, 0.003D, 0.0D);
                }
            }
        }
        for (Map.Entry<Integer, ClientMote> entry : MOTES.entrySet()) {
            ClientMote mote = entry.getValue();
            double angle = minecraft.player.tickCount * 0.16D
                    + entry.getKey() * 1.7D;
            for (int particle = 0; particle < 4; particle++) {
                double localAngle = angle + particle * Math.PI * 2.0D / 4.0D;
                double radius = 0.42D + (particle % 2) * 0.22D;
                minecraft.level.addParticle(
                        ParticleTypes.SOUL,
                        mote.position.x + Math.cos(localAngle) * radius,
                        mote.position.y + Math.sin(localAngle * 1.3D) * 0.48D,
                        mote.position.z + Math.sin(localAngle) * radius,
                        -Math.cos(localAngle) * 0.012D,
                        0.026D,
                        -Math.sin(localAngle) * 0.012D);
            }
            if ((minecraft.player.tickCount + entry.getKey()) % 8 == 0) {
                minecraft.level.addParticle(
                        ParticleTypes.OMINOUS_SPAWNING,
                        mote.position.x,
                        mote.position.y,
                        mote.position.z,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!active || deathRitual
                || !FrozenDawnConfig.ENABLE_FLOOD_SCREEN_EFFECTS.get()) {
            return;
        }
        float k = effectStrength();
        event.setRed(Mth.lerp(k, event.getRed(), 0.004F));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.012F));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.021F));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!active || deathRitual
                || !FrozenDawnConfig.ENABLE_FLOOD_SCREEN_EFFECTS.get()) {
            return;
        }
        float k = effectStrength();
        event.setNearPlaneDistance(Mth.lerp(k, event.getNearPlaneDistance(), 1.7F));
        event.setFarPlaneDistance(Mth.lerp(k, event.getFarPlaneDistance(), 16.0F));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!active
                || deathRitual
                || !FrozenDawnConfig.ENABLE_FLOOD_HUD_FADE.get()
                || !FADING_LAYERS.contains(event.getName())) {
            return;
        }
        float alpha = Mth.lerp(effectStrength(), 1.0F,
                MasterArchitectFloodPolicy.hudAlpha(proximity, floodStrength));
        if (alpha <= 0.025F) {
            event.setCanceled(true);
            return;
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        shaderTintApplied = true;
    }

    @SubscribeEvent
    public static void onGuiLayerPost(RenderGuiLayerEvent.Post event) {
        if (shaderTintApplied && FADING_LAYERS.contains(event.getName())) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            shaderTintApplied = false;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        pendingTelemetryTicks = 0;
    }

    static boolean shouldCorruptSuitTelemetry() {
        Minecraft minecraft = Minecraft.getInstance();
        return active && !deathRitual && minecraft.player != null
                && ThaeIvenMindDimension.isMindLevel(minecraft.level);
    }

    static String corruptedTemperatureText() {
        String[] temperatures = {"-216\u00B0C", "--\u00B0C", "+21\u00B0C", "ERR"};
        int temperatureIndex = Math.floorMod(
                mindSessionTicks / 30, temperatures.length);
        return temperatures[temperatureIndex];
    }

    static float corruptedTemperatureVisual() {
        int temperatureIndex = Math.floorMod(mindSessionTicks / 30, 4);
        return switch (temperatureIndex) {
            case 2 -> 21.0F;
            case 3 -> -120.0F;
            default -> -216.0F;
        };
    }

    static String corruptedOxygenText() {
        if (mindSessionTicks < 35) {
            return "100%";
        }
        if (mindSessionTicks < 85) {
            return "RECALIBRATING...";
        }
        Minecraft minecraft = Minecraft.getInstance();
        AirStatusTelemetry.TankTelemetry telemetry =
                AirStatusTelemetry.getTankTelemetry(minecraft.player);
        return telemetry.fillPercent() + "%";
    }

    static float corruptedOxygenRatio() {
        if (mindSessionTicks < 35) {
            return 1.0F;
        }
        if (mindSessionTicks < 85) {
            return ((mindSessionTicks / 5) & 1) == 0 ? 1.0F : 0.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return AirStatusTelemetry.getTankTelemetry(minecraft.player).fillRatio();
    }

    public static void showSuitDialogue(String translationKey) {
        suitDialogueKey = translationKey;
        suitDialogueTicks = SUIT_DIALOGUE_DURATION_TICKS;
        suitDialogueAge = 0;
        suitDialogueWarning = false;
        suitDialogueSpeakerKey = "ui.frozendawn.master_architect.suit_speaker";
        suitDialogueCorrupted = false;
    }

    public static void showWarningSuitDialogue(String translationKey) {
        suitDialogueKey = translationKey;
        suitDialogueTicks = SUIT_DIALOGUE_DURATION_TICKS;
        suitDialogueAge = 0;
        suitDialogueWarning = true;
        suitDialogueSpeakerKey = "ui.frozendawn.master_architect.suit_speaker_warning";
        suitDialogueCorrupted = false;
    }

    public static void showRadioDialogue(String translationKey) {
        suitDialogueKey = translationKey;
        suitDialogueTicks = SUIT_DIALOGUE_DURATION_TICKS;
        suitDialogueAge = 0;
        suitDialogueWarning = false;
        suitDialogueSpeakerKey = "ui.frozendawn.remnant.radio_speaker";
        suitDialogueCorrupted = true;
    }

    public static void clearRadioDialogue() {
        if (suitDialogueCorrupted) clearSuitDialogue();
    }

    public static float getDeathRitualProgress() {
        return Mth.clamp(
                deathRitualTicks
                        / (float) MasterArchitectFloodPolicy.MIND_DEATH_DISINTEGRATION_TICKS,
                0.0F,
                1.0F);
    }

    private static void clearSuitDialogue() {
        suitDialogueKey = null;
        suitDialogueTicks = 0;
        suitDialogueAge = 0;
        suitDialogueWarning = false;
        suitDialogueSpeakerKey = null;
        suitDialogueCorrupted = false;
    }

    private static void renderSuitDialogue(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (suitDialogueKey == null || suitDialogueTicks <= 0
                || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        String fullText = Component.translatable(suitDialogueKey).getString();
        int revealedCharacters = Math.min(
                fullText.length(), suitDialogueAge * SUIT_DIALOGUE_CHARS_PER_TICK);
        String visibleText = fullText.substring(0, revealedCharacters);
        if (suitDialogueCorrupted && !visibleText.isEmpty()) {
            visibleText = corruptRadioText(visibleText, suitDialogueAge);
        }
        if (revealedCharacters < fullText.length() && (suitDialogueAge / 5 & 1) == 0) {
            visibleText += "_";
        }

        int x = TemperatureHud.HUD_X;
        int y = TemperatureHud.HUD_Y + TemperatureHud.TOTAL_HEIGHT + 28;
        int panelWidth = Math.min(258, graphics.guiWidth() - x * 2);
        int textWidth = Math.max(80, panelWidth - 12);
        List<FormattedCharSequence> lines = minecraft.font.split(
                Component.literal(visibleText), textWidth);
        int visibleLines = Math.min(3, lines.size());
        int panelHeight = 17 + visibleLines * 10;
        float fade = Mth.clamp(suitDialogueTicks / 16.0F, 0.0F, 1.0F);

        boolean corruptPulse = suitDialogueCorrupted && suitDialogueAge % 17 < 3;
        int panelColor = suitDialogueWarning ? 0x181407
                : suitDialogueCorrupted ? 0x07100F : 0x071319;
        int accentColor = suitDialogueWarning ? 0xF0C934
                : corruptPulse ? 0xE7F9E2 : 0x20DCE7;
        int speakerColor = suitDialogueWarning ? 0xFFE06A
                : suitDialogueCorrupted ? 0xB9D8CF : 0x54EAF1;
        int textColor = suitDialogueWarning ? 0xFFF3C4
                : suitDialogueCorrupted ? 0xD8E4DE : 0xD5EEF2;
        graphics.fill(x + 1, y, x + panelWidth - 1, y + panelHeight,
                argb(Math.round(224.0F * fade), panelColor));
        graphics.fill(x, y + 1, x + panelWidth, y + panelHeight - 1,
                argb(Math.round(224.0F * fade), panelColor));
        graphics.fill(x, y, x + panelWidth, y + 2,
                argb(Math.round(255.0F * fade), accentColor));
        graphics.fill(x, y + 2, x + 2, y + panelHeight,
                argb(Math.round(230.0F * fade), accentColor));

        graphics.drawString(
                minecraft.font,
                Component.translatable(suitDialogueSpeakerKey == null
                        ? "ui.frozendawn.master_architect.suit_speaker"
                        : suitDialogueSpeakerKey),
                x + 7,
                y + 5,
                argb(Math.round(255.0F * fade), speakerColor),
                false);
        for (int line = 0; line < visibleLines; line++) {
            graphics.drawString(
                    minecraft.font,
                    lines.get(line),
                    x + 7,
                    y + 15 + line * 10,
                    argb(Math.round(255.0F * fade), textColor),
                    false);
        }
    }

    private static String corruptRadioText(String text, int age) {
        if (age % 11 >= 3) return text;
        char[] characters = text.toCharArray();
        char[] noise = {'#', '/', '?', ':', '0'};
        int first = Math.floorMod(age * 7 + text.length() * 3, characters.length);
        int second = Math.floorMod(first + 5 + age, characters.length);
        if (characters[first] != ' ') characters[first] = noise[Math.floorMod(age, noise.length)];
        if (characters[second] != ' ') {
            characters[second] = noise[Math.floorMod(age + 2, noise.length)];
        }
        return new String(characters);
    }

    private static void renderDoorMemory(
            GuiGraphics graphics, int width, int height, float alpha) {
        int doorWidth = Math.max(22, width / 11);
        int doorHeight = Math.max(42, height / 3);
        int x = width / 2 - doorWidth / 2;
        int y = height / 2 - doorHeight / 2;
        int color = argb(Math.round(alpha * 58.0F), 0x7BA6AC);
        int thickness = 2;
        graphics.fill(x, y, x + doorWidth, y + thickness, color);
        graphics.fill(x, y, x + thickness, y + doorHeight, color);
        graphics.fill(x + doorWidth - thickness, y,
                x + doorWidth, y + doorHeight, color);
    }

    private static void renderIvenProgress(
            GuiGraphics graphics, int width, int height) {
        int centerX = width / 2;
        int y = height - 67;
        int pipSize = 5;
        int gap = 3;
        int totalWidth = MasterArchitectFloodPolicy.IVEN_STACK_CAP * pipSize
                + (MasterArchitectFloodPolicy.IVEN_STACK_CAP - 1) * gap;
        int startX = centerX - totalWidth / 2;
        for (int index = 0; index < MasterArchitectFloodPolicy.IVEN_STACK_CAP; index++) {
            int x = startX + index * (pipSize + gap);
            int color = index < ivenStacks ? 0xFF68E8F4 : 0x8A17313B;
            graphics.fill(x + 1, y, x + pipSize - 1, y + pipSize, color);
            graphics.fill(x, y + 1, x + pipSize, y + pipSize - 1, color);
        }
        int exposureY = y + 9;
        for (int index = 0; index < MasterArchitectFloodPolicy.REQUIRED_EXPOSURES; index++) {
            int x = centerX - 10 + index * 8;
            int color = index < exposureCycle
                    ? 0xFFD8FBFF
                    : coreExposed && index == exposureCycle ? 0xFF8BFBFF : 0x70435D66;
            graphics.fill(x, exposureY, x + 5, exposureY + 2, color);
        }
    }

    private static void renderStarMemory(
            GuiGraphics graphics, int width, int height, float alpha, float time) {
        int starAlpha = Math.round(alpha * 118.0F);
        for (int index = 0; index < 18; index++) {
            int x = Math.floorMod(index * 97 + 23, Math.max(1, width));
            int y = Math.floorMod(index * 53 + 11, Math.max(1, height / 2));
            int twinkle = Math.round(0.55F + 0.45F
                    * Math.abs(Mth.sin(time * 0.08F + index)));
            int color = argb(Math.max(1, starAlpha * twinkle), 0xD7F5FA);
            graphics.fill(x, y, x + 1 + index % 2, y + 1 + index % 2, color);
        }
    }

    private static void renderFrostMemory(
            GuiGraphics graphics, int width, int height, float alpha, float time) {
        int bandAlpha = Math.round(alpha * 42.0F);
        int offset = Math.floorMod(Mth.floor(time * 0.35F), 12);
        for (int y = offset; y < height; y += 12) {
            graphics.fill(0, y, width, y + 1,
                    argb(bandAlpha, 0xA8D8DF));
        }
    }

    private static void renderMemoryPulse(
            GuiGraphics graphics,
            int width,
            int height,
            int memoryType,
            float pulse) {
        int bright = argb(Math.round(170.0F * pulse), 0xC6F5FA);
        int dark = argb(Math.round(190.0F * pulse), 0x02070B);
        int centerX = width / 2;
        int centerY = height / 2;
        switch (memoryType) {
            case 0 -> {
                int doorWidth = Math.max(32, width / 8);
                int doorHeight = Math.max(70, height / 2);
                graphics.fill(centerX - doorWidth / 2,
                        centerY - doorHeight / 2,
                        centerX + doorWidth / 2,
                        centerY + doorHeight / 2,
                        dark);
                graphics.fill(centerX - doorWidth / 2,
                        centerY - doorHeight / 2,
                        centerX + doorWidth / 2,
                        centerY - doorHeight / 2 + 3,
                        bright);
            }
            case 1 -> {
                for (int index = 0; index < 32; index++) {
                    int x = Math.floorMod(index * 113 + 17, Math.max(1, width));
                    int y = Math.floorMod(index * 71 + 9, Math.max(1, height));
                    graphics.fill(x, y, x + 2, y + 2, bright);
                }
            }
            case 2 -> {
                int halfWidth = Math.max(48, width / 5);
                graphics.fill(centerX - halfWidth, centerY - 2,
                        centerX + halfWidth, centerY + 2, dark);
                graphics.fill(centerX - 3, centerY - 8,
                        centerX + 3, centerY + 8, bright);
            }
            case 3 -> {
                for (int ring = 0; ring < 4; ring++) {
                    int radius = 18 + ring * 16;
                    graphics.fill(centerX - radius, centerY - radius,
                            centerX + radius, centerY - radius + 2, bright);
                    graphics.fill(centerX - radius, centerY + radius - 2,
                            centerX + radius, centerY + radius, bright);
                }
            }
            default -> {
                int head = Math.max(8, height / 28);
                graphics.fill(centerX - head, centerY - height / 5,
                        centerX + head, centerY - height / 5 + head * 2, dark);
                graphics.fill(centerX - head * 2, centerY - height / 5 + head * 2,
                        centerX + head * 2, centerY + height / 4, dark);
                graphics.fill(centerX - 1, centerY - height / 5 + head / 2,
                        centerX + 1, centerY - height / 5 + head, bright);
            }
        }
    }

    private static void tickMindWitnesses(Minecraft minecraft) {
        if (!ThaeIvenMindDimension.isMindLevel(minecraft.level)) {
            discardMindWitnesses();
            return;
        }
        var master = minecraft.level.getEntity(masterEntityId);
        if (master == null) {
            return;
        }

        MIND_WITNESSES.removeIf(ShadowFigureEntity::isRemoved);
        if (MIND_WITNESSES.size() != MIND_WITNESS_COUNT) {
            discardMindWitnesses();
            for (int index = 0; index < MIND_WITNESS_COUNT; index++) {
                ShadowFigureEntity witness = ModEntities.SHADOW_FIGURE.get()
                        .create(minecraft.level);
                if (witness == null) {
                    continue;
                }
                witness.setWatcher(true);
                minecraft.level.addEntity(witness);
                MIND_WITNESSES.add(witness);
            }
            mindWitnessSoundTicks = 70 + minecraft.level.random.nextInt(80);
        }

        double tierDrift = (healingTier - 1) * 2.25D;
        for (int index = 0; index < MIND_WITNESSES.size(); index++) {
            ShadowFigureEntity witness = MIND_WITNESSES.get(index);
            double angle = index * Math.PI * 2.0D / MIND_WITNESS_COUNT + 0.21D;
            double radius = MIND_WITNESS_RADIUS - tierDrift + (index % 3) * 1.15D;
            double shakeX = 0.0D;
            double shakeY = 0.0D;
            double shakeZ = 0.0D;
            if (deathRitual) {
                double shake = minecraft.player.tickCount * 2.15D + index * 1.91D;
                shakeX = Math.sin(shake) * 0.24D;
                shakeY = Math.sin(shake * 1.77D) * 0.13D;
                shakeZ = Math.cos(shake * 1.31D) * 0.24D;
            }
            witness.setPos(
                    master.getX() + Math.cos(angle) * radius + shakeX,
                    master.getY() - 0.35D + (index % 2) * 0.45D + shakeY,
                    master.getZ() + Math.sin(angle) * radius + shakeZ);
        }

        if (deathRitual) {
            if (--mindWitnessSoundTicks <= 0 && !MIND_WITNESSES.isEmpty()) {
                int index = Math.floorMod(
                        minecraft.player.tickCount / 2, MIND_WITNESSES.size());
                ShadowFigureEntity source = MIND_WITNESSES.get(index);
                minecraft.level.playLocalSound(
                        source.getX(), source.getY() + 1.0D, source.getZ(),
                        ModSounds.MASTER_ARCHITECT_TETHER_WAIL.get(),
                        SoundSource.MASTER,
                        1.15F + minecraft.level.random.nextFloat() * 0.35F,
                        0.38F + minecraft.level.random.nextFloat() * 0.18F,
                        false);
                mindWitnessSoundTicks = 2;
            }
            return;
        }

        if (--mindWitnessSoundTicks > 0 || MIND_WITNESSES.isEmpty()) {
            return;
        }
        ShadowFigureEntity source = MIND_WITNESSES.get(
                minecraft.level.random.nextInt(MIND_WITNESSES.size()));
        SoundEvent sound = switch (minecraft.level.random.nextInt(3)) {
            case 0 -> ModSounds.SANITY_WHISPER.get();
            case 1 -> ModSounds.SANITY_FOOTSTEP.get();
            default -> ModSounds.SANITY_THUD.get();
        };
        minecraft.level.playLocalSound(
                source.getX(), source.getY() + 1.0D, source.getZ(),
                sound, SoundSource.AMBIENT,
                0.48F + minecraft.level.random.nextFloat() * 0.22F,
                0.58F + minecraft.level.random.nextFloat() * 0.18F,
                false);
        mindWitnessSoundTicks = switch (healingTier) {
            case 3 -> 45 + minecraft.level.random.nextInt(61);
            case 2 -> 75 + minecraft.level.random.nextInt(91);
            default -> 110 + minecraft.level.random.nextInt(151);
        };
    }

    private static void discardMindWitnesses() {
        for (ShadowFigureEntity witness : MIND_WITNESSES) {
            if (!witness.isRemoved()) {
                witness.discard();
            }
        }
        MIND_WITNESSES.clear();
        mindWitnessSoundTicks = 0;
    }

    private static void clear() {
        MasterArchitectFightMusic.stopAll();
        stopMindScore();
        discardMindWitnesses();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getMusicManager() != null) {
            minecraft.getMusicManager().stopPlaying();
        }
        active = false;
        masterEntityId = -1;
        heartbeatTicks = 0;
        memoryPulseTicks = 0;
        memoryPulseType = -1;
        floodStrength = 0.0F;
        proximity = 0.0F;
        immersion = 0.0F;
        audioFade = 0.0F;
        mindScoreStartTicks = MIND_SCORE_START_DELAY_TICKS;
        mindSessionTicks = 0;
        mindScanPlayed = false;
        telemetryMismatchPlayed = false;
        ivenStacks = 0;
        exposureCycle = 0;
        coreExposed = false;
        deathRitual = false;
        deathRitualTicks = 0;
        healingTier = 1;
        corePulseTicks = 0;
        MOTES.clear();
        clearSuitDialogue();
        if (shaderTintApplied) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            shaderTintApplied = false;
        }
    }

    private static int argb(int alpha, int rgb) {
        return Mth.clamp(alpha, 0, 255) << 24 | rgb & 0x00FFFFFF;
    }

    private static float effectStrength() {
        if (deathRitual) {
            return 0.0F;
        }
        float proximityScale = Mth.lerp(proximity, 0.4F, 1.0F);
        return Mth.clamp(immersion * proximityScale
                * MasterArchitectFloodPolicy.exposureIntensity(exposureCycle)
                * (1.0F + (healingTier - 1) * 0.10F)
                * FrozenDawnConfig.MIND_OVERRIDE_INTENSITY.get().floatValue(),
                0.0F, 1.0F);
    }

    private static boolean isInsideMindSanctuary() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !ThaeIvenMindDimension.isMindLevel(minecraft.level)) {
            return false;
        }
        var master = minecraft.level.getEntity(masterEntityId);
        if (master == null) {
            return false;
        }
        Vec3 sanctuary = master.position().add(
                0.0D, -0.5D, ThaeIvenMindDimension.SANCTUARY_Z_OFFSET);
        Vec3 offset = minecraft.player.position().subtract(sanctuary);
        return offset.x * offset.x + offset.z * offset.z
                <= ThaeIvenMindDimension.SANCTUARY_RADIUS
                        * ThaeIvenMindDimension.SANCTUARY_RADIUS;
    }

    private static void tickMindScore(Minecraft minecraft) {
        if (!ThaeIvenMindDimension.isMindLevel(minecraft.level)) {
            stopMindScore();
            mindScoreStartTicks = MIND_SCORE_START_DELAY_TICKS;
            return;
        }
        float baseVolume = Mth.clamp(
                0.72F + floodStrength * 0.20F + (healingTier - 1) * 0.04F,
                0.0F,
                1.0F);
        if (mindScore != null && !mindScore.isStopped()) {
            mindScore.setTargetVolume(baseVolume);
        } else if (mindScoreStartTicks-- <= 0) {
            mindScore = new MindScore(baseVolume, 1.0F);
            minecraft.getSoundManager().play(mindScore);
            FrozenDawn.LOGGER.info("Started Thae Iven mind score after dimension handoff");
        }

        if (healingTier <= 1) {
            if (mindEscalationScore != null) {
                minecraft.getSoundManager().stop(mindEscalationScore);
                mindEscalationScore = null;
            }
            return;
        }
        float escalationVolume = healingTier >= 3 ? 0.46F : 0.24F;
        if (mindEscalationScore != null && !mindEscalationScore.isStopped()) {
            mindEscalationScore.setTargetVolume(escalationVolume);
            return;
        }
        mindEscalationScore = new MindScore(
                escalationVolume, healingTier >= 3 ? 0.68F : 0.78F);
        minecraft.getSoundManager().play(mindEscalationScore);
    }

    private static void stopMindScore() {
        if (mindScore != null) {
            Minecraft.getInstance().getSoundManager().stop(mindScore);
            mindScore = null;
        }
        if (mindEscalationScore != null) {
            Minecraft.getInstance().getSoundManager().stop(mindEscalationScore);
            mindEscalationScore = null;
        }
    }

    private static final class MindScore extends AbstractTickableSoundInstance {
        private static final float FADE_STEP = 0.035F;
        private float targetVolume;

        private MindScore(float targetVolume, float pitch) {
            super(ModSounds.MASTER_ARCHITECT_MUSIC_MIND.get(),
                    SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.volume = 0.0F;
            this.targetVolume = targetVolume;
            this.pitch = pitch;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        private void setTargetVolume(float targetVolume) {
            this.targetVolume = Mth.clamp(targetVolume, 0.0F, 1.0F);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            volume = Mth.approach(volume, targetVolume, FADE_STEP);
        }
    }

    private record ClientMote(int memoryType, Vec3 position) {
    }
}
