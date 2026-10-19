package com.frozendawn.client;

import com.frozendawn.block.PlumbLineBlockEntity;
import com.frozendawn.block.PlumbLineMenu;
import com.frozendawn.gpr.PlumbLineOres;
import com.frozendawn.gpr.PlumbLinePolicy;
import com.frozendawn.gpr.PlumbLineScan.Kind;
import com.frozendawn.gpr.PlumbLineScan.Return;
import com.frozendawn.network.PlumbLineReturnsPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;

/**
 * Setting, target, pulse and power on the left with the last pulse's finds listed by ore and where to dig from here;
 * a legend on the right says what each colour and shape in the overlay means.
 */
public final class PlumbLineScreen extends AbstractContainerScreen<PlumbLineMenu> {
    private static final int W = 320, H = 240, SPLIT = 204, ROW = 10, LEGEND_ROW = 9;
    private static final int TEXT = 0xFFE0E6E8, DIM = 0xFF9BAAB1, FAINT = 0xFF6F7C83;
    private Button survey, deep, target, pulse, print;
    public PlumbLineScreen(PlumbLineMenu m, Inventory i, Component t) { super(m, i, t); imageWidth = W; imageHeight = H; }

    @Override protected void init() {
        super.init();
        survey = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.plumb_line.survey"), b -> press(0)).bounds(leftPos + 8, topPos + 22, 62, 18).build());
        deep = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.plumb_line.deep"), b -> press(1)).bounds(leftPos + 73, topPos + 22, 62, 18).build());
        target = addRenderableWidget(Button.builder(targetLabel(), b -> press(3)).bounds(leftPos + 138, topPos + 22, 100, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.plumb_line.target.tip"))).build());
        pulse = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.plumb_line.pulse"), b -> press(2)).bounds(leftPos + 241, topPos + 22, 71, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.plumb_line.hint"))).build());
        print = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.plumb_line.print"), b -> press(4)).bounds(leftPos + 12, topPos + H - 29, 64, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.plumb_line.print.tip"))).build());
    }
    private void press(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private int get(int i) { return menu.data().get(i); }
    private PlumbLinePolicy.Setting setting() { return PlumbLinePolicy.Setting.byId(get(PlumbLineMenu.SETTING)); }
    private PlumbLineOres.Target targetSetting() { return PlumbLineOres.Target.byId(get(PlumbLineMenu.TARGET)); }
    private Component targetLabel() {
        return Component.translatable("screen.frozendawn.plumb_line.target", Component.translatable("gpr.frozendawn.target." + targetSetting().key()));
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        boolean busy = get(PlumbLineMenu.PULSING) == 1;
        survey.active = !busy && setting() != PlumbLinePolicy.Setting.SURVEY;
        deep.active = !busy && setting() != PlumbLinePolicy.Setting.DEEP;
        target.active = !busy;
        print.active = !busy && get(PlumbLineMenu.HAS_SURVEY) == 1;
        target.setMessage(targetLabel());
        pulse.active = !busy && get(PlumbLineMenu.COOLDOWN) == 0 && get(PlumbLineMenu.AVAILABLE_KW) >= setting().watts / 1000;
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + W, y + H, 0xFF28353D); g.fill(x + 7, y + 44, x + W - 7, y + H - 7, 0xFF152128);
        g.fill(x + SPLIT, y + 48, x + SPLIT + 1, y + H - 11, 0xFF2C3A42);
        var s = setting();
        line(g, Component.translatable("screen.frozendawn.plumb_line.setting", Component.translatable("screen.frozendawn.plumb_line." + s.name().toLowerCase(java.util.Locale.ROOT)), s.watts / 1000, s.radius), 50, TEXT);
        boolean ready = get(PlumbLineMenu.AVAILABLE_KW) >= s.watts / 1000;
        line(g, Component.translatable(ready ? "screen.frozendawn.plumb_line.power.ok" : "screen.frozendawn.plumb_line.power.short", s.watts / 1000), 62, ready ? 0xFF9CD5A5 : 0xFFE5A064);
        int cooldown = get(PlumbLineMenu.COOLDOWN);
        if (get(PlumbLineMenu.PULSING) == 1) {
            int done = Math.min(10, get(PlumbLineMenu.ELAPSED));
            line(g, Component.translatable("screen.frozendawn.plumb_line.pulsing", done), 74, 0xFFE5A064);
            g.fill(x + 12, y + 86, x + SPLIT - 6, y + 91, 0xFF39464F);
            g.fill(x + 12, y + 86, x + 12 + (SPLIT - 18) * done / 10, y + 91, 0xFFE5A064);
        } else line(g, cooldown > 0 ? Component.translatable("screen.frozendawn.plumb_line.cooldown", cooldown) : Component.translatable("screen.frozendawn.plumb_line.ready"), 74, cooldown > 0 ? DIM : 0xFF64C9CC);
        int result = get(PlumbLineMenu.RESULT), y2 = 98;
        var last = PlumbLineOverlay.last(menu.pos());
        if (result == PlumbLineBlockEntity.RESULT_DONE && last != null) y2 = finds(g, last.returns(), y2);
        else if (result == PlumbLineBlockEntity.RESULT_DONE) {
            y2 = wrap(g, Component.translatable("screen.frozendawn.plumb_line.last", get(PlumbLineMenu.ORES), get(PlumbLineMenu.VOIDS), get(PlumbLineMenu.STRUCTURES), get(PlumbLineMenu.METALS)), y2, TEXT);
            y2 = wrap(g, Component.translatable("screen.frozendawn.plumb_line.view"), y2 + 2, DIM);
        } else if (result != PlumbLineBlockEntity.RESULT_NONE)
            y2 = wrap(g, Component.translatable("screen.frozendawn.plumb_line.result." + result), y2, 0xFFE58B83);
        int note = get(PlumbLineMenu.PRINT_NOTE);
        var printLine = note != PlumbLineBlockEntity.PRINT_NONE ? Component.translatable("screen.frozendawn.plumb_line.print." + note)
                : get(PlumbLineMenu.HAS_SURVEY) == 1 ? Component.translatable("screen.frozendawn.plumb_line.print.cost") : null;
        if (printLine != null) g.drawString(font, font.plainSubstrByWidth(printLine.getString(), SPLIT - 6 - 82), x + 82, y + H - 24,
                note == PlumbLineBlockEntity.PRINT_NO_MATERIALS || note == PlumbLineBlockEntity.PRINT_NO_DATA ? 0xFFE58B83 : note == PlumbLineBlockEntity.PRINT_DONE ? 0xFF9CD5A5 : FAINT, false);
        legend(g, last);
    }

    /** One row per ore type, most valuable first, with how many and where the nearest is from the player. */
    private int finds(GuiGraphics g, List<Return> returns, int y) {
        g.drawString(font, Component.translatable("screen.frozendawn.plumb_line.finds"), leftPos + 12, topPos + y, TEXT, false);
        y += ROW + 2;
        var from = minecraft != null && minecraft.player != null ? minecraft.player.position() : Vec3.atCenterOf(menu.pos());
        var groups = new LinkedHashMap<String, List<Return>>();
        var sorted = new ArrayList<>(returns);
        sorted.sort(Comparator.comparingDouble(r -> PlumbLineOverlay.center(r).distanceToSqr(from)));
        for (var r : sorted) {
            String key = switch (r.kind()) { case ORE -> "ore:" + PlumbLineOres.of(r.label()).key(); case STRUCTURE -> "site:" + r.label(); default -> r.kind().name(); };
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        var rows = new ArrayList<>(groups.values());
        rows.sort(Comparator.comparingInt((List<Return> l) -> rank(l.getFirst())).thenComparingDouble(l -> PlumbLineOverlay.center(l.getFirst()).distanceToSqr(from)));
        if (rows.stream().noneMatch(l -> l.getFirst().kind() == Kind.ORE)) {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.frozendawn.plumb_line.none").getString(), SPLIT - 18), leftPos + 12, topPos + y, DIM, false);
            y += ROW;
        }
        for (var group : rows) {
            if (y + ROW > H - 32) break;
            var nearest = group.getFirst();
            String where = PlumbLineOverlay.where(nearest, from);
            String name = (nearest.kind() == Kind.ORE && !nearest.precise() ? "~ " : "") + groupName(nearest) + (group.size() > 1 ? " ×" + group.size() : "");
            int whereW = font.width(where), nameW = SPLIT - 18 - 11 - whereW - 6;
            swatch(g, leftPos + 12, topPos + y, nearest);
            g.drawString(font, font.plainSubstrByWidth(name, nameW), leftPos + 23, topPos + y, TEXT, false);
            g.drawString(font, where, leftPos + SPLIT - 6 - whereW, topPos + y, DIM, false);
            y += ROW;
        }
        return y;
    }

    private static int rank(Return r) {
        return switch (r.kind()) { case ORE -> -PlumbLineOres.of(r.label()).tier(); case METAL -> 10; case STRUCTURE -> 11; case VOID -> 12; };
    }

    private static String groupName(Return r) {
        return r.kind() == Kind.VOID ? Component.translatable("screen.frozendawn.plumb_line.legend.cave").getString()
                : r.kind() == Kind.METAL ? Component.translatable("screen.frozendawn.plumb_line.legend.metal").getString() : PlumbLineOverlay.name(r);
    }

    /** What the overlay's shapes and colours mean; ore colours for the chosen target, or those found when it is All. */
    private void legend(GuiGraphics g, PlumbLineReturnsPayload last) {
        int x = leftPos + SPLIT + 6, y = topPos + 50;
        g.drawString(font, Component.translatable("screen.frozendawn.plumb_line.legend"), x, y, TEXT, false);
        y += LEGEND_ROW + 3;
        int light = 0xFFD0D6D8;
        brackets(g, x, y, light); legendText(g, "estimate", x, y); y += LEGEND_ROW;
        g.renderOutline(x, y, 8, 8, light); legendText(g, "exact", x, y); y += LEGEND_ROW;
        g.renderOutline(x, y, 8, 8, 0xFFFFFFFF); legendText(g, "metal", x, y); y += LEGEND_ROW;
        g.renderOutline(x, y, 8, 8, 0xFF2DD4D2); legendText(g, "site", x, y); y += LEGEND_ROW;
        brackets(g, x, y, 0xFF72A8E0); legendText(g, "cave", x, y); y += LEGEND_ROW + 4;
        var target = targetSetting();
        var ores = new ArrayList<PlumbLineOres.Ore>();
        if (target == PlumbLineOres.Target.ALL && last != null)
            for (var r : last.returns()) if (r.kind() == Kind.ORE && ores.stream().noneMatch(o -> o.key().equals(PlumbLineOres.of(r.label()).key()))) ores.add(PlumbLineOres.of(r.label()));
        if (ores.isEmpty()) for (var o : PlumbLineOres.KNOWN) if (target.matches(o.group())) ores.add(o);
        ores.sort(Comparator.comparingInt((PlumbLineOres.Ore o) -> -o.tier()));
        for (var o : ores) {
            if (y + LEGEND_ROW > topPos + H - 9) break;
            g.fill(x, y, x + 8, y + 8, 0xFF000000 | o.color());
            g.drawString(font, font.plainSubstrByWidth(PlumbLineOverlay.oreName(o, o.key()), W - SPLIT - 6 - 11 - 8), x + 11, y, DIM, false);
            y += LEGEND_ROW;
        }
    }

    private void legendText(GuiGraphics g, String key, int x, int y) {
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.frozendawn.plumb_line.legend." + key).getString(), W - SPLIT - 6 - 11 - 8), x + 11, y, DIM, false);
    }

    private static void swatch(GuiGraphics g, int x, int y, Return r) {
        int color = 0xFF000000 | PlumbLineOverlay.color(r);
        if (r.kind() == Kind.ORE && !r.precise() || r.kind() == Kind.VOID) brackets(g, x, y, color);
        else if (r.kind() == Kind.ORE) g.fill(x, y, x + 8, y + 8, color);
        else g.renderOutline(x, y, 8, 8, color);
    }

    /** The overlay's corner-bracket mark, at 8x8. */
    private static void brackets(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 3, y + 1, color); g.fill(x, y, x + 1, y + 3, color);
        g.fill(x + 5, y, x + 8, y + 1, color); g.fill(x + 7, y, x + 8, y + 3, color);
        g.fill(x, y + 7, x + 3, y + 8, color); g.fill(x, y + 5, x + 1, y + 8, color);
        g.fill(x + 5, y + 7, x + 8, y + 8, color); g.fill(x + 7, y + 5, x + 8, y + 8, color);
    }

    /** Wraps to the left column and returns the y below the last line. */
    private int wrap(GuiGraphics g, Component c, int y, int color) {
        for (var row : font.split(c, SPLIT - 18)) { g.drawString(font, row, leftPos + 12, topPos + y, color, false); y += 10; }
        return y;
    }
    private void line(GuiGraphics g, Component c, int y, int color) { g.drawString(font, font.plainSubstrByWidth(c.getString(), SPLIT - 18), leftPos + 12, topPos + y, color, false); }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) { g.drawString(font, title, 12, 8, TEXT, false); }
}
