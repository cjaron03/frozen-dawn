package com.frozendawn.client;

import com.frozendawn.config.ConfigPresets;
import com.frozendawn.network.SelectDifficultyPresetPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DifficultySelectionScreen extends Screen {
    private static final int PANEL_MIN_W = 420;
    private static final int PANEL_MAX_W = 560;
    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_INNER_PAD = 22;
    private static final int HEADER_H = 42;
    private static final int CARD_GAP = 10;
    private static final int FOOTER_GAP = 4;
    private static final int CONFIRM_H = 20;

    private final List<PresetCard> cards = new ArrayList<>();
    private ConfigPresets selectedPreset = ConfigPresets.DEFAULT;
    private Button confirmButton;
    private boolean submitted;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int contentTotalHeight;
    private int scrollOffset;
    private int maxScroll;
    private int defaultNoticeY;
    private int handbookY;
    private int lockNoticeY;

    public DifficultySelectionScreen() {
        super(Component.translatable("screen.frozendawn.difficulty.title"));
    }

    @Override
    protected void init() {
        super.init();
        cards.clear();

        panelWidth = Math.min(PANEL_MAX_W, Math.max(Math.min(PANEL_MIN_W, width - 8), width - PANEL_MARGIN * 2));
        panelHeight = Math.max(260, height - 8);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        contentX = panelX + PANEL_INNER_PAD;
        contentY = panelY + HEADER_H + 14;
        contentWidth = panelWidth - PANEL_INNER_PAD * 2;

        int confirmY = panelY + panelHeight - PANEL_INNER_PAD - CONFIRM_H;
        contentHeight = Math.max(80, confirmY - 10 - contentY);

        int currentY = 0;
        currentY = addPresetCard(currentY, ConfigPresets.DEFAULT, 0xFF61BCE7, 0xFF0D1824);
        currentY += CARD_GAP;
        currentY = addPresetCard(currentY, ConfigPresets.CINEMATIC, 0xFF90E4F4, 0xFF112029);
        currentY += CARD_GAP;
        currentY = addPresetCard(currentY, ConfigPresets.BRUTAL, 0xFFD44A52, 0xFF261115);
        defaultNoticeY = currentY + 12;

        int defaultNoticeHeight = font.wordWrapHeight(Component.translatable("screen.frozendawn.difficulty.default_notice"), contentWidth);
        handbookY = defaultNoticeY + defaultNoticeHeight + FOOTER_GAP;
        int handbookHeight = font.wordWrapHeight(Component.translatable("screen.frozendawn.difficulty.handbook"), contentWidth);
        lockNoticeY = handbookY + handbookHeight + FOOTER_GAP;
        int lockNoticeHeight = font.wordWrapHeight(Component.translatable("screen.frozendawn.difficulty.lock_notice"), contentWidth);
        contentTotalHeight = lockNoticeY + lockNoticeHeight;
        maxScroll = Math.max(0, contentTotalHeight - contentHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        confirmButton = addRenderableWidget(Button.builder(confirmMessage(), button -> confirmSelection())
                .bounds(contentX, confirmY, contentWidth, CONFIRM_H)
                .build());
        updateSelectionState();
    }

    private int addPresetCard(int startY, ConfigPresets preset, int accentColor, int shadowColor) {
        PresetCard card = new PresetCard(
                preset,
                Component.translatable("screen.frozendawn.difficulty." + preset.name().toLowerCase(Locale.ROOT) + ".title"),
                createLine1(preset),
                createLine2(preset),
                accentColor,
                shadowColor,
                contentWidth,
                font,
                startY
        );
        cards.add(card);
        return startY + card.height();
    }

    private static Component createLine1(ConfigPresets preset) {
        return Component.translatable("screen.frozendawn.difficulty." + preset.name().toLowerCase(Locale.ROOT) + ".line1",
                preset.totalDays, preset.basePhase5Temp);
    }

    private static Component createLine2(ConfigPresets preset) {
        return Component.translatable("screen.frozendawn.difficulty." + preset.name().toLowerCase(Locale.ROOT) + ".line2");
    }

    private void updateSelectionState() {
        if (confirmButton != null) {
            confirmButton.active = !submitted;
            confirmButton.setMessage(confirmMessage());
        }
    }

    private Component confirmMessage() {
        String key = "screen.frozendawn.difficulty." + selectedPreset.name().toLowerCase(Locale.ROOT) + ".title";
        Component base = Component.translatable("screen.frozendawn.difficulty.confirm.selected",
                Component.translatable(key));
        if (selectedPreset == ConfigPresets.BRUTAL) {
            return base.copy().withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        }
        return base;
    }

    private void confirmSelection() {
        if (submitted) {
            return;
        }
        submitted = true;
        updateSelectionState();
        PacketDistributor.sendToServer(new SelectDifficultyPresetPayload(selectedPreset.name().toLowerCase(Locale.ROOT)));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackdrop(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        Component subtitle = Component.translatable("screen.frozendawn.difficulty.subtitle");
        int titleX = panelX + (panelWidth - font.width(title)) / 2;
        int subtitleX = panelX + (panelWidth - font.width(subtitle)) / 2;
        graphics.drawString(font, title, titleX, panelY + 10, 0xFFBDEFFF, false);
        graphics.drawString(font, subtitle, subtitleX, panelY + 24, 0xFF8CBED1, false);

        int clipLeft = contentX;
        int clipTop = contentY;
        int clipRight = contentX + contentWidth;
        int clipBottom = contentY + contentHeight;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);

        for (PresetCard card : cards) {
            int drawY = contentY + card.baseY() - scrollOffset;
            if (drawY + card.height() >= clipTop && drawY <= clipBottom) {
                card.render(graphics, font, contentX, drawY, mouseX, mouseY, selectedPreset == card.preset(), !submitted);
            }
        }

        graphics.drawWordWrap(font,
                Component.translatable("screen.frozendawn.difficulty.default_notice"),
                contentX, contentY + defaultNoticeY - scrollOffset, contentWidth, 0xFFB7D7E8);
        graphics.drawWordWrap(font,
                Component.translatable("screen.frozendawn.difficulty.handbook"),
                contentX, contentY + handbookY - scrollOffset, contentWidth, 0xFFCFE8F4);
        graphics.drawWordWrap(font,
                Component.translatable("screen.frozendawn.difficulty.lock_notice"),
                contentX, contentY + lockNoticeY - scrollOffset, contentWidth, 0xFF97B5C5);

        graphics.disableScissor();
        renderScrollbar(graphics);
    }

    private void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xC60A1320, 0xDE060A12);
        graphics.fill(panelX - 4, panelY - 4, panelX + panelWidth + 4, panelY + panelHeight + 4, 0x3A7DDFFF);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF0B121B);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF6CCAF0);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF61AED9);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF08131D);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF08131D);
        graphics.fill(panelX + 2, panelY + 2, panelX + panelWidth - 2, panelY + HEADER_H - 1, 0xFF112230);
        graphics.fill(panelX + 2, panelY + HEADER_H - 1, panelX + panelWidth - 2, panelY + HEADER_H, 0xFF4AAED7);
        graphics.fill(contentX - 4, contentY - 4, contentX + contentWidth + 4, contentY + contentHeight + 4, 0x30000000);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) {
            return;
        }

        int barX = panelX + panelWidth - 10;
        int barTop = contentY;
        int barBottom = contentY + contentHeight;
        graphics.fill(barX, barTop, barX + 4, barBottom, 0xFF0A121A);

        int thumbHeight = Math.max(18, (int) ((contentHeight / (float) contentTotalHeight) * contentHeight));
        int travel = contentHeight - thumbHeight;
        int thumbY = barTop + (travel <= 0 ? 0 : Math.round((scrollOffset / (float) maxScroll) * travel));
        graphics.fill(barX, thumbY, barX + 4, thumbY + thumbHeight, 0xFF61BCE7);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !submitted && mouseX >= contentX && mouseX <= contentX + contentWidth
                && mouseY >= contentY && mouseY <= contentY + contentHeight) {
            for (PresetCard card : cards) {
                int drawY = contentY + card.baseY() - scrollOffset;
                if (mouseX >= contentX && mouseX <= contentX + contentWidth
                        && mouseY >= drawY && mouseY <= drawY + card.height()) {
                    selectedPreset = card.preset();
                    updateSelectionState();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scrollOffset = clamp(scrollOffset - (int) Math.round(scrollY * 18.0), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        // Intentional: the selector cannot be dismissed without confirming a preset.
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record PresetCard(
            ConfigPresets preset,
            Component title,
            Component line1,
            Component line2,
            int accentColor,
            int shadowColor,
            int width,
            int height,
            int textWidth,
            int line1Height,
            int baseY
    ) {
        private PresetCard(ConfigPresets preset, Component title, Component line1, Component line2,
                           int accentColor, int shadowColor, int width, net.minecraft.client.gui.Font font, int baseY) {
            this(
                    preset,
                    title,
                    line1,
                    line2,
                    accentColor,
                    shadowColor,
                    width,
                    measureHeight(font, width, line1, line2),
                    width - 20,
                    font.wordWrapHeight(line1, width - 20),
                    baseY
            );
        }

        private static int measureHeight(net.minecraft.client.gui.Font font, int width, Component line1, Component line2) {
            int textWidth = width - 20;
            int line1Height = font.wordWrapHeight(line1, textWidth);
            int line2Height = font.wordWrapHeight(line2, textWidth);
            return 12 + 9 + 6 + line1Height + 3 + line2Height + 10;
        }

        private void render(GuiGraphics graphics, net.minecraft.client.gui.Font font, int x, int y,
                            int mouseX, int mouseY, boolean selected, boolean active) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
            int outline = selected ? accentColor : (hovered ? lighten(accentColor, 26) : 0xFF20384B);
            int fill = selected ? shadowColor : 0xFF0F1822;
            if (!active) {
                fill = 0xFF0A1016;
                outline = 0xFF26323A;
            }

            graphics.fill(x, y, x + width, y + height, fill);
            graphics.fill(x, y, x + width, y + 1, outline);
            graphics.fill(x, y, x + 1, y + height, outline);
            graphics.fill(x + width - 1, y, x + width, y + height, 0xFF071119);
            graphics.fill(x, y + height - 1, x + width, y + height, 0xFF071119);
            graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, selected ? darken(fill, 10) : darken(fill, 4));

            int titleColor = selected ? 0xFFF4FBFF : 0xFFDCF4FF;
            int bodyColor = selected ? 0xFFBDD9E6 : 0xFF8FB2C0;
            graphics.drawString(font, title, x + 10, y + 8, titleColor, false);
            int bodyY = y + 23;
            graphics.drawWordWrap(font, line1, x + 10, bodyY, textWidth, bodyColor);
            graphics.drawWordWrap(font, line2, x + 10, bodyY + line1Height + 3, textWidth, bodyColor);

            if (selected) {
                Component selectedText = Component.translatable("screen.frozendawn.difficulty.selected");
                int textX = x + width - font.width(selectedText) - 10;
                graphics.drawString(font, selectedText, textX, y + 8, lighten(accentColor, 42), false);
            }
        }

        private static int lighten(int color, int amount) {
            int a = (color >>> 24) & 0xFF;
            int r = Math.min(255, ((color >>> 16) & 0xFF) + amount);
            int g = Math.min(255, ((color >>> 8) & 0xFF) + amount);
            int b = Math.min(255, (color & 0xFF) + amount);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static int darken(int color, int amount) {
            int a = (color >>> 24) & 0xFF;
            int r = Math.max(0, ((color >>> 16) & 0xFF) - amount);
            int g = Math.max(0, ((color >>> 8) & 0xFF) - amount);
            int b = Math.max(0, (color & 0xFF) - amount);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}
