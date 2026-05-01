package com.frozendawn.client;

import com.frozendawn.phase.PhaseManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

final class WindowBlizzardOverlay {

    private WindowBlizzardOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) {
            return;
        }

        int phase = ApocalypseClientData.getPhase();
        if (phase < 3) {
            return;
        }

        float progress = ApocalypseClientData.getProgress();
        if (PhaseManager.isVacuumActive(phase, progress)) {
            return;
        }

        ClientStormVisibility.WindowView windowView = ClientStormVisibility.findWindowView(mc);
        if (windowView == null) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long time = mc.level.getGameTime();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        float strength = getOverlayStrength(phase, progress);

        int washAlpha = (int) (strength * 168.0F);
        graphics.fill(0, 0, width, height, (washAlpha << 24) | 0xD9ECFF);

        int coldAlpha = (int) (strength * 128.0F);
        graphics.fillGradient(0, 0, width, height / 2, (coldAlpha << 24) | 0xBFDFFF, 0x00BFDFFF);
        graphics.fillGradient(0, height / 2, width, height, 0x00A7CFFF, (coldAlpha << 24) | 0xA7CFFF);

        int edgeAlpha = (int) (strength * 150.0F);
        int edgeColor = (edgeAlpha << 24) | 0xE8F7FF;
        int transparent = 0x00E8F7FF;
        int borderSize = Math.max(22, (int) (height * 0.16F));
        int sideBorder = Math.max(28, (int) (width * 0.08F));
        graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
        graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
        graphics.fillGradient(0, 0, sideBorder, height, edgeColor, transparent);
        graphics.fillGradient(width - sideBorder, 0, width, height, transparent, edgeColor);

        int streakAlpha = (int) (strength * 210.0F);
        int streakColor = (streakAlpha << 24) | 0xF2FBFF;
        float drift = (time + partialTick) * (phase >= 5 ? 5.5F : 3.25F);
        int spacing = phase >= 5 ? 18 : 26;
        for (int i = -height; i < width + height; i += spacing) {
            int x = Mth.floor(i + (drift % spacing));
            int y = Mth.floor((i * 0.37F + drift * 0.45F) % (height + spacing)) - spacing;
            int length = phase >= 5 ? 62 : 42;
            graphics.fill(x, y, x + length, y + 2, streakColor);
            graphics.fill(x + 10, y + 3, x + length + 18, y + 4, (int) (streakAlpha * 0.55F) << 24 | 0xF2FBFF);
        }

        int veilAlpha = (int) (strength * 105.0F);
        for (int y = (int) (drift % 7); y < height; y += 7) {
            graphics.fill(0, y, width, y + 1, (veilAlpha << 24) | 0xEEF9FF);
        }
    }

    private static float getOverlayStrength(int phase, float progress) {
        if (phase >= 6) {
            return switch (PhaseManager.getPhase6Stage(phase, progress)) {
                case EARLY -> 1.0F;
                case MID -> Mth.lerp(PhaseManager.getPhase6MidFadeProgress(progress), 1.0F, 0.25F);
                case VACUUM, INACTIVE -> 0.0F;
            };
        }
        if (phase >= 5) {
            return 1.0F;
        }
        if (phase >= 4) {
            return 0.45F;
        }
        return 0.24F;
    }
}
