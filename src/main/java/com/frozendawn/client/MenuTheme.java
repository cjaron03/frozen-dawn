package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Manages the Frozen Dawn menu theme and music.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class MenuTheme {

    private static SoundInstance menuMusic;
    private static long panStartTime = -1;

    /**
     * Returns true for any screen that is NOT an in-game screen.
     */
    private static boolean isMenuScreen(Screen screen) {
        if (screen instanceof TitleScreen) return true;
        return Minecraft.getInstance().player == null;
    }

    /**
     * Draw the panorama-style panning background on all menu screens.
     * Uses cosine interpolation for smooth, eased panning.
     */
    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!isMenuScreen(event.getScreen())) return;

        renderMenuBackdrop(event.getGuiGraphics());
    }

    public static void renderMenuBackdrop(GuiGraphics graphics) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        if (panStartTime < 0) {
            panStartTime = System.currentTimeMillis();
        }

        float elapsed = (System.currentTimeMillis() - panStartTime) / 1000f;
        float panCycle = 120f;
        float panPhase = (elapsed % panCycle) / panCycle;
        float panFactor = (float) (0.5 - 0.5 * Math.cos(panPhase * Math.PI * 2));

        float overscale = 1.3f;
        int renderWidth = (int) (screenWidth * overscale);
        int renderHeight = (int) (screenHeight * overscale);
        int panRange = renderWidth - screenWidth;
        int offsetX = -(int) (panRange * panFactor);
        int offsetY = -(renderHeight - screenHeight) / 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        renderBackdrop(graphics, screenWidth, screenHeight, elapsed, offsetX, offsetY);
        graphics.fillGradient(0, 0, screenWidth, screenHeight / 3, 0x50000000, 0x00000000);
        graphics.fillGradient(0, screenHeight * 2 / 3, screenWidth, screenHeight, 0x00000000, 0x60000000);
        RenderSystem.disableBlend();
    }

    private static void renderBackdrop(GuiGraphics graphics, int width, int height, float elapsed, int offsetX, int offsetY) {
        graphics.fillGradient(0, 0, width, height, 0xFF09111C, 0xFF142844);
        graphics.fillGradient(0, 0, width, height / 2, 0x502D6E9E, 0x00000000);

        int moonX = width - 180 + offsetX / 8;
        graphics.fillGradient(moonX - 90, 12 + offsetY / 10, moonX + 90, 152 + offsetY / 10, 0x28BFE8FF, 0x00000000);

        renderIceLayer(graphics, width, height, elapsed, 0.64f, 26, 42, 0xCC17263A, 0.0065f, 0.35f);
        renderIceLayer(graphics, width, height, elapsed, 0.73f, 18, 56, 0xE0283C5A, 0.0085f, 0.9f);
        renderIceLayer(graphics, width, height, elapsed, 0.82f, 14, 70, 0xF03A557C, 0.011f, 1.4f);

        renderSnowLayer(graphics, width, height, elapsed, 70, 26.0f, 11.0f, 1, 0x42D7ECFF);
        renderSnowLayer(graphics, width, height, elapsed, 55, 42.0f, 20.0f, 2, 0x66E7F4FF);
        renderSnowLayer(graphics, width, height, elapsed, 40, 58.0f, 34.0f, 3, 0x92F5FBFF);
    }

    private static void renderIceLayer(
            GuiGraphics graphics,
            int width,
            int height,
            float elapsed,
            float horizon,
            int step,
            int amplitude,
            int color,
            float driftScale,
            float phaseOffset
    ) {
        int baseY = (int) (height * horizon);
        for (int x = -step; x < width + step; x += step) {
            float worldX = x - elapsed * 10.0f * driftScale;
            float waveA = Mth.sin(worldX * 0.018f + phaseOffset) * amplitude;
            float waveB = Mth.sin(worldX * 0.043f + phaseOffset * 2.1f) * (amplitude * 0.35f);
            float jag = (hash01((x / step) * 31 + (int) (phaseOffset * 100)) - 0.5f) * amplitude * 0.55f;
            int ridgeTop = Mth.clamp(baseY + Math.round(waveA + waveB + jag), 0, height);
            graphics.fill(x, ridgeTop, x + step + 1, height, color);
        }
    }

    private static void renderSnowLayer(
            GuiGraphics graphics,
            int width,
            int height,
            float elapsed,
            int count,
            float speedX,
            float speedY,
            int size,
            int color
    ) {
        for (int i = 0; i < count; i++) {
            float startX = hash01(i * 17 + 3);
            float startY = hash01(i * 29 + 11);
            int x = (int) (Mth.frac(startX + elapsed / speedX) * (width + 48.0f)) - 24;
            int y = (int) (Mth.frac(startY + elapsed / speedY) * (height + 48.0f)) - 24;
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private static float hash01(int seed) {
        int x = seed * 1664525 + 1013904223;
        x ^= x >>> 16;
        return (x & 0x00FFFFFF) / (float) 0x01000000;
    }

    /**
     * Ensure music is playing whenever we're on a menu screen.
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!isMenuScreen(event.getScreen())) return;

        Minecraft mc = Minecraft.getInstance();

        // Stop vanilla menu music (MusicManagerMixin also suppresses the tick)
        mc.getMusicManager().stopPlaying();

        // Start our music if not already playing
        if (menuMusic == null || !mc.getSoundManager().isActive(menuMusic)) {
            menuMusic = new SimpleSoundInstance(
                    ModSounds.MENU_MUSIC.get().getLocation(),
                    SoundSource.MUSIC,
                    1.0f, 1.0f,
                    SoundInstance.createUnseededRandom(),
                    true,   // looping
                    0,
                    SoundInstance.Attenuation.NONE,
                    0.0, 0.0, 0.0,
                    true    // relative
            );
            mc.getSoundManager().play(menuMusic);
        }
    }

    /**
     * Stop menu music when joining a world.
     */
    @SubscribeEvent
    public static void onJoinWorld(ClientPlayerNetworkEvent.LoggingIn event) {
        stopMusic();
    }

    public static void stopMusic() {
        if (menuMusic != null) {
            Minecraft.getInstance().getSoundManager().stop(menuMusic);
            menuMusic = null;
        }
        panStartTime = -1;
    }
}
