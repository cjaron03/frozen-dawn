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
 * dies away. Once per client: a marker file in the config directory records that it has played.
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
    private static int ticks, tail;
    private static boolean active, record;
    private static Layer wind, howl;
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
    public static boolean suppresses(ResourceLocation sound) { return active && !WIND.equals(sound) && !HOWL.equals(sound); }

    /** Near wind rises out of silence, softens as the words dissolve, and cuts out in the dark before the logo. */
    private static float windVolume(float t) {
        if (t >= BLACK) return 0;
        return 0.9f * ease(t / 60f) * (1 - 0.35f * ease((t - DISSOLVE) / (float) DISSOLVE_TICKS));
    }
    /** The low howl builds under each sentence and peaks as the words dissolve; after the cut it fades under the logo. */
    private static float howlVolume(float t) {
        float build = 0.15f + 0.25f * ease((t - LINE_TWO) / 120f) + 0.35f * ease((t - ATTRIBUTION) / 120f) + 0.25f * ease((t - DISSOLVE) / 50f);
        return Math.min(1f, build) * ease(t / 120f);
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
        if (!active) { if (howl != null && ++tail >= TAIL) stopSounds(mc); return; }
        if (mc.player == null) { finish(mc, false); return; }
        mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(false); mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false); mc.options.keyJump.setDown(false); mc.player.xxa = 0; mc.player.zza = 0;
        if (wind == null) {
            wind = new Layer(ModSounds.WIND_LIGHT.get(), 1f, true); howl = new Layer(ModSounds.WIND_STRONG.get(), 0.55f, false);
            mc.getSoundManager().play(wind); mc.getSoundManager().play(howl);
        }
        if (++ticks >= END) finish(mc, true);
    }

    private static void stopSounds(Minecraft mc) {
        if (wind != null) mc.getSoundManager().stop(wind);
        if (howl != null) mc.getSoundManager().stop(howl);
        wind = howl = null; tail = 0;
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

    /** One wind layer whose volume follows the epigraph's envelope, then the fade-out tail under the logo. */
    private static final class Layer extends AbstractTickableSoundInstance {
        private final boolean near;
        Layer(SoundEvent sound, float pitch, boolean near) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.near = near; this.pitch = pitch; this.volume = 0.001f; this.looping = true;
            this.relative = true; this.attenuation = SoundInstance.Attenuation.NONE; this.x = this.y = this.z = 0;
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            if (active) volume = Math.max(0.001f, near ? windVolume(ticks) : howlVolume(ticks));
            else if (near) stop();
            else volume = Math.max(0.001f, howlVolume(END) * (1 - ease((tail - 40) / (float) (TAIL - 40))));
        }
    }
}
