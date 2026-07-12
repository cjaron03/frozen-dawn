package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.ThaevenTransmissionType;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.OpenThaevenTransmissionPayload;
import com.frozendawn.network.ThaevenTransmissionResultPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Non-screen, breakable first-contact presentation for Thae Iven.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ThaevenTransmissionOverlay {
    private static final int INPUT_GRACE_TICKS = 8;
    private static final int CAMERA_SETTLE_TICKS = 24;
    private static final int LOOK_AWAY_GRACE_TICKS = 32;
    private static final double MAX_DISTANCE_SQUARED = 34.0D * 34.0D;
    private static final double LOOK_AWAY_DOT = 0.18D;

    private static boolean active;
    private static int sessionId;
    private static int sourceEntityId;
    private static int ticks;
    private static int durationTicks;
    private static float startingHealth;
    private static ThaevenTransmissionType transmissionType = ThaevenTransmissionType.VEL_THAE;
    private static boolean secondaryCuePlayed;
    private static boolean resolveCuePlayed;
    private static SoundInstance contactSound;

    private ThaevenTransmissionOverlay() {
    }

    public static void start(OpenThaevenTransmissionPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        stopContactSound(mc);
        active = true;
        sessionId = payload.sessionId();
        sourceEntityId = payload.sourceEntityId();
        ticks = 0;
        transmissionType = ThaevenTransmissionType.fromNetworkId(payload.transmissionType());
        durationTicks = Math.max(60, payload.durationTicks());
        startingHealth = mc.player == null
                ? 0.0F
                : mc.player.getHealth() + mc.player.getAbsorptionAmount();
        secondaryCuePlayed = false;
        resolveCuePlayed = false;
        if (mc.getSoundManager() != null) {
            contactSound = SimpleSoundInstance.forUI(
                    ModSounds.THAEVEN_CONTACT.get(), 1.0F, 0.88F);
            mc.getSoundManager().play(contactSound);
        }
    }

    public static void cancelFromServer(int cancelledSessionId) {
        if (!active || sessionId != cancelledSessionId) {
            return;
        }
        stop(false, false, true);
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isTransmissionSound(ResourceLocation location) {
        return FrozenDawn.MOD_ID.equals(location.getNamespace())
                && location.getPath().startsWith("ui.thaeven_");
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float fade = envelope(ticks, durationTicks);
        float pulse = 0.88F + 0.12F * (float) Math.sin(ticks * 0.12F);
        int barHeight = Math.max(18, height / 11);
        int barAlpha = Math.round(185.0F * fade);
        graphics.fill(0, 0, width, barHeight, argb(barAlpha, 3, 8, 12));
        graphics.fill(0, height - barHeight, width, height, argb(barAlpha, 3, 8, 12));

        int edgeAlpha = Math.round(55.0F * fade * pulse);
        int edgeWidth = Math.max(8, width / 18);
        graphics.fill(0, barHeight, edgeWidth, height - barHeight,
                argb(edgeAlpha, 30, 91, 108));
        graphics.fill(width - edgeWidth, barHeight, width, height - barHeight,
                argb(edgeAlpha, 30, 91, 108));

        Font font = mc.font;
        int contentY = Math.max(barHeight + 16, height - barHeight - 76);
        int maxTextWidth = Math.min(520, Math.max(180, width - 56));
        Component label = Component.translatable("overlay.frozendawn.thaeven.label")
                .withStyle(ChatFormatting.DARK_AQUA);
        drawCenteredWrapped(graphics, font, label, width, contentY,
                maxTextWidth, withAlpha(0xFF60D8DF, fade * 0.78F));

        TransmissionBeat beat = currentBeat();
        float beatFade = beatFade(beat.startedAt());
        int phraseY = contentY + 18;
        if (beat.phrase() != null) {
            Component phrase = Component.translatable(beat.phrase())
                    .withStyle(ChatFormatting.ITALIC);
            phraseY += drawCenteredWrapped(graphics, font, phrase, width, phraseY,
                    maxTextWidth, withAlpha(0xFFE7F7F7, fade * beatFade));
        }
        Component sensation = Component.translatable(beat.sensation());
        drawCenteredWrapped(graphics, font, sensation, width, phraseY + 8,
                maxTextWidth, withAlpha(0xFF8FB7BF, fade * beatFade * 0.9F));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop(false, true, false);
            return;
        }

        Entity source = mc.level.getEntity(sourceEntityId);
        if (source == null || !source.isAlive()
                || source.distanceToSqr(mc.player) > MAX_DISTANCE_SQUARED) {
            stop(false, true, true);
            return;
        }

        if (ticks > INPUT_GRACE_TICKS && shouldBreakContact(mc, source)) {
            stop(false, true, true);
            return;
        }

        if (ticks < CAMERA_SETTLE_TICKS) {
            settleCamera(mc, source);
        }

        if (transmissionType == ThaevenTransmissionType.ORSHA_RECOGNITION
                && !secondaryCuePlayed && ticks >= 52) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.THAEVEN_ORSHA.get(), 1.0F, 0.82F));
            secondaryCuePlayed = true;
        }
        if (!resolveCuePlayed && ticks >= durationTicks - 18) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.THAEVEN_RESOLVE.get(), 1.0F, 0.72F));
            resolveCuePlayed = true;
        }

        ticks++;
        if (ticks >= durationTicks) {
            stop(true, true, false);
        }
    }

    private static boolean shouldBreakContact(Minecraft mc, Entity source) {
        if (mc.screen != null
                || mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown()
                || mc.options.keyShift.isDown()
                || mc.options.keySprint.isDown()
                || mc.options.keyAttack.isDown()
                || mc.options.keyUse.isDown()) {
            return true;
        }

        float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (currentHealth + 0.01F < startingHealth || mc.player.hurtTime > 0) {
            return true;
        }

        if (ticks <= LOOK_AWAY_GRACE_TICKS) {
            return false;
        }
        Vec3 towardSource = source.getEyePosition().subtract(mc.player.getEyePosition());
        return towardSource.lengthSqr() > 0.001D
                && mc.player.getViewVector(1.0F).dot(towardSource.normalize()) < LOOK_AWAY_DOT;
    }

    private static void settleCamera(Minecraft mc, Entity source) {
        Vec3 delta = source.getEyePosition().subtract(mc.player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.001D) {
            return;
        }
        float targetYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float targetPitch = (float) (-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        float settle = 0.055F * smooth(ticks / (float) CAMERA_SETTLE_TICKS);
        mc.player.setYRot(Mth.rotLerp(settle, mc.player.getYRot(), targetYaw));
        mc.player.setXRot(Mth.lerp(settle, mc.player.getXRot(), targetPitch));
    }

    private static TransmissionBeat currentBeat() {
        if (transmissionType == ThaevenTransmissionType.ORSHA_RECOGNITION) {
            if (ticks < 44) {
                return new TransmissionBeat(null,
                        "overlay.frozendawn.thaeven.orsa.presence", 0);
            }
            if (ticks < 104) {
                return new TransmissionBeat("overlay.frozendawn.thaeven.orsa.word",
                        "overlay.frozendawn.thaeven.orsa.warmth", 44);
            }
            if (ticks < 154) {
                return new TransmissionBeat("overlay.frozendawn.thaeven.orsa.word",
                        "overlay.frozendawn.thaeven.orsa.withdrawal", 104);
            }
            return new TransmissionBeat("overlay.frozendawn.thaeven.orsa.recognition",
                    "overlay.frozendawn.thaeven.orsa.uncertainty", 154);
        }

        if (ticks < 44) {
            return new TransmissionBeat(null,
                    "overlay.frozendawn.thaeven.neutral.presence", 0);
        }
        if (ticks < 112) {
            return new TransmissionBeat("overlay.frozendawn.thaeven.neutral.word",
                    "overlay.frozendawn.thaeven.neutral.warmth", 44);
        }
        return new TransmissionBeat("overlay.frozendawn.thaeven.neutral.question",
                "overlay.frozendawn.thaeven.neutral.openness", 112);
    }

    private static void stop(boolean completed, boolean notifyServer, boolean interruptedSound) {
        if (!active) {
            return;
        }
        int endedSessionId = sessionId;
        Minecraft mc = Minecraft.getInstance();
        active = false;
        stopContactSound(mc);
        if (interruptedSound && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.THAEVEN_INTERRUPT.get(), 1.0F, 0.8F));
        }
        if (notifyServer) {
            PacketDistributor.sendToServer(
                    new ThaevenTransmissionResultPayload(endedSessionId, completed));
        }
    }

    private static void stopContactSound(Minecraft mc) {
        if (contactSound != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(contactSound);
        }
        contactSound = null;
    }

    private static int drawCenteredWrapped(GuiGraphics graphics, Font font, Component text,
                                           int screenWidth, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int lineY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, (screenWidth - font.width(line)) / 2,
                    lineY, color, false);
            lineY += font.lineHeight + 2;
        }
        return lines.size() * (font.lineHeight + 2);
    }

    private static float envelope(int age, int total) {
        float in = smooth(age / 12.0F);
        float out = 1.0F - smooth((age - (total - 22.0F)) / 22.0F);
        return Mth.clamp(in * out, 0.0F, 1.0F);
    }

    private static float beatFade(int startedAt) {
        return smooth((ticks - startedAt) / 10.0F);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static int withAlpha(int color, float alpha) {
        return (Mth.clamp(Math.round(255.0F * alpha), 0, 255) << 24)
                | (color & 0x00FFFFFF);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (Mth.clamp(alpha, 0, 255) << 24)
                | (red << 16)
                | (green << 8)
                | blue;
    }

    private record TransmissionBeat(String phrase, String sensation, int startedAt) {
    }
}
