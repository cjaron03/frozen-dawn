package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.GeothermalCoreMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class GeothermalCoreScreen extends AbstractContainerScreen<GeothermalCoreMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/gui/geothermal_core.png");

    // Slot positions (must match GeothermalCoreMenu)
    private static final int SLOT_X = 26;
    private static final int[][] SLOT_ROWS = {
            {SLOT_X, 22},  // Range
            {SLOT_X, 46},  // Temp
            {SLOT_X, 70},  // O2
    };

    public GeothermalCoreScreen(GeothermalCoreMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        imageWidth = 176;
        imageHeight = 184;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        var data = menu.getData();
        int rangeLevel = data.get(0);
        int tempLevel = data.get(1);
        int o2Level = data.get(2);

        int barX = leftPos + 48;
        int barW = 96;
        int barH = 8;

        // Range bar (blue)
        int rangeWidth = maxLevel(rangeLevel, GeothermalCoreBlockEntity.MAX_RANGE_LEVEL, barW);
        graphics.fill(barX, topPos + 25, barX + barW, topPos + 25 + barH, 0xFF2A2A2A); // bg
        graphics.fill(barX, topPos + 25, barX + rangeWidth, topPos + 25 + barH, 0xFF3388DD);

        // Temp bar (orange)
        int tempWidth = maxLevel(tempLevel, GeothermalCoreBlockEntity.MAX_TEMP_LEVEL, barW);
        graphics.fill(barX, topPos + 49, barX + barW, topPos + 49 + barH, 0xFF2A2A2A);
        graphics.fill(barX, topPos + 49, barX + tempWidth, topPos + 49 + barH, 0xFFDD6622);

        // O2 bar (green)
        int o2Width = maxLevel(o2Level, GeothermalCoreBlockEntity.MAX_O2_LEVEL, barW);
        graphics.fill(barX, topPos + 73, barX + barW, topPos + 73 + barH, 0xFF2A2A2A);
        graphics.fill(barX, topPos + 73, barX + o2Width, topPos + 73 + barH, 0xFF22BB44);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        var data = menu.getData();
        int rangeLevel = data.get(0);
        int tempLevel = data.get(1);
        int o2Level = data.get(2);

        int effectiveRange = GeothermalCoreBlockEntity.BASE_RANGE + rangeLevel;
        int effectiveTemp = (int) (GeothermalCoreBlockEntity.BASE_TEMP + tempLevel * 5.0f);
        int effectiveO2 = switch (o2Level) {
            case 1 -> 20; case 2 -> 26; case 3 -> GeothermalCoreBlockEntity.MAX_O2_RANGE;
            default -> GeothermalCoreBlockEntity.BASE_O2_RANGE;
        };

        boolean rangeMax = rangeLevel >= GeothermalCoreBlockEntity.MAX_RANGE_LEVEL;
        boolean tempMax = tempLevel >= GeothermalCoreBlockEntity.MAX_TEMP_LEVEL;
        boolean o2Max = o2Level >= GeothermalCoreBlockEntity.MAX_O2_LEVEL;

        // Row labels (above each bar)
        String rangeText = "Range: " + effectiveRange + " blocks" + (rangeMax ? " \u00A76MAX" : "");
        String tempText = "Heat: +" + effectiveTemp + "\u00B0C" + (tempMax ? " \u00A76MAX" : "");
        String o2Text = "O\u2082 Zone: " + effectiveO2 + " blocks" + (o2Max ? " \u00A76MAX" : "");

        graphics.drawString(font, rangeText, leftPos + 48, topPos + 15, 0xE0E0E0, true);
        graphics.drawString(font, tempText, leftPos + 48, topPos + 39, 0xE0E0E0, true);
        graphics.drawString(font, o2Text, leftPos + 48, topPos + 63, 0xE0E0E0, true);

        // Small hint labels left of slots
        graphics.drawString(font, "\u00A77\u00A7oR", leftPos + 15, topPos + 26, 0x888888, false);
        graphics.drawString(font, "\u00A77\u00A7oT", leftPos + 15, topPos + 50, 0x888888, false);
        graphics.drawString(font, "\u00A77\u00A7oO", leftPos + 14, topPos + 74, 0x888888, false);

        // Slot hover tooltips
        renderSlotTooltips(graphics, mouseX, mouseY, rangeMax, tempMax, o2Max);

        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderSlotTooltips(GuiGraphics graphics, int mouseX, int mouseY,
                                     boolean rangeMax, boolean tempMax, boolean o2Max) {
        // Check if mouse is over each slot (18x18 area)
        for (int i = 0; i < 3; i++) {
            int sx = leftPos + SLOT_ROWS[i][0];
            int sy = topPos + SLOT_ROWS[i][1];
            if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                List<Component> tooltip = new ArrayList<>();
                switch (i) {
                    case 0 -> {
                        tooltip.add(Component.literal("\u00A7bRange Upgrade"));
                        if (rangeMax) {
                            tooltip.add(Component.literal("\u00A76Fully upgraded!"));
                        } else {
                            tooltip.add(Component.literal("\u00A77Obsidian \u00A7f\u2192 +1 block"));
                            tooltip.add(Component.literal("\u00A77Diamond Block \u00A7f\u2192 +4 blocks"));
                        }
                    }
                    case 1 -> {
                        tooltip.add(Component.literal("\u00A76Temperature Upgrade"));
                        if (tempMax) {
                            tooltip.add(Component.literal("\u00A76Fully upgraded!"));
                        } else {
                            tooltip.add(Component.literal("\u00A77Blaze Powder \u00A7f\u2192 +5\u00B0C"));
                            tooltip.add(Component.literal("\u00A77Thermal Core \u00A7f\u2192 +10\u00B0C"));
                        }
                    }
                    case 2 -> {
                        tooltip.add(Component.literal("\u00A7aO\u2082 Production Upgrade"));
                        if (o2Max) {
                            tooltip.add(Component.literal("\u00A76Fully upgraded!"));
                        } else {
                            tooltip.add(Component.literal("\u00A77Nether Star \u00A7f\u2192 +1 level"));
                        }
                    }
                }
                graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    private static int maxLevel(int level, int max, int barWidth) {
        return max > 0 ? (int) (barWidth * (float) level / max) : 0;
    }
}
