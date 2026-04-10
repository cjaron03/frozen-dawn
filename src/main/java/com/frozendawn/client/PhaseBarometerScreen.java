package com.frozendawn.client;

import com.frozendawn.barometer.BarometerWarning;
import com.frozendawn.barometer.PhaseBarometerForecasts;
import com.frozendawn.barometer.PhaseBarometerSnapshot;
import com.frozendawn.block.PhaseBarometerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class PhaseBarometerScreen extends AbstractContainerScreen<PhaseBarometerMenu> {

    private static final int GUI_W = 236;
    private static final int GUI_H = 154;
    private static final int SECTION_X = 10;
    private static final int SECTION_W = GUI_W - 20;
    private static final int VALUE_X = 78;
    private static final int VALUE_W = GUI_W - VALUE_X - 14;
    private static final int BAR_W = GUI_W - 24;

    public PhaseBarometerScreen(PhaseBarometerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        imageWidth = GUI_W;
        imageHeight = GUI_H;
        inventoryLabelY = 999;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x70070D11);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        drawFrame(graphics, x, y, GUI_W, GUI_H);
    }

    private PhaseBarometerSnapshot currentSnapshot() {
        return PhaseBarometerForecasts.evaluate(ApocalypseClientData.getPhase(), ApocalypseClientData.getProgress());
    }

    private void drawFrame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF05090D);
        graphics.fill(x, y, x + w, y + 1, 0xFF4CC6D7);
        graphics.fill(x, y, x + 1, y + h, 0xFF4CC6D7);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF07151A);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF07151A);

        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF091117);
        graphics.fill(x + 2, y + 2, x + w - 2, y + 23, 0xFF0E2730);
        graphics.fill(x + 2, y + 23, x + w - 2, y + 24, 0xFF2E8997);

        drawSection(graphics, x + SECTION_X, y + 31, SECTION_W, 66);
        drawSection(graphics, x + SECTION_X, y + 101, SECTION_W, 26);
    }

    private void drawSection(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF0A141B);
        for (int lineY = y; lineY < y + h; lineY += 6) {
            graphics.fill(x, lineY, x + w, lineY + 1, 0x0D68A9B3);
        }
        graphics.fill(x, y, x + w, y + 1, 0xFF14323A);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF14323A);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        PhaseBarometerSnapshot snapshot = currentSnapshot();

        graphics.drawString(font, title, 12, 11, 0xFF7BE5ED, false);

        drawField(
                graphics,
                SECTION_X,
                36,
                Component.translatable("screen.frozendawn.phase_barometer.current_phase"),
                "Phase " + snapshot.currentPhase() + " // " + snapshot.currentPhaseName(),
                0xFFE6F3F6,
                2
        );
        drawField(
                graphics,
                SECTION_X,
                54,
                Component.translatable("screen.frozendawn.phase_barometer.forecast"),
                snapshot.forecastBand().displayName(),
                colorForSeverity(snapshot.severity(), snapshot.forecastBand().isHighUrgency()),
                2
        );
        drawField(
                graphics,
                SECTION_X,
                72,
                Component.translatable("screen.frozendawn.phase_barometer.upcoming"),
                snapshot.upcomingState().displayName(),
                0xFFB7E7EF,
                2
        );

        String warningText = snapshot.warning() == BarometerWarning.NONE
                ? Component.translatable("screen.frozendawn.phase_barometer.none").getString()
                : snapshot.warning().displayName();
        drawField(
                graphics,
                SECTION_X,
                106,
                Component.translatable("screen.frozendawn.phase_barometer.warning"),
                warningText,
                snapshot.warning() == BarometerWarning.NONE ? 0xFF8A9FA7 : 0xFFE3C87F,
                2
        );

        graphics.drawString(font, Component.translatable("screen.frozendawn.phase_barometer.severity"),
                12, 132, 0xFF6E8B93, false);
        drawSeverityBar(graphics, 12, 141, snapshot);
    }

    private void drawField(GuiGraphics graphics, int x, int y, Component label, String value, int color, int maxLines) {
        graphics.drawString(font, label, x + 4, y, 0xFF738B95, false);
        if (maxLines > 1) {
            drawWrappedValue(graphics, value, x + VALUE_X - SECTION_X, y, VALUE_W, color, maxLines);
        } else {
            graphics.drawString(font, fit(value, VALUE_W), x + VALUE_X - SECTION_X, y, color, false);
        }
    }

    private void drawWrappedValue(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color, int maxLines) {
        List<String> lines = wrap(value, maxWidth, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
    }

    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        String current = "";
        int index = 0;

        while (index < words.length) {
            String candidate = current.isEmpty() ? words[index] : current + " " + words[index];
            if (font.width(candidate) <= maxWidth) {
                current = candidate;
                index++;
                continue;
            }

            if (current.isEmpty()) {
                lines.add(fit(words[index], maxWidth));
                index++;
            } else {
                lines.add(current);
                current = "";
            }

            if (lines.size() == maxLines) {
                break;
            }
        }

        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(current);
        }

        if (index < words.length && !lines.isEmpty()) {
            String remaining = String.join(" ", java.util.Arrays.copyOfRange(words, index, words.length));
            int lastIndex = lines.size() - 1;
            String prefix = lines.get(lastIndex);
            String combined = prefix + (prefix.isEmpty() ? "" : " ") + remaining;
            lines.set(lastIndex, fit(combined, maxWidth));
        }

        if (lines.isEmpty()) {
            lines.add(fit(text, maxWidth));
        }

        return lines;
    }

    private void drawSeverityBar(GuiGraphics graphics, int x, int y, PhaseBarometerSnapshot snapshot) {
        int barX = x;
        int barY = y;
        int barColor = colorForSeverity(snapshot.severity(), snapshot.shouldBlink());
        graphics.fill(barX, barY, barX + BAR_W, barY + 8, 0xFF071015);
        graphics.fill(barX, barY, barX + Math.max(1, Math.round(BAR_W * snapshot.severity())), barY + 8, barColor);
        graphics.fill(barX, barY, barX + BAR_W, barY + 1, 0xFF2A5660);
        graphics.fill(barX, barY + 7, barX + BAR_W, barY + 8, 0xFF102027);

        drawTick(graphics, barX, barY, 0.20f);
        drawTick(graphics, barX, barY, 0.40f);
        drawTick(graphics, barX, barY, 0.70f);
        drawTick(graphics, barX, barY, 0.88f);
    }

    private void drawTick(GuiGraphics graphics, int barX, int barY, float progress) {
        int x = barX + Math.round(BAR_W * progress);
        graphics.fill(x, barY - 3, x + 1, barY + 10, 0xFF20454E);
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int length = value.length();
        while (length > 0 && font.width(value.substring(0, length) + ellipsis) > maxWidth) {
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
