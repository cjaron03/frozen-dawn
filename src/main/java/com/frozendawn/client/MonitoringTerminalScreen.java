package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.OpenMonitoringTerminalPayload;
import com.frozendawn.network.SubmitMonitoringTerminalPayload;
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
        this.closeTicks = state == OpenMonitoringTerminalPayload.STATE_COMPLETE ? 30 : -1;
        if (payload.nonce() != previousNonce || state != OpenMonitoringTerminalPayload.STATE_ACTIVE) {
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
        int boardContentHeight = MonitoringTerminalPuzzle.ROWS * boardLineHeight;

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

        renderStatePanel(graphics);
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
        renderInfoBox(graphics, auditX, auditY, auditW, auditH, "TERMINAL LOG", 0xFFE6EFE9, 0xFF101618);

        int y = auditY + 24;
        int inputY = auditY + auditH - BOX_PAD - auditLineHeight;
        int maxY = inputY - 8;
        for (String line : auditLines) {
            y = drawWrappedScaledLine(graphics, Component.literal(line), auditX + BOX_PAD, y,
                    auditW - BOX_PAD * 2, maxY, 0xFFD4E5DC, auditScale);
            y += 1;
        }

        if (state == OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            String cursor = (blinkTicks / 8) % 2 == 0 ? "_" : "";
            drawScaledString(graphics, Component.literal("> " + terminalInput + cursor),
                    auditX + BOX_PAD, inputY, auditScale, 0xFFF5F7EE);
        }
    }

    private void renderStatePanel(GuiGraphics graphics) {
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
    public boolean charTyped(char codePoint, int modifiers) {
        if (state == OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            if (Character.isLetter(codePoint) && board != null && terminalInput.length() < board.wordLength()) {
                terminalInput += Character.toUpperCase(codePoint);
            }
            return true;
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

    private record SegmentLayout(int addressX, int y, String address, int textX) {
    }

    private record TokenHitbox(int x, int y, int width, int height, int index, boolean word) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
