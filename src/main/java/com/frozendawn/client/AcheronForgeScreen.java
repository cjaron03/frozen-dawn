package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheronForgeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class AcheronForgeScreen extends AbstractContainerScreen<AcheronForgeMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/gui/acheron_forge.png");

    private static final int PROGRESS_BAR_X = 73;
    private static final int PROGRESS_BAR_Y = 35;
    private static final int PROGRESS_BAR_W = 24;
    private static final int PROGRESS_BAR_H = 16;

    public AcheronForgeScreen(AcheronForgeMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 5, 0x40A0B4, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x607080, false);

        // Slot labels
        graphics.drawString(font, "IN", 58, 27, 0x406878, false);
        graphics.drawString(font, "OUT", 116, 27, 0x40A0B4, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        var data = menu.getData();
        int progress = data.get(0);
        boolean hasHeat = data.get(1) != 0;
        boolean isDeep = data.get(2) != 0;

        // Progress arrow (fills left to right)
        if (progress > 0) {
            int arrowWidth = (int) (PROGRESS_BAR_W * (progress / 200.0f));
            graphics.blit(TEXTURE, leftPos + PROGRESS_BAR_X, topPos + PROGRESS_BAR_Y,
                    176, 0, arrowWidth, PROGRESS_BAR_H);
        }

        // Status text below the slots
        int statusY = topPos + 58;
        if (!isDeep) {
            String msg = "\u2716 Place below Y=0";
            int w = font.width(msg);
            graphics.drawString(font, msg, leftPos + (imageWidth - w) / 2, statusY, 0xFF5555, false);
        } else if (!hasHeat) {
            String msg = "\u2716 No heat source nearby";
            int w = font.width(msg);
            graphics.drawString(font, msg, leftPos + (imageWidth - w) / 2, statusY, 0xFF5555, false);
        } else if (progress > 0) {
            String msg = "\u2714 Refining...";
            int w = font.width(msg);
            graphics.drawString(font, msg, leftPos + (imageWidth - w) / 2, statusY, 0x55FFAA, false);
        } else {
            String msg = "\u2714 Ready";
            int w = font.width(msg);
            graphics.drawString(font, msg, leftPos + (imageWidth - w) / 2, statusY, 0x40A0B4, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        var data = menu.getData();
        boolean hasHeat = data.get(1) != 0;
        boolean isDeep = data.get(2) != 0;

        // Tooltip on progress arrow area
        if (mouseX >= leftPos + PROGRESS_BAR_X && mouseX < leftPos + PROGRESS_BAR_X + PROGRESS_BAR_W
                && mouseY >= topPos + PROGRESS_BAR_Y && mouseY < topPos + PROGRESS_BAR_Y + PROGRESS_BAR_H) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("\u00A77Shard \u2192 Refined Acheronite"));
            if (!isDeep) {
                tooltip.add(Component.literal("\u00A7c\u2716 Must be below Y=0"));
            } else {
                tooltip.add(Component.literal("\u00A7a\u2714 Deep enough"));
            }
            if (!hasHeat) {
                tooltip.add(Component.literal("\u00A7c\u2716 Geothermal Core within 8 blocks"));
            } else {
                tooltip.add(Component.literal("\u00A7a\u2714 Heat source detected"));
            }
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }

        renderTooltip(graphics, mouseX, mouseY);
    }
}
