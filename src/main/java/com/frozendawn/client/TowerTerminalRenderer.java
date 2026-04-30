package com.frozendawn.client;

import com.frozendawn.network.OpenTowerTerminalPayload;
import com.frozendawn.terminal.TowerArchive;
import com.frozendawn.terminal.TowerTerminalPuzzle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class TowerTerminalRenderer {

    private static final int BOX_PAD = 10;
    private static final int ARCHIVE_SCROLLBAR_W = 6;
    private static final int PROGRESS_BAR_H = 14;
    private static final int ALIGN_TICKS_TOTAL = 20 * 30;
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
        Component headerTitle = viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE
                && TowerArchive.isBlackglassPage(viewModel.archivePage(), viewModel.archivePasswordPrompt())
                ? Component.literal("ORSA COMMAND ARCHIVE")
                : title;
        drawCenteredScaledString(graphics, font, headerTitle, layout.panelX + layout.panelW / 2, layout.titleY,
                layout.headerScale, 0xFFE4F7FF);
        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE) {
            renderHeaderBadge(graphics, font, layout, viewModel);
        }

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
        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE) {
            return;
        }
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

        int logoSize = OrsaLogoRenderer.bootDrawSize();
        int logoCenterX = centerX;
        int logoCenterY = contentTop + 26;
        OrsaLogoRenderer.drawBoot(graphics, logoCenterX, logoCenterY, logoSize, viewModel.bootTicks() / 22.0F);

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
        boolean blackglass = TowerArchive.isBlackglassPage(viewModel.archivePage(), viewModel.archivePasswordPrompt());
        if (blackglass) {
            renderBlackglassLockdownState(graphics, font, layout, viewModel);
            return;
        }
        renderInfoBox(graphics, font, archiveLayout.directoryX(), archiveLayout.directoryY(),
                archiveLayout.directoryW(), archiveLayout.directoryH(), "DIRECTORY", 0xFFB7F1FF, 0xFF071019);
        renderInfoBox(graphics, font, archiveLayout.detailX(), archiveLayout.detailY(), archiveLayout.detailW(),
                archiveLayout.detailH(), viewModel.archiveTitle().isBlank() ? "ARCHIVE DATA" : viewModel.archiveTitle(),
                0xFFB7F1FF, 0xFF071019);

        int pageTotal = layout.directoryPageTotal(viewModel.archivePageCount());
        int rowY = archiveLayout.directoryContentY() - viewModel.archiveDirectoryScroll();
        int rowHeight = layout.archiveRowHeight();
        graphics.enableScissor(archiveLayout.directoryContentX(), archiveLayout.directoryContentY(),
                archiveLayout.directoryContentX() + archiveLayout.directoryContentW(),
                archiveLayout.directoryContentY() + archiveLayout.directoryContentH());
        for (int i = 0; i < pageTotal; i++) {
            int rowBottom = rowY + rowHeight;
            if (rowBottom >= archiveLayout.directoryContentY() - 2
                    && rowY <= archiveLayout.directoryContentY() + archiveLayout.directoryContentH()) {
                String label = TowerArchive.directoryTitle(i);
                boolean selected = i == (viewModel.archivePage() >= TowerArchive.COMMAND_PAGE
                        ? TowerArchive.COMMAND_PAGE : viewModel.archivePage());
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

        renderArchiveBody(graphics, font, layout, viewModel, blackglass);
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

    private void renderBlackglassLockdownState(GuiGraphics graphics, Font font, TowerTerminalLayout layout,
                                               TowerTerminalViewModel viewModel) {
        TowerTerminalLayout.ArchiveLayout archiveLayout = layout.archiveLayout;
        renderInfoBox(graphics, font, archiveLayout.detailX(), archiveLayout.detailY(), archiveLayout.detailW(),
                archiveLayout.detailH(), "HIGH-AUTH VOICEPRINT MASKING ACTIVE", 0xFFFFB33C, 0xFF050C11);

        int pulse = viewModel.blinkTicks();
        int top = archiveLayout.detailY() + 23;
        graphics.fill(archiveLayout.detailX() + BOX_PAD, top,
                archiveLayout.detailX() + archiveLayout.detailW() - BOX_PAD, top + 1, 0xFFFFB33C);
        for (int i = 0; i < 5; i++) {
            int lineY = archiveLayout.detailContentY() + Math.floorMod(pulse * (i + 2) + i * 37,
                    Math.max(1, archiveLayout.detailContentH()));
            graphics.fill(archiveLayout.detailX() + BOX_PAD, lineY,
                    archiveLayout.detailX() + archiveLayout.detailW() - BOX_PAD, lineY + 1,
                    i % 2 == 0 ? 0x302BDDF4 : 0x24FFB33C);
        }

        int statusY = archiveLayout.detailY() + 27;
        drawScaledString(graphics, font, Component.literal("STATUS: DIRECTORY SEALED // READ-ONLY TRANSCRIPT MODE"),
                archiveLayout.detailX() + BOX_PAD, statusY, layout.auditScale, 0xFF58D7F0);
        drawScaledString(graphics, font, Component.literal("REC ID: BG-556.17"),
                archiveLayout.detailX() + archiveLayout.detailW() - 104, statusY,
                layout.auditScale, 0xFFB7F1FF);
        drawScaledString(graphics, font, Component.literal("AUTH OVERRIDE: BLACKGLASS // OS LOCKDOWN ACTIVE"),
                archiveLayout.detailX() + BOX_PAD, statusY + layout.auditLineHeight + 2,
                layout.auditScale, 0xFFFFB33C);

        renderArchiveBody(graphics, font, layout, viewModel, true);
        renderArchiveScrollbar(graphics, archiveLayout.detailScrollbarX(), archiveLayout.detailContentY(),
                archiveLayout.detailContentH(), viewModel.archiveDetailScroll(), layout.detailScrollMax(),
                archiveLayout.detailContentH(), 0xFF18303C, 0xFFFFB33C);
        renderLockdownSegmentBar(graphics, font, layout, viewModel);
    }

    private void renderHeaderBadge(GuiGraphics graphics, Font font, TowerTerminalLayout layout,
                                   TowerTerminalViewModel viewModel) {
        boolean blackglass = TowerArchive.isBlackglassPage(viewModel.archivePage(), viewModel.archivePasswordPrompt());
        drawScaledString(graphics, font, Component.literal("[ORSA OS v1.3.7]"),
                layout.panelX + 16, layout.panelY + 11, layout.auditScale, 0xFF58BDE4);
        drawScaledString(graphics, font, Component.literal(blackglass ? "FD-ARCHIVE-77A" : "FD-ARCHIVE"),
                layout.panelX + layout.panelW - 132, layout.panelY + 11, layout.auditScale, 0xFF58BDE4);
    }

    private void renderArchiveBody(GuiGraphics graphics, Font font, TowerTerminalLayout layout,
                                   TowerTerminalViewModel viewModel, boolean blackglass) {
        TowerTerminalLayout.ArchiveLayout archiveLayout = layout.archiveLayout;
        int contentY = archiveLayout.detailContentY() - viewModel.archiveDetailScroll();
        int detailMaxY = viewModel.archivePasswordPrompt()
                ? archiveLayout.detailContentY() + archiveLayout.detailContentH() - layout.auditLineHeight - 10
                : archiveLayout.detailContentY() + archiveLayout.detailContentH();
        int scaledWidth = Math.max(24, (int) Math.floor(archiveLayout.detailContentW() / layout.auditScale));
        graphics.enableScissor(archiveLayout.detailContentX(), archiveLayout.detailContentY(),
                archiveLayout.detailContentX() + archiveLayout.detailContentW(),
                archiveLayout.detailContentY() + archiveLayout.detailContentH());
        for (String rawLine : viewModel.archiveBodyLines()) {
            int color = archiveLineColor(rawLine, blackglass);
            for (FormattedCharSequence wrapped : font.split(Component.literal(rawLine), scaledWidth)) {
                if (contentY + layout.auditLineHeight >= archiveLayout.detailContentY() - 2 && contentY <= detailMaxY) {
                    drawScaledString(graphics, font, wrapped, archiveLayout.detailContentX(), contentY,
                            layout.auditScale, color);
                }
                contentY += layout.auditLineHeight;
            }
        }
        graphics.disableScissor();
    }

    private int archiveLineColor(String rawLine, boolean blackglass) {
        if (!blackglass) {
            return 0xFFD4EEF7;
        }
        String line = rawLine.toLowerCase(java.util.Locale.ROOT);
        if (rawLine.isBlank()) {
            return 0xFF5E8B9D;
        }
        if (rawLine.startsWith("BLACKGLASS") || rawLine.startsWith("ORSA EXECUTIVE")
                || rawLine.startsWith("DATE:") || rawLine.startsWith("STATUS:") || rawLine.startsWith("SOURCE:")) {
            return 0xFFB7F1FF;
        }
        if (line.contains("we create demand")
                || line.contains("blackglass arrays")
                || line.contains("mars to become the solution")
                || line.contains("it was never rescue")
                || line.contains("make sure they call us saviors")) {
            return 0xFFFFD27A;
        }
        if (rawLine.startsWith("[") && rawLine.endsWith(":")) {
            return 0xFF58D7F0;
        }
        if (rawLine.contains("Silence") || rawLine.contains("Recording")) {
            return 0xFF7EA6B5;
        }
        return 0xFFE5F7FC;
    }

    private void renderRecoveredAudioPlayer(GuiGraphics graphics, Font font, TowerTerminalLayout layout,
                                            TowerTerminalViewModel viewModel) {
        TowerTerminalLayout.ArchiveLayout archiveLayout = layout.archiveLayout;
        int x = archiveLayout.audioX();
        int y = archiveLayout.audioY();
        int w = archiveLayout.audioW();
        int h = archiveLayout.audioH();
        int segmentIndex = TowerArchive.blackglassSegmentIndex(viewModel.archivePage());

        graphics.fill(x, y, x + w, y + h, 0xFF071019);
        graphics.fill(x, y, x + w, y + 1, 0xFF58BDE4);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF102B36);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF1C5366);

        drawScaledString(graphics, font, Component.literal("RECOVERED AUDIO"),
                x + BOX_PAD, y + 7, layout.auditScale, 0xFF58D7F0);
        drawScaledString(graphics, font,
                Component.literal(TowerArchive.blackglassSegmentTimecode(segmentIndex) + " / 08:36"),
                x + w - 86, y + 7, layout.auditScale, 0xFFB7F1FF);

        int buttonX = layout.audioButtonX();
        int buttonY = layout.audioButtonY();
        int buttonSize = layout.audioButtonSize();
        renderAudioButton(graphics, font, buttonX, buttonY, buttonSize, ">", 0xFF54D7EF, layout);
        renderAudioButton(graphics, font, buttonX + buttonSize + 5, buttonY, buttonSize, "II", 0xFF7EA6B5, layout);
        renderAudioButton(graphics, font, buttonX + (buttonSize + 5) * 2, buttonY, buttonSize, "S", 0xFF7EA6B5, layout);

        int ttsW = Math.min(104, Math.max(86, w / 6));
        int ttsX = x + w - BOX_PAD - ttsW;
        int waveX = buttonX + (buttonSize + 5) * 3 + 10;
        int waveY = y + 24;
        int waveW = Math.max(70, ttsX - waveX - 10);
        renderWaveform(graphics, waveX, waveY, waveW, 18, viewModel.archiveAudioTicks(), viewModel.archiveAudioPlaying());

        int progressY = y + 47;
        graphics.fill(x + BOX_PAD, progressY, x + w - BOX_PAD, progressY + 7, 0xFF0B222C);
        int progressW = Math.max(10, Math.round((w - BOX_PAD * 2) * ((segmentIndex + 0.38f) / TowerArchive.blackglassSegmentCount())));
        graphics.fill(x + BOX_PAD + 2, progressY + 2, x + BOX_PAD + progressW, progressY + 5, 0xFF58D7F0);
        int scrubX = x + BOX_PAD + progressW;
        graphics.fill(scrubX - 2, progressY - 2, scrubX + 2, progressY + 9, 0xFFB7F1FF);

        graphics.fill(ttsX, y + 20, ttsX + ttsW, y + 43, 0xFF0A1A22);
        graphics.fill(ttsX, y + 20, ttsX + ttsW, y + 21, 0xFF1C5366);
        drawScaledString(graphics, font, Component.literal("TTS RECON"),
                ttsX + 6, y + 25, layout.auditScale, 0xFF58D7F0);
        renderMiniTtsBars(graphics, ttsX + 60, y + 30, viewModel.archiveAudioTicks(), viewModel.archiveAudioPlaying());

        drawScaledString(graphics, font, Component.literal("SEG"),
                x + BOX_PAD, layout.segmentTabsY() + 3, layout.auditScale, 0xFF58D7F0);
        int tabY = layout.segmentTabsY();
        for (int i = 0; i < TowerArchive.blackglassSegmentCount(); i++) {
            int tabX = layout.segmentTabX(i, TowerArchive.blackglassSegmentCount());
            boolean selected = i == segmentIndex;
            int border = selected ? 0xFF58D7F0 : 0xFF1C5366;
            graphics.fill(tabX, tabY, tabX + layout.segmentTabW(), tabY + layout.segmentTabH(),
                    selected ? 0xFF12323E : 0xFF071019);
            graphics.fill(tabX, tabY, tabX + layout.segmentTabW(), tabY + 1, border);
            graphics.fill(tabX, tabY + layout.segmentTabH() - 1, tabX + layout.segmentTabW(), tabY + layout.segmentTabH(), border);
            drawCenteredScaledString(graphics, font, Component.literal(String.format(java.util.Locale.US, "%02d", i + 1)),
                    tabX + layout.segmentTabW() / 2, tabY + 3, layout.auditScale, selected ? 0xFFF5F7EE : 0xFF7EA6B5);
        }

        int warningY = y + h - 11;
        graphics.fill(x + BOX_PAD, warningY - 2, x + w - BOX_PAD, warningY - 1, 0xFFFFB33C);
        drawCenteredScaledString(graphics, font, Component.literal("/// EXECUTIVE LIABILITY MATERIAL ///"),
                x + w / 2, warningY, layout.auditScale, 0xFFFFB33C);
    }

    private void renderLockdownSegmentBar(GuiGraphics graphics, Font font, TowerTerminalLayout layout,
                                          TowerTerminalViewModel viewModel) {
        TowerTerminalLayout.ArchiveLayout archiveLayout = layout.archiveLayout;
        int x = archiveLayout.audioX();
        int y = archiveLayout.audioY();
        int w = archiveLayout.audioW();
        int h = archiveLayout.audioH();
        int segmentIndex = TowerArchive.blackglassSegmentIndex(viewModel.archivePage());

        graphics.fill(x, y, x + w, y + h, 0xFF071019);
        graphics.fill(x, y, x + w, y + 1, 0xFFFFB33C);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF102B36);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF1C5366);

        drawScaledString(graphics, font, Component.literal("LOCKDOWN TRANSCRIPT"),
                x + BOX_PAD, y + 7, layout.auditScale, 0xFFFFB33C);
        drawScaledString(graphics, font,
                Component.literal(TowerArchive.blackglassSegmentTitle(segmentIndex)),
                x + 132, y + 7, layout.auditScale, 0xFFB7F1FF);
        drawScaledString(graphics, font,
                Component.literal(TowerArchive.blackglassSegmentTimecode(segmentIndex) + " / 08:36"),
                x + w - 86, y + 7, layout.auditScale, 0xFF58D7F0);

        int buttonX = layout.audioButtonX();
        int buttonY = layout.audioButtonY();
        int buttonSize = layout.audioButtonSize();
        renderAudioButton(graphics, font, buttonX, buttonY, buttonSize, ">", 0xFF54D7EF, layout);
        renderAudioButton(graphics, font, buttonX + buttonSize + 5, buttonY, buttonSize, "S", 0xFFFFB33C, layout);
        drawScaledString(graphics, font,
                Component.literal(viewModel.archiveAudioPlaying() ? "RECOVERED AUDIO PLAYING" : "RECOVERED AUDIO READY"),
                buttonX + (buttonSize + 5) * 2 + 8, buttonY + 4, layout.auditScale,
                viewModel.archiveAudioPlaying() ? 0xFFFFF0C2 : 0xFF7EA6B5);
        int waveX = x + Math.max(260, w / 3);
        int waveW = Math.max(76, w - waveX + x - BOX_PAD);
        renderWaveform(graphics, waveX, buttonY + 1, waveW, 16,
                viewModel.archiveAudioTicks(), viewModel.archiveAudioPlaying());

        drawScaledString(graphics, font, Component.literal("SEGMENTS"),
                x + BOX_PAD, layout.segmentTabsY() + 3, layout.auditScale, 0xFF58D7F0);
        int tabY = layout.segmentTabsY();
        for (int i = 0; i < TowerArchive.blackglassSegmentCount(); i++) {
            int tabX = layout.segmentTabX(i, TowerArchive.blackglassSegmentCount());
            boolean selected = i == segmentIndex;
            int border = selected ? 0xFFFFB33C : 0xFF1C5366;
            graphics.fill(tabX, tabY, tabX + layout.segmentTabW(), tabY + layout.segmentTabH(),
                    selected ? 0xFF2E2108 : 0xFF071019);
            graphics.fill(tabX, tabY, tabX + layout.segmentTabW(), tabY + 1, border);
            graphics.fill(tabX, tabY + layout.segmentTabH() - 1, tabX + layout.segmentTabW(),
                    tabY + layout.segmentTabH(), border);
            graphics.fill(tabX, tabY, tabX + 1, tabY + layout.segmentTabH(), border);
            graphics.fill(tabX + layout.segmentTabW() - 1, tabY, tabX + layout.segmentTabW(),
                    tabY + layout.segmentTabH(), border);
            drawCenteredScaledString(graphics, font,
                    Component.literal(String.format(java.util.Locale.US, "%02d", i + 1)),
                    tabX + layout.segmentTabW() / 2, tabY + 3,
                    layout.auditScale, selected ? 0xFFFFF0C2 : 0xFF7EA6B5);
        }

        int warningY = y + h - 11;
        graphics.fill(x + BOX_PAD, warningY - 2, x + w - BOX_PAD, warningY - 1, 0xFFFFB33C);
        drawCenteredScaledString(graphics, font, Component.literal("/// EXECUTIVE LIABILITY MATERIAL ///"),
                x + w / 2, warningY, layout.auditScale, 0xFFFFB33C);
    }

    private void renderAudioButton(GuiGraphics graphics, Font font, int x, int y, int size, String label, int accent,
                                   TowerTerminalLayout layout) {
        graphics.fill(x, y, x + size, y + size, 0xFF071019);
        graphics.fill(x, y, x + size, y + 1, accent);
        graphics.fill(x, y, x + 1, y + size, accent);
        graphics.fill(x + size - 1, y, x + size, y + size, 0xFF0A1720);
        graphics.fill(x, y + size - 1, x + size, y + size, 0xFF0A1720);
        drawCenteredScaledString(graphics, font, Component.literal(label), x + size / 2, y + 5,
                layout.auditScale, accent);
    }

    private void renderWaveform(GuiGraphics graphics, int x, int y, int w, int h, int ticks, boolean playing) {
        graphics.fill(x, y - 2, x + w, y + h + 2, 0x20000000);
        int centerY = y + h / 2;
        int bars = Math.max(12, w / 5);
        for (int i = 0; i < bars; i++) {
            int phase = playing ? ticks / 2 : 0;
            int magnitude = 4 + Math.abs(((i * 11 + phase) % 25) - 12);
            int barH = Math.min(h - 4, magnitude + (i % 5));
            int barX = x + i * w / bars;
            int color = (i + phase) % 7 == 0 ? 0xFFB7F1FF : 0xFF2FB8D0;
            graphics.fill(barX, centerY - barH / 2, barX + 2, centerY + barH / 2, color);
        }
    }

    private void renderTtsGlyph(GuiGraphics graphics, int x, int y, int ticks, boolean playing) {
        graphics.fill(x, y, x + 24, y + 24, 0xFF163D4A);
        graphics.fill(x + 4, y + 4, x + 20, y + 20, 0xFF2B7180);
        graphics.fill(x + 18, y + 12, x + 30, y + 16, 0xFF2B7180);
        int baseX = x + 42;
        for (int i = 0; i < 12; i++) {
            int phase = playing ? ticks / 3 : 0;
            int barH = 5 + Math.abs(((i * 7 + phase) % 18) - 9);
            graphics.fill(baseX + i * 5, y + 18 - barH, baseX + i * 5 + 2, y + 18, 0xFF58D7F0);
        }
    }

    private void renderMiniTtsBars(GuiGraphics graphics, int x, int y, int ticks, boolean playing) {
        for (int i = 0; i < 7; i++) {
            int phase = playing ? ticks / 3 : 0;
            int barH = 3 + Math.abs(((i * 5 + phase) % 10) - 5);
            graphics.fill(x + i * 5, y + 9 - barH, x + i * 5 + 2, y + 9, 0xFF58D7F0);
        }
    }

    private void renderSpeakerChip(GuiGraphics graphics, Font font, int x, int y, String label, int accent,
                                   TowerTerminalLayout layout) {
        int w = Math.max(34, Math.round(font.width(label) * layout.auditScale) + 12);
        graphics.fill(x, y, x + w, y + 14, 0xFF071019);
        graphics.fill(x, y, x + w, y + 1, accent);
        graphics.fill(x, y, x + 1, y + 14, accent);
        graphics.fill(x + 4, y + 5, x + 7, y + 8, accent);
        drawScaledString(graphics, font, Component.literal(label), x + 10, y + 4, layout.auditScale, 0xFFE5F7FC);
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
