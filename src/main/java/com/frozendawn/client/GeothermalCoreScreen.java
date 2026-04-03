package com.frozendawn.client;

import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.GeothermalCoreMenu;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.O2TankItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Geothermal Core GUI — dark tech style with a side tab for upgrade guide.
 * Click the tab on the right edge to toggle the info panel.
 */
public class GeothermalCoreScreen extends AbstractContainerScreen<GeothermalCoreMenu> {

    private static final int GUI_W = 196;
    private static final int GUI_H = 212;

    private static final int BAR_LEFT = 48;
    private static final int BAR_W = 86;
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

    public List<Rect2i> getJeiExtraAreas() {
        List<Rect2i> extraAreas = new ArrayList<>();
        extraAreas.add(new Rect2i(leftPos + GUI_W - 1, topPos + 18, TAB_W, TAB_H));
        if (guideOpen) {
            extraAreas.add(new Rect2i(leftPos + GUI_W + TAB_W - 1, topPos, GUIDE_W, GUI_H));
        }
        return Collections.unmodifiableList(extraAreas);
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
        BlockPos corePos = menu.getCorePos();
        boolean hasSurfacePenalty = corePos != null && GeothermalCoreBlockEntity.hasSurfaceWarmthPenalty(corePos);

        float effectiveRange = GeothermalCoreBlockEntity.BASE_RANGE + rangeLevel;
        float effectiveTemp = GeothermalCoreBlockEntity.BASE_TEMP + tempLevel * 5.0f;
        if (corePos != null) {
            effectiveRange = GeothermalCoreBlockEntity.applySurfaceWarmthPenalty(effectiveRange, corePos);
            effectiveTemp = GeothermalCoreBlockEntity.applySurfaceWarmthPenalty(effectiveTemp, corePos);
        }
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

        // --- Stat rows ---
        int statX = x + 48;
        int statW = GUI_W - 60;
        int rowH = 16;
        int warmthNoticeY = y + 20;
        int warmthNoticeH = 8;
        int row1Y = hasSurfacePenalty ? y + 31 : y + 22;
        int row2Y = row1Y + 18;
        int row3Y = row2Y + 18;

        int rangeFillW = GeothermalCoreBlockEntity.MAX_RANGE_LEVEL > 0
                ? (int) ((statW - 2) * (float) rangeLevel / GeothermalCoreBlockEntity.MAX_RANGE_LEVEL)
                : 0;
        if (hasSurfacePenalty) {
            String penaltyText = "Surface warmth -50%";
            float penaltyScale = 0.75f;
            int scaledPenaltyWidth = Math.round(font.width(penaltyText) * penaltyScale);
            graphics.fill(statX, warmthNoticeY, statX + statW, warmthNoticeY + warmthNoticeH, 0xFF241C14);
            graphics.fill(statX, warmthNoticeY + warmthNoticeH - 1, statX + statW, warmthNoticeY + warmthNoticeH, 0xFF604830);
            graphics.pose().pushPose();
            graphics.pose().scale(penaltyScale, penaltyScale, 1.0f);
            graphics.drawString(font, penaltyText,
                    Math.round((statX + (statW - scaledPenaltyWidth) / 2.0f) / penaltyScale),
                    Math.round((warmthNoticeY + 1) / penaltyScale),
                    0xFFCCAA66,
                    false);
            graphics.pose().popPose();
        }

        graphics.fill(statX, row1Y, statX + statW, row1Y + rowH, 0xFF10181D);
        graphics.fill(statX, row1Y + rowH - 1, statX + statW, row1Y + rowH, 0xFF182028);
        graphics.fill(statX + 1, row1Y + rowH - 3, statX + 1 + rangeFillW, row1Y + rowH - 1, 0xFF3388DD);
        String rangeText = formatDisplayValue(effectiveRange) + " blk" + (rangeMax ? " \u00A76MAX" : "");
        graphics.drawString(font, "Range", statX + 6, row1Y + 4, 0xFF66B5E8, false);
        graphics.drawString(font, rangeText, statX + statW - 6 - font.width(rangeText), row1Y + 4, 0xFFE0E0E0, false);

        int tempFillW = GeothermalCoreBlockEntity.MAX_TEMP_LEVEL > 0
                ? (int) ((statW - 2) * (float) tempLevel / GeothermalCoreBlockEntity.MAX_TEMP_LEVEL)
                : 0;
        graphics.fill(statX, row2Y, statX + statW, row2Y + rowH, 0xFF10181D);
        graphics.fill(statX, row2Y + rowH - 1, statX + statW, row2Y + rowH, 0xFF182028);
        graphics.fill(statX + 1, row2Y + rowH - 3, statX + 1 + tempFillW, row2Y + rowH - 1, 0xFFDD6622);
        String tempText = "+" + formatDisplayValue(effectiveTemp) + "\u00B0C" + (tempMax ? " \u00A76MAX" : "");
        graphics.drawString(font, "Heat", statX + 6, row2Y + 4, 0xFFFFA366, false);
        graphics.drawString(font, tempText, statX + statW - 6 - font.width(tempText), row2Y + 4, 0xFFE0E0E0, false);

        int o2FillW = GeothermalCoreBlockEntity.MAX_O2_LEVEL > 0
                ? (int) ((statW - 2) * (float) o2Level / GeothermalCoreBlockEntity.MAX_O2_LEVEL)
                : 0;
        graphics.fill(statX, row3Y, statX + statW, row3Y + rowH, 0xFF10181D);
        graphics.fill(statX, row3Y + rowH - 1, statX + statW, row3Y + rowH, 0xFF182028);
        graphics.fill(statX + 1, row3Y + rowH - 3, statX + 1 + o2FillW, row3Y + rowH - 1, 0xFF22BB44);
        String o2Text = effectiveO2 + " blk" + (o2Max ? " \u00A76MAX" : "");
        graphics.drawString(font, "O2", statX + 6, row3Y + 4, 0xFF6BE87A, false);
        graphics.drawString(font, o2Text, statX + statW - 6 - font.width(o2Text), row3Y + 4, 0xFFE0E0E0, false);

        // --- O2 Tank Refill Row ---
        graphics.fill(x + 4, y + 93, x + GUI_W - 4, y + 94, 0xFF604830);
        graphics.drawString(font, "O2 Refill", x + 8, y + 101, 0xFF00CCCC, false);
        // Show fill % when tank is in the slot
        Slot tankSlot = menu.slots.get(3);
        ItemStack tankStack = tankSlot.getItem();
        if (!tankStack.isEmpty() && tankStack.getItem() instanceof O2TankItem tankItem) {
            int tankO2 = tankStack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0);
            int percent = Math.round(100f * tankO2 / tankItem.getMaxO2());
            int pColor = percent >= 100 ? 0xFF00FF88 : 0xFF00CCCC;
            graphics.drawString(font, percent + "%", x + 102, y + 101, pColor, false);
        }

        // Divider above inventory
        graphics.fill(x + 4, y + 117, x + GUI_W - 4, y + 118, 0xFF604830);
        graphics.drawString(font, playerInventoryTitle, x + 8, y + inventoryLabelY, 0xFF607080, false);

        // --- Side tab button ---
        drawSideTab(graphics, x + GUI_W - 1, y + 18, mouseX, mouseY);

        // --- Guide panel ---
        if (guideOpen) {
            drawGuidePanel(graphics, x + GUI_W + TAB_W - 1, y, hasSurfacePenalty);
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

    private void drawGuidePanel(GuiGraphics graphics, int px, int py, boolean hasSurfacePenalty) {
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
        if (hasSurfacePenalty) {
            graphics.drawString(font, "Surface warmth: 50%", tx, ty, 0xFFCCAA66, false);
            ty += lineH + 4;
        }

        // Range
        graphics.drawString(font, "\u00A7bRange", tx, ty, 0xFF3388DD, false);
        ty += lineH;
        graphics.drawString(font, "Below 0: 12 \u2192 32", tx + 2, ty, 0xFF778888, false);
        ty += lineH;
        graphics.drawString(font, "\u2022 Obsidian: +1 block", tx + 2, ty, 0xFF99AABB, false);
        ty += lineH;
        graphics.drawString(font, "\u2022 Diamond Block: +4", tx + 2, ty, 0xFF99AABB, false);
        ty += lineH + 6;

        // Temperature
        graphics.drawString(font, "\u00A76Temperature", tx, ty, 0xFFDD6622, false);
        ty += lineH;
        graphics.drawString(font, "Below 0: +50 \u2192 +100\u00B0C", tx + 2, ty, 0xFF778888, false);
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

    private static String formatDisplayValue(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int getSurfacePenaltyPercent() {
        return Math.round((1.0f - GeothermalCoreBlockEntity.SURFACE_WARMTH_MULTIPLIER) * 100.0f);
    }
}
