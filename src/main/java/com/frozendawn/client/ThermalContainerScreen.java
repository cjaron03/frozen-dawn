package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.item.ThermalContainerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Thermal Container GUI — dark tech style matching other Frozen Dawn UIs.
 */
public class ThermalContainerScreen extends AbstractContainerScreen<ThermalContainerMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/gui/thermal_container.png");

    public ThermalContainerScreen(ThermalContainerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        imageWidth = 176;
        imageHeight = 133;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 5, 0xDD8833, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF607080, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;

        // Dark panel background
        graphics.fill(x, y, x + w, y + h, 0xFF141A1F);

        // Border (warm metal frame)
        graphics.fill(x, y, x + w, y + 1, 0xFF604830);
        graphics.fill(x, y, x + 1, y + h, 0xFF604830);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF302418);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF302418);
        // Inner border
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF504028);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF504028);
        graphics.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF201810);
        graphics.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF201810);

        // Inner panel
        graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF101518);

        // Title bar
        graphics.fill(x + 3, y + 3, x + w - 3, y + 15, 0xFF1A2228);
        graphics.fill(x + 3, y + 15, x + w - 3, y + 16, 0xFF604830);

        // Draw slot backgrounds
        for (Slot slot : menu.slots) {
            drawSlotBg(graphics, x + slot.x - 1, y + slot.y - 1);
        }
    }

    private void drawSlotBg(GuiGraphics graphics, int x, int y) {
        // Dark slot border
        graphics.fill(x, y, x + 18, y + 1, 0xFF303840);
        graphics.fill(x, y + 1, x + 1, y + 17, 0xFF303840);
        // Light edge
        graphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFF1A2028);
        graphics.fill(x + 1, y + 17, x + 17, y + 18, 0xFF1A2028);
        // Inner slot area
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF202830);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
