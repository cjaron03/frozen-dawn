package com.frozendawn.client;

import com.frozendawn.block.IsotopeColumnBlockEntity;
import com.frozendawn.block.IsotopeColumnMenu;
import com.frozendawn.power.IsotopeColumnLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Input, progress arrow and output, with the ice buffer gauge on the left and one status line. */
public final class IsotopeColumnScreen extends AbstractContainerScreen<IsotopeColumnMenu> {
    private static final int ARROW = 0xFF4C5961, FILL = 0xFF7FD3E8, ICE = 0xFFB8E6F2;

    public IsotopeColumnScreen(IsotopeColumnMenu m, Inventory inv, Component title) { super(m, inv, title); imageWidth = 176; imageHeight = 166; inventoryLabelY = 73; }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos; var data = menu.data();
        g.fill(x, y, x + 176, y + 166, 0xFF28353D);
        for (var s : menu.slots) g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, 0xFF152128);
        arrow(g, x + 68, y + 38, 40, ARROW);
        int total = Math.max(1, data.get(1)), fill = 40 * Math.clamp(data.get(0), 0, total) / total;
        g.enableScissor(x + 68, y + 38, x + 68 + fill, y + 50); arrow(g, x + 68, y + 38, 40, FILL); g.disableScissor();
        // The buffer gauge shows progress toward the next canister's worth of ice.
        int units = data.get(3), need = IsotopeColumnLogic.ICE_PER_CANISTER;
        g.fill(x + 16, y + 20, x + 26, y + 70, 0xFF152128);
        int h = 48 * Math.min(units, need) / need; g.fill(x + 17, y + 69 - h, x + 25, y + 69, ICE);
        int status = data.get(2); int kw = IsotopeColumnLogic.DRAW_WATTS / 1000;
        var text = switch (status) {
            case IsotopeColumnBlockEntity.WORKING -> Component.translatable("screen.frozendawn.isotope_column.working", kw);
            case IsotopeColumnBlockEntity.NO_POWER -> Component.translatable("screen.frozendawn.isotope_column.no_power", kw);
            case IsotopeColumnBlockEntity.OUTPUT_FULL -> Component.translatable("screen.frozendawn.isotope_column.output_full");
            default -> Component.translatable("screen.frozendawn.isotope_column.idle");
        };
        int colour = status == IsotopeColumnBlockEntity.WORKING ? 0xFF9CD5A5 : status == IsotopeColumnBlockEntity.NO_POWER ? 0xFFE5A064 : 0xFF9BAAB1;
        g.drawString(font, text, x + 100 - font.width(text) / 2, y + 58, colour, false);
        var buffer = Component.translatable("screen.frozendawn.isotope_column.buffer", units, need);
        g.drawString(font, buffer, x + 100 - font.width(buffer) / 2, y + 22, 0xFF9BAAB1, false);
    }
    private static void arrow(GuiGraphics g, int x, int y, int len, int colour) {
        g.fill(x, y + 3, x + len - 5, y + 9, colour);
        for (int n = 0; n < 5; n++) g.fill(x + len - 5 + n, y + n, x + len - 4 + n, y + 12 - n, colour);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, 0xFFE0E6E8, false); g.drawString(font, playerInventoryTitle, 8, 73, 0xFF9BAAB1, false);
        int job = menu.data().get(4);
        if (job > 0) {
            var label = Component.translatable("screen.frozendawn.isotope_column.job." + (job == 1 ? "deuterium" : "lithium"));
            g.drawString(font, label, 100 - font.width(label) / 2, 32 - 1 - 9 + 1, 0xFF7FD3E8, false);
        }
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) { super.render(g, mx, my, partial); renderTooltip(g, mx, my); }
}
