package com.frozendawn.client;

import com.frozendawn.block.SfrControllerMenu;
import com.frozendawn.block.SfrMultiblock;
import com.frozendawn.power.FusionPhysics;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.frozendawn.block.SfrControllerBlockEntity.*;

/** SFR-1 Controller. Readout text on the left, Start, Stop, Load-follow and Extract on the right. */
public final class SfrControllerScreen extends AbstractContainerScreen<SfrControllerMenu> {
    private static final int BG = 0xFF28353D, PANEL = 0xFF152128, TEXT = 0xFFE0E6E8, MUTED = 0xFF9BAAB1, GREEN = 0xFF9CD5A5,
            AMBER = 0xFFE5A064, RED = 0xFFE5534B, CYAN = 0xFF64C9CC;
    private Button start, stop, follow, extract;

    public SfrControllerScreen(SfrControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = SfrControllerMenu.WIDTH; imageHeight = SfrControllerMenu.HEIGHT;
        inventoryLabelX = SfrControllerMenu.INV_X; inventoryLabelY = SfrControllerMenu.INV_Y - 11;
    }
    private int data(int i) { return menu.data().get(i); }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override protected void init() {
        super.init();
        int x = leftPos + imageWidth - 70, y = topPos + 26;
        start = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.sfr.start"), b -> send(SfrControllerMenu.START)).bounds(x, y, 62, 20).build());
        stop = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.sfr.stop"), b -> send(SfrControllerMenu.STOP)).bounds(x, y + 23, 62, 20).build());
        follow = addRenderableWidget(Button.builder(Component.empty(), b -> send(SfrControllerMenu.LOAD_FOLLOW)).bounds(x, y + 46, 62, 20).build());
        extract = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.sfr.extract"), b -> send(SfrControllerMenu.EXTRACT)).bounds(x, y + 69, 62, 20).build());
    }
    private FusionPhysics.State state() { return FusionPhysics.State.values()[Math.clamp(data(STATE), 0, FusionPhysics.State.values().length - 1)]; }
    private FusionPhysics.Status status() { return FusionPhysics.Status.values()[Math.clamp(data(STATUS), 0, FusionPhysics.Status.values().length - 1)]; }

    @Override protected void containerTick() {
        super.containerTick();
        var s = state();
        start.active = s == FusionPhysics.State.OFF || s == FusionPhysics.State.READY;
        stop.active = s != FusionPhysics.State.OFF;
        extract.active = data(TRITIUM) >= 100;
        follow.setMessage(Component.translatable(menu.flag(F_FOLLOW) ? "screen.frozendawn.reactor.follow_on" : "screen.frozendawn.reactor.follow_off"));
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.fill(x + 6, y + 27, x + imageWidth - 76, y + 114, PANEL);
        for (var s : menu.slots) g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, PANEL);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, TEXT, false);
        var stateText = Component.translatable("screen.frozendawn.sfr.state", Component.translatable("sfr.frozendawn.state." + state().name().toLowerCase(Locale.ROOT)));
        g.drawString(font, stateText, imageWidth - 8 - font.width(stateText), 6, MUTED, false);
        g.drawString(font, shellLine(), 8, 16, menu.flag(F_SHELL) ? GREEN : AMBER, false);
        var status = status();
        line(g, 0, Component.translatable("sfr.frozendawn.status." + status.name().toLowerCase(Locale.ROOT)), statusColor(status));
        if (state() == FusionPhysics.State.COOLDOWN || state() == FusionPhysics.State.IGNITION)
            line(g, 0, Component.literal(String.format(Locale.ROOT, "%d%%", data(PROGRESS) / 10)), MUTED, 100);
        line(g, 1, Component.translatable("screen.frozendawn.sfr.loop", tenths(LOOP)), TEXT);
        line(g, 2, Component.translatable("screen.frozendawn.sfr.heat", tenths(HEAT), tenths(REMOVED)), TEXT);
        line(g, 3, Component.translatable("screen.frozendawn.sfr.power", tenths(GROSS), tenths(NET)), CYAN);
        line(g, 4, Component.translatable("screen.frozendawn.sfr.request", tenths(REQUEST),
                String.format(Locale.ROOT, "%.0f", state() == FusionPhysics.State.BURNING ? FusionPhysics.HOUSE_KW : 0)), MUTED);
        line(g, 5, Component.translatable("screen.frozendawn.sfr.radiators", data(RADIATORS)), TEXT);
        line(g, 6, Component.translatable("screen.frozendawn.sfr.fuel", data(DEUTERIUM), String.format(Locale.ROOT, "%.2f", data(TRITIUM) / 100.0), FusionPhysics.TRITIUM_TANK), TEXT);
        line(g, 7, Component.translatable("screen.frozendawn.sfr.channel", data(PRESSURE) / 10, data(WEAR) / 10), data(WEAR) > 0 ? AMBER : MUTED);
        g.drawString(font, Component.translatable("screen.frozendawn.sfr.tritium_slots"), 12, SfrControllerMenu.SLOT_Y - 9, MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }
    private static int statusColor(FusionPhysics.Status s) {
        return switch (s) {
            case BURNING -> GREEN;
            case READY, IGNITION, COOLDOWN -> CYAN;
            case IDLE -> MUTED;
            case DISRUPTION, MAGNET_QUENCH, ASH_POISONED, WALL_WORN -> RED;
            default -> AMBER;
        };
    }
    private void line(GuiGraphics g, int row, Component text, int color) { line(g, row, text, color, 10); }
    private void line(GuiGraphics g, int row, Component text, int color, int x) { g.drawString(font, text, x, 31 + row * 10, color, false); }
    private String tenths(int index) { return String.format(Locale.ROOT, "%.1f", data(index) / 10.0); }
    private Component shellLine() {
        if (menu.flag(F_SHELL)) return Component.translatable("screen.frozendawn.reactor.shell.valid");
        var problem = SfrMultiblock.Problem.values()[Math.clamp(data(SHELL), 0, SfrMultiblock.Problem.values().length - 1)];
        return Component.translatable("screen.frozendawn.reactor.shell.invalid",
                Component.translatable("screen.frozendawn.sfr.shell." + problem.name().toLowerCase(Locale.ROOT)));
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }
}
