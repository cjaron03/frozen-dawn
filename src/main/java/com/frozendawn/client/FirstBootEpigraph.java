package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * First Boot Epigraph (Notion, decided 2026-10-14): black screen, wind only, two lines fading in one at a time with
 * long pauses, the attribution last, a three-second hold, then a hard cut to the ORSA intro. Once per client: a marker
 * file in the client's config directory records that it has played, so it never repeats in any world.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class FirstBootEpigraph {
    /** Ticks of black before the first key, per typed character, between lines, and the final hold (about 3 s). */
    private static final int LEAD = 50, LINE_TICKS_PER_CHAR = 3, ATTRIBUTION_TICKS_PER_CHAR = 2, PAUSE = 45, HOLD = 60;
    private static final float WIND_VOLUME = 0.3f;
    private static final ResourceLocation WIND = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.wind_light");
    private static int ticks;
    private static boolean active, record;
    private static SoundInstance wind;
    private static Runnable then;

    private FirstBootEpigraph() { }

    private static Path marker() { return FMLPaths.CONFIGDIR.get().resolve("frozendawn-first-boot-epigraph.seen"); }
    public static boolean seen() { return Files.exists(marker()); }
    public static boolean active() { return active; }

    /** Plays the epigraph, then runs {@code next}. {@code once} records it so it never plays again on this client. */
    public static void play(boolean once, Runnable next) {
        ticks = 0; active = true; record = once; then = next; wind = null;
        var mc = Minecraft.getInstance();
        for (SoundSource source : SoundSource.values()) mc.getSoundManager().stop(null, source);
        mc.getMusicManager().stopPlaying();
    }

    /** During the epigraph only its own wind is heard. */
    public static boolean suppresses(ResourceLocation sound) { return active && !WIND.equals(sound); }

    private static String text(String key) { return Component.translatable(key).getString(); }
    /** Start ticks of each line, from the localized lengths: lines type out one after another with long pauses. */
    private static int[] schedule() {
        int one = LEAD, two = one + text("epigraph.frozendawn.line1").length() * LINE_TICKS_PER_CHAR + PAUSE;
        int three = two + text("epigraph.frozendawn.line2").length() * LINE_TICKS_PER_CHAR + PAUSE;
        int end = three + text("epigraph.frozendawn.attribution").length() * ATTRIBUTION_TICKS_PER_CHAR + HOLD;
        return new int[] {one, two, three, end};
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!active) return;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0xFF000000);
        float t = ticks + delta.getGameTimeDeltaPartialTick(false);
        var at = schedule();
        int y = h / 2 - 18;
        boolean cursor = ((int) t / 10) % 2 == 0;
        type(g, text("epigraph.frozendawn.line1"), w, y, t - at[0], LINE_TICKS_PER_CHAR, 0xD8D8D8, cursor && t < at[1]);
        type(g, text("epigraph.frozendawn.line2"), w, y + 14, t - at[1], LINE_TICKS_PER_CHAR, 0xD8D8D8, cursor && t >= at[1] && t < at[2]);
        type(g, text("epigraph.frozendawn.attribution"), w, y + 40, t - at[2], ATTRIBUTION_TICKS_PER_CHAR, 0x7D878C, cursor && t >= at[2]);
    }

    /** Types {@code full} one character at a time; the line is centred on its finished width so it never shifts. */
    private static void type(GuiGraphics g, String full, int width, int y, float since, int perChar, int rgb, boolean cursor) {
        if (since < 0) { return; }
        var font = Minecraft.getInstance().font;
        int shown = Mth.clamp((int) (since / perChar), 0, full.length());
        int x = (width - font.width(full)) / 2;
        String typed = full.substring(0, shown);
        g.drawString(font, typed, x, y, 0xFF000000 | rgb, false);
        if (cursor) g.drawString(font, "_", x + font.width(typed), y, 0xFF000000 | rgb, false);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!active) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) { finish(mc, false); return; }
        mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(false); mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false); mc.options.keyJump.setDown(false); mc.player.xxa = 0; mc.player.zza = 0;
        if (wind == null) { wind = SimpleSoundInstance.forUI(ModSounds.WIND_LIGHT.get(), 1f, WIND_VOLUME); mc.getSoundManager().play(wind); }
        if (++ticks >= schedule()[3]) finish(mc, true);
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
        if (wind != null) mc.getSoundManager().stop(wind);
        wind = null;
        if (record && completed) {
            try { Files.createDirectories(marker().getParent()); Files.writeString(marker(), "First Boot Epigraph shown\n"); }
            catch (IOException e) { FrozenDawn.LOGGER.warn("Could not record the First Boot Epigraph: {}", e.toString()); }
        }
        var next = then; then = null;
        if (completed && next != null) next.run();
    }
}
