package com.frozendawn.client;

import com.frozendawn.item.ThermalContainerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Thermal Container GUI — programmatic rendering to match vanilla pixel-perfect.
 * No texture file needed; drawn entirely with fill() calls.
 */
public class ThermalContainerScreen extends AbstractContainerScreen<ThermalContainerMenu> {

    public ThermalContainerScreen(ThermalContainerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        imageWidth = 176;
        imageHeight = 133;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;

        // Main background
        graphics.fill(x, y, x + w, y + h, 0xFFC6C6C6);

        // 3D border — top/left highlight
        graphics.fill(x, y, x + w - 1, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + h - 1, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 2, 0xFFFFFFFF);

        // 3D border — bottom/right shadow
        graphics.fill(x + 1, y + h - 1, x + w, y + h, 0xFF555555);
        graphics.fill(x + w - 1, y + 1, x + w, y + h, 0xFF555555);
        graphics.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF8B8B8B);
        graphics.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF8B8B8B);

        // Draw all slot backgrounds
        for (Slot slot : menu.slots) {
            drawSlotBg(graphics, x + slot.x - 1, y + slot.y - 1);
        }
    }

    private void drawSlotBg(GuiGraphics graphics, int x, int y) {
        // Top/left dark border
        graphics.fill(x, y, x + 18, y + 1, 0xFF373737);
        graphics.fill(x, y + 1, x + 1, y + 17, 0xFF373737);
        // Bottom/right light border
        graphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 17, x + 17, y + 18, 0xFFFFFFFF);
        // Inner slot area
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
