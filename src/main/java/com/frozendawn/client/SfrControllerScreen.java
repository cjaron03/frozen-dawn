package com.frozendawn.client;

import com.frozendawn.block.SfrControllerMenu;
import com.frozendawn.block.SfrMultiblock;
import com.frozendawn.power.FusionPhysics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import static com.frozendawn.block.SfrControllerBlockEntity.*;

/** SFR-1 Controller. Readout text on the left, Start, Stop, Load-follow and Extract on the right. The title sits alone
 * above ContainerTheme's header rule (y 20) and its corner frost; shell and state share the row below it. Drive and the
 * disruption mitigation toggle sit beside the tritium and helium slots; disabling mitigation takes a 3 s hold on an overlay. */
public final class SfrControllerScreen extends AbstractContainerScreen<SfrControllerMenu> {
    private static final int BG = 0xFF28353D, PANEL = 0xFF152128, TEXT = 0xFFE0E6E8, MUTED = 0xFF9BAAB1, GREEN = 0xFF9CD5A5,
            AMBER = 0xFFE5A064, RED = 0xFFE5534B, CYAN = 0xFF64C9CC, HOLD_TICKS = 60;
    private static final int STATUS_Y = 25, PANEL_TOP = 37;
    private Button start, stop, follow, extract, mitigation, keep, hold;
    private DriveSlider drive;
    private final List<AbstractWidget> controls = new ArrayList<>();
    private boolean modal, holding;
    private int holdTicks;

    public SfrControllerScreen(SfrControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = SfrControllerMenu.WIDTH; imageHeight = SfrControllerMenu.HEIGHT;
        inventoryLabelX = SfrControllerMenu.INV_X; inventoryLabelY = SfrControllerMenu.INV_Y - 11;
    }
    private int data(int i) { return menu.data().get(i); }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override protected void init() {
        super.init(); controls.clear();
        int x = leftPos + imageWidth - 70, y = topPos + PANEL_TOP;
        start = control(Button.builder(Component.translatable("screen.frozendawn.sfr.start"), b -> send(SfrControllerMenu.START)).bounds(x, y, 62, 20).build());
        stop = control(Button.builder(Component.translatable("screen.frozendawn.sfr.stop"), b -> send(SfrControllerMenu.STOP)).bounds(x, y + 23, 62, 20).build());
        follow = control(Button.builder(Component.empty(), b -> send(SfrControllerMenu.LOAD_FOLLOW)).bounds(x, y + 46, 62, 20).build());
        extract = control(Button.builder(Component.translatable("screen.frozendawn.sfr.extract"), b -> send(SfrControllerMenu.EXTRACT)).bounds(x, y + 69, 62, 20).build());
        int row = topPos + SfrControllerMenu.SLOT_Y - 2;
        drive = control(new DriveSlider(leftPos + 84, row, 96, 20));
        mitigation = control(Button.builder(Component.empty(), b -> { if (menu.flag(F_MITIGATION)) openModal(true); else send(SfrControllerMenu.MITIGATION_ON); })
                .bounds(x, row, 62, 20).tooltip(Tooltip.create(Component.translatable("screen.frozendawn.sfr.dms.tooltip"))).build());
        int mx = leftPos + 28, my = topPos + imageHeight - 32;
        keep = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.sfr.confirm.keep"), b -> openModal(false)).bounds(mx, my, 104, 20).build());
        hold = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.sfr.confirm.hold"), b -> {}).bounds(mx + 116, my, 104, 20).build());
        openModal(modal);
    }
    private <T extends AbstractWidget> T control(T widget) { controls.add(widget); return addRenderableWidget(widget); }
    private void openModal(boolean value) {
        modal = value; holding = false; holdTicks = 0;
        for (var w : controls) w.visible = !modal;
        keep.visible = hold.visible = modal;
        setFocused(modal ? keep : null);
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
        boolean mitigated = menu.flag(F_MITIGATION);
        mitigation.setMessage(mitigated ? Component.translatable("screen.frozendawn.sfr.dms_on")
                : Component.translatable("screen.frozendawn.sfr.dms_off").withStyle(ChatFormatting.RED));
        drive.active = !mitigated;
        drive.sync(data(DRIVE));
        if (modal && !mitigated) openModal(false);
        if (modal && holding && ++holdTicks >= HOLD_TICKS) { send(SfrControllerMenu.MITIGATION_OFF); openModal(false); }
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (modal && hold.isMouseOver(mx, my) && button == 0) { holding = true; holdTicks = 0; setFocused(hold); return true; }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (holding && button == 0) { holding = false; holdTicks = 0; }
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (modal && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { openModal(false); return true; }
        boolean activate = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
        if (modal && getFocused() == hold && activate) { if (!holding) { holding = true; holdTicks = 0; } return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean keyReleased(int key, int scan, int mods) {
        if (holding && getFocused() == hold) { holding = false; holdTicks = 0; }
        return super.keyReleased(key, scan, mods);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.fill(x + 6, y + PANEL_TOP, x + imageWidth - 76, y + PANEL_TOP + 87, PANEL);
        for (var s : menu.slots) g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, PANEL);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, TEXT, false);
        if (modal) return;
        var stateText = Component.translatable("screen.frozendawn.sfr.state", Component.translatable("sfr.frozendawn.state." + state().name().toLowerCase(Locale.ROOT)));
        int stateX = imageWidth - 8 - font.width(stateText);
        g.drawString(font, stateText, stateX, STATUS_Y, MUTED, false);
        // A long invalid-shell reason is cut short of the state; the blueprint names the missing parts in full.
        String shell = shellLine().getString();
        int room = stateX - 16;
        if (font.width(shell) > room) shell = font.plainSubstrByWidth(shell, room - font.width("...")) + "...";
        g.drawString(font, shell, 8, STATUS_Y, menu.flag(F_SHELL) ? GREEN : AMBER, false);
        var status = status();
        line(g, 0, Component.translatable("sfr.frozendawn.status." + status.name().toLowerCase(Locale.ROOT)), statusColor(status));
        if (state() == FusionPhysics.State.COOLDOWN || state() == FusionPhysics.State.IGNITION)
            line(g, 0, Component.literal(String.format(Locale.ROOT, "%d%%", data(PROGRESS) / 10)), MUTED, 100);
        // A loaded canister boosts the next Start from OFF; once spent, the flag shows the cooldown it is boosting.
        boolean heliumReady = state() == FusionPhysics.State.OFF && !menu.slots.get(HELIUM).getItem().isEmpty();
        if (menu.flag(F_BOOSTED) || heliumReady)
            line(g, 0, Component.translatable(heliumReady ? "screen.frozendawn.sfr.helium_ready" : "screen.frozendawn.sfr.boosted"), CYAN, 130);
        line(g, 1, Component.translatable("screen.frozendawn.sfr.loop", tenths(LOOP)), TEXT);
        line(g, 2, Component.translatable("screen.frozendawn.sfr.heat", tenths(HEAT), tenths(REMOVED)), TEXT);
        line(g, 3, Component.translatable("screen.frozendawn.sfr.power", tenths(GROSS), tenths(NET)), CYAN);
        line(g, 4, Component.translatable("screen.frozendawn.sfr.request", tenths(REQUEST),
                String.format(Locale.ROOT, "%.0f", state() == FusionPhysics.State.BURNING ? FusionPhysics.HOUSE_KW : 0)), MUTED);
        line(g, 5, Component.translatable("screen.frozendawn.sfr.radiators", data(RADIATORS)), TEXT);
        line(g, 6, Component.translatable("screen.frozendawn.sfr.fuel", data(DEUTERIUM), String.format(Locale.ROOT, "%.2f", data(TRITIUM) / 100.0), FusionPhysics.TRITIUM_TANK), TEXT);
        line(g, 7, Component.translatable("screen.frozendawn.sfr.channel", data(PRESSURE) / 10, data(WEAR) / 10), data(WEAR) > 0 ? AMBER : MUTED);
        g.drawString(font, Component.translatable("screen.frozendawn.sfr.slot.tritium_in"), 12, SfrControllerMenu.SLOT_Y - 9, MUTED, false);
        g.drawString(font, Component.translatable("screen.frozendawn.sfr.slot.helium"), 36, SfrControllerMenu.SLOT_Y - 9, MUTED, false);
        g.drawString(font, Component.translatable("screen.frozendawn.sfr.slot.tritium_out"), 56, SfrControllerMenu.SLOT_Y - 9, MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }
    private static int statusColor(FusionPhysics.Status s) {
        return switch (s) {
            case BURNING -> GREEN;
            case READY, IGNITION, COOLDOWN -> CYAN;
            case IDLE -> MUTED;
            case DISRUPTION, MAGNET_QUENCH, ASH_POISONED, WALL_WORN, WALL_MELT -> RED;
            default -> AMBER;
        };
    }
    private void line(GuiGraphics g, int row, Component text, int color) { line(g, row, text, color, 10); }
    private void line(GuiGraphics g, int row, Component text, int color, int x) { g.drawString(font, text, x, PANEL_TOP + 4 + row * 10, color, false); }
    private String tenths(int index) { return String.format(Locale.ROOT, "%.1f", data(index) / 10.0); }
    private Component shellLine() {
        if (menu.flag(F_SHELL)) return Component.translatable("screen.frozendawn.reactor.shell.valid");
        var problem = SfrMultiblock.Problem.values()[Math.clamp(data(SHELL), 0, SfrMultiblock.Problem.values().length - 1)];
        return Component.translatable("screen.frozendawn.reactor.shell.invalid",
                Component.translatable("screen.frozendawn.sfr.shell." + problem.name().toLowerCase(Locale.ROOT)));
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        if (modal) { renderBackground(g, mx, my, partial); renderModal(g, mx, my); return; }
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }
    private void renderModal(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + 14, y = topPos + 10, w = imageWidth - 28;
        g.pose().pushPose(); g.pose().translate(0, 0, 300);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101418);
        g.fill(x, y, x + w, topPos + imageHeight - 6, 0xFF1D2A31); g.fill(x, y, x + w, y + 2, RED);
        String base = "screen.frozendawn.sfr.confirm.";
        g.drawString(font, Component.translatable(base + "title"), x + 8, y + 8, RED, false);
        int ty = y + 22;
        for (var line : List.of("intro", "risk", "history", "tycho", "warranty")) {
            for (FormattedCharSequence wrapped : font.split(Component.translatable(base + line), w - 16)) {
                g.drawString(font, wrapped, x + 8, ty, line.equals("history") ? MUTED : TEXT, false); ty += 10;
            }
            ty += 3;
        }
        if (holdTicks > 0) g.fill(hold.getX(), hold.getY() + hold.getHeight(), hold.getX() + hold.getWidth() * holdTicks / HOLD_TICKS, hold.getY() + hold.getHeight() + 3, RED);
        g.pose().popPose();
        for (var b : List.of(keep, hold)) { g.pose().pushPose(); g.pose().translate(0, 0, 310); b.render(g, mouseX, mouseY, 0); g.pose().popPose(); }
    }

    /** Drive from 100% to MAX_DRIVE in DRIVE_STEP steps; locked at 100% while mitigation is on. */
    private final class DriveSlider extends AbstractSliderButton {
        private static final int STEPS = SfrControllerMenu.DRIVE_STEPS;
        private int sent = -1;
        private boolean dragging;
        DriveSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), 0); updateMessage(); }
        private int steps() { return (int) Math.round(value * STEPS); }
        void sync(int percent) {
            int server = Math.clamp(Math.round((percent - 100) / (float) (FusionPhysics.DRIVE_STEP * 100)), 0, STEPS);
            if (!dragging && server != steps()) { value = server / (double) STEPS; sent = server; updateMessage(); }
        }
        @Override protected void updateMessage() {
            var text = Component.translatable("screen.frozendawn.sfr.drive", 100 + steps() * (int) Math.round(FusionPhysics.DRIVE_STEP * 100));
            setMessage(steps() > 0 ? text.withStyle(ChatFormatting.GOLD) : text);
        }
        @Override protected void applyValue() { if (steps() != sent) { sent = steps(); send(SfrControllerMenu.DRIVE + sent); } }
        @Override public void onClick(double mx, double my) { dragging = true; super.onClick(mx, my); }
        @Override public void onRelease(double mx, double my) { dragging = false; value = steps() / (double) STEPS; updateMessage(); super.onRelease(mx, my); }
    }
}
