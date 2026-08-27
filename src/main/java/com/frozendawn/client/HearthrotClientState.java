package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.hearthrot.HearthrotPolicy;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.HearthrotPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client mirror and accessibility-aware Hearthrot presentation. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HearthrotClientState {
    private static final ResourceLocation[] VISOR_TEXTURES = {
            null,
            id("textures/gui/hearthrot_visor_1.png"),
            id("textures/gui/hearthrot_visor_2.png"),
            id("textures/gui/hearthrot_visor_3.png"),
            id("textures/gui/hearthrot_visor_4.png")
    };
    private static final ResourceLocation COUGH_RESIDUE_TEXTURE =
            id("textures/gui/hearthrot_cough_residue.png");
    private static int stage;
    private static float progress;
    private static int colonization;
    private static int heartShatterTicks;
    private static int sensorFlickerTicks;
    private static int salvationTicks;
    private static int salvationAge;
    private static int coughResidueTicks;
    private static int coughResidueAge;
    private static int breathCatchTicks;
    private static int breathCatchCooldown;

    private HearthrotClientState() {
    }

    public static void update(HearthrotPayload payload) {
        int previousStage = stage;
        stage = Mth.clamp(payload.stage(), 0, 6);
        progress = Mth.clamp(payload.progress(), 0.0F, 1.0F);
        colonization = Mth.clamp(
                payload.colonization(), 0, HearthrotPolicy.MAX_COLONIZATION);
        if (stage < 4) {
            breathCatchTicks = 0;
            breathCatchCooldown = 0;
        } else if (previousStage < 4 && breathCatchCooldown <= 0) {
            scheduleBreathCatch();
        }
        switch (payload.eventId()) {
            case HearthrotPayload.CONTAMINATION_WARNING -> {
                play(ModSounds.SUIT_HEARTHROT_CONTAMINATION.get(), 1.0F, 1.0F);
                MasterArchitectFloodClient.showWarningSuitDialogue(
                        "ui.frozendawn.suit.hearthrot_contamination");
            }
            case HearthrotPayload.STAGE_ADVANCED -> {
                if (stage >= 2 && stage > previousStage) {
                    heartShatterTicks = 42;
                    play(ModSounds.HEARTHROT_CRYSTALLIZE.get(), 0.92F, 1.0F);
                }
            }
            case HearthrotPayload.COUGH -> {
                playCough();
                if (stage >= 4 && screenEffectsEnabled()) {
                    coughResidueTicks = 60;
                    coughResidueAge = 0;
                }
            }
            case HearthrotPayload.WHEEZE -> playWheeze();
            case HearthrotPayload.BREATH_CATCH ->
                    breathCatchTicks = 20 + Math.max(4, stage) * 2;
            case HearthrotPayload.DEATH_ROLLBACK -> {
                if (stage > 0) {
                    play(ModSounds.HEARTHROT_CRYSTALLIZE.get(), 0.42F, 0.68F);
                }
            }
            default -> {
            }
        }
    }

    public static void showSalvation() {
        salvationTicks = 150;
        salvationAge = 0;
    }

    public static int stage() {
        return stage;
    }

    public static float progress() {
        return progress;
    }

    public static int colonization() {
        return colonization;
    }

    public static int visualColonizationStage() {
        return HearthrotPolicy.visualStage(colonization);
    }

    public static boolean shouldFlickerSensors() {
        return screenEffectsEnabled()
                && sensorFlickerTicks > 0;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (screenEffectsEnabled()) {
            renderVisor(graphics);
            renderCoughResidue(graphics);
        }
        renderHeartShatter(graphics);
        renderLostHeartHusks(graphics);
        renderSalvation(graphics);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        if (heartShatterTicks > 0) {
            heartShatterTicks--;
        }
        if (salvationTicks > 0) {
            salvationTicks--;
            salvationAge++;
        }
        if (sensorFlickerTicks > 0) {
            sensorFlickerTicks--;
        } else if (stage >= 5 && screenEffectsEnabled()) {
            int chance = stage >= 6 ? 110 : 190;
            if (minecraft.level.random.nextInt(chance) == 0) {
                sensorFlickerTicks = stage >= 6
                        ? 10 + minecraft.level.random.nextInt(14)
                        : 5 + minecraft.level.random.nextInt(8);
            }
        }
        if (coughResidueTicks > 0) {
            coughResidueTicks--;
            coughResidueAge++;
        }
        tickBreathingInterruption(minecraft);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        stage = 0;
        progress = 0.0F;
        colonization = 0;
        heartShatterTicks = 0;
        sensorFlickerTicks = 0;
        salvationTicks = 0;
        salvationAge = 0;
        coughResidueTicks = 0;
        coughResidueAge = 0;
        breathCatchTicks = 0;
        breathCatchCooldown = 0;
    }

    public static float breathingVolumeMultiplier() {
        return breathCatchTicks > 0 ? 0.02F : 1.0F;
    }

    private static void renderVisor(GuiGraphics graphics) {
        int visualStage = visualColonizationStage();
        if (visualStage <= 0) {
            return;
        }
        float stageStart = HearthrotPolicy.VISIBLE_THRESHOLDS[visualStage - 1];
        float stageEnd = visualStage >= 4
                ? HearthrotPolicy.MAX_COLONIZATION
                : HearthrotPolicy.VISIBLE_THRESHOLDS[visualStage];
        float withinStage = stageEnd <= stageStart ? 1.0F
                : Mth.clamp((colonization - stageStart) / (stageEnd - stageStart),
                        0.0F, 1.0F);
        float alpha = 0.28F + visualStage * 0.10F + withinStage * 0.10F;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Math.min(0.78F, alpha));
        graphics.blit(
                VISOR_TEXTURES[visualStage],
                0, 0, graphics.guiWidth(), graphics.guiHeight(),
                0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderCoughResidue(GuiGraphics graphics) {
        if (coughResidueTicks <= 0) {
            return;
        }
        float fadeIn = Mth.clamp(coughResidueAge / 5.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(coughResidueTicks / 30.0F, 0.0F, 1.0F);
        float alpha = 0.72F * Math.min(fadeIn, fadeOut);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.92F, 1.0F, 1.0F, alpha);
        graphics.blit(
                COUGH_RESIDUE_TEXTURE,
                0, 0, graphics.guiWidth(), graphics.guiHeight(),
                0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderHeartShatter(GuiGraphics graphics) {
        if (heartShatterTicks <= 0) {
            return;
        }
        float life = heartShatterTicks / 42.0F;
        float spread = 1.0F - life;
        int centerX = graphics.guiWidth() / 2 - 87;
        int centerY = graphics.guiHeight() - 42;
        int alpha = Math.round(255.0F * Math.min(1.0F, life * 2.5F));
        int core = alpha << 24 | 0xA8E6EA;
        int edge = alpha << 24 | 0xDDF9F7;
        graphics.fill(centerX - 4, centerY - 3, centerX, centerY + 4, edge);
        graphics.fill(centerX, centerY - 3, centerX + 4, centerY + 4, edge);
        graphics.fill(centerX - 6, centerY - 1, centerX + 6, centerY + 3, core);
        graphics.fill(centerX - 3, centerY + 3, centerX + 3, centerY + 7, core);
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4.0D + 0.3D;
            int distance = Math.round(spread * (10 + index % 3 * 4));
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int y = centerY + (int) Math.round(Math.sin(angle) * distance);
            graphics.fill(x, y, x + 2, y + 2, edge);
        }
    }

    private static void renderLostHeartHusks(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.player.isCreative()
                || minecraft.player.isSpectator()) {
            return;
        }
        int lost = HearthrotPolicy.maxHealthPenaltyHearts(stage);
        if (lost <= 0) {
            return;
        }
        int left = graphics.guiWidth() / 2 - 91;
        int top = graphics.guiHeight() - 39;
        for (int index = 0; index < lost; index++) {
            int slot = 9 - index;
            drawHeartHusk(graphics, left + slot * 8, top);
        }
    }

    private static void drawHeartHusk(GuiGraphics graphics, int x, int y) {
        int shadow = 0xE81E292D;
        int dead = 0xE8486268;
        int crystal = 0xF0A3D5D7;
        graphics.fill(x + 1, y, x + 4, y + 2, shadow);
        graphics.fill(x + 5, y, x + 8, y + 2, shadow);
        graphics.fill(x, y + 1, x + 9, y + 5, shadow);
        graphics.fill(x + 1, y + 5, x + 8, y + 7, shadow);
        graphics.fill(x + 2, y + 7, x + 7, y + 8, shadow);
        graphics.fill(x + 3, y + 8, x + 6, y + 9, shadow);
        graphics.fill(x + 2, y + 2, x + 7, y + 5, dead);
        graphics.fill(x + 2, y + 5, x + 4, y + 6, dead);
        graphics.fill(x + 5, y + 5, x + 7, y + 6, dead);
        graphics.fill(x + 4, y + 1, x + 5, y + 4, crystal);
        graphics.fill(x + 3, y + 4, x + 5, y + 5, crystal);
        graphics.fill(x + 4, y + 5, x + 6, y + 7, crystal);
    }

    private static void renderSalvation(GuiGraphics graphics) {
        if (salvationTicks <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float fadeIn = Mth.clamp(salvationAge / 18.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(salvationTicks / 30.0F, 0.0F, 1.0F);
        int alpha = Math.round(255.0F * Math.min(fadeIn, fadeOut));
        Component phrase = Component.translatable(
                "overlay.frozendawn.hearthrot.salvation")
                .withStyle(ChatFormatting.ITALIC);
        int barHeight = Math.max(18, graphics.guiHeight() / 11);
        int y = Math.max(barHeight + 34,
                graphics.guiHeight() - barHeight - 58);
        graphics.drawCenteredString(
                minecraft.font, phrase, graphics.guiWidth() / 2, y,
                alpha << 24 | 0xE7F7F7);
    }

    private static void playCough() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        int variant = minecraft.level.random.nextInt(3);
        play(switch (variant) {
            case 1 -> ModSounds.HEARTHROT_COUGH_TWO.get();
            case 2 -> ModSounds.HEARTHROT_COUGH_THREE.get();
            default -> ModSounds.HEARTHROT_COUGH_ONE.get();
        }, Math.min(1.65F, 1.40F + stage * 0.04F),
                0.96F + minecraft.level.random.nextFloat() * 0.06F);
    }

    private static void playWheeze() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        play(ModSounds.HEARTHROT_WHEEZE.get(),
                Math.min(1.55F, 1.28F + stage * 0.04F),
                0.97F + minecraft.level.random.nextFloat() * 0.04F);
    }

    private static void tickBreathingInterruption(Minecraft minecraft) {
        if (stage < 4 || minecraft.level == null) {
            breathCatchTicks = 0;
            breathCatchCooldown = 0;
            return;
        }
        if (breathCatchTicks > 0) {
            breathCatchTicks--;
            if (breathCatchTicks == 0) {
                play(ModSounds.HEARTHROT_BREATH_CATCH.get(), 1.15F, 0.98F);
                scheduleBreathCatch();
            }
            return;
        }
        if (breathCatchCooldown <= 0) {
            scheduleBreathCatch();
            return;
        }
        breathCatchCooldown--;
        if (breathCatchCooldown == 0) {
            breathCatchTicks = 20 + stage * 2;
        }
    }

    private static void scheduleBreathCatch() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || stage < 4) {
            breathCatchCooldown = 0;
            return;
        }
        int minimum = HearthrotPolicy.breathCatchMinimumSeconds(stage) * 20;
        int maximum = HearthrotPolicy.breathCatchMaximumSeconds(stage) * 20;
        breathCatchCooldown = minimum <= 0 || maximum <= 0
                ? 0 : minecraft.level.random.nextInt(minimum, maximum + 1);
    }

    private static void play(
            net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, path);
    }

    private static boolean screenEffectsEnabled() {
        return FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                && FrozenDawnConfig.ENABLE_FLOOD_SCREEN_EFFECTS.get();
    }
}
