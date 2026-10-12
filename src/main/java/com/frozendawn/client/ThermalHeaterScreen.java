package com.frozendawn.client;

import com.frozendawn.block.ThermalHeaterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Simplified status screen for Thermal Heaters.
 * Shows burn time bar, shelter status, and fuel hint.
 * Detailed diagnostics are available via the ORSA MultiTool.
 */
public class ThermalHeaterScreen extends AbstractContainerScreen<ThermalHeaterMenu> {

    private static final int GUI_W = 176;
    private static final int GUI_H = 116;

    public ThermalHeaterScreen(ThermalHeaterMenu menu, Inventory playerInv, Component title) {
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
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, 0xE09040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Dark panel background
        graphics.fill(x, y, x + GUI_W, y + GUI_H, 0xFF141A1F);

        // Border (warm metal frame)
        graphics.fill(x, y, x + GUI_W, y + 1, 0xFF604830);
        graphics.fill(x, y, x + 1, y + GUI_H, 0xFF604830);
        graphics.fill(x, y + GUI_H - 1, x + GUI_W, y + GUI_H, 0xFF302418);
        graphics.fill(x + GUI_W - 1, y, x + GUI_W, y + GUI_H, 0xFF302418);
        // Inner border
        graphics.fill(x + 1, y + 1, x + GUI_W - 1, y + 2, 0xFF504028);
        graphics.fill(x + 1, y + 1, x + 2, y + GUI_H - 1, 0xFF504028);
        graphics.fill(x + 2, y + GUI_H - 2, x + GUI_W - 1, y + GUI_H - 1, 0xFF201810);
        graphics.fill(x + GUI_W - 2, y + 2, x + GUI_W - 1, y + GUI_H - 1, 0xFF201810);

        // Inner panel
        graphics.fill(x + 3, y + 3, x + GUI_W - 3, y + GUI_H - 3, 0xFF101518);

        // Title bar
        graphics.fill(x + 3, y + 3, x + GUI_W - 3, y + 15, 0xFF1A2228);
        graphics.fill(x + 3, y + 15, x + GUI_W - 3, y + 16, 0xFF604830);

        var data = menu.getData();
        int etaMinutes = data.get(0);
        boolean isLit = data.get(1) != 0;
        boolean sheltered = data.get(2) != 0;
        boolean industrialDrain = data.get(3) != 0;
        int burnPercent = data.get(4);
        boolean redstoneOff = data.get(5) != 0;
        int mode = data.get(6);
        int temperature = data.get(7);
        int target = data.get(8);

        // --- Burn time bar ---
        int barX = x + 8;
        int barY = y + 22;
        int barW = 160;
        int barH = 16;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF0A0C0F);

        if (isLit) {
            int barColor;
            if (etaMinutes > 60) barColor = 0xFF22AA44;
            else if (etaMinutes > 10) barColor = 0xFFDDAA22;
            else barColor = 0xFFDD3322;

            int fillW = Math.min(barW, (int) (barW * Math.min(etaMinutes, 120) / 120f));
            graphics.fill(barX, barY, barX + fillW, barY + barH, barColor);

            String etaText;
            if (burnPercent == 0) {
                etaText = "Fuel held";
            } else if (etaMinutes >= 60) {
                etaText = (etaMinutes / 60) + "h " + (etaMinutes % 60) + "m est.";
            } else {
                etaText = etaMinutes + " min est.";
            }
            int textW = font.width(etaText);
            graphics.drawString(font, etaText, barX + (barW - textW) / 2, barY + 4, 0xFFE0E0E0, true);
        } else {
            String offText = redstoneOff ? "REDSTONE OFF" : "NO FUEL";
            int textW = font.width(offText);
            graphics.drawString(font, offText, barX + (barW - textW) / 2, barY + 4, 0xFF888888, true);
        }

        // --- Shelter indicator ---
        int indicatorY = y + 44;
        String icon = sheltered ? "\u2714" : "\u2716";
        int iconColor = sheltered ? 0xFF22AA44 : 0xFFDD3322;
        String shelterText = sheltered ? "Sheltered" : "Exposed";
        graphics.drawString(font, icon, x + 10, indicatorY, iconColor, false);
        graphics.drawString(font, shelterText, x + 22, indicatorY, sheltered ? 0xFFAABBAA : 0xFFCC6644, false);

        String status = redstoneOff ? "Paused by redstone" : !isLit ? "Off" :
                (mode != 0 && temperature >= target*10-5 ? "Holding " : "Heating ") + burnPercent + "%";
        graphics.drawString(font, status, x + 10, y + 58, 0xFFE0C090, false);
        String reading = mode == 0 || temperature == -32768 ? "Open camp | Target "+target+"C" :
                String.format(java.util.Locale.ROOT,"%s %.1fC | Target %dC",mode >= 3 ? "Sensor" : mode == 2 ? "Walls" : "Air",temperature/10.0,target);
        graphics.drawString(font, reading, x + 10, y + 70, 0xFFAABBAA, false);
        if (industrialDrain) {
            int warningY = y + 84;
            String warning = "! Silo draw: increased drain";
            graphics.fill(x + 7, warningY - 2, x + GUI_W - 7, warningY + 10, 0x552A1C08);
            graphics.drawString(font, warning, x + 12, warningY, 0xFFFFB84A, false);
        }

        // --- Fuel hint ---
        String hint = "Right-click with fuel to refuel";
        int hintW = font.width(hint);
        graphics.drawString(font, hint, x + (GUI_W - hintW) / 2, y + GUI_H - 16, 0xFF506070, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
