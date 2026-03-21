package com.frozendawn.client;

import com.frozendawn.network.OpenTowerTerminalPayload;
import com.frozendawn.network.SubmitTowerTerminalPayload;
import com.frozendawn.terminal.TowerTerminalPuzzle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TowerTerminalScreen extends Screen {

    private static final int CHAR_STEP = 5;
    private static final int LINE_HEIGHT = 9;
    private static final int ADDRESS_GAP = 6;
    private static final int SEGMENT_GAP = 18;
    private static final int PANEL_MIN_W = 420;
    private static final int PANEL_MAX_W = 820;
    private static final int PANEL_MIN_H = 260;
    private static final int PANEL_MAX_H = 500;
    private static final int PANEL_PAD = 18;
    private static final int BOX_PAD = 10;
    private static final int BOX_GAP = 12;
    private static final int STATUS_LINE_GAP = 2;
    private static final List<String> HELP_LINES = List.of(
            "Type a cyan word already on the board.",
            "Enter submits. Backspace edits the prompt.",
            "Likeness = matching letters in the same slots.",
            "Bracket pairs can remove a dud or reset tries."
    );

    private final BlockPos consolePos;
    private long nonce;
    private TowerTerminalPuzzle.Board board;
    private int triesLeft;
    private int state;
    private long removedMask;
    private long usedPairMask;
    private List<String> auditLines = new ArrayList<>();
    private final List<TokenHitbox> pairHitboxes = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private boolean compactLayout;
    private boolean ultraCompactLayout;
    private int charStep = CHAR_STEP;
    private int boardX;
    private int boardY;
    private int promptX;
    private int promptY;
    private int promptW;
    private int promptH;
    private int leftBoxX;
    private int leftBoxY;
    private int leftBoxW;
    private int leftBoxH;
    private int rightBoxX;
    private int rightBoxY;
    private int rightBoxW;
    private int rightBoxH;
    private int segmentWidth;
    private int addressWidth;
    private int titleY;
    private int subtitleY;
    private int attemptsY;
    private int headerBottom;
    private int footerTop;
    private int footerY;
    private int blinkTicks;
    private int closeTicks = -1;
    private int localStatusTicks;
    private String typedGuess = "";
    private String localStatus = "";
    private int localStatusColor = 0xFF7FC7DD;
    private boolean showHeaderSubtitle;
    private boolean showHelpBox;
    private boolean showAuditBox;

    public TowerTerminalScreen(OpenTowerTerminalPayload payload) {
        super(Component.literal("ORSA UPLINK TERMINAL"));
        this.consolePos = payload.pos();
        applySnapshot(payload);
    }

    public boolean sameConsole(BlockPos pos) {
        return consolePos.equals(pos);
    }

    public void applySnapshot(OpenTowerTerminalPayload payload) {
        long previousNonce = this.nonce;
        this.nonce = payload.nonce();
        this.board = TowerTerminalPuzzle.create(payload.nonce());
        this.triesLeft = payload.triesLeft();
        this.state = payload.state();
        this.removedMask = payload.removedMask();
        this.usedPairMask = payload.usedPairMask();
        this.auditLines = payload.auditLog().isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(payload.auditLog().split("\n")));
        if (state == OpenTowerTerminalPayload.STATE_SOLVED || state == OpenTowerTerminalPayload.STATE_LOCKED_OUT) {
            closeTicks = 20;
        } else {
            closeTicks = -1;
        }
        if (state != OpenTowerTerminalPayload.STATE_ACTIVE || previousNonce != this.nonce) {
            typedGuess = "";
        }
        localStatus = "";
        localStatusTicks = 0;
        if (minecraft != null && minecraft.screen == this) {
            recalculateLayout();
            rebuildHitboxes();
        }
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(PANEL_MAX_W, width - 12);
        if (panelW < PANEL_MIN_W) {
            panelW = Math.max(320, width - 8);
        }
        panelH = Math.min(PANEL_MAX_H, height - 12);
        if (panelH < PANEL_MIN_H) {
            panelH = Math.max(240, height - 8);
        }
        panelW = Math.min(panelW, width - 8);
        panelH = Math.min(panelH, height - 8);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        recalculateLayout();
        rebuildHitboxes();
    }

    private void recalculateLayout() {
        compactLayout = panelW < 700 || panelH < 420;
        ultraCompactLayout = panelW < 600 || panelH < 320;
        charStep = ultraCompactLayout ? 4 : 5;

        addressWidth = font.width("0xF1B0");
        segmentWidth = TowerTerminalPuzzle.SEGMENT_LENGTH * charStep;
        int lineWidth = (addressWidth + ADDRESS_GAP + segmentWidth) * 2 + SEGMENT_GAP;

        boardX = panelX + Math.max(PANEL_PAD, (panelW - lineWidth) / 2);

        titleY = panelY + 12;
        int headerGap = ultraCompactLayout ? 3 : 4;
        showHeaderSubtitle = !ultraCompactLayout;
        if (showHeaderSubtitle) {
            subtitleY = titleY + LINE_HEIGHT + headerGap;
            attemptsY = subtitleY + LINE_HEIGHT + headerGap;
        } else {
            subtitleY = -1;
            attemptsY = titleY + LINE_HEIGHT + headerGap;
        }
        headerBottom = attemptsY + LINE_HEIGHT + 7;
        boardY = headerBottom + (ultraCompactLayout ? 5 : compactLayout ? 8 : 10);

        promptX = panelX + PANEL_PAD;
        promptW = panelW - PANEL_PAD * 2;
        promptH = ultraCompactLayout ? 36 : compactLayout ? 40 : 46;
        promptY = boardY + TowerTerminalPuzzle.ROWS * LINE_HEIGHT + (ultraCompactLayout ? 5 : compactLayout ? 8 : 12);

        int footerBandHeight = measureFooterBandHeight();
        footerTop = panelY + panelH - footerBandHeight;
        int footerTextHeight = Math.max(1, font.split(currentFooter(), promptW).size()) * LINE_HEIGHT;
        footerY = footerTop + Math.max(4, (footerBandHeight - footerTextHeight) / 2);

        layoutBottomPanels(promptY + promptH + (ultraCompactLayout ? 5 : 8),
                footerTop - (ultraCompactLayout ? 4 : 8));
    }

    private void layoutBottomPanels(int topY, int bottomY) {
        showHelpBox = false;
        showAuditBox = false;
        leftBoxX = promptX;
        leftBoxY = topY;
        leftBoxW = 0;
        leftBoxH = 0;
        rightBoxX = promptX;
        rightBoxY = topY;
        rightBoxW = 0;
        rightBoxH = 0;

        int availableHeight = bottomY - topY;
        if (availableHeight <= 0) {
            return;
        }

        int contentWidth = panelW - PANEL_PAD * 2;
        int twoColumnWidth = (contentWidth - BOX_GAP) / 2;
        int minAuditHeight = ultraCompactLayout ? 52 : compactLayout ? 60 : 72;
        int helpHeight = measureInfoBoxHeight(HELP_LINES, twoColumnWidth);
        if (twoColumnWidth >= 160 && availableHeight >= Math.max(minAuditHeight, helpHeight)) {
            showHelpBox = true;
            showAuditBox = true;
            leftBoxW = twoColumnWidth;
            rightBoxW = contentWidth - twoColumnWidth - BOX_GAP;
            leftBoxH = availableHeight;
            rightBoxH = availableHeight;
            rightBoxX = promptX + leftBoxW + BOX_GAP;
            return;
        }

        if (availableHeight >= minAuditHeight) {
            showAuditBox = true;
            rightBoxW = contentWidth;
            rightBoxH = availableHeight;
        }
    }

    private int measureFooterBandHeight() {
        int lines = Math.max(1, font.split(currentFooter(), promptW).size());
        int paddedHeight = lines * LINE_HEIGHT + (ultraCompactLayout ? 10 : 12);
        return Math.max(ultraCompactLayout ? 22 : 24, paddedHeight);
    }

    private int measureInfoBoxHeight(List<String> lines, int width) {
        int textWidth = Math.max(40, width - BOX_PAD * 2);
        int height = 22 + BOX_PAD;
        for (String line : lines) {
            height += font.split(Component.literal(line), textWidth).size() * LINE_HEIGHT;
            height += STATUS_LINE_GAP;
        }
        return height;
    }

    private void rebuildHitboxes() {
        pairHitboxes.clear();
        if (board == null) {
            return;
        }

        for (TowerTerminalPuzzle.PairToken token : board.pairTokens()) {
            if (((usedPairMask >> token.pairIndex()) & 1L) != 0L) {
                continue;
            }
            SegmentLayout layout = segmentLayout(token.segmentIndex());
            pairHitboxes.add(new TokenHitbox(layout.textX() + token.start() * charStep, layout.y(),
                    token.length() * charStep, LINE_HEIGHT, token.pairIndex()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        blinkTicks++;
        if (localStatusTicks > 0) {
            localStatusTicks--;
            if (localStatusTicks == 0) {
                localStatus = "";
            }
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

        graphics.drawCenteredString(font, title, panelX + panelW / 2, titleY, 0xFFE4F7FF);
        if (showHeaderSubtitle) {
            graphics.drawCenteredString(font, Component.literal("Type a board word, then press Enter."),
                    panelX + panelW / 2, subtitleY, 0xFF87C5D8);
        }
        graphics.drawCenteredString(font, Component.literal("ATTEMPTS: " + "*".repeat(Math.max(triesLeft, 0))),
                panelX + panelW / 2, attemptsY, triesLeft > 1 ? 0xFFB7F1FF : 0xFFFF8B8B);

        renderBoard(graphics, mouseX, mouseY);
        renderPrompt(graphics);
        renderHelp(graphics);
        renderAudit(graphics);
        renderFooter(graphics);
    }

    private void renderFrame(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xD0081018, 0xE004090E);
        graphics.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0x4054C6F6);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF081018);
        graphics.fill(panelX + 2, panelY + 2, panelX + panelW - 2, headerBottom, 0xFF10202E);
        graphics.fill(panelX + 2, headerBottom, panelX + panelW - 2, headerBottom + 1, 0xFF58BDE4);
        graphics.fill(panelX + 2, footerTop, panelX + panelW - 2, footerTop + 1, 0xFF1B3341);
        graphics.fill(panelX + 2, panelY + 2, panelX + 3, panelY + panelH - 2, 0xFF294D60);
        graphics.fill(panelX + panelW - 3, panelY + 2, panelX + panelW - 2, panelY + panelH - 2, 0xFF0A1720);
    }

    private void renderBoard(GuiGraphics graphics, int mouseX, int mouseY) {
        TokenHitbox hovered = hoveredToken(mouseX, mouseY);
        if (hovered != null) {
            graphics.fill(hovered.x(), hovered.y() - 1, hovered.x() + hovered.width(), hovered.y() + hovered.height(), 0x4435A7D1);
        }

        for (int row = 0; row < TowerTerminalPuzzle.ROWS; row++) {
            renderSegment(graphics, row * 2);
            renderSegment(graphics, row * 2 + 1);
        }
    }

    private void renderSegment(GuiGraphics graphics, int segmentIndex) {
        SegmentLayout layout = segmentLayout(segmentIndex);
        graphics.drawString(font, layout.address(), layout.addressX(), layout.y(), 0xFF5E8B9D, false);
        String rendered = board.renderSegment(segmentIndex, removedMask, usedPairMask);
        for (int i = 0; i < rendered.length(); i++) {
            graphics.drawString(font, String.valueOf(rendered.charAt(i)), layout.textX() + i * charStep, layout.y(), 0xFFB6EEF8, false);
        }
    }

    private void renderPrompt(GuiGraphics graphics) {
        graphics.fill(promptX, promptY, promptX + promptW, promptY + promptH, 0xFF0D1820);
        graphics.fill(promptX, promptY, promptX + promptW, promptY + 1, 0xFF4CB7E3);
        graphics.fill(promptX, promptY + promptH - 1, promptX + promptW, promptY + promptH, 0xFF0A1219);
        graphics.drawString(font, Component.literal("TYPE WORD / ENTER TO SUBMIT"), promptX + BOX_PAD, promptY + 4, 0xFFD9F4FF, false);

        String cursor = (blinkTicks / 8) % 2 == 0 ? "_" : " ";
        String shownGuess = typedGuess.isEmpty() ? cursor : typedGuess + cursor;
        graphics.drawString(font, Component.literal("> " + shownGuess), promptX + BOX_PAD, promptY + 16, 0xFFB6EEF8, false);
        String countLabel = typedGuess.length() + "/" + board.wordLength();
        graphics.drawString(font, Component.literal(countLabel),
                promptX + promptW - BOX_PAD - font.width(countLabel), promptY + 16, 0xFF79AFC0, false);

        if (!localStatus.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal(localStatus), promptX + promptW / 2, promptY + promptH - 12, localStatusColor);
        } else if (ultraCompactLayout) {
            graphics.drawCenteredString(font, Component.literal("Type a cyan board word from the grid."),
                    promptX + promptW / 2, promptY + promptH - 12, 0xFF7FC7DD);
        } else {
            graphics.drawCenteredString(font, Component.literal("Type any cyan board word. Brackets are optional."), promptX + promptW / 2, promptY + promptH - 12, 0xFF7FC7DD);
        }
    }

    private void renderHelp(GuiGraphics graphics) {
        if (!showHelpBox) {
            return;
        }
        renderInfoBox(graphics, leftBoxX, leftBoxY, leftBoxW, leftBoxH, "HOW TO HACK", 0xFF63BBE4, 0xFF0E1720, HELP_LINES);
    }

    private void renderAudit(GuiGraphics graphics) {
        if (!showAuditBox) {
            return;
        }
        renderInfoBox(graphics, rightBoxX, rightBoxY, rightBoxW, rightBoxH, "AUDIT LOG", 0xFFD9F4FF, 0xFF0C141B, null);

        int y = rightBoxY + 22;
        if (auditLines.isEmpty()) {
            graphics.drawString(font, Component.literal("awaiting input..."), rightBoxX + BOX_PAD, y, 0xFF84A8B5, false);
            return;
        }

        int limitY = rightBoxY + rightBoxH - BOX_PAD - LINE_HEIGHT;
        boolean truncated = false;
        for (String line : auditLines) {
            for (FormattedCharSequence wrapped : font.split(Component.literal(line), rightBoxW - BOX_PAD * 2)) {
                if (y > limitY) {
                    truncated = true;
                    break;
                }
                graphics.drawString(font, wrapped, rightBoxX + BOX_PAD, y, 0xFF9FD5E4, false);
                y += LINE_HEIGHT;
            }
            if (truncated) {
                break;
            }
            y += STATUS_LINE_GAP;
        }
        if (truncated) {
            graphics.drawString(font, Component.literal("..."), rightBoxX + BOX_PAD, limitY, 0xFF678996, false);
        }
    }

    private void renderFooter(GuiGraphics graphics) {
        int color = currentFooterColor();
        int y = footerY;
        for (FormattedCharSequence line : font.split(currentFooter(), promptW)) {
            graphics.drawString(font, line, panelX + panelW / 2 - font.width(line) / 2, y, color, false);
            y += LINE_HEIGHT;
        }
    }

    private void renderInfoBox(GuiGraphics graphics, int x, int y, int w, int h, String title, int accent, int fill, List<String> lines) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, accent);
        graphics.fill(x, y, x + 1, y + h, accent);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF0A1720);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF0A1720);
        graphics.drawString(font, Component.literal(title), x + BOX_PAD, y + 7, accent, false);

        if (lines == null) {
            return;
        }

        int currentY = y + 22;
        for (String line : lines) {
            currentY = drawWrappedLine(graphics, Component.literal(line), x + BOX_PAD, currentY, w - BOX_PAD * 2, y + h - BOX_PAD, 0xFFD4EEF7);
            currentY += STATUS_LINE_GAP;
            if (currentY > y + h - BOX_PAD) {
                break;
            }
        }
    }

    private Component currentFooter() {
        if (state == OpenTowerTerminalPayload.STATE_SOLVED) {
            return Component.literal("PASSWORD ACCEPTED // ALIGNMENT STARTING");
        }
        if (state == OpenTowerTerminalPayload.STATE_LOCKED_OUT) {
            return Component.literal("TERMINAL LOCKED // REOPEN CONSOLE");
        }
        return Component.literal("ENTER SUBMITS // BRACKET PAIRS ARE OPTIONAL");
    }

    private int currentFooterColor() {
        if (state == OpenTowerTerminalPayload.STATE_SOLVED) {
            return 0xFF9DF4B6;
        }
        if (state == OpenTowerTerminalPayload.STATE_LOCKED_OUT) {
            return 0xFFFF8B8B;
        }
        return 0xFF7FC7DD;
    }

    private int drawWrappedLine(GuiGraphics graphics, Component line, int x, int y, int width, int maxY, int color) {
        for (FormattedCharSequence wrapped : font.split(line, width)) {
            if (y > maxY) {
                return y;
            }
            graphics.drawString(font, wrapped, x, y, color, false);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private SegmentLayout segmentLayout(int segmentIndex) {
        int row = segmentIndex / 2;
        boolean right = (segmentIndex % 2) == 1;
        int addressBase = right ? 0xF1B0 : 0xF000;
        int x = boardX + (right ? addressWidth + ADDRESS_GAP + segmentWidth + SEGMENT_GAP : 0);
        int y = boardY + row * LINE_HEIGHT;
        String address = String.format("0x%04X", addressBase + row * 0x10);
        return new SegmentLayout(x, y, address, x + addressWidth + ADDRESS_GAP);
    }

    private TokenHitbox hoveredToken(double mouseX, double mouseY) {
        for (TokenHitbox hitbox : pairHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                return hitbox;
            }
        }
        return null;
    }

    private boolean submitTypedGuess() {
        if (state != OpenTowerTerminalPayload.STATE_ACTIVE || board == null) {
            return false;
        }
        if (typedGuess.length() != board.wordLength()) {
            localStatus = "Need a " + board.wordLength() + "-letter board word.";
            localStatusColor = 0xFFFF8B8B;
            localStatusTicks = 60;
            return true;
        }

        PacketDistributor.sendToServer(new SubmitTowerTerminalPayload(
                consolePos,
                nonce,
                SubmitTowerTerminalPayload.ACTION_TYPED_GUESS,
                -1,
                typedGuess
        ));
        typedGuess = "";
        localStatus = "TRANSMITTING...";
        localStatusColor = 0xFF9DF4B6;
        localStatusTicks = 40;
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (state != OpenTowerTerminalPayload.STATE_ACTIVE || board == null) {
            return super.charTyped(codePoint, modifiers);
        }

        if (isAsciiLetter(codePoint) && typedGuess.length() < board.wordLength()) {
            typedGuess = typedGuess + Character.toUpperCase(codePoint);
            localStatus = "";
            localStatusTicks = 0;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state == OpenTowerTerminalPayload.STATE_ACTIVE && board != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!typedGuess.isEmpty()) {
                    typedGuess = typedGuess.substring(0, typedGuess.length() - 1);
                }
                localStatus = "";
                localStatusTicks = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                return submitTypedGuess();
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state != OpenTowerTerminalPayload.STATE_ACTIVE || board == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        TokenHitbox hitbox = hoveredToken(mouseX, mouseY);
        if (hitbox == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        PacketDistributor.sendToServer(new SubmitTowerTerminalPayload(
                consolePos,
                nonce,
                SubmitTowerTerminalPayload.ACTION_USE_PAIR,
                hitbox.index(),
                ""
        ));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void openOrUpdate(Minecraft minecraft, OpenTowerTerminalPayload payload) {
        if (minecraft.screen instanceof TowerTerminalScreen screen && screen.sameConsole(payload.pos())) {
            screen.applySnapshot(payload);
            return;
        }
        minecraft.setScreen(new TowerTerminalScreen(payload));
    }

    private record SegmentLayout(int addressX, int y, String address, int textX) {
    }

    private record TokenHitbox(int x, int y, int width, int height, int index) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private static boolean isAsciiLetter(char codePoint) {
        return (codePoint >= 'a' && codePoint <= 'z') || (codePoint >= 'A' && codePoint <= 'Z');
    }
}
