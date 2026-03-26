package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.OpenMonitoringTerminalPayload;
import com.frozendawn.network.SubmitMonitoringTerminalPayload;
import com.frozendawn.terminal.MonitoringStationArchive;
import com.frozendawn.terminal.MonitoringTerminalPuzzle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MonitoringTerminalScreen extends Screen {

    private static final int CHAR_STEP = 5;
    private static final int LINE_HEIGHT = 9;
    private static final int ADDRESS_GAP = 6;
    private static final int SEGMENT_GAP = 18;
    private static final int PANEL_MIN_W = 560;
    private static final int PANEL_MAX_W = 760;
    private static final int PANEL_MIN_H = 320;
    private static final int PANEL_MAX_H = 500;
    private static final int PANEL_PAD = 18;
    private static final int BOX_PAD = 10;
    private static final int ARCHIVE_GAP = 12;
    private static final int ARCHIVE_DIRECTORY_W = 176;
    private static final int ARCHIVE_TOP_GAP = 8;
    private static final int ARCHIVE_SCROLLBAR_W = 6;
    private static final int ARCHIVE_SCROLLBAR_GAP = 6;
    private static final int LOCKOUT_TICKS_TOTAL = 20 * 45;
    private static final int BOOT_DURATION = 65;
    private static final ResourceLocation ORSA_LOGO =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_logo.png");

    private final BlockPos consolePos;
    private long nonce;
    private MonitoringTerminalPuzzle.Board board;
    private int triesLeft;
    private int state;
    private long removedMask;
    private long usedPairMask;
    private int lockoutTicksRemaining;
    private List<String> auditLines = new ArrayList<>();
    private List<String> archiveBodyLines = new ArrayList<>();
    private String archiveTitle = "";
    private int archivePage;
    private int archivePageCount;
    private boolean archivePasswordPrompt;
    private final List<TokenHitbox> interactiveHitboxes = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int charStep = CHAR_STEP;
    private int segmentGap = SEGMENT_GAP;
    private int titleY;
    private int attemptsY;
    private int headerBottomY;
    private int boardY;
    private int boardBottom;
    private int boardLineHeight;
    private int boardCenterX;
    private int scaledCharStep;
    private int scaledAddressWidth;
    private int scaledAddressGap;
    private int scaledSegmentWidth;
    private int scaledSegmentGap;
    private int scaledGroupWidth;
    private int boardRenderedWidth;
    private int auditX;
    private int auditY;
    private int auditW;
    private int auditH;
    private int segmentWidth;
    private int addressWidth;
    private int closeTicks = -1;
    private int blinkTicks;
    private int bootTicks;
    private boolean bootSoundPlayed;
    private String terminalInput = "";
    private String archivePasswordInput = "";
    private int archiveDirectoryScroll;
    private int archiveDetailScroll;
    private float headerScale = 0.82f;
    private float boardScale = 0.78f;
    private float auditScale = 0.78f;
    private int headerLineHeight;
    private int auditLineHeight;

    public MonitoringTerminalScreen(OpenMonitoringTerminalPayload payload) {
        super(Component.literal("ORSA WEATHER INGEST TERMINAL"));
        this.consolePos = payload.pos();
        applySnapshot(payload);
    }

    public boolean sameConsole(BlockPos pos) {
        return consolePos.equals(pos);
    }

    public void applySnapshot(OpenMonitoringTerminalPayload payload) {
        long previousNonce = this.nonce;
        int previousArchivePage = this.archivePage;
        boolean previousPasswordPrompt = this.archivePasswordPrompt;
        this.nonce = payload.nonce();
        this.board = payload.nonce() == 0L ? null : MonitoringTerminalPuzzle.create(payload.nonce());
        this.triesLeft = payload.triesLeft();
        this.state = payload.state();
        this.removedMask = payload.removedMask();
        this.usedPairMask = payload.usedPairMask();
        this.lockoutTicksRemaining = payload.lockoutTicksRemaining();
        this.auditLines = payload.auditLog().isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(payload.auditLog().split("\n")));
        this.archiveTitle = payload.archiveTitle();
        this.archiveBodyLines = payload.archiveBody().isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(payload.archiveBody().split("\n")));
        this.archivePage = payload.archivePage();
        this.archivePageCount = payload.archivePageCount();
        this.archivePasswordPrompt = payload.archivePasswordPrompt();
        if (payload.archivePage() != previousArchivePage) {
            this.archiveDetailScroll = 0;
        }
        this.closeTicks = state == OpenMonitoringTerminalPayload.STATE_COMPLETE ? 30 : -1;
        if (payload.nonce() != previousNonce || state != OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            this.terminalInput = "";
        }
        if (payload.archivePage() != previousArchivePage || !archivePasswordPrompt || archivePasswordPrompt != previousPasswordPrompt) {
            this.archivePasswordInput = "";
        }
        if (minecraft != null && minecraft.screen == this) {
            recalculateLayout();
            clampArchiveScrolls();
            rebuildHitboxes();
        }
    }

    @Override
    protected void init() {
        super.init();
        int maxUsableW = Math.max(320, width - 12);
        int maxUsableH = Math.max(220, height - 12);
        panelW = Math.min(PANEL_MAX_W, maxUsableW);
        panelH = Math.min(PANEL_MAX_H, maxUsableH);
        if (panelW < PANEL_MIN_W) {
            panelW = maxUsableW;
        }
        if (panelH < PANEL_MIN_H) {
            panelH = maxUsableH;
        }
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        recalculateLayout();
        rebuildHitboxes();
    }

    private void recalculateLayout() {
        charStep = CHAR_STEP;
        segmentGap = SEGMENT_GAP;
        addressWidth = font.width("0xF000");
        segmentWidth = MonitoringTerminalPuzzle.SEGMENT_LENGTH * charStep;

        int contentWidth = panelW - PANEL_PAD * 2;
        int lineWidth = (addressWidth + ADDRESS_GAP + segmentWidth) * 2 + segmentGap;
        while (lineWidth > contentWidth && charStep > 4) {
            charStep--;
            segmentWidth = MonitoringTerminalPuzzle.SEGMENT_LENGTH * charStep;
            lineWidth = (addressWidth + ADDRESS_GAP + segmentWidth) * 2 + segmentGap;
        }
        if (lineWidth > contentWidth) {
            segmentGap = Math.max(8, contentWidth - (addressWidth + ADDRESS_GAP + segmentWidth) * 2);
            lineWidth = (addressWidth + ADDRESS_GAP + segmentWidth) * 2 + segmentGap;
        }
        headerScale = 0.82f;
        boardScale = Math.max(0.64f, Math.min(0.76f, (contentWidth - 6.0f) / (float) lineWidth));
        auditScale = Math.max(0.66f, Math.min(0.78f, boardScale + 0.02f));
        headerLineHeight = scaledLineHeight(headerScale);
        boardLineHeight = scaledLineHeight(boardScale);
        auditLineHeight = scaledLineHeight(auditScale);
        scaledCharStep = Math.max(3, Math.round(charStep * boardScale));
        scaledAddressWidth = Math.round(addressWidth * boardScale);
        scaledAddressGap = Math.max(3, Math.round(ADDRESS_GAP * boardScale));
        scaledSegmentWidth = Math.round(segmentWidth * boardScale);
        scaledSegmentGap = Math.max(6, Math.round(segmentGap * boardScale));
        scaledGroupWidth = scaledAddressWidth + scaledAddressGap + scaledSegmentWidth;
        boardRenderedWidth = scaledGroupWidth * 2 + scaledSegmentGap;

        titleY = panelY + 10;
        attemptsY = titleY + headerLineHeight + 6;
        headerBottomY = attemptsY + headerLineHeight + 8;
        boardCenterX = panelX + panelW / 2;
        int bottomMargin = 14;
        int contentTop = headerBottomY + 12;
        auditX = panelX + PANEL_PAD;
        auditW = contentWidth;
        int maxAuditH = Math.max(84, panelY + panelH - bottomMargin - contentTop - 96);
        auditH = Math.min(maxAuditH, desiredAuditHeight(auditW - BOX_PAD * 2));
        auditY = panelY + panelH - bottomMargin - auditH;

        if (state == OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            int boardContentHeight = MonitoringTerminalPuzzle.ROWS * boardLineHeight;
            int boardAreaTop = contentTop;
            int boardAreaBottom = auditY - 14;
            int boardAreaHeight = Math.max(boardContentHeight, boardAreaBottom - boardAreaTop);
            boardY = boardAreaTop + Math.max(0, (boardAreaHeight - boardContentHeight) / 2);
            boardBottom = boardY + boardContentHeight;
        } else {
            boardY = contentTop;
            boardBottom = auditY - 8;
        }
        clampArchiveScrolls();
    }

    private void rebuildHitboxes() {
        interactiveHitboxes.clear();
        if (state == OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            return;
        }
        if (board == null || state != OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            return;
        }

        for (MonitoringTerminalPuzzle.WordToken token : board.wordTokens()) {
            if (((removedMask >> token.wordIndex()) & 1L) != 0L) {
                continue;
            }
            SegmentLayout layout = segmentLayout(token.segmentIndex());
            interactiveHitboxes.add(new TokenHitbox(
                    layout.textX() + token.start() * scaledCharStep,
                    layout.y(),
                    Math.max(8, token.length() * scaledCharStep),
                    boardLineHeight,
                    token.wordIndex(),
                    true));
        }
        for (MonitoringTerminalPuzzle.PairToken token : board.pairTokens()) {
            if (((usedPairMask >> token.pairIndex()) & 1L) != 0L) {
                continue;
            }
            SegmentLayout layout = segmentLayout(token.segmentIndex());
            interactiveHitboxes.add(new TokenHitbox(
                    layout.textX() + token.start() * scaledCharStep,
                    layout.y(),
                    Math.max(8, token.length() * scaledCharStep),
                    boardLineHeight,
                    token.pairIndex(),
                    false));
        }
    }

    @Override
    public void tick() {
        super.tick();
        blinkTicks++;
        if (bootTicks < BOOT_DURATION) {
            bootTicks++;
            if (bootTicks == 8 && !bootSoundPlayed) {
                bootSoundPlayed = true;
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(ModSounds.TERMINAL_BOOT_ORSA.get(), 1.0f));
            }
        }
        if (state == OpenMonitoringTerminalPayload.STATE_LOCKED_OUT && lockoutTicksRemaining > 0) {
            lockoutTicksRemaining--;
        }
        if (closeTicks > 0) {
            closeTicks--;
            if (closeTicks == 0 && minecraft != null) {
                minecraft.setScreen(null);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderFrame(graphics);
        drawCenteredScaledString(graphics, title, panelX + panelW / 2, titleY, headerScale, 0xFFF5F2E8);

        if (bootTicks < BOOT_DURATION) {
            renderBootSequence(graphics);
            return;
        }

        if (state == OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            renderAttempts(graphics);
            renderBoard(graphics);
            renderAudit(graphics);
            return;
        }

        renderStatePanel(graphics, mouseX, mouseY);
        renderAudit(graphics);
    }

    private void renderFrame(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xD0141618, 0xE0090B0C);
        graphics.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0x406BA590);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF111618);
        graphics.fill(panelX + 2, panelY + 2, panelX + panelW - 2, headerBottomY - 2, 0xFF1B2426);
        graphics.fill(panelX + 2, headerBottomY - 2, panelX + panelW - 2, headerBottomY - 1, 0xFFB7D4C4);
        graphics.fill(panelX + 2, panelY + 2, panelX + 3, panelY + panelH - 2, 0xFF3E5E53);
        graphics.fill(panelX + panelW - 3, panelY + 2, panelX + panelW - 2, panelY + panelH - 2, 0xFF0B1011);
    }

    private static final String[] BOOT_LINES = {
            "[  OK  ] POST COMPLETE",
            "[  OK  ] MEMORY 64K ... VERIFIED",
            "[  OK  ] ORSA KERNEL v4.2.14-FRZN",
            "[  OK  ] LOADING WEATHER DRIVERS",
            "[  OK  ] INGEST BUFFER ALLOCATED",
            "[  OK  ] METEOROLOGICAL ARRAY ONLINE",
            "[  OK  ] DATA LINK ESTABLISHED",
            "[READY ] TERMINAL AWAITING INPUT",
    };

    private void renderBootSequence(GuiGraphics graphics) {
        int centerX = panelX + panelW / 2;
        int contentTop = headerBottomY + 8;
        int contentBottom = panelY + panelH - 14;

        // 3D-spinning ORSA logo (Y-axis rotation faked via X-scale)
        int logoSize = 32;
        int logoCenterX = centerX;
        int logoCenterY = contentTop + 26;
        float angle = bootTicks * (720.0f / BOOT_DURATION);
        float xSquash = (float) Math.cos(Math.toRadians(angle));
        float logoScale = logoSize / 16.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(logoCenterX, logoCenterY, 0);
        graphics.pose().scale(xSquash * logoScale, logoScale, 1.0f);
        graphics.blit(ORSA_LOGO, -8, -8, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();

        // Kernel boot lines
        int textX = panelX + PANEL_PAD + 4;
        int textY = logoCenterY + logoSize / 2 + 10;
        int lineStep = headerLineHeight + 2;
        for (int i = 0; i < BOOT_LINES.length; i++) {
            int showAt = 4 + i * 6;
            if (bootTicks >= showAt) {
                int color = BOOT_LINES[i].startsWith("[READY") ? 0xFFAEE8B5 : 0xFF7D978D;
                String prefix = BOOT_LINES[i].substring(0, 8);
                String body = BOOT_LINES[i].substring(8);
                drawScaledString(graphics, prefix, textX, textY + i * lineStep, headerScale, 0xFF6BA590);
                drawScaledString(graphics, body, textX + (int) (font.width(prefix) * headerScale), textY + i * lineStep, headerScale, color);
            }
        }

        // Progress bar
        int barX = panelX + 40;
        int barW = panelW - 80;
        int barY = contentBottom - 6;
        float barProgress = Math.max(0.0f, Math.min(1.0f, (bootTicks - 4.0f) / (BOOT_DURATION - 8.0f)));
        graphics.fill(barX, barY, barX + barW, barY + 3, 0xFF1A2A22);
        graphics.fill(barX, barY, barX + (int) (barW * barProgress), barY + 3, 0xFF6BA590);
    }

    private void renderAttempts(GuiGraphics graphics) {
        drawCenteredScaledString(graphics,
                Component.literal("ALLOWANCE: " + "*".repeat(Math.max(triesLeft, 0))),
                panelX + panelW / 2,
                attemptsY,
                headerScale,
                triesLeft > 1 ? 0xFFCCE9DA : 0xFFFFA7A7);
    }

    private void renderBoard(GuiGraphics graphics) {
        for (int segmentIndex = 0; segmentIndex < MonitoringTerminalPuzzle.SEGMENTS; segmentIndex++) {
            SegmentLayout layout = segmentLayout(segmentIndex);
            drawScaledString(graphics, layout.address(), layout.addressX(), layout.y(), boardScale, 0xFF7D978D);
            String rendered = board.renderSegment(segmentIndex, removedMask, usedPairMask);
            for (int i = 0; i < rendered.length(); i++) {
                drawScaledString(graphics, String.valueOf(rendered.charAt(i)),
                        layout.textX() + i * scaledCharStep, layout.y(), boardScale, 0xFFB8D0C3);
            }
        }
    }

    private void renderAudit(GuiGraphics graphics) {
        String title = state == OpenMonitoringTerminalPayload.STATE_ARCHIVE ? "URGENT ORSA DIRECTIVE" : "TERMINAL LOG";
        int accent = state == OpenMonitoringTerminalPayload.STATE_ARCHIVE ? 0xFFF2C38A : 0xFFE6EFE9;
        renderInfoBox(graphics, auditX, auditY, auditW, auditH, title, accent, 0xFF101618);

        int y = auditY + 24;
        int inputY = auditY + auditH - BOX_PAD - auditLineHeight;
        int maxY = inputY - 8;
        for (String line : auditLines) {
            int color = 0xFFD4E5DC;
            if (state == OpenMonitoringTerminalPayload.STATE_ARCHIVE
                    && (line.startsWith("TRANSFER DIRECTIVE")
                    || line.startsWith("DESIGNATED TRANSFER SITE")
                    || line.startsWith("COORDS"))) {
                color = 0xFFF5E0B1;
            }
            y = drawWrappedScaledLine(graphics, Component.literal(line), auditX + BOX_PAD, y,
                    auditW - BOX_PAD * 2, maxY, color, auditScale);
            y += 1;
        }

        if (state == OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            String cursor = (blinkTicks / 8) % 2 == 0 ? "_" : "";
            drawScaledString(graphics, Component.literal("> " + terminalInput + cursor),
                    auditX + BOX_PAD, inputY, auditScale, 0xFFF5F7EE);
        }
    }

    private void renderStatePanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int boxX = panelX + PANEL_PAD;
        int boxY = panelY + 66;
        int boxW = panelW - PANEL_PAD * 2;
        int boxH = Math.max(110, auditY - boxY - 12);

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF12191B);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFFB7D4C4);
        graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF0A1011);

        if (state == OpenMonitoringTerminalPayload.STATE_LOCKED_OUT) {
            graphics.drawCenteredString(font, Component.literal("TERMINAL LOCKED"), boxX + boxW / 2, boxY + 18, 0xFFFF9E9E);
            graphics.drawCenteredString(font, Component.literal("Automatic reset pending."), boxX + boxW / 2, boxY + 36, 0xFFD4E5DC);
            String reset = "RESET IN " + Math.max(1, (lockoutTicksRemaining + 19) / 20) + "s";
            graphics.drawCenteredString(font, Component.literal(reset), boxX + boxW / 2, boxY + 56, 0xFFCBE7D8);
            return;
        }

        if (state == OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            ArchiveLayout layout = archiveLayout();
            renderInfoBox(graphics, layout.directoryX(), layout.directoryY(), layout.directoryW(), layout.directoryH(),
                    "DIRECTORY", 0xFFAEE8B5, 0xFF12191B);
            renderInfoBox(graphics, layout.detailX(), layout.detailY(), layout.detailW(), layout.detailH(),
                    archiveTitle.isBlank() ? "ARCHIVE DATA" : archiveTitle, 0xFFAEE8B5, 0xFF12191B);

            int pageTotal = archivePageCount > 0 ? archivePageCount : MonitoringStationArchive.PAGE_COUNT;
            int rowY = layout.directoryContentY() - archiveDirectoryScroll;
            int rowHeight = archiveRowHeight();
            graphics.enableScissor(layout.directoryContentX(), layout.directoryContentY(),
                    layout.directoryContentX() + layout.directoryContentW(),
                    layout.directoryContentY() + layout.directoryContentH());
            for (int i = 0; i < pageTotal; i++) {
                int rowBottom = rowY + rowHeight;
                if (rowBottom >= layout.directoryContentY() - 2
                        && rowY <= layout.directoryContentY() + layout.directoryContentH()) {
                    String label = i < MonitoringStationArchive.PAGE_TITLES.length
                            ? MonitoringStationArchive.PAGE_TITLES[i]
                            : "ARCHIVE PAGE " + (i + 1);
                    boolean selected = i == archivePage;
                    boolean hovered = hoveredArchiveEntry(mouseX, mouseY, i);
                    int rowX = layout.directoryContentX() - 2;
                    int rowW = layout.directoryContentW() + 4;
                    int fill = selected ? 0xFF223630 : (hovered ? 0xFF182324 : 0x00000000);
                    if (fill != 0) {
                        graphics.fill(rowX, rowY - 1, rowX + rowW, rowBottom - 1, fill);
                        if (selected) {
                            graphics.fill(rowX, rowY - 1, rowX + 3, rowBottom - 1, 0xFFAEE8B5);
                        }
                    }
                    String prefix = selected ? "> " : "  ";
                    drawScaledString(graphics, Component.literal(prefix + label),
                            layout.directoryContentX(), rowY + 2, auditScale,
                            selected ? 0xFFF5F7EE : 0xFFD4E5DC);
                }
                rowY += rowHeight + 2;
            }
            graphics.disableScissor();
            renderArchiveScrollbar(graphics, layout.directoryScrollbarX(), layout.directoryContentY(),
                    layout.directoryContentH(), archiveDirectoryScroll, directoryScrollMax(layout),
                    directoryVisibleHeight(layout), 0xFF2C463C, 0xFFAEE8B5);

            List<FormattedCharSequence> wrappedBody = archiveWrappedBody(layout);
            int contentY = layout.detailContentY() - archiveDetailScroll;
            int detailMaxY = archivePasswordPrompt
                    ? layout.detailContentY() + layout.detailContentH() - auditLineHeight - 10
                    : layout.detailContentY() + layout.detailContentH();
            graphics.enableScissor(layout.detailContentX(), layout.detailContentY(),
                    layout.detailContentX() + layout.detailContentW(),
                    layout.detailContentY() + layout.detailContentH());
            for (FormattedCharSequence line : wrappedBody) {
                if (contentY + auditLineHeight >= layout.detailContentY() - 2
                        && contentY <= detailMaxY) {
                    drawScaledString(graphics, line, layout.detailContentX(), contentY, auditScale, 0xFFD4E5DC);
                }
                contentY += auditLineHeight;
            }
            graphics.disableScissor();
            renderArchiveScrollbar(graphics, layout.detailScrollbarX(), layout.detailContentY(),
                    layout.detailContentH(), archiveDetailScroll, detailScrollMax(layout),
                    detailVisibleHeight(layout), 0xFF2C463C, 0xFFAEE8B5);
            if (archivePasswordPrompt) {
                int promptY = layout.detailY() + layout.detailH() - BOX_PAD - auditLineHeight - 2;
                String cursor = (blinkTicks / 8) % 2 == 0 ? "_" : "";
                drawScaledString(graphics, Component.literal("PASSWORD: " + archivePasswordInput + cursor),
                        layout.detailContentX(), promptY, auditScale, 0xFFF5F7EE);
            }
            return;
        }

        graphics.drawCenteredString(font, Component.literal("ARCHIVE UNSEALED"), boxX + boxW / 2, boxY + 30, 0xFFAEE8B5);
        graphics.drawCenteredString(font, Component.literal("Back room access restored."), boxX + boxW / 2, boxY + 50, 0xFFD4E5DC);
    }

    private void renderInfoBox(GuiGraphics graphics, int x, int y, int w, int h, String title, int accent, int fill) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, accent);
        graphics.fill(x, y, x + 1, y + h, accent);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF0A1011);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF0A1011);
        graphics.drawString(font, Component.literal(title), x + BOX_PAD, y + 7, accent, false);
    }

    private int desiredAuditHeight(int contentWidth) {
        int scaledWidth = Math.max(24, (int) Math.floor(contentWidth / auditScale));
        int wrappedLines = 0;
        for (String line : auditLines) {
            wrappedLines += Math.max(1, font.split(Component.literal(line), scaledWidth).size());
        }
        if (state == OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            int contentHeight = 24 + BOX_PAD + wrappedLines * auditLineHeight + BOX_PAD + 4;
            return Math.max(82, Math.min(96, contentHeight));
        }
        int visibleLines = Math.max(state == OpenMonitoringTerminalPayload.STATE_ACTIVE ? 4 : 3,
                Math.min(8, wrappedLines + (state == OpenMonitoringTerminalPayload.STATE_ACTIVE ? 1 : 0)));
        int contentHeight = 24 + BOX_PAD + visibleLines * auditLineHeight + BOX_PAD + 6;
        return Math.max(110, contentHeight);
    }

    private int drawWrappedScaledLine(GuiGraphics graphics, Component line, int x, int y, int width, int maxY, int color, float scale) {
        int scaledWidth = Math.max(24, (int) Math.floor(width / scale));
        for (FormattedCharSequence wrapped : font.split(line, scaledWidth)) {
            if (y > maxY) {
                return y;
            }
            drawScaledString(graphics, wrapped, x, y, scale, color);
            y += scaledLineHeight(scale);
        }
        return y;
    }

    private void drawCenteredScaledString(GuiGraphics graphics, Component text, int centerX, int y, float scale, int color) {
        int width = Math.round(font.width(text) * scale);
        drawScaledString(graphics, text, centerX - width / 2, y, scale, color);
    }

    private void drawScaledString(GuiGraphics graphics, Component text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledString(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledString(GuiGraphics graphics, FormattedCharSequence text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private int scaledLineHeight(float scale) {
        return Math.max(6, Math.round(LINE_HEIGHT * scale));
    }

    private SegmentLayout segmentLayout(int segmentIndex) {
        int row = segmentIndex / 2;
        boolean right = (segmentIndex % 2) == 1;
        int addressBase = right ? 0xF1B0 : 0xF000;
        int textOffset = scaledAddressWidth + scaledAddressGap;
        int x = right
                ? panelX + panelW / 2 + scaledSegmentGap / 2
                : panelX + panelW / 2 - scaledSegmentGap / 2 - scaledGroupWidth;
        int y = boardY + row * boardLineHeight;
        String address = String.format("0x%04X", addressBase + row * 0x10);
        return new SegmentLayout(x, y, address, x + textOffset);
    }

    private TokenHitbox hoveredToken(double mouseX, double mouseY) {
        for (TokenHitbox hitbox : interactiveHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                return hitbox;
            }
        }
        return null;
    }

    private boolean hoveredArchiveEntry(double mouseX, double mouseY, int pageIndex) {
        return archiveEntryAt(mouseX, mouseY) == pageIndex;
    }

    private int archiveEntryAt(double mouseX, double mouseY) {
        if (state != OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            return -1;
        }
        ArchiveLayout layout = archiveLayout();
        int minX = layout.directoryContentX() - 2;
        int maxX = layout.directoryContentX() + layout.directoryContentW() + 2;
        int minY = layout.directoryContentY();
        int maxY = layout.directoryContentY() + layout.directoryContentH();
        if (mouseX < minX || mouseX >= maxX || mouseY < minY || mouseY >= maxY) {
            return -1;
        }

        int pageTotal = archivePageCount > 0 ? archivePageCount : MonitoringStationArchive.PAGE_COUNT;
        int rowHeight = archiveRowHeight();
        int rowStride = rowHeight + 2;
        double localY = mouseY - layout.directoryContentY() + archiveDirectoryScroll;
        int rowIndex = (int) Math.floor(localY / rowStride);
        if (rowIndex < 0 || rowIndex >= pageTotal) {
            return -1;
        }
        double rowOffset = localY - rowIndex * rowStride;
        return rowOffset <= rowHeight ? rowIndex : -1;
    }

    private boolean isInsideDirectoryPane(ArchiveLayout layout, double mouseX, double mouseY) {
        return mouseX >= layout.directoryX() && mouseX < layout.directoryX() + layout.directoryW()
                && mouseY >= layout.directoryY() && mouseY < layout.directoryY() + layout.directoryH();
    }

    private boolean isInsideDetailPane(ArchiveLayout layout, double mouseX, double mouseY) {
        return mouseX >= layout.detailX() && mouseX < layout.detailX() + layout.detailW()
                && mouseY >= layout.detailY() && mouseY < layout.detailY() + layout.detailH();
    }

    private ArchiveLayout archiveLayout() {
        int boxX = panelX + PANEL_PAD;
        int boxY = headerBottomY + ARCHIVE_TOP_GAP;
        int boxW = panelW - PANEL_PAD * 2;
        int boxH = Math.max(110, auditY - boxY - 12);
        int directoryW = Math.min(ARCHIVE_DIRECTORY_W, Math.max(150, boxW / 3));
        int detailX = boxX + directoryW - 1;
        int detailW = Math.max(180, boxX + boxW - detailX);
        int contentTop = boxY + 24;
        int contentBottom = boxY + boxH - BOX_PAD;
        int contentHeight = Math.max(24, contentBottom - contentTop);
        int directoryScrollbarX = boxX + directoryW - BOX_PAD - ARCHIVE_SCROLLBAR_W;
        int directoryContentX = boxX + BOX_PAD;
        int directoryContentW = Math.max(60, directoryScrollbarX - ARCHIVE_SCROLLBAR_GAP - directoryContentX);
        int detailScrollbarX = detailX + detailW - BOX_PAD - ARCHIVE_SCROLLBAR_W;
        int detailContentX = detailX + BOX_PAD;
        int detailContentW = Math.max(100, detailScrollbarX - ARCHIVE_SCROLLBAR_GAP - detailContentX);
        return new ArchiveLayout(boxX, boxY, directoryW, boxH, detailX, boxY, detailW, boxH,
                directoryContentX, contentTop, directoryContentW, contentHeight, directoryScrollbarX,
                detailContentX, contentTop, detailContentW, contentHeight, detailScrollbarX);
    }

    private void clampArchiveScrolls() {
        if (state != OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            archiveDirectoryScroll = 0;
            archiveDetailScroll = 0;
            return;
        }
        ArchiveLayout layout = archiveLayout();
        archiveDirectoryScroll = clamp(archiveDirectoryScroll, 0, directoryScrollMax(layout));
        ensureSelectedArchiveEntryVisible(layout);
        archiveDirectoryScroll = clamp(archiveDirectoryScroll, 0, directoryScrollMax(layout));
        archiveDetailScroll = clamp(archiveDetailScroll, 0, detailScrollMax(layout));
    }

    private void ensureSelectedArchiveEntryVisible(ArchiveLayout layout) {
        int rowStride = archiveRowHeight() + 2;
        int rowTop = archivePage * rowStride;
        int rowBottom = rowTop + archiveRowHeight();
        int visibleTop = archiveDirectoryScroll;
        int visibleBottom = archiveDirectoryScroll + directoryVisibleHeight(layout);
        if (rowTop < visibleTop) {
            archiveDirectoryScroll = rowTop;
        } else if (rowBottom > visibleBottom) {
            archiveDirectoryScroll = rowBottom - directoryVisibleHeight(layout);
        }
    }

    private int directoryVisibleHeight(ArchiveLayout layout) {
        return layout.directoryContentH();
    }

    private int detailVisibleHeight(ArchiveLayout layout) {
        return layout.detailContentH();
    }

    private int directoryScrollMax(ArchiveLayout layout) {
        int pageTotal = archivePageCount > 0 ? archivePageCount : MonitoringStationArchive.PAGE_COUNT;
        int contentHeight = Math.max(0, pageTotal * (archiveRowHeight() + 2) - 2);
        return Math.max(0, contentHeight - directoryVisibleHeight(layout));
    }

    private int detailScrollMax(ArchiveLayout layout) {
        int reservedPromptHeight = archivePasswordPrompt ? auditLineHeight + 10 : 0;
        int contentHeight = archiveWrappedBody(layout).size() * auditLineHeight;
        return Math.max(0, contentHeight - (detailVisibleHeight(layout) - reservedPromptHeight));
    }

    private List<FormattedCharSequence> archiveWrappedBody(ArchiveLayout layout) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String line : archiveBodyLines) {
            wrapped.addAll(font.split(Component.literal(line), Math.max(24, (int) Math.floor(layout.detailContentW() / auditScale))));
        }
        return wrapped;
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int archiveRowHeight() {
        return Math.max(12, auditLineHeight + 2);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state == OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            int entryIndex = archiveEntryAt(mouseX, mouseY);
            if (entryIndex >= 0) {
                sendArchiveOpenAction(entryIndex);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (state != OpenMonitoringTerminalPayload.STATE_ACTIVE || board == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        TokenHitbox hitbox = hoveredToken(mouseX, mouseY);
        if (hitbox == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        PacketDistributor.sendToServer(new SubmitMonitoringTerminalPayload(
                consolePos,
                nonce,
                hitbox.word() ? SubmitMonitoringTerminalPayload.ACTION_TYPED_GUESS : SubmitMonitoringTerminalPayload.ACTION_USE_PAIR,
                hitbox.index(),
                ""
        ));
        terminalInput = "";
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (state == OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            ArchiveLayout layout = archiveLayout();
            int delta = (int) Math.round(-scrollY * Math.max(10, auditLineHeight * 2));
            if (delta == 0) {
                delta = scrollY > 0 ? -Math.max(10, auditLineHeight * 2) : Math.max(10, auditLineHeight * 2);
            }
            boolean handled = false;
            if (isInsideDetailPane(layout, mouseX, mouseY) && detailScrollMax(layout) > 0) {
                archiveDetailScroll = clamp(archiveDetailScroll + delta, 0, detailScrollMax(layout));
                handled = true;
            } else if (isInsideDirectoryPane(layout, mouseX, mouseY) && directoryScrollMax(layout) > 0) {
                archiveDirectoryScroll = clamp(archiveDirectoryScroll + delta, 0, directoryScrollMax(layout));
                handled = true;
            }
            if (handled) {
                rebuildHitboxes();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (state == OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            if (Character.isLetter(codePoint) && board != null && terminalInput.length() < board.wordLength()) {
                terminalInput += Character.toUpperCase(codePoint);
            }
            return true;
        }
        if (state == OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            if (archivePasswordPrompt) {
                if (Character.isLetterOrDigit(codePoint) && archivePasswordInput.length() < 24) {
                    archivePasswordInput += Character.toUpperCase(codePoint);
                    return true;
                }
                return true;
            }
            if (codePoint == '[') {
                sendArchiveAction(SubmitMonitoringTerminalPayload.ACTION_ARCHIVE_PREVIOUS);
                return true;
            }
            if (codePoint == ']') {
                sendArchiveAction(SubmitMonitoringTerminalPayload.ACTION_ARCHIVE_NEXT);
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state == OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            if (keyCode == 259) {
                if (!terminalInput.isEmpty()) {
                    terminalInput = terminalInput.substring(0, terminalInput.length() - 1);
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (!terminalInput.isBlank()) {
                    PacketDistributor.sendToServer(new SubmitMonitoringTerminalPayload(
                            consolePos,
                            nonce,
                            SubmitMonitoringTerminalPayload.ACTION_TYPED_GUESS,
                            -1,
                            terminalInput
                    ));
                    terminalInput = "";
                }
                return true;
            }
        }
        if (state == OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            if (archivePasswordPrompt) {
                if (keyCode == 259) {
                    if (!archivePasswordInput.isEmpty()) {
                        archivePasswordInput = archivePasswordInput.substring(0, archivePasswordInput.length() - 1);
                    }
                    return true;
                }
                if (keyCode == 257 || keyCode == 335) {
                    sendArchiveAuthAction();
                    return true;
                }
                return true;
            }
            if (keyCode == 263 || keyCode == 65) {
                sendArchiveAction(SubmitMonitoringTerminalPayload.ACTION_ARCHIVE_PREVIOUS);
                return true;
            }
            if (keyCode == 262 || keyCode == 68) {
                sendArchiveAction(SubmitMonitoringTerminalPayload.ACTION_ARCHIVE_NEXT);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void openOrUpdate(Minecraft minecraft, OpenMonitoringTerminalPayload payload) {
        if (minecraft.screen instanceof MonitoringTerminalScreen screen && screen.sameConsole(payload.pos())) {
            screen.applySnapshot(payload);
            return;
        }
        minecraft.setScreen(new MonitoringTerminalScreen(payload));
    }

    private void sendArchiveAction(int actionType) {
        PacketDistributor.sendToServer(new SubmitMonitoringTerminalPayload(
                consolePos,
                nonce,
                actionType,
                0,
                ""
        ));
    }

    private void sendArchiveOpenAction(int pageIndex) {
        PacketDistributor.sendToServer(new SubmitMonitoringTerminalPayload(
                consolePos,
                nonce,
                SubmitMonitoringTerminalPayload.ACTION_ARCHIVE_OPEN_PAGE,
                pageIndex,
                ""
        ));
    }

    private void sendArchiveAuthAction() {
        PacketDistributor.sendToServer(new SubmitMonitoringTerminalPayload(
                consolePos,
                nonce,
                SubmitMonitoringTerminalPayload.ACTION_ARCHIVE_AUTH,
                archivePage,
                archivePasswordInput
        ));
        archivePasswordInput = "";
    }

    private record SegmentLayout(int addressX, int y, String address, int textX) {
    }

    private record ArchiveLayout(int directoryX, int directoryY, int directoryW, int directoryH,
                                 int detailX, int detailY, int detailW, int detailH,
                                 int directoryContentX, int directoryContentY, int directoryContentW, int directoryContentH,
                                 int directoryScrollbarX,
                                 int detailContentX, int detailContentY, int detailContentW, int detailContentH,
                                 int detailScrollbarX) {
    }

    private record TokenHitbox(int x, int y, int width, int height, int index, boolean word) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
