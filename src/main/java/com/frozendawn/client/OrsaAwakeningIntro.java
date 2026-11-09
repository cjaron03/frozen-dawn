package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnClientConfig;
import com.frozendawn.init.ModSounds;
import com.frozendawn.mixin.GameRendererAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class OrsaAwakeningIntro {

    private static final int START_DELAY_TICKS = 40;
    private static final int TOTAL_TICKS = 360;
    /** The replay runs on until its stuttering eyes are open. */
    private static final int REPLAY_TOTAL_TICKS = 390;
    private static final int LOGO_IN_START = 10;
    private static final int STRIPE_IN_START = 24;
    private static final int LIFT_START = 70;
    private static final int DIAGNOSTIC_START = 96;
    private static final int FADE_START = 310;
    private static final int TEMPERATURE_RETURN_START = 338;
    private static final int LOGO_DRAW_TICKS = 22;
    private static final int PANEL_OPEN_START = DIAGNOSTIC_START + 5;
    private static final int PANEL_SHRINK_START = FADE_START - 20;
    /** The replay's glitches, on fixed ticks so every replay is the same (owner mockup, 2026-11-09). */
    private static final Set<Integer> TEAR_TICKS = Set.of(18, 19, 26, 41, 42, 58, 97, 143, 144, 188, 233, 234, 271);
    private static final Set<Integer> JUMP_TICKS = Set.of(77, 190, 252);
    private static final Set<Integer> GHOST_TICKS = Set.of(34, 35, 120, 121, 205);
    private static final Set<Integer> CORRUPT_TICKS = Set.of(118, 119, 137, 170, 171, 199, 240, 241, 262);
    private static final Set<Integer> BAR_DROP_TICKS = Set.of(150, 151, 215);
    private static final ResourceLocation AWAKENING_BLUR_EFFECT =
            ResourceLocation.withDefaultNamespace("shaders/post/blur.json");
    private static final float[] STRIPE_Y_RATIOS = {214.0F / 362.0F, 241.0F / 362.0F, 266.0F / 362.0F};
    private static final float[] STRIPE_HEIGHT_RATIOS = {28.0F / 362.0F, 26.0F / 362.0F, 26.0F / 362.0F};
    private static final int[] STRIPE_DELAYS = {0, 3, 6};

    private static final String KEY = "screen.frozendawn.awakening.";
    private static final List<String> OPENING = List.of(KEY + "opening.1", KEY + "opening.2", KEY + "opening.3", KEY + "opening.4");
    private static final List<String> REPLAY = List.of(KEY + "opening.1", KEY + "replay.2", KEY + "replay.3", KEY + "opening.4");
    private static final String TEMP_HUD = KEY + "temp_hud";
    private static final String DEATH_LINE_1 = "screen.frozendawn.payoff.death_line.1";
    private static final String DEATH_LINE_2 = "screen.frozendawn.payoff.death_line.2";

    /** The CH-18 lines show on the lead-in's black for these pending-delay ticks, as with immediate respawn. */
    private static final int FLICKER_FROM = 30;
    private static final int FLICKER_TO = 10;
    /**
     * A respawn's boot cue: its length, its ring's volume, and how long it rests after playing (five minutes). The
     * volume is 0.35 of the opening's ring, which plays at the UI default of 0.25.
     */
    private static final int BOOT_POWER_ON_TICKS = 8;
    private static final int BOOT_CUE_TICKS = BOOT_POWER_ON_TICKS + 40;
    private static final float BOOT_CUE_VOLUME = 0.35F * 0.25F;
    private static final long BOOT_CUE_REST_TICKS = 6000L;

    private static int pendingDelayTicks;
    private static int ticks;
    private static boolean active;
    private static boolean blackoutApplied;
    private static boolean soundPlayed;
    private static boolean ringingPlayed;
    private static boolean temperatureBlinkStarted;
    private static boolean blurManaged;
    private static List<String> readout = OPENING;
    private static boolean replay;
    private static Palette palette = Palette.OPENING;
    private static boolean flickerDeathLine;

    private static long clientTicks;
    private static long lastBootCue = Long.MIN_VALUE;
    private static int bootCueTicks;

    private OrsaAwakeningIntro() {
    }

    /** The four readout lines' lang keys, in order: the opening's, or the replay's (CH-35). */
    public static List<String> readout(boolean replay) {
        return replay ? REPLAY : OPENING;
    }

    /**
     * The respawn power-on: a line across the black for three ticks, then the view opens from it, fully open after
     * {@value #BOOT_POWER_ON_TICKS} ticks (owner mockup, 2026-11-09).
     */
    static float powerOnOpen(int elapsed) {
        if (elapsed < 3) return 0.0F;
        if (elapsed >= BOOT_POWER_ON_TICKS) return 1.0F;
        return (elapsed - 2) / 6.0F;
    }

    /** The ticks of static over the temperature panel, just before it blinks back. */
    static boolean bootCueStatic(int elapsed) {
        return elapsed >= 5 && elapsed < BOOT_POWER_ON_TICKS;
    }

    private static final int[] OPENING_LINE_START = {16, 32, 48, 64};
    private static final int[] REPLAY_LINE_START = {16, 34, 64, 130};
    /** Ticks per letter, as a numerator over three: the opening types three letters a tick. */
    private static final int[] REPLAY_THIRDS_PER_CHAR = {1, 3, 5, 1};

    /**
     * How much of a readout line shows this many ticks into the diagnostics: -1 before it starts, else its letters.
     * The opening types each line quickly. The replay types its two changed lines slowly, the enrollee line slowest
     * (owner mockups, 2026-11-09).
     */
    static int readoutChars(boolean replay, int line, int ticks, int length) {
        int start = (replay ? REPLAY_LINE_START : OPENING_LINE_START)[line];
        if (ticks < start) return -1;
        int thirds = replay ? REPLAY_THIRDS_PER_CHAR[line] : 1;
        return Math.min(length, (ticks - start) * 3 / thirds + 1);
    }

    /** How much of the logo has drawn in, top down. The replay's draw stalls for four ticks. */
    static float logoReveal(int ticks, boolean replay) {
        int t = replay && ticks >= 20 && ticks < 24 ? 20 : ticks;
        return Mth.clamp((t - LOGO_IN_START) / (float) LOGO_DRAW_TICKS, 0.0F, 1.0F);
    }

    /**
     * How much of the view the eyelids still cover: all of it until the fade, then they open. The replay's open a
     * third, sag nearly shut, then open slowly.
     */
    static float lidCover(int ticks, boolean replay) {
        if (ticks < FADE_START) return 1.0F;
        int u = ticks - FADE_START;
        if (!replay) return 1.0F - smooth(u / 36.0F);
        float open;
        if (u < 12) open = smooth(u / 12.0F) * 0.35F;
        else if (u < 20) open = Mth.lerp(smooth((u - 12) / 8.0F), 0.35F, 0.12F);
        else open = Mth.lerp(smooth((u - 20) / 50.0F), 0.12F, 1.0F);
        return 1.0F - open;
    }

    static int totalTicks(boolean replay) {
        return replay ? REPLAY_TOTAL_TICKS : TOTAL_TICKS;
    }

    /** Whether a readout line shows corrupted letters on this tick. Only the replay glitches, never its enrollee line. */
    static boolean corrupts(boolean replay, int line, int ticks) {
        return replay && line != 2 && CORRUPT_TICKS.contains(ticks);
    }

    /** In the drained replay, the enrollee line keeps its yellow once typed: the only warm color left. */
    static boolean holdsYellow(boolean replay, int line) {
        return replay && line == 2;
    }

    /** Whether a boot cue may play now, given when the last one played ({@link Long#MIN_VALUE} for never). */
    public static boolean bootCueDue(long now, long lastPlayed) {
        return lastPlayed == Long.MIN_VALUE || now - lastPlayed >= BOOT_CUE_REST_TICKS;
    }

    public static void start() {
        begin(OPENING, false);
    }

    /** The opening again after the payoff, changed; the CH-18 lines flicker first if no death screen showed them. */
    public static void startReplay(boolean flickerDeathLine) {
        begin(REPLAY, flickerDeathLine);
    }

    /** A respawn's boot: the ring, the power-on and the temperature readout's return, with no words. */
    public static void startBootCue() {
        if (active || !FrozenDawnClientConfig.RESPAWN_BOOT_CUE.get() || !bootCueDue(clientTicks, lastBootCue)) {
            return;
        }
        lastBootCue = clientTicks;
        bootCueTicks = BOOT_CUE_TICKS;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.ORSA_AWAKENING_RING.get(), 1.0F, BOOT_CUE_VOLUME));
    }

    private static void begin(List<String> lines, boolean flicker) {
        readout = lines;
        replay = lines == REPLAY;
        palette = replay ? Palette.MUTED : Palette.OPENING;
        flickerDeathLine = flicker;
        bootCueTicks = 0;
        pendingDelayTicks = START_DELAY_TICKS;
        ticks = 0;
        active = true;
        blackoutApplied = false;
        soundPlayed = false;
        ringingPlayed = false;
        temperatureBlinkStarted = false;
        blurManaged = false;
    }

    /** Straight on from the First Boot Epigraph: its black runs into the intro's own lead-in, and its howl keeps
     * playing (everything else is already silent) and fades out under the logo by itself. */
    public static void startImmediately() {
        start();
        blackoutApplied = true;
    }

    public static boolean shouldSuppressSurvivalHud() {
        return FirstBootEpigraph.active() || active && ticks < TEMPERATURE_RETURN_START;
    }

    public static boolean shouldSuppressNonIntroSound(ResourceLocation soundLocation) {
        if (FirstBootEpigraph.active()) {
            return FirstBootEpigraph.suppresses(soundLocation);
        }
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
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        if (!active) {
            if (bootCueTicks > 0) {
                renderBootCue(graphics, mc.font, BOOT_CUE_TICKS - bootCueTicks);
            }
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (pendingDelayTicks > 0) {
            graphics.fill(0, 0, width, height, 0xFF000000);
            if (flickerDeathLine) {
                renderDeathLineFlicker(graphics, mc.font, width, height);
            }
            return;
        }

        float fadeOut = 1.0F - smooth((ticks - FADE_START) / 24.0F);
        float coldWake = smooth(ticks / 24.0F) * fadeOut;

        boolean jump = replay && JUMP_TICKS.contains(ticks);
        if (jump) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 3.0F, 0.0F);
        }
        renderWakeWash(graphics, width, height, coldWake, fadeOut);
        renderMasthead(graphics, width, height, fadeOut);
        renderDiagnostics(graphics, mc.font, width, height);
        if (jump) {
            graphics.pose().popPose();
        }
        renderTemperatureReturn(graphics, mc.font);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        clientTicks++;
        if (bootCueTicks > 0) {
            bootCueTicks--;
            if (BOOT_CUE_TICKS - bootCueTicks == BOOT_POWER_ON_TICKS) {
                TemperatureHud.startIntroBlink();
            }
        }
        if (!active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        // A death that ignores the shield (the void, a kill command) ends it: the death screen takes over.
        if (mc.player == null || mc.level == null || mc.player.isDeadOrDying()) {
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
            // The replay's ring is the same sound, lower and quieter: 0.6 of the UI default 0.25.
            mc.getSoundManager().play(replay
                    ? SimpleSoundInstance.forUI(ModSounds.ORSA_AWAKENING_RING.get(), 0.8F, 0.6F * 0.25F)
                    : SimpleSoundInstance.forUI(ModSounds.ORSA_AWAKENING_RING.get(), 1.0F));
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

        if (ticks >= totalTicks(replay)) {
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
        // The eyes open: the black parts from the middle, top and bottom, with a soft edge.
        float cover = lidCover(ticks, replay);
        int half = Math.round(height / 2.0F * cover);
        graphics.fill(0, 0, width, half, 0xFF000000);
        graphics.fill(0, height - half, width, height, 0xFF000000);
        if (half > 0 && half * 2 < height) {
            graphics.fill(0, half, width, half + 6, 0x80000000);
            graphics.fill(0, height - half - 6, width, height - half, 0x80000000);
        }
        graphics.fill(0, 0, width, height, withAlpha(palette.coldWash, 0.06F * coldWake * cover));

        int edge = Math.max(26, height / 7);
        graphics.fillGradient(0, 0, width, edge, withAlpha(palette.topEdge, 0.12F * fadeOut), 0x00000000);
        graphics.fillGradient(0, height - edge, width, height, 0x00000000, withAlpha(0xFF06151A, 0.45F * fadeOut));

        int side = Math.max(28, width / 9);
        graphics.fillGradient(0, 0, side, height, withAlpha(0xFF000000, 0.52F * fadeOut), 0x00000000);
        graphics.fillGradient(width - side, 0, width, height, 0x00000000, withAlpha(0xFF000000, 0.52F * fadeOut));

        int scanAlpha = (int) (32.0F * fadeOut);
        for (int y = Math.floorMod(ticks, 6); y < height; y += 6) {
            graphics.fill(0, y, width, y + 1, (scanAlpha << 24) | palette.scan);
        }
        if (replay && fadeOut > 0.0F) {
            int bar = Math.floorMod(ticks * 3, height + 40) - 20;
            graphics.fill(0, bar, width, bar + 14, withAlpha(0xFFAAB4BA, 0.05F * fadeOut));
        }
    }

    private static void renderMasthead(GuiGraphics graphics, int width, int height, float fadeOut) {
        float reveal = logoReveal(ticks, replay);
        if (reveal <= 0.0F || fadeOut <= 0.0F) {
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
        // One sideways jolt as the draw-in settles.
        int logoX = width / 2 - logoWidth / 2 + (ticks == 34 ? 3 : ticks == 35 ? -1 : 0);

        if (!replay) {
            int glowColor = withAlpha(0xFF3DE1F6, 0.08F * fadeOut);
            graphics.fill(logoX + 8, logoY + 8, logoX + logoWidth - 8, logoY + logoHeight - 8, glowColor);
        }

        // Drawn in row by row, four pixels at a time, with a bright scan line at the leading edge.
        int rows = reveal >= 1.0F ? logoHeight : (int) (logoHeight * reveal) / 4 * 4;
        if (rows <= 0) {
            return;
        }
        graphics.enableScissor(0, logoY, width, logoY + rows);
        OrsaLogoRenderer.drawAwakeningBase(graphics, logoX, logoY, logoWidth, logoHeight, fadeOut, replay);
        renderAwakeningStripes(graphics, logoX, logoY, logoWidth, logoHeight, fadeOut);
        graphics.disableScissor();
        if (rows < logoHeight) {
            graphics.fill(logoX, logoY + rows, logoX + logoWidth, logoY + rows + 1, withAlpha(palette.scan, 0.9F * fadeOut));
        }

        if (replay && TEAR_TICKS.contains(ticks)) {
            RandomSource random = RandomSource.create(ticks);
            for (int k = 0; k < 3; k++) {
                int sliceY = logoY + random.nextInt(Math.max(1, rows / 4)) * 4;
                int sliceHeight = 2 + random.nextInt(5);
                int dx = random.nextInt(19) - 9;
                graphics.fill(logoX, sliceY, logoX + logoWidth, sliceY + sliceHeight, 0xFF000000);
                graphics.enableScissor(logoX + dx, sliceY, logoX + dx + logoWidth, sliceY + sliceHeight);
                OrsaLogoRenderer.drawAwakeningBase(graphics, logoX + dx, logoY, logoWidth, logoHeight, fadeOut, true);
                graphics.disableScissor();
            }
        }
        if (replay && GHOST_TICKS.contains(ticks)) {
            OrsaLogoRenderer.drawAwakeningBase(graphics, logoX + 4, logoY - 1, logoWidth, logoHeight, 0.28F * fadeOut, true);
        }
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
                    stripeHeight, stripeAlpha, replay);
            OrsaLogoRenderer.drawAwakeningStripe(graphics, row, true, rightX + travel, stripeY, stripeWidth,
                    stripeHeight, stripeAlpha, replay);
        }
    }

    private static void renderDiagnostics(GuiGraphics graphics, Font font, int width, int height) {
        if (ticks < DIAGNOSTIC_START || ticks >= FADE_START) {
            return;
        }

        int panelWidth = Math.min(width - 36, 430);
        int panelHeight = Math.min(height / 2, 126);
        int x = width / 2 - panelWidth / 2;
        int y = Math.min(height - panelHeight - 22, Math.max(96, height / 2 - 6));

        // On the way out, the panel shrinks into the corner the temperature readout returns to.
        boolean shrinking = ticks >= PANEL_SHRINK_START;
        if (shrinking) {
            float k = smooth((ticks - PANEL_SHRINK_START) / 20.0F);
            x = (int) Mth.lerp(k, x, TemperatureHud.HUD_X);
            y = (int) Mth.lerp(k, y, TemperatureHud.HUD_Y);
            panelWidth = (int) Mth.lerp(k, panelWidth, 54);
            panelHeight = (int) Mth.lerp(k, panelHeight, TemperatureHud.TOTAL_HEIGHT);
        }

        // It builds itself: the top edge draws across, then the box drops open in steps. The replay stutters.
        float edge = Mth.clamp((ticks - DIAGNOSTIC_START) / 5.0F, 0.0F, 1.0F);
        if (replay && ticks == DIAGNOSTIC_START + 3) edge = 0.3F;
        float open = ticks < PANEL_OPEN_START ? 0.0F : (float) Math.floor(Mth.clamp((ticks - PANEL_OPEN_START) / 7.0F, 0.0F, 1.0F) * 8.0F) / 8.0F;
        if (replay && (ticks == PANEL_OPEN_START + 3 || ticks == PANEL_OPEN_START + 4)) open = 0.25F;
        graphics.fill(x, y, x + Math.round(panelWidth * edge), y + 2, withAlpha(palette.top, 0.75F));
        if (open <= 0.0F) {
            return;
        }
        int shownHeight = Math.max(3, Math.round(panelHeight * open));
        graphics.fill(x, y, x + panelWidth, y + shownHeight, withAlpha(palette.panel, 0.72F));
        graphics.fill(x, y, x + panelWidth, y + 2, withAlpha(palette.top, 0.75F));
        graphics.fill(x, y + shownHeight - 2, x + panelWidth, y + shownHeight, withAlpha(palette.bottom, 0.65F));
        if (open < 1.0F || shrinking) {
            return;
        }
        graphics.fill(x + 10, y + 22, x + panelWidth - 10, y + 23, withAlpha(palette.top, 0.22F));

        Component header = Component.translatable(KEY + "header");
        Component subject = Component.translatable(KEY + "readout");
        graphics.drawString(font, header, x + 12, y + 9, withAlpha(palette.header, 1.0F), false);
        int subjectX = x + panelWidth - 12 - font.width(subject);
        graphics.drawString(font, subject, subjectX, y + 9, withAlpha(palette.subject, 1.0F), false);
        int traceX = x + 12 + font.width(header) + 12;
        renderHeartbeat(graphics, traceX, y + 12, Math.min(64, subjectX - 12 - traceX));

        int into = ticks - DIAGNOSTIC_START;
        int lineY = y + 32;
        for (int i = 0; i < readout.size(); i++) {
            String line = Component.translatable(readout.get(i)).getString();
            int shown = readoutChars(replay, i, into, line.length());
            if (shown < 0) {
                break;
            }
            boolean typing = shown < line.length();
            int color;
            if (typing) {
                color = ((ticks / 2) & 1) == 0 ? palette.highlight : palette.line;
            } else {
                color = holdsYellow(replay, i) ? 0xFFD83D : palette.line;
            }
            String part = line.substring(0, shown);
            int dx = 0;
            if (corrupts(replay, i, ticks)) {
                part = corrupt(part, ticks * 7 + i);
                dx = (ticks & 1) == 1 ? 2 : -1;
            }
            graphics.drawString(font, part, x + 13 + dx, lineY + i * 13, withAlpha(color, 1.0F), false);
            if (typing && ((ticks / 4) & 1) == 0) {
                graphics.drawString(font, "_", x + 13 + font.width(part), lineY + i * 13, withAlpha(color, 1.0F), false);
            }
        }

        int barX = x + 13;
        int barY = y + panelHeight - 17;
        int barWidth = panelWidth - 26;
        // The replay has nothing left to project: its bar is full from the start, and drops out now and then.
        int progress = replay ? barWidth : (int) (barWidth * Mth.clamp((ticks - DIAGNOSTIC_START - 16) / 120.0F, 0.0F, 1.0F));
        graphics.fill(barX, barY, barX + barWidth, barY + 5, withAlpha(palette.barTrack, 1.0F));
        if (!(replay && BAR_DROP_TICKS.contains(ticks))) {
            graphics.fill(barX, barY, barX + progress, barY + 5, withAlpha(palette.bar, 1.0F));
        }
    }

    /** A small pulse trace for "PULSE ELEVATED". In the replay it flatlines for a moment, and once drops out. */
    private static void renderHeartbeat(GuiGraphics graphics, int x, int y, int width) {
        if (width < 24 || replay && (ticks == 208 || ticks == 209)) {
            return;
        }
        int color = withAlpha(palette.bar, 0.9F);
        if (replay && ticks >= 176 && ticks < 190) {
            graphics.fill(x, y, x + width, y + 1, color);
            return;
        }
        int previous = 0;
        for (int i = 0; i < width; i++) {
            int phase = Math.floorMod(i + ticks * 2, 28);
            int v = phase == 10 ? -2 : phase == 12 ? -7 : phase == 13 ? 4 : phase == 15 ? -1 : 0;
            int top = Math.min(previous, v);
            int bottom = Math.max(previous, v);
            graphics.fill(x + i, y + top, x + i + 1, y + Math.max(bottom, top + 1), color);
            previous = v;
        }
    }

    /** A few letters swapped for symbols, the same way for the same seed. */
    private static String corrupt(String text, int seed) {
        if (text.isEmpty()) return text;
        RandomSource random = RandomSource.create(seed);
        char[] letters = text.toCharArray();
        for (int k = 1 + random.nextInt(3); k > 0; k--) {
            int at = random.nextInt(letters.length);
            if (letters[at] != ' ') letters[at] = "#%&@".charAt(random.nextInt(4));
        }
        return new String(letters);
    }

    private static void renderTemperatureReturn(GuiGraphics graphics, Font font) {
        float cue = smooth((ticks - TEMPERATURE_RETURN_START) / 16.0F);
        if (cue <= 0.0F) {
            return;
        }

        drawTempHudOnline(graphics, font, ticks, cue);
    }

    /** A respawn's boot: the power-on from a line on the black, static over the panel, then the blink. */
    private static void renderBootCue(GuiGraphics graphics, Font font, int elapsed) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (elapsed < BOOT_POWER_ON_TICKS) {
            float open = powerOnOpen(elapsed);
            if (open == 0.0F) {
                graphics.fill(0, 0, width, height, 0xFF000000);
                int lineWidth = width * (elapsed + 1) / 3;
                graphics.fill((width - lineWidth) / 2, height / 2, (width + lineWidth) / 2, height / 2 + 1, 0xFF9FEAF2);
            } else {
                int band = Math.max(2, Math.round(height * open));
                int top = (height - band) / 2;
                graphics.fill(0, 0, width, top, 0xFF000000);
                graphics.fill(0, top + band, width, height, 0xFF000000);
                graphics.fill(0, top, width, top + band, withAlpha(0xFF8FD6FF, 0.18F * (BOOT_POWER_ON_TICKS - elapsed) / 5.0F));
                for (int y = top; y < top + band; y += 3) {
                    graphics.fill(0, y, width, y + 1, withAlpha(0xFF6EA4AF, 0.10F));
                }
                graphics.fill(0, top, width, top + 1, withAlpha(0xFF9FEAF2, 0.5F));
                graphics.fill(0, top + band - 1, width, top + band, withAlpha(0xFF9FEAF2, 0.5F));
            }
        }
        if (bootCueStatic(elapsed)) {
            RandomSource random = RandomSource.create(elapsed);
            int x0 = TemperatureHud.HUD_X;
            int y0 = TemperatureHud.HUD_Y;
            for (int y = y0; y < y0 + TemperatureHud.TOTAL_HEIGHT; y++) {
                for (int x = x0; x < x0 + 56; x++) {
                    if (random.nextFloat() < 0.45F) {
                        int v = 40 + random.nextInt(200);
                        graphics.fill(x, y, x + 1, y + 1, 0xFF000000 | v << 16 | Math.min(255, v + 15) << 8 | Math.min(255, v + 20));
                    }
                }
            }
        }
        if (elapsed >= BOOT_POWER_ON_TICKS) {
            drawTempHudOnline(graphics, font, elapsed - BOOT_POWER_ON_TICKS, 1.0F);
        }
    }

    private static void drawTempHudOnline(GuiGraphics graphics, Font font, int tick, float cue) {
        float blink = ((tick / 4) & 1) == 0 ? 0.35F : 1.0F;
        int x = TemperatureHud.HUD_X;
        int y = TemperatureHud.HUD_Y + 20;
        graphics.drawString(font, Component.translatable(TEMP_HUD), x, y, withAlpha(0xFF9FEAF2, cue * blink), false);
    }

    /** About a second of the CH-18 lines on the black, dropping out for a frame near each end. */
    private static void renderDeathLineFlicker(GuiGraphics graphics, Font font, int width, int height) {
        int shown = FLICKER_FROM - pendingDelayTicks;
        if (pendingDelayTicks > FLICKER_FROM || pendingDelayTicks <= FLICKER_TO || shown == 2 || shown == 4 || shown == 17) {
            return;
        }
        int y = height / 2 - font.lineHeight;
        graphics.drawCenteredString(font, Component.translatable(DEATH_LINE_1), width / 2, y, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(DEATH_LINE_2), width / 2, y + font.lineHeight + 1, 0xFFFFFF);
    }

    /** The opening's colors, and the replay's: the same, drained to a cold grey. */
    private record Palette(int coldWash, int topEdge, int scan, int panel, int top, int bottom, int header,
                           int subject, int line, int highlight, int barTrack, int bar) {
        static final Palette OPENING = new Palette(0x8FD6FF, 0x7ED9FF, 0x6EA4AF, 0x061B20, 0x8BEAF5, 0xFFCE00,
                0xE9FBFF, 0xFFD83D, 0xBDEFF4, 0xFFD83D, 0x153238, 0x7EE8F3);
        static final Palette MUTED = new Palette(0x9AA4AA, 0x8E989E, 0x78808A, 0x0E1215, 0x7A868B, 0x5F6062,
                0xC3C8CB, 0x9A9FA2, 0xA0A8AC, 0xD4D9DB, 0x23282B, 0x868F93);
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
