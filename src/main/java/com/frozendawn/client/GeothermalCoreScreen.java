package com.frozendawn.client;

import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.GeothermalCoreMenu;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.O2TankItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Geothermal Core GUI — dark tech style with a side tab for upgrade guide.
 * Click the tab on the right edge to toggle the info panel.
 */
public class GeothermalCoreScreen extends AbstractContainerScreen<GeothermalCoreMenu> {

    private static final int GUI_W = 176;
    private static final int GUI_H = 212;

    private static final int BAR_LEFT = 48;
    private static final int BAR_W = 96;
    private static final int BAR_H = 10;

    private static final int TAB_W = 20;
    private static final int TAB_H = 60;
    private static final int GUIDE_W = 130;

    private boolean guideOpen = false;

    public GeothermalCoreScreen(GeothermalCoreMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        imageWidth = GUI_W;
        imageHeight = GUI_H;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tabX = leftPos + GUI_W - 1;
        int tabY = topPos + 18;
        if (mouseX >= tabX && mouseX < tabX + TAB_W && mouseY >= tabY && mouseY < tabY + TAB_H) {
            guideOpen = !guideOpen;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // All rendering done in renderBg/render with absolute coords
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // --- Main panel background ---
        drawPanel(graphics, x, y, GUI_W, GUI_H);

        // Title
        graphics.drawString(font, title, x + (GUI_W - font.width(title)) / 2, y + 5, 0xFFDD8833, false);

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

        // --- Slot backgrounds ---
        for (Slot slot : menu.slots) {
            drawSlotBg(graphics, x + slot.x - 1, y + slot.y - 1);
        }

        // --- Progress bars ---
        int barX = x + BAR_LEFT;

        // Range bar (blue)
        graphics.drawString(font, "Range", barX, y + 15, 0xFF6688AA, false);
        drawUpgradeBar(graphics, barX, y + 24, BAR_W, BAR_H,
                rangeLevel, GeothermalCoreBlockEntity.MAX_RANGE_LEVEL, 0xFF3388DD);
        String rangeText = effectiveRange + " blk" + (rangeMax ? " \u00A76MAX" : "");
        graphics.drawString(font, rangeText, barX + BAR_W + 4, y + 25, 0xFFE0E0E0, true);

        // Temp bar (orange)
        graphics.drawString(font, "Heat", barX, y + 39, 0xFF6688AA, false);
        drawUpgradeBar(graphics, barX, y + 48, BAR_W, BAR_H,
                tempLevel, GeothermalCoreBlockEntity.MAX_TEMP_LEVEL, 0xFFDD6622);
        String tempText = "+" + effectiveTemp + "\u00B0C" + (tempMax ? " \u00A76MAX" : "");
        graphics.drawString(font, tempText, barX + BAR_W + 4, y + 49, 0xFFE0E0E0, true);

        // O2 bar (green)
        graphics.drawString(font, "O2", barX, y + 63, 0xFF6688AA, false);
        drawUpgradeBar(graphics, barX, y + 72, BAR_W, BAR_H,
                o2Level, GeothermalCoreBlockEntity.MAX_O2_LEVEL, 0xFF22BB44);
        String o2Text = effectiveO2 + " blk" + (o2Max ? " \u00A76MAX" : "");
        graphics.drawString(font, o2Text, barX + BAR_W + 4, y + 73, 0xFFE0E0E0, true);

        // --- O2 Tank Refill Row ---
        graphics.fill(x + 4, y + 90, x + GUI_W - 4, y + 91, 0xFF604830);
        graphics.drawString(font, "O2 Refill", x + 8, y + 98, 0xFF00CCCC, false);
        // Show fill % when tank is in the slot
        Slot tankSlot = menu.slots.get(3);
        ItemStack tankStack = tankSlot.getItem();
        if (!tankStack.isEmpty() && tankStack.getItem() instanceof O2TankItem tankItem) {
            int tankO2 = tankStack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0);
            int percent = Math.round(100f * tankO2 / tankItem.getMaxO2());
            int pColor = percent >= 100 ? 0xFF00FF88 : 0xFF00CCCC;
            graphics.drawString(font, percent + "%", x + 102, y + 98, pColor, false);
        }

        // Divider above inventory
        graphics.fill(x + 4, y + 114, x + GUI_W - 4, y + 115, 0xFF604830);
        graphics.drawString(font, playerInventoryTitle, x + 8, y + inventoryLabelY, 0xFF607080, false);

        // --- Side tab button ---
        drawSideTab(graphics, x + GUI_W - 1, y + 18, mouseX, mouseY);

        // --- Guide panel ---
        if (guideOpen) {
            drawGuidePanel(graphics, x + GUI_W + TAB_W - 1, y);
        }
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF141A1F);
        // Outer border
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
        graphics.fill(x + 3, y + 3, x + w - 3, y + 14, 0xFF1A2228);
        graphics.fill(x + 3, y + 14, x + w - 3, y + 15, 0xFF604830);
    }

    private void drawSlotBg(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 1, 0xFF303840);
        graphics.fill(x, y + 1, x + 1, y + 17, 0xFF303840);
        graphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFF1A2028);
        graphics.fill(x + 1, y + 17, x + 17, y + 18, 0xFF1A2028);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF202830);
    }

    private void drawUpgradeBar(GuiGraphics graphics, int x, int y, int w, int h,
                                  int level, int maxLevel, int fillColor) {
        graphics.fill(x, y, x + w, y + h, 0xFF0A0C0F);
        if (level > 0 && maxLevel > 0) {
            int fillW = (int) (w * (float) level / maxLevel);
            graphics.fill(x, y, x + fillW, y + h, fillColor);
        }
        graphics.fill(x, y, x + w, y + 1, 0xFF303840);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF1A2028);
    }

    private void drawSideTab(GuiGraphics graphics, int tabX, int tabY, int mouseX, int mouseY) {
        boolean hovered = mouseX >= tabX && mouseX < tabX + TAB_W
                && mouseY >= tabY && mouseY < tabY + TAB_H;

        int bg = hovered ? 0xFF1E2830 : 0xFF161C22;
        int border = guideOpen ? 0xFFDD8833 : 0xFF604830;

        graphics.fill(tabX, tabY, tabX + TAB_W, tabY + TAB_H, bg);
        graphics.fill(tabX, tabY, tabX + TAB_W, tabY + 1, border);
        graphics.fill(tabX + TAB_W - 1, tabY, tabX + TAB_W, tabY + TAB_H, border);
        graphics.fill(tabX, tabY + TAB_H - 1, tabX + TAB_W, tabY + TAB_H, border);

        String icon = guideOpen ? "x" : "?";
        int iconColor = guideOpen ? 0xFFDD8833 : 0xFF99AABB;
        graphics.drawString(font, icon, tabX + (TAB_W - font.width(icon)) / 2,
                tabY + (TAB_H - 8) / 2, iconColor, false);
    }

    private void drawGuidePanel(GuiGraphics graphics, int px, int py) {
        int h = GUI_H;

        graphics.fill(px, py, px + GUIDE_W, py + h, 0xFF141A1F);
        graphics.fill(px, py, px + GUIDE_W, py + 1, 0xFF604830);
        graphics.fill(px + GUIDE_W - 1, py, px + GUIDE_W, py + h, 0xFF302418);
        graphics.fill(px, py + h - 1, px + GUIDE_W, py + h, 0xFF302418);
        graphics.fill(px, py, px + 1, py + h, 0xFF504028);
        graphics.fill(px + 2, py + 2, px + GUIDE_W - 2, py + h - 2, 0xFF101518);

        int tx = px + 6;
        int ty = py + 6;
        int lineH = 10;

        graphics.drawString(font, "UPGRADE GUIDE", tx, ty, 0xFFDD8833, false);
        ty += lineH + 2;
        graphics.fill(tx, ty, px + GUIDE_W - 6, ty + 1, 0xFF604830);
        ty += 5;

        // Range
        graphics.drawString(font, "\u00A7bRange", tx, ty, 0xFF3388DD, false);
        ty += lineH;
        graphics.drawString(font, "Base: 12 \u2192 Max: 32", tx + 2, ty, 0xFF778888, false);
        ty += lineH;
        graphics.drawString(font, "\u2022 Obsidian: +1 block", tx + 2, ty, 0xFF99AABB, false);
        ty += lineH;
        graphics.drawString(font, "\u2022 Diamond Block: +4", tx + 2, ty, 0xFF99AABB, false);
        ty += lineH + 6;

        // Temperature
        graphics.drawString(font, "\u00A76Temperature", tx, ty, 0xFFDD6622, false);
        ty += lineH;
        graphics.drawString(font, "+50 \u2192 +100\u00B0C", tx + 2, ty, 0xFF778888, false);
        ty += lineH;
        graphics.drawString(font, "\u2022 Blaze Powder: +5\u00B0C", tx + 2, ty, 0xFF99AABB, false);
        ty += lineH;
        graphics.drawString(font, "\u2022 Thermal Core: +10\u00B0C", tx + 2, ty, 0xFF99AABB, false);
        ty += lineH + 6;

        // O2
        graphics.drawString(font, "\u00A7aO2 Zone", tx, ty, 0xFF22BB44, false);
        ty += lineH;
        graphics.drawString(font, "3 levels (Nether Star)", tx + 2, ty, 0xFF778888, false);
        ty += lineH;
        graphics.drawString(font, "16 \u2192 20 \u2192 26 \u2192 32 blk", tx + 2, ty, 0xFF99AABB, false);
        ty += lineH + 6;

        // O2 Tank Refill
        graphics.drawString(font, "\u00A73O2 Tank Refill", tx, ty, 0xFF00CCCC, false);
        ty += lineH;
        graphics.drawString(font, "Place tank in slot", tx + 2, ty, 0xFF778888, false);
        ty += lineH;
        graphics.drawString(font, "\u2022 Auto-refills over time", tx + 2, ty, 0xFF99AABB, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Slot hover tooltips (upgrade hints)
        var data = menu.getData();
        boolean rangeMax = data.get(0) >= GeothermalCoreBlockEntity.MAX_RANGE_LEVEL;
        boolean tempMax = data.get(1) >= GeothermalCoreBlockEntity.MAX_TEMP_LEVEL;
        boolean o2Max = data.get(2) >= GeothermalCoreBlockEntity.MAX_O2_LEVEL;

        int[][] slotPositions = {{26, 22}, {26, 46}, {26, 70}};
        for (int i = 0; i < 3; i++) {
            int sx = leftPos + slotPositions[i][0];
            int sy = topPos + slotPositions[i][1];
            if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                List<Component> tooltip = new ArrayList<>();
                switch (i) {
                    case 0 -> {
                        tooltip.add(Component.literal("\u00A7bRange Upgrade"));
                        if (rangeMax) tooltip.add(Component.literal("\u00A76Fully upgraded!"));
                        else {
                            tooltip.add(Component.literal("\u00A77Obsidian \u00A7f\u2192 +1 block"));
                            tooltip.add(Component.literal("\u00A77Diamond Block \u00A7f\u2192 +4 blocks"));
                        }
                    }
                    case 1 -> {
                        tooltip.add(Component.literal("\u00A76Temperature Upgrade"));
                        if (tempMax) tooltip.add(Component.literal("\u00A76Fully upgraded!"));
                        else {
                            tooltip.add(Component.literal("\u00A77Blaze Powder \u00A7f\u2192 +5\u00B0C"));
                            tooltip.add(Component.literal("\u00A77Thermal Core \u00A7f\u2192 +10\u00B0C"));
                        }
                    }
                    case 2 -> {
                        tooltip.add(Component.literal("\u00A7aO2 Production Upgrade"));
                        if (o2Max) tooltip.add(Component.literal("\u00A76Fully upgraded!"));
                        else tooltip.add(Component.literal("\u00A77Nether Star \u00A7f\u2192 +1 level"));
                    }
                }
                graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                break;
            }
        }

        // O2 tank refill slot tooltip
        int tankSx = leftPos + 80;
        int tankSy = topPos + 94;
        if (mouseX >= tankSx && mouseX < tankSx + 18 && mouseY >= tankSy && mouseY < tankSy + 18) {
            ItemStack tankStack = menu.slots.get(3).getItem();
            if (tankStack.isEmpty()) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("\u00A73O2 Tank Refill"));
                tooltip.add(Component.literal("\u00A77Place an O2 Tank here to refill"));
                graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            }
        }

        renderTooltip(graphics, mouseX, mouseY);
    }
}
