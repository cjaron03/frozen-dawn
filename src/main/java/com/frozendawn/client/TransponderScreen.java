package com.frozendawn.client;

import com.frozendawn.block.TransponderBlockEntity;
import com.frozendawn.block.TransponderMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Status screen for the ORSA Transponder. Displays signal strength,
 * sky detection, power source, and broadcast progress.
 * Code-drawn dark tech style matching other Frozen Dawn UIs.
 */
public class TransponderScreen extends AbstractContainerScreen<TransponderMenu> {

    private static final int GUI_W = 176;
    private static final int GUI_H = 110;

    private static final int BAR_X = 8;
    private static final int BAR_Y = 20;
    private static final int BAR_W = 160;
    private static final int BAR_H = 14;

    private static final int ROW_START_Y = 40;
    private static final int ROW_H = 12;

    // Pause button
    private static final int BTN_W = 60;
    private static final int BTN_H = 14;

    public TransponderScreen(TransponderMenu menu, Inventory playerInv, Component title) {
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
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Rendered in renderBg with absolute coords instead
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Compute center explicitly every frame
        int x = (this.width - GUI_W) / 2;
        int y = (this.height - GUI_H) / 2;

        // Dark panel background
        graphics.fill(x, y, x + GUI_W, y + GUI_H, 0xFF0E1418);

        // Border (cool cyan-tinted frame)
        graphics.fill(x, y, x + GUI_W, y + 1, 0xFF1A4050);
        graphics.fill(x, y, x + 1, y + GUI_H, 0xFF1A4050);
        graphics.fill(x, y + GUI_H - 1, x + GUI_W, y + GUI_H, 0xFF0A2028);
        graphics.fill(x + GUI_W - 1, y, x + GUI_W, y + GUI_H, 0xFF0A2028);
        // Inner border
        graphics.fill(x + 1, y + 1, x + GUI_W - 1, y + 2, 0xFF184048);
        graphics.fill(x + 1, y + 1, x + 2, y + GUI_H - 1, 0xFF184048);
        graphics.fill(x + 2, y + GUI_H - 2, x + GUI_W - 1, y + GUI_H - 1, 0xFF081820);
        graphics.fill(x + GUI_W - 2, y + 2, x + GUI_W - 1, y + GUI_H - 1, 0xFF081820);

        // Inner panel
        graphics.fill(x + 3, y + 3, x + GUI_W - 3, y + GUI_H - 3, 0xFF0A1015);

        // Title bar
        graphics.fill(x + 3, y + 3, x + GUI_W - 3, y + 15, 0xFF101C22);
        graphics.fill(x + 3, y + 15, x + GUI_W - 3, y + 16, 0xFF1A4050);

        // Title text
        graphics.drawString(font, title, x + (GUI_W - font.width(title)) / 2, y + 5, 0xFF40C8E0, false);

        var data = menu.getData();
        int state = data.get(0);
        int progressPct = data.get(1);
        int minutesLeft = data.get(2);
        boolean shaftOk = data.get(3) != 0;
        boolean coreOk = data.get(4) != 0;
        boolean depthOk = data.get(5) != 0;
        boolean schematicOk = data.get(6) != 0;

        // --- Signal progress bar ---
        int barLeft = x + BAR_X;
        int barTop = y + BAR_Y;

        graphics.fill(barLeft, barTop, barLeft + BAR_W, barTop + BAR_H, 0xFF0A0C0F);

        if (state == TransponderBlockEntity.STATE_BROADCASTING || state == TransponderBlockEntity.STATE_PAUSED) {
            int fillW = (int) (BAR_W * progressPct / 100f);
            int barColor = state == TransponderBlockEntity.STATE_PAUSED ? 0xFFCC3322 : 0xFF22AADD;
            graphics.fill(barLeft, barTop, barLeft + fillW, barTop + BAR_H, barColor);
        } else if (state == TransponderBlockEntity.STATE_COMPLETE) {
            graphics.fill(barLeft, barTop, barLeft + BAR_W, barTop + BAR_H, 0xFF22DD44);
        }

        String progressText = switch (state) {
            case TransponderBlockEntity.STATE_IDLE -> "IDLE";
            case TransponderBlockEntity.STATE_BROADCASTING -> "BROADCASTING \u2014 " + minutesLeft + " min";
            case TransponderBlockEntity.STATE_COMPLETE -> "SIGNAL SENT";
            case TransponderBlockEntity.STATE_PAUSED -> "PAUSED \u2014 " + minutesLeft + " min";
            default -> "";
        };
        int textColor = state == TransponderBlockEntity.STATE_PAUSED ? 0xFFFF6644 : 0xFFE0E0E0;
        int textW = font.width(progressText);
        graphics.drawString(font, progressText, barLeft + (BAR_W - textW) / 2, barTop + 3, textColor, true);

        // --- Status checks (short labels only) ---
        renderStatusRow(graphics, x, y, 0, depthOk, "Below Y=0", "Above Y=0");
        renderStatusRow(graphics, x, y, 1, schematicOk, "Schematic", "No Schematic");
        renderStatusRow(graphics, x, y, 2, coreOk, "Geothermal Core", "No Core");
        renderStatusRow(graphics, x, y, 3, shaftOk, "Sky Access", "Shaft Blocked");

        // --- Pause/Resume button (only when broadcasting or paused) ---
        if (state == TransponderBlockEntity.STATE_BROADCASTING || state == TransponderBlockEntity.STATE_PAUSED) {
            int btnX = x + 8;
            int btnY = y + GUI_H - 20;
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + BTN_W
                    && mouseY >= btnY && mouseY < btnY + BTN_H;

            int btnBg = btnHovered ? 0xFF1E2830 : 0xFF141A1F;
            int btnBorder = state == TransponderBlockEntity.STATE_BROADCASTING ? 0xFF1A4050 : 0xFFDD8833;
            graphics.fill(btnX, btnY, btnX + BTN_W, btnY + BTN_H, btnBg);
            graphics.fill(btnX, btnY, btnX + BTN_W, btnY + 1, btnBorder);
            graphics.fill(btnX, btnY + BTN_H - 1, btnX + BTN_W, btnY + BTN_H, btnBorder);
            graphics.fill(btnX, btnY, btnX + 1, btnY + BTN_H, btnBorder);
            graphics.fill(btnX + BTN_W - 1, btnY, btnX + BTN_W, btnY + BTN_H, btnBorder);

            String btnLabel = state == TransponderBlockEntity.STATE_BROADCASTING ? "PAUSE" : "RESUME";
            int btnColor = state == TransponderBlockEntity.STATE_BROADCASTING ? 0xFFAABBCC : 0xFFDD8833;
            int lblW = font.width(btnLabel);
            graphics.drawString(font, btnLabel, btnX + (BTN_W - lblW) / 2, btnY + 3, btnColor, false);
        }

        // --- Signal strength indicator (bottom right) ---
        int sigX = x + GUI_W - 38;
        int sigY = y + GUI_H - 18;
        int bars = 0;
        if (depthOk) bars++;
        if (schematicOk) bars++;
        if (coreOk) bars++;
        if (shaftOk) bars++;

        graphics.drawString(font, "SIG", sigX - 20, sigY + 2, 0xFF607080, false);
        for (int i = 0; i < 4; i++) {
            int bx = sigX + i * 8;
            int bh = 4 + i * 2;
            int by = sigY + (10 - bh);
            int color = i < bars ? 0xFF22DD66 : 0xFF1A1A1A;
            graphics.fill(bx, by, bx + 6, sigY + 10, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - GUI_W) / 2;
        int y = (this.height - GUI_H) / 2;
        int state = menu.getData().get(0);

        if (state == TransponderBlockEntity.STATE_BROADCASTING || state == TransponderBlockEntity.STATE_PAUSED) {
            int btnX = x + 8;
            int btnY = y + GUI_H - 20;
            if (mouseX >= btnX && mouseX < btnX + BTN_W && mouseY >= btnY && mouseY < btnY + BTN_H) {
                Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, 0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderStatusRow(GuiGraphics graphics, int panelX, int panelY,
                                  int row, boolean ok, String okText, String failText) {
        int ry = panelY + ROW_START_Y + row * ROW_H;
        int rx = panelX + BAR_X;

        String indicator = ok ? "\u2714" : "\u2716";
        int indicatorColor = ok ? 0xFF22DD66 : 0xFFDD3322;
        graphics.drawString(font, indicator, rx + 2, ry + 1, indicatorColor, false);

        String text = ok ? okText : failText;
        int textColor = ok ? 0xFF88CCAA : 0xFFCC8866;
        graphics.drawString(font, text, rx + 14, ry + 1, textColor, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
