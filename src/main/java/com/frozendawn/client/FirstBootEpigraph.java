package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * First Boot Epigraph (Notion, decided 2026-10-14; staging revised by the owner 2026-10-17): black screen and wind.
 * Each sentence fades in slowly with long pauses, the attribution last. After a hold the words dissolve, the near wind
 * cuts out and only a low howl remains in the dark, which carries under the ORSA logo as the intro fades it in, then
 * dies away. Two owner-chosen beats (2026-10-17): every sound drops out for about a second just before the attribution,
 * which returns with the wind; and faint, uneven breathing rises in the final black and carries under the logo,
 * so the player hears themselves waking before the intro's vitals readout. Once per client: a marker file in the
 * config directory records that it has played.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class FirstBootEpigraph {
    // Timeline, in ticks.
    private static final int LINE_ONE = 60, LINE_TWO = 200, ATTRIBUTION = 340, FADE_IN = 70, ATTRIBUTION_FADE = 50;
    private static final int DISSOLVE = 460, DISSOLVE_TICKS = 50, BLACK = DISSOLVE + DISSOLVE_TICKS, END = BLACK + 25;
    /** After the epigraph the howl holds through the intro's two-second black lead-in, then fades under the logo. */
    private static final int TAIL = 150;
    private static final ResourceLocation WIND = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.wind_light");
    private static final ResourceLocation HOWL = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.wind_strong");
    private static final ResourceLocation BREATH = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.eva_breathing");
    /** The silent beat: everything drops out this many ticks before the attribution and returns with it. */
    private static final int HUSH = 26;
    /** Breathing starts as the words finish dissolving. */
    private static final int BREATH_START = DISSOLVE + 25;
    private static int ticks, tail;
    private static boolean active, record;
    private enum Kind { WIND, HOWL, BREATH }
    private static Layer wind, howl, breath;
    private static Runnable then;

    private FirstBootEpigraph() { }

    private static Path marker() { return FMLPaths.CONFIGDIR.get().resolve("frozendawn-first-boot-epigraph.seen"); }
    public static boolean seen() { return Files.exists(marker()); }
    public static boolean active() { return active; }

    /** Plays the epigraph, then runs {@code next}. {@code once} records it so it never plays again on this client. */
    public static void play(boolean once, Runnable next) {
        var mc = Minecraft.getInstance();
        stopSounds(mc);
        ticks = 0; tail = 0; active = true; record = once; then = next;
        for (SoundSource source : SoundSource.values()) mc.getSoundManager().stop(null, source);
        mc.getMusicManager().stopPlaying();
    }

    /** During the epigraph only its own wind is heard. */
    public static boolean suppresses(ResourceLocation sound) { return active && !WIND.equals(sound) && !HOWL.equals(sound) && !BREATH.equals(sound); }

    /** Near wind rises out of silence, softens as the words dissolve, and cuts out in the dark before the logo. */
    private static float windVolume(float t) {
        if (t >= BLACK) return 0;
        return 0.9f * ease(t / 60f) * (1 - 0.35f * ease((t - DISSOLVE) / (float) DISSOLVE_TICKS)) * hush(t);
    }
    /** 1 normally; falls to nothing just before the attribution and comes back with it. */
    private static float hush(float t) {
        return Mth.clamp(1 - ease((t - (ATTRIBUTION - HUSH)) / 5f) + ease((t - ATTRIBUTION) / 12f), 0, 1);
    }
    /** Faint and uneven: a slow swell with an irregular wobble, never steady enough to feel mechanical. */
    private static float breathVolume(float t) {
        float wobble = 0.7f + 0.3f * Mth.sin(t * 0.21f) * Mth.sin(t * 0.047f + 1f);
        return 0.45f * ease((t - BREATH_START) / 40f) * wobble;
    }
    /** The low howl builds under each sentence and peaks as the words dissolve; after the cut it fades under the logo. */
    private static float howlVolume(float t) {
        float build = 0.15f + 0.25f * ease((t - LINE_TWO) / 120f) + 0.35f * ease((t - ATTRIBUTION) / 120f) + 0.25f * ease((t - DISSOLVE) / 50f);
        return Math.min(1f, build) * ease(t / 120f) * hush(t);
    }
    private static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!active) return;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0xFF000000);
        float t = ticks + delta.getGameTimeDeltaPartialTick(false);
        float out = 1 - ease((t - DISSOLVE) / DISSOLVE_TICKS);
        int y = h / 2 - 20;
        draw(g, "epigraph.frozendawn.line1", w, y, ease((t - LINE_ONE) / FADE_IN) * out, 0xD8D8D8, false);
        draw(g, "epigraph.frozendawn.line2", w, y + 14, ease((t - LINE_TWO) / FADE_IN) * out, 0xD8D8D8, false);
        draw(g, "epigraph.frozendawn.attribution", w, y + 42, ease((t - ATTRIBUTION) / ATTRIBUTION_FADE) * out, 0x7D878C, true);
    }

    /** Fades a sentence in from black while it settles two pixels upward. */
    private static void draw(GuiGraphics g, String key, int width, int y, float alpha, int rgb, boolean italic) {
        if (alpha <= 0.01f) return;
        var font = Minecraft.getInstance().font;
        var text = Component.translatable(key).withStyle(s -> s.withItalic(italic));
        int a = Mth.clamp((int) (alpha * 255), 4, 255);
        g.pose().pushPose();
        g.pose().translate(0, (1 - alpha) * 2f, 0);
        g.drawString(font, text, (width - font.width(text)) / 2, y, (a << 24) | rgb, false);
        g.pose().popPose();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!active) { if ((howl != null || breath != null) && ++tail >= TAIL) stopSounds(mc); return; }
        if (mc.player == null) { finish(mc, false); return; }
        mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(false); mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false); mc.options.keyJump.setDown(false); mc.player.xxa = 0; mc.player.zza = 0;
        if (wind == null) {
            wind = new Layer(ModSounds.WIND_LIGHT.get(), 1f, Kind.WIND); howl = new Layer(ModSounds.WIND_STRONG.get(), 0.55f, Kind.HOWL);
            mc.getSoundManager().play(wind); mc.getSoundManager().play(howl);
        }
        if (breath == null && ticks >= BREATH_START - 1) {
            breath = new Layer(ModSounds.EVA_BREATHING.get(), 0.92f, Kind.BREATH);
            mc.getSoundManager().play(breath);
        }
        if (++ticks >= END) finish(mc, true);
    }

    private static void stopSounds(Minecraft mc) {
        if (wind != null) mc.getSoundManager().stop(wind);
        if (howl != null) mc.getSoundManager().stop(howl);
        if (breath != null) mc.getSoundManager().stop(breath);
        wind = howl = breath = null; tail = 0;
    }

    /** Development clients skip the ORSA intro; {@code /fdpreview epigraph} replays both without recording. */
    @SubscribeEvent public static void commands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        if (net.neoforged.fml.loading.FMLEnvironment.production) return;
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("fdpreview")
                .then(net.minecraft.commands.Commands.literal("epigraph").executes(c -> {
                    play(false, OrsaAwakeningIntro::startImmediately);
                    return 1;
                })));
    }

    private static void finish(Minecraft mc, boolean completed) {
        active = false;
        // The howl outlives the epigraph and fades under the logo; an interrupted epigraph goes silent at once.
        if (!completed) stopSounds(mc);
        else { if (wind != null) mc.getSoundManager().stop(wind); wind = null; tail = 0; }
        if (record && completed) {
            try { Files.createDirectories(marker().getParent()); Files.writeString(marker(), "First Boot Epigraph shown\n"); }
            catch (IOException e) { FrozenDawn.LOGGER.warn("Could not record the First Boot Epigraph: {}", e.toString()); }
        }
        var next = then; then = null;
        if (completed && next != null) next.run();
    }

    /** One layer whose volume follows the epigraph's envelope; the howl and breathing then fade out under the logo. */
    private static final class Layer extends AbstractTickableSoundInstance {
        private final Kind kind;
        Layer(SoundEvent sound, float pitch, Kind kind) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.kind = kind; this.pitch = pitch; this.volume = 0.001f; this.looping = true;
            this.relative = true; this.attenuation = SoundInstance.Attenuation.NONE; this.x = this.y = this.z = 0;
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            if (active) {
                volume = Math.max(0.001f, switch (kind) { case WIND -> windVolume(ticks); case HOWL -> howlVolume(ticks); case BREATH -> breathVolume(ticks); });
                return;
            }
            if (kind == Kind.WIND) { stop(); return; }
            float fade = 1 - ease((tail - 40) / (float) (TAIL - 40));
            volume = Math.max(0.001f, (kind == Kind.HOWL ? howlVolume(END) : breathVolume(END + tail)) * fade);
        }
    }
}
