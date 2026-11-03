package com.frozendawn.client;

import com.frozendawn.block.HyperionControllerBlockEntity;
import com.frozendawn.block.HyperionControllerMenu;
import com.frozendawn.block.HyperionMultiblock;
import com.frozendawn.power.HyperionPhysics;
import com.frozendawn.power.HyperionPhysics.Stage;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** SMS-1 Hyperion Controller: a read-out of the client copy of the Controller. Status first (why it is not formed, or
 * its stage), then charge, flow, temperatures, cooler draw and, while warning, the time left. */
public final class HyperionControllerScreen extends AbstractContainerScreen<HyperionControllerMenu> {
    private static final int BG = 0xFF1E262B, PANEL = 0xFF12191D, TEXT = 0xFFE0E6E8, MUTED = 0xFF9BAAB1, GREEN = 0xFF9CD5A5,
            AMBER = 0xFFE5A064, RED = 0xFFE5534B, CYAN = 0xFF64C9CC;

    public HyperionControllerScreen(HyperionControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = HyperionControllerMenu.WIDTH; imageHeight = HyperionControllerMenu.HEIGHT;
    }
    private HyperionControllerBlockEntity.Snapshot snap() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof HyperionControllerBlockEntity c
                ? c.snapshot() : HyperionControllerBlockEntity.Snapshot.EMPTY;
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG);
        g.fill(leftPos + 6, topPos + 34, leftPos + imageWidth - 6, topPos + imageHeight - 6, PANEL);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        var s = snap();
        g.drawString(font, title, 8, 8, TEXT, false);
        Component status; int color;
        if (s.problem() != HyperionMultiblock.Problem.NONE) { status = Component.translatable("hyperion.frozendawn.problem." + lower(s.problem())); color = AMBER; }
        else { status = Component.translatable("hyperion.frozendawn.stage." + lower(s.stage())); color = switch (s.stage()) { case COLD -> GREEN; case CHIRP, WARNING -> AMBER; case FINAL -> RED; case WARM -> CYAN; }; }
        g.drawString(font, status, 8, 21, color, false);
        if (s.problem() != HyperionMultiblock.Problem.NONE) return;
        int y = 40;
        double pct = s.capacity() <= 0 ? 0 : 100.0 * s.stored() / s.capacity();
        y = line(g, y, "screen.frozendawn.hyperion.stored", energy(s.stored()), energy(s.capacity()), String.format(Locale.ROOT, "%.1f", pct));
        y = line(g, y, "screen.frozendawn.hyperion.flow", power(s.flowIn()), power(s.flowOut()));
        y = line(g, y, "screen.frozendawn.hyperion.ports", s.ports(), power(s.rateWatts()));
        y = line(g, y, "screen.frozendawn.hyperion.cells", celsius(s.cellC()), celsius(HyperionPhysics.QUENCH_C));
        y = line(g, y, "screen.frozendawn.hyperion.surroundings", celsius(s.surroundingsC()));
        y = line(g, y, "screen.frozendawn.hyperion.cooler", power(s.coolerWatts()));
        if (s.stage() == Stage.CHIRP || s.stage() == Stage.WARNING || s.stage() == Stage.FINAL) {
            int left = (int) Math.ceil(s.secondsToQuench());
            g.drawString(font, Component.translatable("screen.frozendawn.hyperion.quench_in", String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60)), 12, y, s.stage() == Stage.FINAL ? RED : AMBER, false);
        }
    }
    private int line(GuiGraphics g, int y, String key, Object... args) { g.drawString(font, Component.translatable(key, args), 12, y, MUTED, false); return y + 12; }
    private static String lower(Enum<?> e) { return e.name().toLowerCase(Locale.ROOT); }
    private static String celsius(double c) { return String.format(Locale.ROOT, "%.0f C", c); }
    static String energy(long j) {
        if (j >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2f GJ", j / 1e9);
        if (j >= 1_000_000L) return String.format(Locale.ROOT, "%.1f MJ", j / 1e6);
        return String.format(Locale.ROOT, "%.1f kJ", j / 1e3);
    }
    static String power(long w) { return w >= 1_000_000 ? String.format(Locale.ROOT, "%.2f MW", w / 1e6) : String.format(Locale.ROOT, "%.1f kW", w / 1e3); }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) { super.render(g, mx, my, partial); renderTooltip(g, mx, my); }
}
