package com.frozendawn.client;

import com.frozendawn.block.ReactorControllerBlock;
import com.frozendawn.block.ReactorControllerBlockEntity;
import com.frozendawn.block.ReactorControllerMenu;
import com.frozendawn.block.ReactorMultiblock;
import com.frozendawn.power.ReactorPhysics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import static com.frozendawn.block.ReactorControllerBlockEntity.*;

/** CFU-7 Controller. Disabling automatic SCRAM takes two in-screen confirmations; the second is a 3 s hold.
 * Both run as overlays so the container stays open. Alarms are text, colour and klaxon only. */
public final class ReactorControllerScreen extends AbstractContainerScreen<ReactorControllerMenu> {
    private static final int BG = 0xFF28353D, PANEL = 0xFF152128, TEXT = 0xFFE0E6E8, MUTED = 0xFF9BAAB1, GREEN = 0xFF9CD5A5,
            AMBER = 0xFFE5A064, RED = 0xFFE5534B, CYAN = 0xFF64C9CC, HOLD_TICKS = 60;
    private Button scram, reset, follow, auto, keep, understand, cancel, hold;
    private InsertionSlider insertion;
    private final List<AbstractWidget> controls = new ArrayList<>();
    private int modal, holdTicks;
    private boolean holding;

    public ReactorControllerScreen(ReactorControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = ReactorControllerMenu.WIDTH; imageHeight = ReactorControllerMenu.HEIGHT;
        inventoryLabelX = ReactorControllerMenu.INV_X; inventoryLabelY = ReactorControllerMenu.INV_Y - 11;
    }
    private int data(int i) { return menu.data().get(i); }
    private boolean flag(int bit) { return menu.flag(bit); }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override protected void init() {
        super.init(); controls.clear();
        int x = leftPos + 176, y = topPos + 26;
        scram = control(Button.builder(Component.translatable("screen.frozendawn.reactor.scram"), b -> send(ReactorControllerMenu.SCRAM)).bounds(x, y, 62, 20).build());
        reset = control(Button.builder(Component.translatable("screen.frozendawn.reactor.reset"), b -> send(ReactorControllerMenu.RESET)).bounds(x, y + 23, 62, 20).build());
        follow = control(Button.builder(Component.empty(), b -> send(ReactorControllerMenu.LOAD_FOLLOW)).bounds(x, y + 46, 62, 20).build());
        auto = control(Button.builder(Component.empty(), b -> { if (flag(F_AUTO)) openModal(1); else send(ReactorControllerMenu.AUTO_ON); }).bounds(x, y + 69, 62, 20).build());
        insertion = control(new InsertionSlider(leftPos + 8, topPos + 126, 160, 14));
        int mx = leftPos + 28, my = topPos + imageHeight - 32;
        keep = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.reactor.confirm.keep"), b -> openModal(0)).bounds(mx, my, 104, 20).build());
        understand = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.reactor.confirm.understand"), b -> openModal(2)).bounds(mx + 116, my, 104, 20).build());
        cancel = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.reactor.confirm.cancel"), b -> openModal(0)).bounds(mx, my, 104, 20).build());
        hold = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.reactor.confirm.hold"), b -> {}).bounds(mx + 116, my, 104, 20).build());
        openModal(modal);
    }
    private <T extends AbstractWidget> T control(T widget) { controls.add(widget); return addRenderableWidget(widget); }
    private void openModal(int value) {
        modal = value; holding = false; holdTicks = 0;
        for (var w : controls) w.visible = modal == 0;
        keep.visible = understand.visible = modal == 1; cancel.visible = hold.visible = modal == 2;
        if (modal == 1) setFocused(keep); else if (modal == 2) setFocused(cancel); else setFocused(null);
    }

    @Override protected void containerTick() {
        super.containerTick();
        boolean melted = data(STATE) == ReactorPhysics.State.MELTDOWN.ordinal();
        reset.active = flag(F_RESETTABLE); insertion.active = follow.active = !melted;
        follow.setMessage(Component.translatable(flag(F_FOLLOW) ? "screen.frozendawn.reactor.follow_on" : "screen.frozendawn.reactor.follow_off"));
        auto.setMessage(Component.translatable(flag(F_AUTO) ? "screen.frozendawn.reactor.auto_on" : "screen.frozendawn.reactor.auto_off"));
        insertion.sync(data(INSERTION) / 1000.0);
        if (modal == 1 && !flag(F_AUTO)) openModal(0);
        if (modal == 2 && holding) {
            if (++holdTicks >= HOLD_TICKS) { send(ReactorControllerMenu.AUTO_OFF); openModal(0); }
        }
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (modal == 2 && hold.visible && hold.isMouseOver(mx, my) && button == 0) { holding = true; holdTicks = 0; setFocused(hold); return true; }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (holding && button == 0) { holding = false; holdTicks = 0; }
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (modal != 0 && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { openModal(0); return true; }
        boolean activate = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
        if (modal == 2 && getFocused() == hold && activate) { if (!holding) { holding = true; holdTicks = 0; } return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean keyReleased(int key, int scan, int mods) {
        if (holding && getFocused() == hold) { holding = false; holdTicks = 0; }
        return super.keyReleased(key, scan, mods);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.fill(x + 6, y + 27, x + 170, y + 124, PANEL);
        for (var s : menu.slots) g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, PANEL);
        if (flag(F_LOCKED)) for (int i = 0; i < ReactorPhysics.ROD_SLOTS; i++) { var s = menu.slots.get(i); g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y, AMBER); }
        for (int n = 0; n < 4; n++) g.fill(x + 100 + n, y + ReactorControllerMenu.ROD_Y + 4 + n, x + 101 + n, y + ReactorControllerMenu.ROD_Y + 12 - n, MUTED);
        thermometer(g, x + 246, y + 26, 16, 118);
    }
    private void thermometer(GuiGraphics g, int x, int y, int w, int h) {
        double min = -100, max = 850, c = data(CORE) / 10.0;
        g.fill(x, y, x + w, y + h, PANEL);
        int level = (int) Math.round(h * Math.clamp((c - min) / (max - min), 0, 1));
        g.fill(x + 3, y + h - level, x + w - 3, y + h, color(c));
        for (double mark : new double[] {ReactorPhysics.WARNING_C, ReactorPhysics.AUTO_SCRAM_C, ReactorPhysics.DAMAGE_C, ReactorPhysics.LOSS_OF_COOLANT_C, ReactorPhysics.MELTDOWN_C}) {
            int my = y + h - (int) Math.round(h * (mark - min) / (max - min));
            g.fill(x - 3, my, x, my + 1, color(mark));
        }
    }
    private static int color(double c) {
        return c >= ReactorPhysics.DAMAGE_C ? RED : c >= ReactorPhysics.WARNING_C ? AMBER : c >= 100 ? GREEN : CYAN;
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, TEXT, false);
        if (modal != 0) return;
        g.drawString(font, shellLine(), 8, 16, flag(F_SHELL) ? GREEN : AMBER, false);
        double c = data(CORE) / 10.0;
        var state = ReactorPhysics.State.values()[Math.clamp(data(STATE), 0, ReactorPhysics.State.values().length - 1)];
        int stateColor = switch (state) { case NOMINAL -> GREEN; case WARNING, SCRAM -> AMBER; default -> RED; };
        line(g, 0, Component.translatable("screen.frozendawn.reactor.core", String.format(Locale.ROOT, "%.1f", c)), color(c));
        var stateText = Component.translatable("screen.frozendawn.reactor.state." + state.name().toLowerCase(Locale.ROOT));
        g.drawString(font, stateText, 166 - font.width(stateText), 30, stateColor, false);
        line(g, 1, Component.translatable("screen.frozendawn.reactor.heat_in", kw(HEAT_IN)), TEXT);
        line(g, 2, Component.translatable("screen.frozendawn.reactor.heat_out", kw(HEAT_OUT)), TEXT);
        line(g, 3, Component.translatable("screen.frozendawn.reactor.power", kw(POWER), data(EFFICIENCY) / 10), CYAN);
        var coolant = ReactorPhysics.Coolant.values()[Math.clamp(data(COOLANT), 0, ReactorPhysics.Coolant.values().length - 1)];
        line(g, 4, Component.translatable("screen.frozendawn.reactor.radiators", data(RADIATORS), kw(RADIATOR_KW)), TEXT);
        double climb = data(CLIMB) / 100.0;
        boolean coolantFault = coolant == ReactorPhysics.Coolant.FROZEN || coolant == ReactorPhysics.Coolant.LOST;
        line(g, 5, Component.translatable("screen.frozendawn.reactor.climb",
                Component.translatable("screen.frozendawn.reactor.coolant." + coolant.name().toLowerCase(Locale.ROOT)),
                String.format(Locale.ROOT, "%+.2f", climb)), coolantFault ? RED : climb > 0.05 ? AMBER : MUTED);
        line(g, 6, Component.translatable("screen.frozendawn.reactor.rods", (int) menu.slots.subList(0, ReactorPhysics.ROD_SLOTS).stream()
                .filter(s -> ReactorControllerBlockEntity.fuel(s.getItem())).count(), kw(RATED), data(DAMAGE) / 10), data(DAMAGE) > 0 ? RED : MUTED);
        if (flag(F_SCRAM) && state != ReactorPhysics.State.MELTDOWN)
            line(g, 7, Component.translatable(flag(F_RESETTABLE) ? "screen.frozendawn.reactor.scram_reset" : "screen.frozendawn.reactor.scram_wait"), AMBER);
        else line(g, 7, data(TARGET) >= 0 ? Component.translatable("screen.frozendawn.reactor.target", kw(TARGET))
                : Component.translatable("screen.frozendawn.reactor.manual"), MUTED);
        if (data(FAILURE) >= 0 && flag(F_AUTO) && !flag(F_SCRAM) && climb > 0 && c < ReactorPhysics.AUTO_SCRAM_C)
            line(g, 8, Component.translatable("screen.frozendawn.reactor.auto_scram_in", (int) Math.ceil((ReactorPhysics.AUTO_SCRAM_C - c) / climb)), AMBER);
        else if (data(FAILURE) >= 0 && !flag(F_AUTO)) line(g, 8, Component.translatable("screen.frozendawn.reactor.failure", data(FAILURE)), RED);
        else if (flag(F_FROZEN)) line(g, 8, Component.translatable("screen.frozendawn.reactor.frozen"), RED);
        else if (!flag(F_AUTO)) {
            g.fill(6, 110, 170, 121, 0xFF5A3A12);
            line(g, 8, Component.translatable("screen.frozendawn.reactor.auto_disabled"), AMBER);
        }
        g.drawString(font, Component.translatable("screen.frozendawn.reactor.rod_slots"), 12, ReactorControllerMenu.ROD_Y - 9, MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }
    private void line(GuiGraphics g, int row, Component text, int color) { g.drawString(font, text, 10, 30 + row * 10 + (row > 0 ? 1 : 0), color, false); }
    private String kw(int index) { return String.format(Locale.ROOT, "%.1f", data(index) / 10.0); }
    private Component shellLine() {
        if (flag(F_SHELL)) return Component.translatable("screen.frozendawn.reactor.shell.valid");
        var problem = ReactorMultiblock.Problem.values()[Math.clamp(data(SHELL), 0, ReactorMultiblock.Problem.values().length - 1)];
        var reason = Component.translatable("screen.frozendawn.reactor.shell." + problem.name().toLowerCase(Locale.ROOT));
        var pos = menu.pos();
        if (pos == null || minecraft == null || minecraft.level == null || data(SHELL_AT) < 0
                || !(minecraft.level.getBlockState(pos).getBlock() instanceof ReactorControllerBlock)) return Component.translatable("screen.frozendawn.reactor.shell.invalid", reason);
        var at = ReactorMultiblock.offsetPos(ReactorMultiblock.core(pos, minecraft.level.getBlockState(pos).getValue(ReactorControllerBlock.FACING)), data(SHELL_AT));
        return Component.translatable("screen.frozendawn.reactor.shell.invalid_at", reason, at.getX(), at.getY(), at.getZ());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        if (modal != 0) { renderBackground(g, mx, my, partial); renderModal(g, mx, my); return; }
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }
    private void renderModal(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + 14, y = topPos + 10, w = imageWidth - 28;
        g.pose().pushPose(); g.pose().translate(0, 0, 300);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101418);
        g.fill(x, y, x + w, topPos + imageHeight - 6, 0xFF1D2A31); g.fill(x, y, x + w, y + 2, AMBER);
        String base = "screen.frozendawn.reactor.confirm." + (modal == 1 ? "first" : "second");
        g.drawString(font, Component.translatable(base + ".title"), x + 8, y + 8, AMBER, false);
        int ty = y + 22;
        for (var line : modal == 1 ? List.of("intro", "history", "cydonia", "kestrel", "indemnity") : List.of("warned", "logged")) {
            for (FormattedCharSequence wrapped : font.split(Component.translatable(base + "." + line), w - 16)) {
                g.drawString(font, wrapped, x + 8, ty, line.equals("history") ? MUTED : TEXT, false); ty += 10;
            }
            ty += 3;
        }
        if (modal == 2 && holdTicks > 0) g.fill(hold.getX(), hold.getY() + hold.getHeight(), hold.getX() + hold.getWidth() * holdTicks / HOLD_TICKS, hold.getY() + hold.getHeight() + 3, RED);
        g.pose().popPose();
        for (var b : modal == 1 ? List.of(keep, understand) : List.of(cancel, hold)) {
            g.pose().pushPose(); g.pose().translate(0, 0, 310); b.render(g, mouseX, mouseY, 0); g.pose().popPose();
        }
    }

    /** Manual insertion. Moving it hands control back from load-follow. */
    private final class InsertionSlider extends AbstractSliderButton {
        private int sent = -1;
        private boolean dragging;
        InsertionSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), 0); updateMessage(); }
        void sync(double server) { if (!dragging && Math.abs(server - value) > 0.004) { value = Math.clamp(server, 0, 1); sent = (int) Math.round(value * 100); updateMessage(); } }
        @Override protected void updateMessage() { setMessage(Component.translatable("screen.frozendawn.reactor.insertion", (int) Math.round(value * 100))); }
        @Override protected void applyValue() {
            int percent = (int) Math.round(value * 100);
            if (percent != sent) { sent = percent; send(ReactorControllerMenu.INSERTION + percent); }
        }
        @Override public void onClick(double mx, double my) { dragging = true; super.onClick(mx, my); }
        @Override public void onRelease(double mx, double my) { dragging = false; super.onRelease(mx, my); }
    }
}
