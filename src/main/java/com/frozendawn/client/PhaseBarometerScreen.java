package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.barometer.BarometerWarning;
import com.frozendawn.barometer.PhaseBarometerSnapshot;
import com.frozendawn.network.OpenPhaseBarometerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class PhaseBarometerScreen extends Screen {

    private static final ResourceLocation ORSA_LOGO =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_logo.png");
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/phase_barometer_panel.png");
    private static final int GUI_W = 248;
    private static final int GUI_H = 138;
    private static final int BAR_W = 184;
    private static final int BAR_H = 8;

    private final BlockPos barometerPos;
    private PhaseBarometerSnapshot snapshot;

    public PhaseBarometerScreen(OpenPhaseBarometerPayload payload) {
        super(Component.translatable("screen.frozendawn.phase_barometer.title"));
        this.barometerPos = payload.pos();
        this.snapshot = payload.toSnapshot();
    }

    public boolean sameBarometer(BlockPos pos) {
        return barometerPos.equals(pos);
    }

    public void applySnapshot(OpenPhaseBarometerPayload payload) {
        snapshot = payload.toSnapshot();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = (width - GUI_W) / 2;
        int y = (height - GUI_H) / 2;
        graphics.blit(PANEL_TEXTURE, x, y, 0, 0, GUI_W, GUI_H, GUI_W, GUI_H);

        graphics.blit(ORSA_LOGO, x + GUI_W - 28, y + 4, 0, 0, 16, 16, 16, 16);
        graphics.drawString(font, title, x + 12, y + 8, 0xFF7BE5ED, false);
        graphics.drawString(font, "ORSA DIAGNOSTIC FORECAST", x + 12, y + 18, 0xFF6A97A3, false);

        drawLabelValue(graphics, x + 14, y + 38,
                Component.translatable("screen.frozendawn.phase_barometer.current_phase"),
                "Phase " + snapshot.currentPhase() + " // " + snapshot.currentPhaseName(),
                0xFFE5F0F2);

        drawLabelValue(graphics, x + 14, y + 52,
                Component.translatable("screen.frozendawn.phase_barometer.forecast"),
                snapshot.forecastBand().displayName(),
                colorForSeverity(snapshot.severity(), snapshot.forecastBand().isHighUrgency()));

        drawLabelValue(graphics, x + 14, y + 66,
                Component.translatable("screen.frozendawn.phase_barometer.upcoming"),
                snapshot.upcomingState().displayName(),
                0xFF9ED3DC);

        String warningText = snapshot.warning() == BarometerWarning.NONE
                ? Component.translatable("screen.frozendawn.phase_barometer.none").getString()
                : snapshot.warning().displayName();
        drawLabelValue(graphics, x + 14, y + 98,
                Component.translatable("screen.frozendawn.phase_barometer.warning"),
                warningText,
                snapshot.warning() == BarometerWarning.NONE ? 0xFF78909A : 0xFFE3C87F);

        graphics.drawString(font, Component.translatable("screen.frozendawn.phase_barometer.severity"),
                x + 14, y + 113, 0xFF6E8B93, false);

        int barX = x + 14;
        int barY = y + 124;
        graphics.fill(barX, barY, barX + BAR_W, barY + BAR_H, 0xFF081015);
        int fillW = Math.max(1, Math.round(BAR_W * snapshot.severity()));
        graphics.fill(barX, barY, barX + fillW, barY + BAR_H, colorForSeverity(snapshot.severity(), snapshot.shouldBlink()));
        graphics.fill(barX, barY, barX + BAR_W, barY + 1, 0xFF2A5660);
        graphics.fill(barX, barY + BAR_H - 1, barX + BAR_W, barY + BAR_H, 0xFF11252C);
        drawTick(graphics, barX, barY, 0.40f);
        drawTick(graphics, barX, barY, 0.70f);
        drawTick(graphics, barX, barY, 0.88f);

    }

    private void drawLabelValue(GuiGraphics graphics, int x, int y, Component label, String value, int valueColor) {
        graphics.drawString(font, label, x, y, 0xFF6E8B93, false);
        graphics.drawString(font, fit(value, 138), x + 88, y, valueColor, false);
    }

    private void drawTick(GuiGraphics graphics, int barX, int barY, float progress) {
        int x = barX + Math.round(BAR_W * progress);
        graphics.fill(x, barY - 2, x + 1, barY + BAR_H + 2, 0xFF24434B);
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        int length = value.length();
        while (length > 0 && font.width(value.substring(0, length)) + ellipsisWidth > maxWidth) {
            length--;
        }
        return length <= 0 ? ellipsis : value.substring(0, length) + ellipsis;
    }

    private static int colorForSeverity(float severity, boolean highUrgency) {
        if (highUrgency) {
            return 0xFFDA6B4A;
        }
        if (severity >= 0.70f) {
            return 0xFFD5B45A;
        }
        if (severity >= 0.40f) {
            return 0xFF55C3D0;
        }
        return 0xFF47A86E;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void openOrUpdate(Minecraft minecraft, OpenPhaseBarometerPayload payload) {
        if (minecraft.screen instanceof PhaseBarometerScreen screen && screen.sameBarometer(payload.pos())) {
            screen.applySnapshot(payload);
            return;
        }
        minecraft.setScreen(new PhaseBarometerScreen(payload));
    }
}
