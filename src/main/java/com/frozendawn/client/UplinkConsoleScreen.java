package com.frozendawn.client;

import com.frozendawn.block.UplinkConsoleBlockEntity;
import com.frozendawn.block.UplinkConsoleMenu;
import com.frozendawn.uplink.UplinkPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Mode selector, link/dish/power status, handshake progress and the carrier log. Transmit always asks first. */
public final class UplinkConsoleScreen extends AbstractContainerScreen<UplinkConsoleMenu> {
    private static final int W = 252, H = 238, LOG_TOP = 156, LOG_ROWS = 6;
    private Button off, listen, transmit, boost, confirm, cancel, logTab, archiveTab;
    private boolean archiveView;
    public UplinkConsoleScreen(UplinkConsoleMenu m, Inventory i, Component t) { super(m, i, t); imageWidth = W; imageHeight = H; }

    @Override protected void init() {
        super.init();
        int y = topPos + 22;
        off = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.uplink.off"), b -> press(0)).bounds(leftPos + 8, y, 56, 18).build());
        listen = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.uplink.listen"), b -> press(1)).bounds(leftPos + 68, y, 56, 18).build());
        transmit = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.uplink.transmit"), b -> press(2)).bounds(leftPos + 128, y, 56, 18).build());
        boost = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.uplink.boost"), b -> press(3)).bounds(leftPos + 188, y, 56, 18).build());
        confirm = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.uplink.confirm"), b -> press(4)).bounds(leftPos + 40, topPos + 122, 80, 16).build());
        logTab = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.uplink.tab.log"), b -> archiveView = false).bounds(leftPos + W - 108, topPos + 96, 46, 14).build());
        archiveTab = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.uplink.tab.archive"), b -> archiveView = true).bounds(leftPos + W - 58, topPos + 96, 46, 14).build());
        cancel = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.uplink.cancel"), b -> press(5)).bounds(leftPos + 132, topPos + 122, 80, 16).build());
    }

    private void press(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private int get(int i) { return menu.data().get(i); }
    private int needKw() { return (get(UplinkConsoleMenu.BOOST) == 1 ? UplinkPolicy.Mode.BOOST : UplinkPolicy.Mode.TRANSMIT).watts / 1000; }
    private boolean gridReady() { return get(UplinkConsoleMenu.AVAILABLE_KW) >= needKw(); }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        int mode = get(UplinkConsoleMenu.MODE), state = get(UplinkConsoleMenu.STATE);
        boolean busy = state == UplinkConsoleBlockEntity.State.TRANSMITTING.ordinal(), armed = state == UplinkConsoleBlockEntity.State.ARMED.ordinal();
        off.active = !busy && mode != UplinkPolicy.Mode.OFF.ordinal();
        listen.active = !busy && mode != UplinkPolicy.Mode.LISTEN.ordinal();
        transmit.active = boost.active = !busy && !armed;
        confirm.visible = cancel.visible = armed;
        confirm.active = gridReady();
        logTab.active = archiveView; archiveTab.active = !archiveView;
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + W, y + H, 0xFF28353D); g.fill(x + 7, y + 44, x + W - 7, y + H - 7, 0xFF152128);
        int state = get(UplinkConsoleMenu.STATE), dish = get(UplinkConsoleMenu.DISH);
        boolean powered = get(UplinkConsoleMenu.POWERED) == 1;
        String modeKey = state == UplinkConsoleBlockEntity.State.TRANSMITTING.ordinal()
                ? (get(UplinkConsoleMenu.BOOST) == 1 ? "boost" : "transmit") : UplinkPolicy.Mode.byId(get(UplinkConsoleMenu.MODE)).name().toLowerCase(java.util.Locale.ROOT);
        line(g, Component.translatable("screen.frozendawn.uplink.mode", Component.translatable("screen.frozendawn.uplink." + modeKey), get(UplinkConsoleMenu.DEMAND_KW)), x + 12, y + 50, 0xFFE0E6E8);
        line(g, Component.translatable(dish == 1 ? "screen.frozendawn.uplink.dish.ok" : dish == 2 ? "screen.frozendawn.uplink.dish.sky" : "screen.frozendawn.uplink.dish.none"),
                x + 12, y + 62, dish == 1 ? 0xFF9CD5A5 : 0xFFE58B83);
        line(g, Component.translatable(get(UplinkConsoleMenu.DEMAND_KW) == 0 ? "screen.frozendawn.uplink.power.idle" : powered ? "screen.frozendawn.uplink.power.ok" : "screen.frozendawn.uplink.power.short"),
                x + 12, y + 74, get(UplinkConsoleMenu.DEMAND_KW) == 0 ? 0xFF9BAAB1 : powered ? 0xFF9CD5A5 : 0xFFE5A064);
        int cooldown = get(UplinkConsoleMenu.COOLDOWN);
        line(g, cooldown > 0 ? Component.translatable("screen.frozendawn.uplink.cooldown", cooldown / 60, cooldown % 60) : Component.translatable("screen.frozendawn.uplink.ready"),
                x + 12, y + 86, cooldown > 0 ? 0xFF9BAAB1 : 0xFF64C9CC);
        line(g, Component.translatable("screen.frozendawn.uplink.archive", get(UplinkConsoleMenu.ARCHIVE), UplinkPolicy.ARCHIVE_SIZE), x + 12, y + 98, 0xFF9BAAB1);
        if (state == UplinkConsoleBlockEntity.State.ARMED.ordinal()) {
            g.fill(x + 12, y + 112, x + W - 12, y + 152, 0xFF3A1E1E);
            var warn = Component.translatable("screen.frozendawn.uplink.warning");
            g.drawString(font, warn, x + (W - font.width(warn)) / 2, y + 115, 0xFFE58B83, false);
            // The armed prompt schedules the load: show the grid ramping toward it.
            var grid = gridReady() ? Component.translatable("screen.frozendawn.uplink.grid.ready")
                    : Component.translatable("screen.frozendawn.uplink.grid.ramping", get(UplinkConsoleMenu.AVAILABLE_KW), needKw());
            g.drawString(font, grid, x + (W - font.width(grid)) / 2, y + 140, gridReady() ? 0xFF9CD5A5 : 0xFFE5A064, false);
        } else if (state == UplinkConsoleBlockEntity.State.TRANSMITTING.ordinal()) {
            int total = Math.max(1, get(UplinkConsoleMenu.TOTAL)), done = Math.min(total, get(UplinkConsoleMenu.ELAPSED));
            line(g, Component.translatable("screen.frozendawn.uplink.handshake", done, total), x + 12, y + 116, 0xFFE5A064);
            g.fill(x + 12, y + 130, x + W - 12, y + 136, 0xFF39464F);
            g.fill(x + 12, y + 130, x + 12 + (W - 24) * done / total, y + 136, 0xFFE5A064);
        }
        g.fill(x + 12, y + LOG_TOP - 5, x + W - 12, y + LOG_TOP - 4, 0xFF39464F);
        if (archiveView) { renderArchive(g, x, y); return; }
        var console = minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof UplinkConsoleBlockEntity c ? c : null;
        if (console == null) return;
        // Entries wrap to the panel width; the newest wrapped rows stay visible, the newest entry brighter.
        var log = console.log();
        var rows = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        var colors = new java.util.ArrayList<Integer>();
        for (int i = 0; i < log.size(); i++) {
            var entry = log.get(i);
            for (var wrapped : font.split(Component.translatable(entry.key(), entry.args().toArray()), W - 24)) {
                rows.add(wrapped); colors.add(i == log.size() - 1 ? 0xFFE0E6E8 : 0xFF9BAAB1);
            }
        }
        int first = Math.max(0, rows.size() - LOG_ROWS);
        for (int i = first; i < rows.size(); i++) g.drawString(font, rows.get(i), x + 12, y + LOG_TOP + (i - first) * 11, colors.get(i), false);
    }

    /** Received relay transcripts by title; their text lives in the Field Manual, so it has one source. */
    private void renderArchive(GuiGraphics g, int x, int y) {
        int received = get(UplinkConsoleMenu.ARCHIVE), row = 0;
        for (int i = 1; i <= UplinkPolicy.ARCHIVE_SIZE; i++, row++)
            line(g, i <= received ? Component.translatable("screen.frozendawn.uplink.archive.received", i, Component.translatable("uplink.frozendawn.archive." + i + ".title"))
                    : Component.translatable("screen.frozendawn.uplink.archive.pending", i), x + 12, y + LOG_TOP + row * 11, i <= received ? 0xFFE0E6E8 : 0xFF9BAAB1);
        var where = font.split(Component.translatable("screen.frozendawn.uplink.archive.where"), W - 24);
        for (int i = 0; i < where.size() && row < LOG_ROWS; i++, row++) g.drawString(font, where.get(i), x + 12, y + LOG_TOP + 6 + row * 11, 0xFF64C9CC, false);
    }

    private void line(GuiGraphics g, Component c, int x, int y, int color) { g.drawString(font, font.plainSubstrByWidth(c.getString(), W - 24), x, y, color, false); }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) { g.drawString(font, title, 12, 8, 0xFFE0E6E8, false); }
}
