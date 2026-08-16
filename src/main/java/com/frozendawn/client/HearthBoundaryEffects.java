package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.HearthBoundaryEffectPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client-local warning audio plus a short non-directive Orsathae impact. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HearthBoundaryEffects {
    private static final int PULSE_DURATION_TICKS = 34;
    private static final int SHAKE_DURATION_TICKS = 18;
    private static final int MAEVE_SHAKE_DURATION_TICKS = 52;
    private static final int MAEVE_DEATH_SHAKE_DURATION_TICKS = 100;
    private static final int RESCUE_DURATION_TICKS = 56;
    private static final int WORLD_EVENT_SILENCE_TICKS = 140;
    private static final int WORLD_EVENT_OMEN_TICKS = 260;
    private static final int COLLAPSE_RESPONSE_TICKS = 480;
    private static final int BLOOM_RUMBLE_SHAKE_TICKS = 60;
    private static final int BLOOM_IMPACT_SHAKE_TICKS = 36;
    private static final int AGGREGATE_FORMATION_SHAKE_TICKS = 96;
    private static final int AGGREGATE_IMPACT_SHAKE_TICKS = 28;

    private static int pulseTicks;
    private static int shakeTicks;
    private static int shakeDuration = SHAKE_DURATION_TICKS;
    private static int rescueTicks;
    private static int silenceTicks;
    private static int omenTicks;

    private HearthBoundaryEffects() {
    }

    public static void trigger(HearthBoundaryEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.WARNING) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.HEARTH_BOUNDARY_WARNING.get(), 0.92F, 1.15F));
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.MAEVE_BREAK) {
            pulseTicks = PULSE_DURATION_TICKS;
            shakeTicks = MAEVE_SHAKE_DURATION_TICKS;
            shakeDuration = MAEVE_SHAKE_DURATION_TICKS;
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.LAST_WITNESS_RESCUE) {
            rescueTicks = RESCUE_DURATION_TICKS;
            shakeTicks = 24;
            shakeDuration = 24;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.LAST_WITNESS_SAVE.get(), 1.0F, 1.0F));
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.MAEVE_DEATH) {
            shakeTicks = MAEVE_DEATH_SHAKE_DURATION_TICKS;
            shakeDuration = MAEVE_DEATH_SHAKE_DURATION_TICKS;
            return;
        }
        if (payload.effectType()
                == HearthBoundaryEffectPayload.WORLD_EVENT_SILENCE) {
            silenceTicks = WORLD_EVENT_SILENCE_TICKS;
            omenTicks = 0;
            stopWorldAmbience(minecraft);
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.WORLD_EVENT_OMEN) {
            omenTicks = WORLD_EVENT_OMEN_TICKS;
            stopCompetingAudio(minecraft);
            return;
        }
        if (payload.effectType()
                == HearthBoundaryEffectPayload.WORLD_EVENT_COLLAPSE_RESPONSE) {
            silenceTicks = 0;
            omenTicks = COLLAPSE_RESPONSE_TICKS;
            stopCompetingAudio(minecraft);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.THAE_IVEN_HEART_COLLAPSE_RESPONSE.get(), 1.0F, 1.0F));
            return;
        }
        if (payload.effectType()
                == HearthBoundaryEffectPayload.WORLD_EVENT_BIOLOGICAL_WARNING) {
            omenTicks = Math.max(omenTicks, 140);
            stopCompetingAudio(minecraft);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.SUIT_BIOLOGICAL_ACTIVITY_WARNING.get(), 1.0F, 1.0F));
            MasterArchitectFloodClient.showWarningSuitDialogue(
                    "ui.frozendawn.suit.biological_activity_warning");
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.UNDONE_CONTACT) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.SUIT_UNDONE_CONTACT.get(), 1.0F, 1.0F));
            MasterArchitectFloodClient.showSuitDialogue(
                    "ui.frozendawn.suit.undone_contact");
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.BLOOM_CONTACT) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.SUIT_BLOOM_CONTACT.get(), 1.0F, 1.0F));
            MasterArchitectFloodClient.showSuitDialogue(
                    "ui.frozendawn.suit.bloom_contact");
            return;
        }
        if (payload.effectType()
                == HearthBoundaryEffectPayload.BLOOM_ERUPTION_RUMBLE) {
            shakeTicks = BLOOM_RUMBLE_SHAKE_TICKS;
            shakeDuration = BLOOM_RUMBLE_SHAKE_TICKS;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.BLOOM_DRONE.get(), 0.56F, 1.45F));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.BLOOM_CRACK.get(), 0.64F, 0.72F));
            return;
        }
        if (payload.effectType()
                == HearthBoundaryEffectPayload.BLOOM_ERUPTION_IMPACT) {
            shakeTicks = BLOOM_IMPACT_SHAKE_TICKS;
            shakeDuration = BLOOM_IMPACT_SHAKE_TICKS;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.BLOOM_CORE_BREAK.get(), 0.72F, 1.8F));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.BLOOM_SPORE_GROWTH_START.get(), 0.76F, 1.65F));
            return;
        }
        if (payload.effectType() >= HearthBoundaryEffectPayload.REMNANT_RADIO_ROOM
                && payload.effectType() <= HearthBoundaryEffectPayload.REMNANT_RADIO_FORGIVE) {
            String key = switch (payload.effectType()) {
                case HearthBoundaryEffectPayload.REMNANT_RADIO_WARM ->
                        "ui.frozendawn.remnant.radio_warm";
                case HearthBoundaryEffectPayload.REMNANT_RADIO_ALONE ->
                        "ui.frozendawn.remnant.radio_alone";
                case HearthBoundaryEffectPayload.REMNANT_RADIO_FORGIVE ->
                        "ui.frozendawn.remnant.radio_forgive";
                default -> "ui.frozendawn.remnant.radio_room";
            };
            MasterArchitectFloodClient.showRadioDialogue(key);
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.REMNANT_RADIO_CUTOFF) {
            MasterArchitectFloodClient.clearRadioDialogue();
            return;
        }
        if (payload.effectType()
                == HearthBoundaryEffectPayload.AGGREGATE_FORMATION_RUMBLE) {
            shakeTicks = AGGREGATE_FORMATION_SHAKE_TICKS;
            shakeDuration = AGGREGATE_FORMATION_SHAKE_TICKS;
            return;
        }
        if (payload.effectType() == HearthBoundaryEffectPayload.AGGREGATE_IMPACT) {
            shakeTicks = AGGREGATE_IMPACT_SHAKE_TICKS;
            shakeDuration = AGGREGATE_IMPACT_SHAKE_TICKS;
            return;
        }
        if (payload.effectType() >= HearthBoundaryEffectPayload.AGGREGATE_DEPOSIT_DIAGNOSTIC
                && payload.effectType()
                <= HearthBoundaryEffectPayload.AGGREGATE_RESOLVED_DIAGNOSTIC) {
            int line = payload.effectType()
                    - HearthBoundaryEffectPayload.AGGREGATE_DEPOSIT_DIAGNOSTIC;
            var sound = switch (line) {
                case 1 -> ModSounds.AGGREGATE_OSSUARY_TTS.get();
                case 2 -> ModSounds.AGGREGATE_GESTATION_TTS.get();
                case 3 -> ModSounds.AGGREGATE_RESOLVED_TTS.get();
                default -> ModSounds.AGGREGATE_DEPOSIT_TTS.get();
            };
            String key = switch (line) {
                case 1 -> "ui.frozendawn.suit.aggregate_ossuary";
                case 2 -> "ui.frozendawn.suit.aggregate_gestation";
                case 3 -> "ui.frozendawn.suit.aggregate_resolved";
                default -> "ui.frozendawn.suit.aggregate_deposit";
            };
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 1.0F));
            MasterArchitectFloodClient.showSuitDialogue(key);
            return;
        }
        if (payload.effectType() != HearthBoundaryEffectPayload.ORSATHAE) {
            return;
        }

        pulseTicks = PULSE_DURATION_TICKS;
        shakeTicks = SHAKE_DURATION_TICKS;
        shakeDuration = SHAKE_DURATION_TICKS;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                ModSounds.HEARTH_BOUNDARY_ORSATHAE.get(), 0.95F, 1.25F));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                ModSounds.HEARTH_BOUNDARY_ORSATHAE.get(), 0.76F, 0.62F));
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (rescueTicks > 0) {
            renderLastWitnessRescue(graphics);
        }
        if (pulseTicks <= 0) {
            return;
        }
        int elapsed = PULSE_DURATION_TICKS - pulseTicks;
        float fade = Mth.clamp(pulseTicks / (float) PULSE_DURATION_TICKS, 0.0F, 1.0F);
        float wave = 0.45F + 0.55F * Math.abs(Mth.sin(elapsed * 0.46F));
        int washAlpha = Math.round(68.0F * fade * wave);
        int edgeAlpha = Math.round(128.0F * fade);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        graphics.fill(0, 0, width, height, argb(washAlpha, 0x00151A1D));
        int edge = 3;
        int edgeColor = argb(edgeAlpha, 0x001BC7CF);
        graphics.fill(0, 0, width, edge, edgeColor);
        graphics.fill(0, height - edge, width, height, edgeColor);
        graphics.fill(0, 0, edge, height, edgeColor);
        graphics.fill(width - edge, 0, width, height, edgeColor);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        if (pulseTicks > 0) {
            pulseTicks--;
        }
        if (shakeTicks > 0) {
            shakeTicks--;
        }
        if (rescueTicks > 0) {
            rescueTicks--;
        }
        if (silenceTicks > 0) {
            silenceTicks--;
            if (silenceTicks % 5 == 0) {
                stopWorldAmbience(minecraft);
            }
        }
        if (omenTicks > 0) {
            omenTicks--;
            if (omenTicks % 5 == 0) {
                stopCompetingAudio(minecraft);
            }
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (shakeTicks <= 0 || minecraft.level == null) {
            return;
        }
        float remaining = shakeTicks / (float) Math.max(1, shakeDuration);
        float strength = remaining;
        float magnitude = shakeDuration == MAEVE_SHAKE_DURATION_TICKS
                ? 2.35F : shakeDuration == MAEVE_DEATH_SHAKE_DURATION_TICKS
                        ? 4.8F : shakeDuration == BLOOM_IMPACT_SHAKE_TICKS
                        ? 5.2F : shakeDuration == AGGREGATE_IMPACT_SHAKE_TICKS
                        ? 3.8F : rescueTicks > 0 ? 0.55F : 1.0F;
        if (shakeDuration == BLOOM_RUMBLE_SHAKE_TICKS) {
            float progress = 1.0F - remaining;
            strength = 0.25F + progress * 0.75F;
            magnitude = 0.25F + progress * 1.35F;
        }
        if (shakeDuration == AGGREGATE_FORMATION_SHAKE_TICKS) {
            float progress = 1.0F - remaining;
            strength = 0.18F + progress * 0.82F;
            magnitude = 0.3F + progress * 2.0F;
        }
        double time = minecraft.level.getGameTime() + shakeTicks * 0.37D;
        float pitch = (float) (Math.sin(time * 3.7D) * 0.72D * strength);
        float yaw = (float) (Math.cos(time * 4.9D) * 0.92D * strength);
        event.setPitch(event.getPitch() + pitch * magnitude);
        event.setYaw(event.getYaw() + yaw * magnitude);
    }

    private static void renderLastWitnessRescue(GuiGraphics graphics) {
        int elapsed = RESCUE_DURATION_TICKS - rescueTicks;
        float arrival = Mth.clamp(elapsed / 12.0F, 0.0F, 1.0F);
        float release = Mth.clamp(rescueTicks / 32.0F, 0.0F, 1.0F);
        float strength = Math.min(arrival, release);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        graphics.fill(0, 0, width, height,
                argb(Math.round(112.0F * strength), 0x00040B12));
        for (int ring = 0; ring < 9; ring++) {
            float phase = Mth.clamp(
                    strength * 1.3F - ring * 0.055F, 0.0F, 1.0F);
            int halfWidth = Math.max(3,
                    Math.round((width * 0.46F - ring * 13.0F) * phase));
            int halfHeight = Math.max(3,
                    Math.round((height * 0.42F - ring * 8.0F) * phase));
            int thickness = ring % 3 == 0 ? 3 : 1;
            int alpha = Math.round((145.0F - ring * 8.0F) * strength);
            int color = argb(alpha, ring % 2 == 0 ? 0x0016C7DF : 0x000A4A67);
            graphics.fill(centerX - halfWidth, centerY - halfHeight,
                    centerX + halfWidth, centerY - halfHeight + thickness, color);
            graphics.fill(centerX - halfWidth, centerY + halfHeight - thickness,
                    centerX + halfWidth, centerY + halfHeight, color);
            graphics.fill(centerX - halfWidth, centerY - halfHeight,
                    centerX - halfWidth + thickness, centerY + halfHeight, color);
            graphics.fill(centerX + halfWidth - thickness, centerY - halfHeight,
                    centerX + halfWidth, centerY + halfHeight, color);
        }
        int coreAlpha = Math.round(220.0F * strength);
        int coreColor = argb(coreAlpha, 0x0068EFFF);
        graphics.fill(centerX - 1, centerY - 28,
                centerX + 2, centerY + 29, coreColor);
        graphics.fill(centerX - 18, centerY - 2,
                centerX + 19, centerY + 2, coreColor);
        graphics.fill(centerX - 10, centerY - 13,
                centerX + 11, centerY - 10, coreColor);
        graphics.fill(centerX - 10, centerY + 10,
                centerX + 11, centerY + 13, coreColor);
    }

    private static int argb(int alpha, int rgb) {
        return Mth.clamp(alpha, 0, 255) << 24 | rgb & 0x00FFFFFF;
    }

    private static void stopWorldAmbience(Minecraft minecraft) {
        minecraft.getMusicManager().stopPlaying();
        minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
        minecraft.getSoundManager().stop(null, SoundSource.AMBIENT);
        minecraft.getSoundManager().stop(null, SoundSource.WEATHER);
        minecraft.getSoundManager().stop(null, SoundSource.RECORDS);
        minecraft.getSoundManager().stop(null, SoundSource.VOICE);
    }

    private static void stopCompetingAudio(Minecraft minecraft) {
        minecraft.getMusicManager().stopPlaying();
        minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
        minecraft.getSoundManager().stop(null, SoundSource.AMBIENT);
        minecraft.getSoundManager().stop(null, SoundSource.WEATHER);
        minecraft.getSoundManager().stop(null, SoundSource.RECORDS);
    }
}
