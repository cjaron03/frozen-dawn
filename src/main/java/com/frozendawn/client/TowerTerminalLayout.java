package com.frozendawn.client;

import com.frozendawn.network.OpenTowerTerminalPayload;
import com.frozendawn.terminal.TowerArchive;
import com.frozendawn.terminal.TowerTerminalPuzzle;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

final class TowerTerminalLayout {

    private static final int CHAR_STEP = 5;
    private static final int LINE_HEIGHT = 9;
    private static final int ADDRESS_GAP = 6;
    private static final int SEGMENT_GAP = 18;
    private static final int PANEL_MIN_W = 560;
    private static final int PANEL_MAX_W = 920;
    private static final int PANEL_MIN_H = 320;
    private static final int PANEL_MAX_H = 560;
    private static final int PANEL_PAD = 18;
    private static final int BOX_PAD = 10;
    private static final int ARCHIVE_TOP_GAP = 8;
    private static final int ARCHIVE_DIRECTORY_W = 176;
    private static final int ARCHIVE_SCROLLBAR_W = 6;
    private static final int ARCHIVE_SCROLLBAR_GAP = 6;
    private static final int ARCHIVE_AUDIO_H = 64;
    private static final int ARCHIVE_AUDIO_BUTTON = 18;
    private static final int ARCHIVE_SEGMENT_TAB_W = 22;
    private static final int ARCHIVE_SEGMENT_TAB_H = 13;
    private static final int ARCHIVE_SEGMENT_TAB_GAP = 4;

    final int panelX;
    final int panelY;
    final int panelW;
    final int panelH;
    final int titleY;
    final int attemptsY;
    final int headerBottomY;
    final int boardY;
    final int boardLineHeight;
    final int boardCenterX;
    final int scaledCharStep;
    final int scaledAddressWidth;
    final int scaledAddressGap;
    final int scaledSegmentGap;
    final int scaledGroupWidth;
    final int auditX;
    final int auditY;
    final int auditW;
    final int auditH;
    final float headerScale;
    final float boardScale;
    final float auditScale;
    final int headerLineHeight;
    final int auditLineHeight;
    final ArchiveLayout archiveLayout;
    final List<FormattedCharSequence> archiveWrappedBody;

    private final List<TokenHitbox> interactiveHitboxes;
    private final int directoryScrollMax;
    private final int detailScrollMax;

    private TowerTerminalLayout(int panelX, int panelY, int panelW, int panelH,
                                int titleY, int attemptsY, int headerBottomY,
                                int boardY, int boardLineHeight, int boardCenterX,
                                int scaledCharStep, int scaledAddressWidth, int scaledAddressGap,
                                int scaledSegmentGap, int scaledGroupWidth,
                                int auditX, int auditY, int auditW, int auditH,
                                float headerScale, float boardScale, float auditScale,
                                int headerLineHeight, int auditLineHeight,
                                ArchiveLayout archiveLayout, List<FormattedCharSequence> archiveWrappedBody,
                                List<TokenHitbox> interactiveHitboxes,
                                int directoryScrollMax, int detailScrollMax) {
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;
        this.panelH = panelH;
        this.titleY = titleY;
        this.attemptsY = attemptsY;
        this.headerBottomY = headerBottomY;
        this.boardY = boardY;
        this.boardLineHeight = boardLineHeight;
        this.boardCenterX = boardCenterX;
        this.scaledCharStep = scaledCharStep;
        this.scaledAddressWidth = scaledAddressWidth;
        this.scaledAddressGap = scaledAddressGap;
        this.scaledSegmentGap = scaledSegmentGap;
        this.scaledGroupWidth = scaledGroupWidth;
        this.auditX = auditX;
        this.auditY = auditY;
        this.auditW = auditW;
        this.auditH = auditH;
        this.headerScale = headerScale;
        this.boardScale = boardScale;
        this.auditScale = auditScale;
        this.headerLineHeight = headerLineHeight;
        this.auditLineHeight = auditLineHeight;
        this.archiveLayout = archiveLayout;
        this.archiveWrappedBody = archiveWrappedBody;
        this.interactiveHitboxes = interactiveHitboxes;
        this.directoryScrollMax = directoryScrollMax;
        this.detailScrollMax = detailScrollMax;
    }

    static TowerTerminalLayout create(int width, int height, Font font, TowerTerminalViewModel viewModel) {
        int maxUsableW = Math.max(320, width - 12);
        int maxUsableH = Math.max(220, height - 12);
        int panelW = Math.min(PANEL_MAX_W, maxUsableW);
        int panelH = Math.min(PANEL_MAX_H, maxUsableH);
        if (panelW < PANEL_MIN_W) {
            panelW = maxUsableW;
        }
        if (panelH < PANEL_MIN_H) {
            panelH = maxUsableH;
        }
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        int charStep = CHAR_STEP;
        int segmentGap = SEGMENT_GAP;
        int addressWidth = font.width("0xF000");
        int segmentWidth = TowerTerminalPuzzle.SEGMENT_LENGTH * charStep;

        int contentWidth = panelW - PANEL_PAD * 2;
        int lineWidth = (addressWidth + ADDRESS_GAP + segmentWidth) * 2 + segmentGap;
        while (lineWidth > contentWidth && charStep > 4) {
            charStep--;
            segmentWidth = TowerTerminalPuzzle.SEGMENT_LENGTH * charStep;
            lineWidth = (addressWidth + ADDRESS_GAP + segmentWidth) * 2 + segmentGap;
        }
        if (lineWidth > contentWidth) {
            segmentGap = Math.max(8, contentWidth - (addressWidth + ADDRESS_GAP + segmentWidth) * 2);
        }

        float headerScale = 0.82f;
        float boardScale = Math.max(0.64f, Math.min(0.76f, (contentWidth - 6.0f) / (float) lineWidth));
        float auditScale = Math.max(0.66f, Math.min(0.78f, boardScale + 0.02f));
        int headerLineHeight = scaledLineHeight(headerScale);
        int boardLineHeight = scaledLineHeight(boardScale);
        int auditLineHeight = scaledLineHeight(auditScale);
        int scaledCharStep = Math.max(3, Math.round(charStep * boardScale));
        int scaledAddressWidth = Math.round(addressWidth * boardScale);
        int scaledAddressGap = Math.max(3, Math.round(ADDRESS_GAP * boardScale));
        int scaledSegmentGap = Math.max(6, Math.round(segmentGap * boardScale));
        int scaledSegmentWidth = Math.round(segmentWidth * boardScale);
        int scaledGroupWidth = scaledAddressWidth + scaledAddressGap + scaledSegmentWidth;

        int titleY = panelY + 10;
        int attemptsY = titleY + headerLineHeight + 6;
        int headerBottomY = attemptsY + headerLineHeight + 8;
        int boardCenterX = panelX + panelW / 2;
        int contentTop = headerBottomY + 12;
        int auditX = panelX + PANEL_PAD;
        int auditW = contentWidth;
        int bottomMargin = 14;
        int maxAuditH = Math.max(84, panelY + panelH - bottomMargin - contentTop - 96);
        boolean archiveMode = viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE;
        int auditH = archiveMode
                ? 0
                : Math.min(maxAuditH,
                desiredAuditHeight(font, auditScale, auditLineHeight, auditW - BOX_PAD * 2, viewModel));
        int auditY = archiveMode ? panelY + panelH - bottomMargin : panelY + panelH - bottomMargin - auditH;

        int boardY;
        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ACTIVE) {
            int boardContentHeight = TowerTerminalPuzzle.ROWS * boardLineHeight;
            int boardAreaTop = contentTop;
            int boardAreaBottom = auditY - 14;
            int boardAreaHeight = Math.max(boardContentHeight, boardAreaBottom - boardAreaTop);
            boardY = boardAreaTop + Math.max(0, (boardAreaHeight - boardContentHeight) / 2);
        } else {
            boardY = contentTop;
        }

        ArchiveLayout archiveLayout = createArchiveLayout(panelX, panelW, headerBottomY, auditY, viewModel);
        List<FormattedCharSequence> archiveWrappedBody = wrapArchiveBody(font, auditScale, archiveLayout.detailContentW(), viewModel);
        int directoryScrollMax = directoryScrollMax(archiveLayout, auditLineHeight, viewModel.archivePageCount());
        int detailScrollMax = detailScrollMax(archiveWrappedBody, archiveLayout, auditLineHeight, viewModel.archivePasswordPrompt());
        List<TokenHitbox> interactiveHitboxes = buildHitboxes(viewModel, boardCenterX, boardY, boardLineHeight,
                scaledCharStep, scaledAddressWidth, scaledAddressGap, scaledSegmentGap, scaledGroupWidth);

        return new TowerTerminalLayout(panelX, panelY, panelW, panelH, titleY, attemptsY, headerBottomY,
                boardY, boardLineHeight, boardCenterX, scaledCharStep, scaledAddressWidth, scaledAddressGap,
                scaledSegmentGap, scaledGroupWidth, auditX, auditY, auditW, auditH, headerScale, boardScale,
                auditScale, headerLineHeight, auditLineHeight, archiveLayout, archiveWrappedBody,
                interactiveHitboxes, directoryScrollMax, detailScrollMax);
    }

    SegmentLayout segmentLayout(int segmentIndex) {
        int row = segmentIndex / 2;
        boolean right = (segmentIndex % 2) == 1;
        int addressBase = right ? 0xF1B0 : 0xF000;
        int x = right
                ? boardCenterX + scaledSegmentGap / 2
                : boardCenterX - scaledSegmentGap / 2 - scaledGroupWidth;
        int y = boardY + row * boardLineHeight;
        String address = String.format("0x%04X", addressBase + row * 0x10);
        return new SegmentLayout(x, y, address, x + scaledAddressWidth + scaledAddressGap);
    }

    TokenHitbox hoveredToken(double mouseX, double mouseY) {
        for (TokenHitbox hitbox : interactiveHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                return hitbox;
            }
        }
        return null;
    }

    int archiveEntryAt(double mouseX, double mouseY, int archiveDirectoryScroll, int archivePageCount) {
        int minX = archiveLayout.directoryContentX() - 2;
        int maxX = archiveLayout.directoryContentX() + archiveLayout.directoryContentW() + 2;
        int minY = archiveLayout.directoryContentY();
        int maxY = archiveLayout.directoryContentY() + archiveLayout.directoryContentH();
        if (mouseX < minX || mouseX >= maxX || mouseY < minY || mouseY >= maxY) {
            return -1;
        }

        int rowHeight = archiveRowHeight();
        int rowStride = rowHeight + 2;
        double localY = mouseY - archiveLayout.directoryContentY() + archiveDirectoryScroll;
        int rowIndex = (int) Math.floor(localY / rowStride);
        if (rowIndex < 0 || rowIndex >= directoryPageTotal(archivePageCount)) {
            return -1;
        }
        double rowOffset = localY - rowIndex * rowStride;
        return rowOffset <= rowHeight ? rowIndex : -1;
    }

    boolean isInsideDirectoryPane(double mouseX, double mouseY) {
        return mouseX >= archiveLayout.directoryX() && mouseX < archiveLayout.directoryX() + archiveLayout.directoryW()
                && mouseY >= archiveLayout.directoryY() && mouseY < archiveLayout.directoryY() + archiveLayout.directoryH();
    }

    boolean isInsideDetailPane(double mouseX, double mouseY) {
        return mouseX >= archiveLayout.detailX() && mouseX < archiveLayout.detailX() + archiveLayout.detailW()
                && mouseY >= archiveLayout.detailY() && mouseY < archiveLayout.detailY() + archiveLayout.detailH();
    }

    int pageTotal(int archivePageCount) {
        return archivePageCount > 0 ? archivePageCount : TowerArchive.PAGE_COUNT;
    }

    int directoryPageTotal(int archivePageCount) {
        int total = archivePageCount > 0 ? archivePageCount : TowerArchive.DIRECTORY_PAGE_COUNT;
        return Math.min(TowerArchive.DIRECTORY_PAGE_COUNT, total);
    }

    int clampDirectoryScroll(int scroll, int archivePageCount) {
        return clamp(scroll, 0, directoryScrollMax(archivePageCount));
    }

    int ensureSelectedArchiveEntryVisible(int archivePage, int currentScroll) {
        int directoryPage = archivePage >= TowerArchive.COMMAND_PAGE ? TowerArchive.COMMAND_PAGE : archivePage;
        int rowStride = archiveRowHeight() + 2;
        int rowTop = directoryPage * rowStride;
        int rowBottom = rowTop + archiveRowHeight();
        int visibleTop = currentScroll;
        int visibleBottom = currentScroll + archiveLayout.directoryContentH();
        if (rowTop < visibleTop) {
            return rowTop;
        }
        if (rowBottom > visibleBottom) {
            return rowBottom - archiveLayout.directoryContentH();
        }
        return currentScroll;
    }

    int clampDetailScroll(int scroll) {
        return clamp(scroll, 0, detailScrollMax);
    }

    int directoryScrollMax(int archivePageCount) {
        return directoryScrollMax(archiveLayout, auditLineHeight, archivePageCount);
    }

    int detailScrollMax() {
        return detailScrollMax;
    }

    int archiveRowHeight() {
        return Math.max(12, auditLineHeight + 2);
    }

    boolean isBlackglassArchive(TowerTerminalViewModel viewModel) {
        return TowerArchive.isBlackglassPage(viewModel.archivePage(), viewModel.archivePasswordPrompt());
    }

    int archiveAudioButtonAt(double mouseX, double mouseY, TowerTerminalViewModel viewModel) {
        if (!isBlackglassArchive(viewModel) || archiveLayout.audioH() <= 0) {
            return -1;
        }

        int y = audioButtonY();
        int size = audioButtonSize();
        if (mouseY < y || mouseY >= y + size) {
            return -1;
        }

        int playX = audioButtonX();
        if (mouseX >= playX && mouseX < playX + size) {
            return 0;
        }

        int stopX = playX + size + 5;
        if (mouseX >= stopX && mouseX < stopX + size) {
            return 1;
        }

        return -1;
    }

    int blackglassSegmentAt(double mouseX, double mouseY, TowerTerminalViewModel viewModel) {
        if (!isBlackglassArchive(viewModel) || archiveLayout.audioH() <= 0) {
            return -1;
        }
        int count = TowerArchive.blackglassSegmentCount();
        int y = segmentTabsY();
        for (int i = 0; i < count; i++) {
            int x = segmentTabX(i, count);
            if (mouseX >= x && mouseX < x + ARCHIVE_SEGMENT_TAB_W
                    && mouseY >= y && mouseY < y + ARCHIVE_SEGMENT_TAB_H) {
                return i;
            }
        }
        return -1;
    }

    int audioButtonX() {
        return archiveLayout.audioX() + BOX_PAD;
    }

    int audioButtonY() {
        return archiveLayout.audioY() + 17;
    }

    int audioButtonSize() {
        return ARCHIVE_AUDIO_BUTTON;
    }

    int segmentTabsY() {
        return archiveLayout.audioY() + archiveLayout.audioH() - 26;
    }

    int segmentTabX(int segmentIndex, int segmentCount) {
        int totalW = segmentCount * ARCHIVE_SEGMENT_TAB_W + (segmentCount - 1) * ARCHIVE_SEGMENT_TAB_GAP;
        int startX = archiveLayout.audioX() + archiveLayout.audioW() - BOX_PAD - totalW;
        return startX + segmentIndex * (ARCHIVE_SEGMENT_TAB_W + ARCHIVE_SEGMENT_TAB_GAP);
    }

    int segmentTabW() {
        return ARCHIVE_SEGMENT_TAB_W;
    }

    int segmentTabH() {
        return ARCHIVE_SEGMENT_TAB_H;
    }

    private static int desiredAuditHeight(Font font, float auditScale, int auditLineHeight, int contentWidth,
                                          TowerTerminalViewModel viewModel) {
        int scaledWidth = Math.max(24, (int) Math.floor(contentWidth / auditScale));
        int wrappedLines = 0;
        for (String line : viewModel.auditLines()) {
            wrappedLines += Math.max(1, font.split(Component.literal(line), scaledWidth).size());
        }
        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE) {
            int contentHeight = 24 + BOX_PAD + wrappedLines * auditLineHeight + BOX_PAD + 4;
            return Math.max(82, Math.min(96, contentHeight));
        }
        int visibleLines = Math.max(viewModel.state() == OpenTowerTerminalPayload.STATE_ACTIVE ? 4 : 3,
                Math.min(8, wrappedLines + (viewModel.state() == OpenTowerTerminalPayload.STATE_ACTIVE ? 1 : 0)));
        int contentHeight = 24 + BOX_PAD + visibleLines * auditLineHeight + BOX_PAD + 6;
        return Math.max(110, contentHeight);
    }

    private static ArchiveLayout createArchiveLayout(int panelX, int panelW, int headerBottomY, int auditY,
                                                     TowerTerminalViewModel viewModel) {
        int boxX = panelX + PANEL_PAD;
        int boxY = headerBottomY + ARCHIVE_TOP_GAP;
        int boxW = panelW - PANEL_PAD * 2;
        int boxH = Math.max(110, auditY - boxY - 12);
        boolean blackglass = TowerArchive.isBlackglassPage(viewModel.archivePage(), viewModel.archivePasswordPrompt());
        int directoryW = blackglass ? 0 : Math.min(ARCHIVE_DIRECTORY_W, Math.max(150, boxW / 3));
        int detailX = blackglass ? boxX : boxX + directoryW - 1;
        int detailW = Math.max(180, boxX + boxW - detailX);
        int contentTop = boxY + (blackglass ? 44 : 24);
        int audioH = blackglass ? ARCHIVE_AUDIO_H : 0;
        int contentBottom = boxY + boxH - BOX_PAD - audioH - (blackglass ? 8 : 0);
        int contentHeight = Math.max(24, contentBottom - contentTop);
        int audioX = detailX + BOX_PAD;
        int audioY = contentBottom + 8;
        int audioW = Math.max(0, detailW - BOX_PAD * 2);
        int directoryScrollbarX = boxX + directoryW - BOX_PAD - ARCHIVE_SCROLLBAR_W;
        int directoryContentX = boxX + BOX_PAD;
        int directoryContentW = Math.max(60, directoryScrollbarX - ARCHIVE_SCROLLBAR_GAP - directoryContentX);
        int detailScrollbarX = detailX + detailW - BOX_PAD - ARCHIVE_SCROLLBAR_W;
        int detailContentX = detailX + BOX_PAD;
        int detailContentW = Math.max(100, detailScrollbarX - ARCHIVE_SCROLLBAR_GAP - detailContentX);
        return new ArchiveLayout(boxX, boxY, directoryW, boxH, detailX, boxY, detailW, boxH,
                directoryContentX, contentTop, directoryContentW, contentHeight, directoryScrollbarX,
                detailContentX, contentTop, detailContentW, contentHeight, detailScrollbarX,
                audioX, audioY, audioW, audioH);
    }

    private static List<FormattedCharSequence> wrapArchiveBody(Font font, float auditScale, int detailContentWidth,
                                                               TowerTerminalViewModel viewModel) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        int scaledWidth = Math.max(24, (int) Math.floor(detailContentWidth / auditScale));
        for (String line : viewModel.archiveBodyLines()) {
            wrapped.addAll(font.split(Component.literal(line), scaledWidth));
        }
        return wrapped;
    }

    private static int directoryScrollMax(ArchiveLayout archiveLayout, int auditLineHeight, int archivePageCount) {
        int rowHeight = Math.max(12, auditLineHeight + 2);
        int pageTotal = Math.min(TowerArchive.DIRECTORY_PAGE_COUNT,
                archivePageCount > 0 ? archivePageCount : TowerArchive.DIRECTORY_PAGE_COUNT);
        int contentHeight = Math.max(0, pageTotal * (rowHeight + 2) - 2);
        return Math.max(0, contentHeight - archiveLayout.directoryContentH());
    }

    private static int detailScrollMax(List<FormattedCharSequence> archiveWrappedBody, ArchiveLayout archiveLayout,
                                       int auditLineHeight, boolean archivePasswordPrompt) {
        int reservedPromptHeight = archivePasswordPrompt ? auditLineHeight + 10 : 0;
        int contentHeight = archiveWrappedBody.size() * auditLineHeight;
        return Math.max(0, contentHeight - (archiveLayout.detailContentH() - reservedPromptHeight));
    }

    private static List<TokenHitbox> buildHitboxes(TowerTerminalViewModel viewModel, int boardCenterX, int boardY,
                                                   int boardLineHeight, int scaledCharStep, int scaledAddressWidth,
                                                   int scaledAddressGap, int scaledSegmentGap, int scaledGroupWidth) {
        List<TokenHitbox> hitboxes = new ArrayList<>();
        if (viewModel.state() != OpenTowerTerminalPayload.STATE_ACTIVE || viewModel.board() == null) {
            return hitboxes;
        }

        for (TowerTerminalPuzzle.WordToken token : viewModel.board().wordTokens()) {
            if (((viewModel.removedMask() >> token.wordIndex()) & 1L) != 0L) {
                continue;
            }
            SegmentLayout layout = segmentLayout(boardCenterX, boardY, boardLineHeight, scaledAddressWidth,
                    scaledAddressGap, scaledSegmentGap, scaledGroupWidth, token.segmentIndex());
            hitboxes.add(new TokenHitbox(
                    layout.textX() + token.start() * scaledCharStep,
                    layout.y(),
                    Math.max(8, token.length() * scaledCharStep),
                    boardLineHeight,
                    token.wordIndex(),
                    true));
        }
        for (TowerTerminalPuzzle.PairToken token : viewModel.board().pairTokens()) {
            if (((viewModel.usedPairMask() >> token.pairIndex()) & 1L) != 0L) {
                continue;
            }
            SegmentLayout layout = segmentLayout(boardCenterX, boardY, boardLineHeight, scaledAddressWidth,
                    scaledAddressGap, scaledSegmentGap, scaledGroupWidth, token.segmentIndex());
            hitboxes.add(new TokenHitbox(
                    layout.textX() + token.start() * scaledCharStep,
                    layout.y(),
                    Math.max(8, token.length() * scaledCharStep),
                    boardLineHeight,
                    token.pairIndex(),
                    false));
        }
        return hitboxes;
    }

    private static SegmentLayout segmentLayout(int boardCenterX, int boardY, int boardLineHeight,
                                               int scaledAddressWidth, int scaledAddressGap,
                                               int scaledSegmentGap, int scaledGroupWidth, int segmentIndex) {
        int row = segmentIndex / 2;
        boolean right = (segmentIndex % 2) == 1;
        int addressBase = right ? 0xF1B0 : 0xF000;
        int x = right
                ? boardCenterX + scaledSegmentGap / 2
                : boardCenterX - scaledSegmentGap / 2 - scaledGroupWidth;
        int y = boardY + row * boardLineHeight;
        String address = String.format("0x%04X", addressBase + row * 0x10);
        return new SegmentLayout(x, y, address, x + scaledAddressWidth + scaledAddressGap);
    }

    private static int scaledLineHeight(float scale) {
        return Math.max(6, Math.round(LINE_HEIGHT * scale));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record SegmentLayout(int addressX, int y, String address, int textX) {
    }

    record ArchiveLayout(int directoryX, int directoryY, int directoryW, int directoryH,
                         int detailX, int detailY, int detailW, int detailH,
                         int directoryContentX, int directoryContentY, int directoryContentW, int directoryContentH,
                         int directoryScrollbarX,
                         int detailContentX, int detailContentY, int detailContentW, int detailContentH,
                         int detailScrollbarX,
                         int audioX, int audioY, int audioW, int audioH) {
    }

    record TokenHitbox(int x, int y, int width, int height, int index, boolean word) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
