package com.frozendawn.client;

import com.frozendawn.block.PlumbLineBlockEntity;
import com.frozendawn.block.PlumbLineMenu;
import com.frozendawn.gpr.PlumbLinePolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Setting, pulse, power and the last pulse's returns. */
public final class PlumbLineScreen extends AbstractContainerScreen<PlumbLineMenu> {
    private static final int W = 232, H = 190;
    private Button survey, deep, pulse;
    public PlumbLineScreen(PlumbLineMenu m, Inventory i, Component t) { super(m, i, t); imageWidth = W; imageHeight = H; }

    @Override protected void init() {
        super.init();
        survey = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.plumb_line.survey"), b -> press(0)).bounds(leftPos + 8, topPos + 22, 70, 18).build());
        deep = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.plumb_line.deep"), b -> press(1)).bounds(leftPos + 81, topPos + 22, 70, 18).build());
        pulse = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.plumb_line.pulse"), b -> press(2)).bounds(leftPos + 154, topPos + 22, 70, 18).build());
    }
    private void press(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private int get(int i) { return menu.data().get(i); }
    private PlumbLinePolicy.Setting setting() { return PlumbLinePolicy.Setting.byId(get(PlumbLineMenu.SETTING)); }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        boolean busy = get(PlumbLineMenu.PULSING) == 1;
        survey.active = !busy && setting() != PlumbLinePolicy.Setting.SURVEY;
        deep.active = !busy && setting() != PlumbLinePolicy.Setting.DEEP;
        pulse.active = !busy && get(PlumbLineMenu.COOLDOWN) == 0 && get(PlumbLineMenu.AVAILABLE_KW) >= setting().watts / 1000;
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + W, y + H, 0xFF28353D); g.fill(x + 7, y + 44, x + W - 7, y + H - 7, 0xFF152128);
        var s = setting();
        line(g, Component.translatable("screen.frozendawn.plumb_line.setting", Component.translatable("screen.frozendawn.plumb_line." + s.name().toLowerCase(java.util.Locale.ROOT)), s.watts / 1000, s.radius), 50, 0xFFE0E6E8);
        boolean ready = get(PlumbLineMenu.AVAILABLE_KW) >= s.watts / 1000;
        line(g, Component.translatable(ready ? "screen.frozendawn.plumb_line.power.ok" : "screen.frozendawn.plumb_line.power.short", s.watts / 1000), 62, ready ? 0xFF9CD5A5 : 0xFFE5A064);
        int cooldown = get(PlumbLineMenu.COOLDOWN);
        if (get(PlumbLineMenu.PULSING) == 1) {
            int done = Math.min(10, get(PlumbLineMenu.ELAPSED));
            line(g, Component.translatable("screen.frozendawn.plumb_line.pulsing", done), 74, 0xFFE5A064);
            g.fill(x + 12, y + 86, x + W - 12, y + 91, 0xFF39464F);
            g.fill(x + 12, y + 86, x + 12 + (W - 24) * done / 10, y + 91, 0xFFE5A064);
        } else line(g, cooldown > 0 ? Component.translatable("screen.frozendawn.plumb_line.cooldown", cooldown) : Component.translatable("screen.frozendawn.plumb_line.ready"), 74, cooldown > 0 ? 0xFF9BAAB1 : 0xFF64C9CC);
        int result = get(PlumbLineMenu.RESULT);
        int y2 = 98;
        if (result == PlumbLineBlockEntity.RESULT_DONE) {
            y2 = wrap(g, Component.translatable("screen.frozendawn.plumb_line.last", get(PlumbLineMenu.ORES), get(PlumbLineMenu.VOIDS), get(PlumbLineMenu.STRUCTURES), get(PlumbLineMenu.METALS)), y2, 0xFFE0E6E8);
            y2 = wrap(g, Component.translatable("screen.frozendawn.plumb_line.view"), y2 + 2, 0xFF9BAAB1);
        } else if (result != PlumbLineBlockEntity.RESULT_NONE)
            y2 = wrap(g, Component.translatable("screen.frozendawn.plumb_line.result." + result), y2, 0xFFE58B83);
        wrap(g, Component.translatable("screen.frozendawn.plumb_line.hint"), Math.max(y2 + 6, 136), 0xFF6F7C83);
    }
    /** Wraps to the panel width and returns the y below the last line. */
    private int wrap(GuiGraphics g, Component c, int y, int color) {
        for (var row : font.split(c, W - 24)) { g.drawString(font, row, leftPos + 12, topPos + y, color, false); y += 10; }
        return y;
    }
    private void line(GuiGraphics g, Component c, int y, int color) { g.drawString(font, font.plainSubstrByWidth(c.getString(), W - 24), leftPos + 12, topPos + y, color, false); }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) { g.drawString(font, title, 12, 8, 0xFFE0E6E8, false); }
}
