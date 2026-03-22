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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TowerTerminalScreen extends Screen {

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
    private static final int BOX_GAP = 12;
    private static final int PROGRESS_BAR_H = 14;
    private static final int ALIGN_TICKS_TOTAL = 20 * 30;
    private static final int LOCKOUT_TICKS_TOTAL = 20 * 60;
    private final BlockPos consolePos;
    private long nonce;
    private TowerTerminalPuzzle.Board board;
    private int triesLeft;
    private int state;
    private long removedMask;
    private long usedPairMask;
    private int alignTicksRemaining;
    private int lockoutTicksRemaining;
    private List<String> auditLines = new ArrayList<>();
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
    private int boardX;
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
    private String terminalInput = "";
    private float headerScale = 0.82f;
    private float boardScale = 0.78f;
    private float auditScale = 0.78f;
    private int headerLineHeight;
    private int auditLineHeight;

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
        this.board = payload.nonce() == 0L ? null : TowerTerminalPuzzle.create(payload.nonce());
        this.triesLeft = payload.triesLeft();
        this.state = payload.state();
        this.removedMask = payload.removedMask();
        this.usedPairMask = payload.usedPairMask();
        this.alignTicksRemaining = payload.alignTicksRemaining();
        this.lockoutTicksRemaining = payload.lockoutTicksRemaining();
        this.auditLines = payload.auditLog().isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(payload.auditLog().split("\n")));
        this.closeTicks = state == OpenTowerTerminalPayload.STATE_COMPLETE ? 30 : -1;
        if (payload.nonce() != previousNonce || state != OpenTowerTerminalPayload.STATE_ACTIVE) {
            this.terminalInput = "";
        }
        if (minecraft != null && minecraft.screen == this) {
            recalculateLayout();
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
        segmentWidth = TowerTerminalPuzzle.SEGMENT_LENGTH * charStep;

        int contentWidth = panelW - PANEL_PAD * 2;
        int lineWidth = (addressWidth + ADDRESS_GAP + segmentWidth) * 2 + segmentGap;
        while (lineWidth > contentWidth && charStep > 4) {
            charStep--;
            segmentWidth = TowerTerminalPuzzle.SEGMENT_LENGTH * charStep;
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
        boardX = boardCenterX - boardRenderedWidth / 2;
        int boardContentHeight = TowerTerminalPuzzle.ROWS * boardLineHeight;

        int boardAreaTop = headerBottomY + 12;
        int bottomMargin = 14;
        int desiredAuditH = Math.max(120, Math.min(160, panelH / 3));
        int boardAreaBottom = panelY + panelH - bottomMargin - desiredAuditH - 14;
        int boardAreaHeight = Math.max(boardContentHeight, boardAreaBottom - boardAreaTop);
        boardY = boardAreaTop + Math.max(0, (boardAreaHeight - boardContentHeight) / 2);
        boardBottom = boardY + boardContentHeight;

        auditX = panelX + PANEL_PAD;
        auditW = contentWidth;
        auditY = boardBottom + 8;
        int availableAuditH = Math.max(96, panelY + panelH - bottomMargin - auditY);
        auditH = Math.min(availableAuditH, desiredAuditHeight(auditW - BOX_PAD * 2));
    }

    private void rebuildHitboxes() {
        interactiveHitboxes.clear();
        if (board == null || state != OpenTowerTerminalPayload.STATE_ACTIVE) {
            return;
        }

        for (TowerTerminalPuzzle.WordToken token : board.wordTokens()) {
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
        for (TowerTerminalPuzzle.PairToken token : board.pairTokens()) {
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
        if (state == OpenTowerTerminalPayload.STATE_ALIGNING && alignTicksRemaining > 0) {
            alignTicksRemaining--;
        }
        if (state == OpenTowerTerminalPayload.STATE_LOCKED_OUT && lockoutTicksRemaining > 0) {
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
        drawCenteredScaledString(graphics, title, panelX + panelW / 2, titleY, headerScale, 0xFFE4F7FF);

        if (state == OpenTowerTerminalPayload.STATE_ACTIVE) {
            renderAttempts(graphics);
            renderBoard(graphics, mouseX, mouseY);
            renderAudit(graphics);
            return;
        }

        renderStatePanel(graphics);
        renderAudit(graphics);
    }

    private void renderFrame(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xD0081018, 0xE004090E);
        graphics.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0x4054C6F6);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF081018);
        graphics.fill(panelX + 2, panelY + 2, panelX + panelW - 2, headerBottomY - 2, 0xFF10202E);
        graphics.fill(panelX + 2, headerBottomY - 2, panelX + panelW - 2, headerBottomY - 1, 0xFF58BDE4);
        graphics.fill(panelX + 2, panelY + 2, panelX + 3, panelY + panelH - 2, 0xFF294D60);
        graphics.fill(panelX + panelW - 3, panelY + 2, panelX + panelW - 2, panelY + panelH - 2, 0xFF0A1720);
    }

    private void renderAttempts(GuiGraphics graphics) {
        drawCenteredScaledString(graphics,
                Component.literal("ATTEMPTS: " + "*".repeat(Math.max(triesLeft, 0))),
                panelX + panelW / 2,
                attemptsY,
                headerScale,
                triesLeft > 1 ? 0xFFB7F1FF : 0xFFFF8B8B);
    }

    private void renderBoard(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int segmentIndex = 0; segmentIndex < TowerTerminalPuzzle.SEGMENTS; segmentIndex++) {
            SegmentLayout layout = segmentLayout(segmentIndex);
            drawScaledString(graphics, layout.address(), layout.addressX(), layout.y(), boardScale, 0xFF5E8B9D);
            String rendered = board.renderSegment(segmentIndex, removedMask, usedPairMask);
            for (int i = 0; i < rendered.length(); i++) {
                int color = glyphColor(segmentIndex, i);
                drawScaledString(graphics, String.valueOf(rendered.charAt(i)),
                        layout.textX() + i * scaledCharStep, layout.y(), boardScale, color);
            }
        }
    }

    private int glyphColor(int segmentIndex, int charIndex) {
        return 0xFF5F91A3;
    }

    private void renderAudit(GuiGraphics graphics) {
        renderInfoBox(graphics, auditX, auditY, auditW, auditH, "AUDIT LOG", 0xFFD9F4FF, 0xFF0C141B, null);

        int y = auditY + 24;
        int inputY = auditY + auditH - BOX_PAD - auditLineHeight;
        int maxY = inputY - 8;
        for (String line : auditLines) {
            y = drawWrappedScaledLine(graphics, Component.literal(line), auditX + BOX_PAD, y,
                    auditW - BOX_PAD * 2, maxY, 0xFF9FD5E4, auditScale);
            y += 1;
        }

        if (state == OpenTowerTerminalPayload.STATE_ACTIVE) {
            String cursor = (blinkTicks / 8) % 2 == 0 ? "_" : "";
            drawScaledString(graphics, Component.literal("> " + terminalInput + cursor),
                    auditX + BOX_PAD, inputY, auditScale, 0xFFBDF8FF);
        }
    }

    private void renderStatePanel(GuiGraphics graphics) {
        int boxX = panelX + PANEL_PAD;
        int boxY = panelY + 66;
        int boxW = panelW - PANEL_PAD * 2;
        int boxH = Math.max(110, auditY - boxY - 12);

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF0D1820);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF4CB7E3);
        graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF0A1219);

        if (state == OpenTowerTerminalPayload.STATE_LOCKED_OUT) {
            graphics.drawCenteredString(font, Component.literal("TERMINAL LOCKED"), boxX + boxW / 2, boxY + 18, 0xFFFF8B8B);
            graphics.drawCenteredString(font, Component.literal("Please contact an administrator."), boxX + boxW / 2, boxY + 36, 0xFFB7C7D0);
            String reset = "RESET IN " + Math.max(1, (lockoutTicksRemaining + 19) / 20) + "s";
            graphics.drawCenteredString(font, Component.literal(reset), boxX + boxW / 2, boxY + 56, 0xFF87C5D8);
            return;
        }

        if (state == OpenTowerTerminalPayload.STATE_ALIGNING) {
            graphics.drawCenteredString(font, Component.literal("TRANSMISSION IN PROGRESS"), boxX + boxW / 2, boxY + 18, 0xFF9DF4B6);
            graphics.drawCenteredString(font, Component.literal("Maintaining ORSA uplink lock..."), boxX + boxW / 2, boxY + 36, 0xFFB7C7D0);
            renderProgressBar(graphics, boxX + 28, boxY + 58, boxW - 56, Math.max(0, 1.0f - (float) alignTicksRemaining / ALIGN_TICKS_TOTAL));
            String time = String.format("%02d:%02d", Math.max(0, alignTicksRemaining) / 20 / 60, (Math.max(0, alignTicksRemaining) / 20) % 60);
            graphics.drawCenteredString(font, Component.literal("TIME TO TRANSMISSION  " + time), boxX + boxW / 2, boxY + 78, 0xFFB6EEF8);
            return;
        }

        graphics.drawCenteredString(font, Component.literal("TRANSMISSION COMPLETE"), boxX + boxW / 2, boxY + 30, 0xFF9DF4B6);
        graphics.drawCenteredString(font, Component.literal("Tower cache released ORSA locator data."), boxX + boxW / 2, boxY + 50, 0xFFB7C7D0);
    }

    private void renderProgressBar(GuiGraphics graphics, int x, int y, int w, float progress) {
        int clamped = Math.max(0, Math.min(w, Math.round(w * progress)));
        graphics.fill(x, y, x + w, y + PROGRESS_BAR_H, 0xFF071218);
        graphics.fill(x + 1, y + 1, x + w - 1, y + PROGRESS_BAR_H - 1, 0xFF10202E);
        graphics.fill(x + 2, y + 2, x + 2 + clamped, y + PROGRESS_BAR_H - 2, 0xFF59CBEA);
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

        int currentY = y + 24;
        for (String line : lines) {
            currentY = drawWrappedLine(graphics, Component.literal(line), x + BOX_PAD, currentY, w - BOX_PAD * 2, y + h - BOX_PAD, 0xFFD4EEF7);
            currentY += 1;
            if (currentY > y + h - BOX_PAD) {
                break;
            }
        }
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

    private int desiredAuditHeight(int contentWidth) {
        int scaledWidth = Math.max(24, (int) Math.floor(contentWidth / auditScale));
        int wrappedLines = 0;
        for (String line : auditLines) {
            wrappedLines += Math.max(1, font.split(Component.literal(line), scaledWidth).size());
        }
        int visibleLines = Math.max(state == OpenTowerTerminalPayload.STATE_ACTIVE ? 4 : 3,
                Math.min(8, wrappedLines + (state == OpenTowerTerminalPayload.STATE_ACTIVE ? 1 : 0)));
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
                ? boardCenterX + scaledSegmentGap / 2
                : boardCenterX - scaledSegmentGap / 2 - scaledGroupWidth;
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
                hitbox.word() ? SubmitTowerTerminalPayload.ACTION_TYPED_GUESS : SubmitTowerTerminalPayload.ACTION_USE_PAIR,
                hitbox.index(),
                ""
        ));
        terminalInput = "";
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (state == OpenTowerTerminalPayload.STATE_ACTIVE) {
            if (Character.isLetter(codePoint) && board != null && terminalInput.length() < board.wordLength()) {
                terminalInput += Character.toUpperCase(codePoint);
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state == OpenTowerTerminalPayload.STATE_ACTIVE) {
            if (keyCode == 259) {
                if (!terminalInput.isEmpty()) {
                    terminalInput = terminalInput.substring(0, terminalInput.length() - 1);
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (!terminalInput.isBlank()) {
                    PacketDistributor.sendToServer(new SubmitTowerTerminalPayload(
                            consolePos,
                            nonce,
                            SubmitTowerTerminalPayload.ACTION_TYPED_GUESS,
                            -1,
                            terminalInput
                    ));
                    terminalInput = "";
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private record TokenHitbox(int x, int y, int width, int height, int index, boolean word) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
