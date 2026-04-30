package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.mixin.GameRendererAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class OrsaAwakeningIntro {

    private static final int START_DELAY_TICKS = 40;
    private static final int TOTAL_TICKS = 360;
    private static final int LOGO_IN_START = 10;
    private static final int STRIPE_IN_START = 24;
    private static final int LIFT_START = 70;
    private static final int DIAGNOSTIC_START = 96;
    private static final int FADE_START = 310;
    private static final int TEMPERATURE_RETURN_START = 338;
    private static final ResourceLocation AWAKENING_BLUR_EFFECT =
            ResourceLocation.withDefaultNamespace("shaders/post/blur.json");
    private static final float[] STRIPE_Y_RATIOS = {214.0F / 362.0F, 241.0F / 362.0F, 266.0F / 362.0F};
    private static final float[] STRIPE_HEIGHT_RATIOS = {28.0F / 362.0F, 26.0F / 362.0F, 26.0F / 362.0F};
    private static final int[] STRIPE_DELAYS = {0, 3, 6};

    private static final String[] DIAGNOSTIC_LINES = {
            "VITALS: PULSE ELEVATED / RESPIRATION IRREGULAR",
            "TRAUMA: MINOR CRANIAL CONCUSSION",
            "FINAL SURFACE SHUTTLE: DEPARTED 03:17 UTC",
            "CURRENT TIME: 88:14 LOCAL / CLOCK COMPROMISED"
    };

    private static int pendingDelayTicks;
    private static int ticks;
    private static boolean active;
    private static boolean blackoutApplied;
    private static boolean soundPlayed;
    private static boolean ringingPlayed;
    private static boolean temperatureBlinkStarted;
    private static boolean blurManaged;

    private OrsaAwakeningIntro() {
    }

    public static void start() {
        pendingDelayTicks = START_DELAY_TICKS;
        ticks = 0;
        active = true;
        blackoutApplied = false;
        soundPlayed = false;
        ringingPlayed = false;
        temperatureBlinkStarted = false;
        blurManaged = false;
    }

    public static boolean shouldSuppressSurvivalHud() {
        return active && ticks < TEMPERATURE_RETURN_START;
    }

    public static boolean shouldSuppressNonIntroSound(ResourceLocation soundLocation) {
        if (!active) {
            return false;
        }
        if (!soundLocation.getNamespace().equals(FrozenDawn.MOD_ID)) {
            return true;
        }
        String path = soundLocation.getPath();
        return !path.equals("ui.orsa_awakening_voice") && !path.equals("ui.orsa_awakening_ring");
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null || mc.options.hideGui) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (pendingDelayTicks > 0) {
            graphics.fill(0, 0, width, height, 0xFF000000);
            return;
        }

        float fadeOut = 1.0F - smooth((ticks - FADE_START) / 24.0F);
        float coldWake = smooth(ticks / 24.0F) * fadeOut;
        float jitter = (float) Math.sin(ticks * 1.73F) * 1.5F;

        renderWakeWash(graphics, width, height, coldWake, fadeOut);
        renderMasthead(graphics, width, height, fadeOut, jitter);
        renderDiagnostics(graphics, mc.font, width, height, fadeOut);
        renderTemperatureReturn(graphics, mc.font);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            disableBlur(mc);
            active = false;
            return;
        }

        freezePlayerInput(mc);
        if (!blackoutApplied) {
            stopAllSounds(mc);
            blackoutApplied = true;
        }

        if (pendingDelayTicks > 0) {
            pendingDelayTicks--;
            return;
        }

        syncBlur(mc);

        if (!ringingPlayed && ticks >= FADE_START - 26) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ORSA_AWAKENING_RING.get(), 1.0F));
            ringingPlayed = true;
        }

        if (!soundPlayed) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ORSA_AWAKENING_VOICE.get(), 1.0F));
            soundPlayed = true;
        }

        ticks++;

        if (!temperatureBlinkStarted && ticks >= TEMPERATURE_RETURN_START) {
            TemperatureHud.startIntroBlink();
            temperatureBlinkStarted = true;
        }

        if (ticks >= TOTAL_TICKS) {
            disableBlur(mc);
            active = false;
        }
    }

    private static void freezePlayerInput(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.player.xxa = 0.0F;
        mc.player.zza = 0.0F;
        mc.player.setSprinting(false);
    }

    private static void stopAllSounds(Minecraft mc) {
        for (SoundSource source : SoundSource.values()) {
            mc.getSoundManager().stop(null, source);
        }
    }

    private static void syncBlur(Minecraft mc) {
        float blurAmount = blurAmount();
        if (blurAmount <= 0.0F) {
            return;
        }

        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain currentEffect = accessor.frozendawn$getPostEffect();
        boolean blurActive = currentEffect != null && AWAKENING_BLUR_EFFECT.toString().equals(currentEffect.getName());
        if (currentEffect != null && !blurActive) {
            return;
        }

        if (!blurActive) {
            mc.gameRenderer.loadEffect(AWAKENING_BLUR_EFFECT);
            currentEffect = accessor.frozendawn$getPostEffect();
            blurActive = currentEffect != null && AWAKENING_BLUR_EFFECT.toString().equals(currentEffect.getName());
        }

        if (blurActive && currentEffect != null) {
            blurManaged = true;
            currentEffect.setUniform("Radius", 4.0F + blurAmount * 8.0F);
        }
    }

    private static void disableBlur(Minecraft mc) {
        if (!blurManaged) {
            return;
        }
        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain currentEffect = accessor.frozendawn$getPostEffect();
        if (currentEffect != null && AWAKENING_BLUR_EFFECT.toString().equals(currentEffect.getName())) {
            accessor.frozendawn$shutdownEffect();
        }
        blurManaged = false;
    }

    private static float blurAmount() {
        if (pendingDelayTicks > 0 || ticks < FADE_START) {
            return 0.0F;
        }
        return 1.0F - smooth((ticks - FADE_START) / 44.0F);
    }

    private static void renderWakeWash(GuiGraphics graphics, int width, int height, float coldWake, float fadeOut) {
        float blackAlpha = 1.0F - smooth((ticks - FADE_START) / 36.0F);
        graphics.fill(0, 0, width, height, withAlpha(0xFF000000, blackAlpha));
        graphics.fill(0, 0, width, height, withAlpha(0xFF8FD6FF, 0.06F * coldWake * blackAlpha));

        int edge = Math.max(26, height / 7);
        graphics.fillGradient(0, 0, width, edge, withAlpha(0xFF7ED9FF, 0.12F * fadeOut), 0x00000000);
        graphics.fillGradient(0, height - edge, width, height, 0x00000000, withAlpha(0xFF06151A, 0.45F * fadeOut));

        int side = Math.max(28, width / 9);
        graphics.fillGradient(0, 0, side, height, withAlpha(0xFF000000, 0.52F * fadeOut), 0x00000000);
        graphics.fillGradient(width - side, 0, width, height, 0x00000000, withAlpha(0xFF000000, 0.52F * fadeOut));

        int scanAlpha = (int) (32.0F * fadeOut);
        for (int y = Math.floorMod(ticks, 6); y < height; y += 6) {
            graphics.fill(0, y, width, y + 1, (scanAlpha << 24) | 0x6EA4AF);
        }
    }

    private static void renderMasthead(GuiGraphics graphics, int width, int height, float fadeOut, float jitter) {
        float logoIn = smooth((ticks - LOGO_IN_START) / 24.0F);
        if (logoIn <= 0.0F || fadeOut <= 0.0F) {
            return;
        }

        float lift = smooth((ticks - LIFT_START) / 34.0F);
        int largeWidth = Math.min((int) (width * 0.56F), 480);
        int compactWidth = Math.min((int) (width * 0.34F), 230);
        int logoWidth = (int) Mth.lerp(lift, largeWidth, compactWidth);
        int logoHeight = Math.max(1, (int) (logoWidth / OrsaLogoRenderer.mastheadAspectRatio()));
        int startY = (int) (height * 0.37F - logoHeight / 2.0F);
        int endY = Math.max(18, (int) (height * 0.10F));
        int logoY = (int) Mth.lerp(lift, startY, endY);
        int logoX = width / 2 - logoWidth / 2 + (int) (jitter * (1.0F - lift) * 0.6F);

        float alpha = logoIn * fadeOut;
        int glowColor = withAlpha(0xFF3DE1F6, 0.08F * alpha);
        graphics.fill(logoX + 8, logoY + 8, logoX + logoWidth - 8, logoY + logoHeight - 8, glowColor);

        OrsaLogoRenderer.drawAwakeningBase(graphics, logoX, logoY, logoWidth, logoHeight, alpha);
        renderAwakeningStripes(graphics, logoX, logoY, logoWidth, logoHeight, alpha);
    }

    private static void renderAwakeningStripes(GuiGraphics graphics, int logoX, int logoY, int logoWidth,
                                               int logoHeight, float baseAlpha) {
        int stripeWidth = Math.max(1, Math.round(logoWidth * (409.0F / 817.0F)));
        int rightX = logoX + logoWidth - stripeWidth;

        for (int row = 0; row < STRIPE_Y_RATIOS.length; row++) {
            float progress = smooth((ticks - STRIPE_IN_START - STRIPE_DELAYS[row]) / 18.0F);
            if (progress <= 0.0F) {
                continue;
            }
            int stripeY = logoY + Math.round(logoHeight * STRIPE_Y_RATIOS[row]);
            int stripeHeight = Math.max(1, Math.round(logoHeight * STRIPE_HEIGHT_RATIOS[row]));
            int travel = Math.round(stripeWidth * 0.64F * (1.0F - progress));
            float stripeAlpha = baseAlpha * progress;
            OrsaLogoRenderer.drawAwakeningStripe(graphics, row, false, logoX - travel, stripeY, stripeWidth,
                    stripeHeight, stripeAlpha);
            OrsaLogoRenderer.drawAwakeningStripe(graphics, row, true, rightX + travel, stripeY, stripeWidth,
                    stripeHeight, stripeAlpha);
        }
    }

    private static void renderDiagnostics(GuiGraphics graphics, Font font, int width, int height, float fadeOut) {
        float panelIn = smooth((ticks - DIAGNOSTIC_START) / 28.0F);
        float alpha = panelIn * fadeOut;
        if (alpha <= 0.0F) {
            return;
        }

        int panelWidth = Math.min(width - 36, 430);
        int panelHeight = Math.min(height / 2, 126);
        int x = width / 2 - panelWidth / 2;
        int y = Math.min(height - panelHeight - 22, Math.max(96, height / 2 - 6));

        graphics.fill(x, y, x + panelWidth, y + panelHeight, withAlpha(0xFF061B20, 0.72F * alpha));
        graphics.fill(x, y, x + panelWidth, y + 2, withAlpha(0xFF8BEAF5, 0.75F * alpha));
        graphics.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, withAlpha(0xFFFFCE00, 0.65F * alpha));
        graphics.fill(x + 10, y + 22, x + panelWidth - 10, y + 23, withAlpha(0xFF8BEAF5, 0.22F * alpha));

        graphics.drawString(font, "FIRST AWAKENING TRIAGE", x + 12, y + 9, withAlpha(0xFFE9FBFF, alpha), false);
        graphics.drawString(font, "SUBJECT READOUT", x + panelWidth - 12 - font.width("SUBJECT READOUT"), y + 9,
                withAlpha(0xFFFFD83D, alpha), false);

        int visible = Mth.clamp((ticks - DIAGNOSTIC_START - 8) / 10 + 1, 0, DIAGNOSTIC_LINES.length);
        int lineY = y + 32;
        for (int i = 0; i < visible; i++) {
            int lineAlpha = withAlpha(i == visible - 1 && ((ticks / 4) & 1) == 0 ? 0xFFFFD83D : 0xFFBDEFF4, alpha);
            graphics.drawString(font, DIAGNOSTIC_LINES[i], x + 13, lineY + i * 13, lineAlpha, false);
        }

        int barX = x + 13;
        int barY = y + panelHeight - 17;
        int barWidth = panelWidth - 26;
        int progress = (int) (barWidth * Mth.clamp((ticks - DIAGNOSTIC_START) / 96.0F, 0.0F, 1.0F));
        graphics.fill(barX, barY, barX + barWidth, barY + 5, withAlpha(0xFF153238, alpha));
        graphics.fill(barX, barY, barX + progress, barY + 5, withAlpha(0xFF7EE8F3, alpha));
    }

    private static void renderTemperatureReturn(GuiGraphics graphics, Font font) {
        float cue = smooth((ticks - TEMPERATURE_RETURN_START) / 16.0F);
        if (cue <= 0.0F) {
            return;
        }

        float blink = ((ticks / 4) & 1) == 0 ? 0.35F : 1.0F;
        int x = TemperatureHud.HUD_X;
        int y = TemperatureHud.HUD_Y + 20;
        graphics.drawString(font, "TEMP HUD ONLINE", x, y, withAlpha(0xFF9FEAF2, cue * blink), false);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
