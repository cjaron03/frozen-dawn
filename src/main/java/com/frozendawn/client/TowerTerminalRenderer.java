package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.OpenTowerTerminalPayload;
import com.frozendawn.terminal.TowerArchive;
import com.frozendawn.terminal.TowerTerminalPuzzle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

final class TowerTerminalRenderer {

    private static final int BOX_PAD = 10;
    private static final int ARCHIVE_SCROLLBAR_W = 6;
    private static final int PROGRESS_BAR_H = 14;
    private static final int ALIGN_TICKS_TOTAL = 20 * 30;
    private static final ResourceLocation ORSA_LOGO =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_logo.png");
    private static final String[] BOOT_LINES = {
            "[  OK  ] POST COMPLETE",
            "[  OK  ] MEMORY 64K ... VERIFIED",
            "[  OK  ] ORSA KERNEL v4.3.07-UPLK",
            "[  OK  ] ANTENNA ARRAY INITIALIZED",
            "[  OK  ] SATELLITE HANDSHAKE COMPLETE",
            "[  OK  ] ENCRYPTION LAYER ACTIVE",
            "[  OK  ] UPLINK CHANNEL OPEN",
            "[READY ] TERMINAL AWAITING INPUT",
    };

    void render(GuiGraphics graphics, int mouseX, int mouseY, int width, int height, Font font, Component title,
                TowerTerminalLayout layout, TowerTerminalViewModel viewModel) {
        renderFrame(graphics, width, height, layout);
        drawCenteredScaledString(graphics, font, title, layout.panelX + layout.panelW / 2, layout.titleY,
                layout.headerScale, 0xFFE4F7FF);

        if (viewModel.booting()) {
            renderBootSequence(graphics, font, layout, viewModel);
            return;
        }

        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ACTIVE) {
            renderAttempts(graphics, font, layout, viewModel);
            renderBoard(graphics, font, layout, viewModel);
            renderAudit(graphics, font, layout, viewModel);
            return;
        }

        renderStatePanel(graphics, font, mouseX, mouseY, layout, viewModel);
        renderAudit(graphics, font, layout, viewModel);
    }

    private void renderFrame(GuiGraphics graphics, int width, int height, TowerTerminalLayout layout) {
        graphics.fillGradient(0, 0, width, height, 0xD0081018, 0xE004090E);
        graphics.fill(layout.panelX - 3, layout.panelY - 3, layout.panelX + layout.panelW + 3,
                layout.panelY + layout.panelH + 3, 0x4054C6F6);
        graphics.fill(layout.panelX, layout.panelY, layout.panelX + layout.panelW, layout.panelY + layout.panelH, 0xFF081018);
        graphics.fill(layout.panelX + 2, layout.panelY + 2, layout.panelX + layout.panelW - 2,
                layout.headerBottomY - 2, 0xFF10202E);
        graphics.fill(layout.panelX + 2, layout.headerBottomY - 2, layout.panelX + layout.panelW - 2,
                layout.headerBottomY - 1, 0xFF58BDE4);
        graphics.fill(layout.panelX + 2, layout.panelY + 2, layout.panelX + 3, layout.panelY + layout.panelH - 2, 0xFF294D60);
        graphics.fill(layout.panelX + layout.panelW - 3, layout.panelY + 2, layout.panelX + layout.panelW - 2,
                layout.panelY + layout.panelH - 2, 0xFF0A1720);
    }

    private void renderBootSequence(GuiGraphics graphics, Font font, TowerTerminalLayout layout,
                                    TowerTerminalViewModel viewModel) {
        int centerX = layout.panelX + layout.panelW / 2;
        int contentTop = layout.headerBottomY + 8;
        int contentBottom = layout.panelY + layout.panelH - 14;

        int logoSize = 32;
        int logoCenterX = centerX;
        int logoCenterY = contentTop + 26;
        float angle = viewModel.bootTicks() * (720.0f / viewModel.bootDuration());
        float xSquash = (float) Math.cos(Math.toRadians(angle));
        float logoScale = logoSize / 16.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(logoCenterX, logoCenterY, 0);
        graphics.pose().scale(xSquash * logoScale, logoScale, 1.0f);
        graphics.blit(ORSA_LOGO, -8, -8, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();

        int textX = layout.panelX + 22;
        int textY = logoCenterY + logoSize / 2 + 10;
        int lineStep = layout.headerLineHeight + 2;
        for (int i = 0; i < BOOT_LINES.length; i++) {
            int showAt = 4 + i * 6;
            if (viewModel.bootTicks() >= showAt) {
                int color = BOOT_LINES[i].startsWith("[READY") ? 0xFF9DF4B6 : 0xFF5E8B9D;
                String prefix = BOOT_LINES[i].substring(0, 8);
                String body = BOOT_LINES[i].substring(8);
                drawScaledString(graphics, font, prefix, textX, textY + i * lineStep, layout.headerScale, 0xFF4CB7E3);
                drawScaledString(graphics, font, body,
                        textX + (int) (font.width(prefix) * layout.headerScale),
                        textY + i * lineStep, layout.headerScale, color);
            }
        }

        int barX = layout.panelX + 40;
        int barW = layout.panelW - 80;
        int barY = contentBottom - 6;
        float barProgress = Math.max(0.0f,
                Math.min(1.0f, (viewModel.bootTicks() - 4.0f) / (viewModel.bootDuration() - 8.0f)));
        graphics.fill(barX, barY, barX + barW, barY + 3, 0xFF0D1E2A);
        graphics.fill(barX, barY, barX + (int) (barW * barProgress), barY + 3, 0xFF4CB7E3);
    }

    private void renderAttempts(GuiGraphics graphics, Font font, TowerTerminalLayout layout,
                                TowerTerminalViewModel viewModel) {
        drawCenteredScaledString(graphics, font,
                Component.literal("ATTEMPTS: " + "*".repeat(Math.max(viewModel.triesLeft(), 0))),
                layout.panelX + layout.panelW / 2,
                layout.attemptsY,
                layout.headerScale,
                viewModel.triesLeft() > 1 ? 0xFFB7F1FF : 0xFFFF8B8B);
    }

    private void renderBoard(GuiGraphics graphics, Font font, TowerTerminalLayout layout,
                             TowerTerminalViewModel viewModel) {
        for (int segmentIndex = 0; segmentIndex < TowerTerminalPuzzle.SEGMENTS; segmentIndex++) {
            TowerTerminalLayout.SegmentLayout segmentLayout = layout.segmentLayout(segmentIndex);
            drawScaledString(graphics, font, segmentLayout.address(), segmentLayout.addressX(), segmentLayout.y(),
                    layout.boardScale, 0xFF5E8B9D);
            String rendered = viewModel.board().renderSegment(segmentIndex, viewModel.removedMask(), viewModel.usedPairMask());
            for (int i = 0; i < rendered.length(); i++) {
                drawScaledString(graphics, font, String.valueOf(rendered.charAt(i)),
                        segmentLayout.textX() + i * layout.scaledCharStep, segmentLayout.y(),
                        layout.boardScale, 0xFF9FD5E4);
            }
        }
    }

    private void renderAudit(GuiGraphics graphics, Font font, TowerTerminalLayout layout,
                             TowerTerminalViewModel viewModel) {
        String title = viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE ? "PRIORITY ROUTING" : "AUDIT LOG";
        renderInfoBox(graphics, font, layout.auditX, layout.auditY, layout.auditW, layout.auditH, title, 0xFFD9F4FF, 0xFF0C141B);

        int y = layout.auditY + 24;
        int inputY = layout.auditY + layout.auditH - BOX_PAD - layout.auditLineHeight;
        int maxY = inputY - 8;
        for (String line : viewModel.auditLines()) {
            int color = 0xFF9FD5E4;
            if (viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE
                    && (line.startsWith("ORSA PRIORITY")
                    || line.startsWith("LAST HANDSHAKE")
                    || line.startsWith("QUEUE DEPTH"))) {
                color = 0xFFD9F4FF;
            }
            y = drawWrappedScaledLine(graphics, font, Component.literal(line), layout.auditX + BOX_PAD, y,
                    layout.auditW - BOX_PAD * 2, maxY, color, layout.auditScale);
            y += 1;
        }

        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ACTIVE) {
            String cursor = (viewModel.blinkTicks() / 8) % 2 == 0 ? "_" : "";
            drawScaledString(graphics, font, Component.literal("> " + viewModel.terminalInput() + cursor),
                    layout.auditX + BOX_PAD, inputY, layout.auditScale, 0xFFBDF8FF);
        }
    }

    private void renderStatePanel(GuiGraphics graphics, Font font, int mouseX, int mouseY,
                                  TowerTerminalLayout layout, TowerTerminalViewModel viewModel) {
        int boxX = layout.panelX + 18;
        int boxY = layout.headerBottomY + 8;
        int boxW = layout.panelW - 36;
        int boxH = Math.max(110, layout.auditY - boxY - 12);

        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE) {
            renderArchiveState(graphics, font, mouseX, mouseY, layout, viewModel);
            return;
        }

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF0D1820);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF4CB7E3);
        graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF0A1219);

        if (viewModel.state() == OpenTowerTerminalPayload.STATE_LOCKED_OUT) {
            graphics.drawCenteredString(font, Component.literal("TERMINAL LOCKED"), boxX + boxW / 2, boxY + 18, 0xFFFF8B8B);
            graphics.drawCenteredString(font, Component.literal("Please contact an administrator."), boxX + boxW / 2, boxY + 36, 0xFFB7C7D0);
            String reset = "RESET IN " + Math.max(1, (viewModel.lockoutTicksRemaining() + 19) / 20) + "s";
            graphics.drawCenteredString(font, Component.literal(reset), boxX + boxW / 2, boxY + 56, 0xFF87C5D8);
            return;
        }

        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ALIGNING) {
            graphics.drawCenteredString(font, Component.literal("TRANSMISSION IN PROGRESS"), boxX + boxW / 2, boxY + 18, 0xFF9DF4B6);
            graphics.drawCenteredString(font, Component.literal("Maintaining ORSA uplink lock..."), boxX + boxW / 2, boxY + 36, 0xFFB7C7D0);
            renderProgressBar(graphics, boxX + 28, boxY + 58, boxW - 56,
                    Math.max(0, 1.0f - (float) viewModel.alignTicksRemaining() / ALIGN_TICKS_TOTAL));
            String time = String.format("%02d:%02d",
                    Math.max(0, viewModel.alignTicksRemaining()) / 20 / 60,
                    (Math.max(0, viewModel.alignTicksRemaining()) / 20) % 60);
            graphics.drawCenteredString(font, Component.literal("TIME TO TRANSMISSION  " + time),
                    boxX + boxW / 2, boxY + 78, 0xFFB6EEF8);
            return;
        }

        graphics.drawCenteredString(font, Component.literal("TRANSMISSION COMPLETE"), boxX + boxW / 2, boxY + 30, 0xFF9DF4B6);
        graphics.drawCenteredString(font, Component.literal("Tower cache released ORSA locator data."),
                boxX + boxW / 2, boxY + 50, 0xFFB7C7D0);
    }

    private void renderArchiveState(GuiGraphics graphics, Font font, int mouseX, int mouseY,
                                    TowerTerminalLayout layout, TowerTerminalViewModel viewModel) {
        TowerTerminalLayout.ArchiveLayout archiveLayout = layout.archiveLayout;
        renderInfoBox(graphics, font, archiveLayout.directoryX(), archiveLayout.directoryY(),
                archiveLayout.directoryW(), archiveLayout.directoryH(), "DIRECTORY", 0xFFB7F1FF, 0xFF0D1820);
        renderInfoBox(graphics, font, archiveLayout.detailX(), archiveLayout.detailY(), archiveLayout.detailW(),
                archiveLayout.detailH(), viewModel.archiveTitle().isBlank() ? "ARCHIVE DATA" : viewModel.archiveTitle(),
                0xFFB7F1FF, 0xFF0D1820);

        int pageTotal = layout.pageTotal(viewModel.archivePageCount());
        int rowY = archiveLayout.directoryContentY() - viewModel.archiveDirectoryScroll();
        int rowHeight = layout.archiveRowHeight();
        graphics.enableScissor(archiveLayout.directoryContentX(), archiveLayout.directoryContentY(),
                archiveLayout.directoryContentX() + archiveLayout.directoryContentW(),
                archiveLayout.directoryContentY() + archiveLayout.directoryContentH());
        for (int i = 0; i < pageTotal; i++) {
            int rowBottom = rowY + rowHeight;
            if (rowBottom >= archiveLayout.directoryContentY() - 2
                    && rowY <= archiveLayout.directoryContentY() + archiveLayout.directoryContentH()) {
                String label = i < TowerArchive.PAGE_TITLES.length ? TowerArchive.PAGE_TITLES[i] : "ARCHIVE PAGE " + (i + 1);
                boolean selected = i == viewModel.archivePage();
                boolean hovered = layout.archiveEntryAt(mouseX, mouseY, viewModel.archiveDirectoryScroll(),
                        viewModel.archivePageCount()) == i;
                int rowX = archiveLayout.directoryContentX() - 2;
                int rowW = archiveLayout.directoryContentW() + 4;
                int fill = selected ? 0xFF18303C : (hovered ? 0xFF122028 : 0x00000000);
                if (fill != 0) {
                    graphics.fill(rowX, rowY - 1, rowX + rowW, rowBottom - 1, fill);
                    if (selected) {
                        graphics.fill(rowX, rowY - 1, rowX + 3, rowBottom - 1, 0xFF58BDE4);
                    }
                }
                String prefix = selected ? "> " : "  ";
                drawScaledString(graphics, font, Component.literal(prefix + label),
                        archiveLayout.directoryContentX(), rowY + 2, layout.auditScale,
                        selected ? 0xFFF5F7EE : 0xFFCAE6EF);
            }
            rowY += rowHeight + 2;
        }
        graphics.disableScissor();
        renderArchiveScrollbar(graphics, archiveLayout.directoryScrollbarX(), archiveLayout.directoryContentY(),
                archiveLayout.directoryContentH(), viewModel.archiveDirectoryScroll(),
                layout.directoryScrollMax(viewModel.archivePageCount()), archiveLayout.directoryContentH(),
                0xFF18303C, 0xFF58BDE4);

        int contentY = archiveLayout.detailContentY() - viewModel.archiveDetailScroll();
        int detailMaxY = viewModel.archivePasswordPrompt()
                ? archiveLayout.detailContentY() + archiveLayout.detailContentH() - layout.auditLineHeight - 10
                : archiveLayout.detailContentY() + archiveLayout.detailContentH();
        graphics.enableScissor(archiveLayout.detailContentX(), archiveLayout.detailContentY(),
                archiveLayout.detailContentX() + archiveLayout.detailContentW(),
                archiveLayout.detailContentY() + archiveLayout.detailContentH());
        for (FormattedCharSequence line : layout.archiveWrappedBody) {
            if (contentY + layout.auditLineHeight >= archiveLayout.detailContentY() - 2 && contentY <= detailMaxY) {
                drawScaledString(graphics, font, line, archiveLayout.detailContentX(), contentY, layout.auditScale, 0xFFD4EEF7);
            }
            contentY += layout.auditLineHeight;
        }
        graphics.disableScissor();
        renderArchiveScrollbar(graphics, archiveLayout.detailScrollbarX(), archiveLayout.detailContentY(),
                archiveLayout.detailContentH(), viewModel.archiveDetailScroll(), layout.detailScrollMax(),
                archiveLayout.detailContentH(), 0xFF18303C, 0xFF58BDE4);
        if (viewModel.archivePasswordPrompt()) {
            int promptY = archiveLayout.detailY() + archiveLayout.detailH() - BOX_PAD - layout.auditLineHeight - 2;
            String cursor = (viewModel.blinkTicks() / 8) % 2 == 0 ? "_" : "";
            drawScaledString(graphics, font, Component.literal("AUTH: " + viewModel.archivePasswordInput() + cursor),
                    archiveLayout.detailContentX(), promptY, layout.auditScale, 0xFFF5F7EE);
        }
    }

    private void renderProgressBar(GuiGraphics graphics, int x, int y, int w, float progress) {
        int clamped = Math.max(0, Math.min(w, Math.round(w * progress)));
        graphics.fill(x, y, x + w, y + PROGRESS_BAR_H, 0xFF071218);
        graphics.fill(x + 1, y + 1, x + w - 1, y + PROGRESS_BAR_H - 1, 0xFF10202E);
        graphics.fill(x + 2, y + 2, x + 2 + clamped, y + PROGRESS_BAR_H - 2, 0xFF59CBEA);
    }

    private void renderInfoBox(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                               String title, int accent, int fill) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, accent);
        graphics.fill(x, y, x + 1, y + h, accent);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF0A1720);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF0A1720);
        graphics.drawString(font, Component.literal(title), x + BOX_PAD, y + 7, accent, false);
    }

    private int drawWrappedScaledLine(GuiGraphics graphics, Font font, Component line, int x, int y, int width,
                                      int maxY, int color, float scale) {
        int scaledWidth = Math.max(24, (int) Math.floor(width / scale));
        for (FormattedCharSequence wrapped : font.split(line, scaledWidth)) {
            if (y > maxY) {
                return y;
            }
            drawScaledString(graphics, font, wrapped, x, y, scale, color);
            y += Math.max(6, Math.round(9 * scale));
        }
        return y;
    }

    private void drawCenteredScaledString(GuiGraphics graphics, Font font, Component text, int centerX, int y,
                                          float scale, int color) {
        int width = Math.round(font.width(text) * scale);
        drawScaledString(graphics, font, text, centerX - width / 2, y, scale, color);
    }

    private void drawScaledString(GuiGraphics graphics, Font font, Component text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledString(GuiGraphics graphics, Font font, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledString(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
                                  float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void renderArchiveScrollbar(GuiGraphics graphics, int x, int y, int height, int scroll, int maxScroll,
                                        int visibleHeight, int trackColor, int thumbColor) {
        if (maxScroll <= 0) {
            return;
        }
        graphics.fill(x, y, x + ARCHIVE_SCROLLBAR_W, y + height, trackColor);
        int thumbHeight = Math.max(16, Math.round((visibleHeight / (float) (visibleHeight + maxScroll)) * height));
        int thumbTravel = Math.max(1, height - thumbHeight);
        int thumbY = y + Math.round((scroll / (float) maxScroll) * thumbTravel);
        graphics.fill(x, thumbY, x + ARCHIVE_SCROLLBAR_W, thumbY + thumbHeight, thumbColor);
    }
}
