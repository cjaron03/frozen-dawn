package com.frozendawn.client;

import com.frozendawn.block.IcarusControllerBlock;
import com.frozendawn.block.IcarusControllerMenu;
import com.frozendawn.block.IcarusStructure;
import com.frozendawn.network.IcarusSettingsPayload;
import com.frozendawn.power.IcarusBlast;
import com.frozendawn.power.IcarusPhysics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.frozendawn.block.IcarusControllerBlockEntity.*;

/** AMR-1 Icarus Controller. The readout fills the left panel; Start, Cole Protocol, CLIP and the blueprint toggle sit on the
 * right; the three settings sliders run along the bottom. CLIP fires only after its button is held for a second. While the
 * structure is incomplete the panel lists what is still to build instead of the readout. */
public final class IcarusControllerScreen extends AbstractContainerScreen<IcarusControllerMenu> {
    private static final int BG = 0xFF28353D, PANEL = 0xFF152128, TEXT = 0xFFE0E6E8, MUTED = 0xFF9BAAB1, GREEN = 0xFF9CD5A5,
            AMBER = 0xFFE5A064, RED = 0xFFE5534B, CYAN = 0xFF64C9CC, CLIP_HOLD_TICKS = 20;
    private static final int STATUS_Y = 25, PANEL_TOP = 37, PANEL_ROWS = 14, SLIDER_Y = 192;
    private Button start, cole, clip;
    private Setting output, shield, feed;
    private boolean clipHolding;
    private int clipTicks;
    private List<Component> remaining = List.of();

    public IcarusControllerScreen(IcarusControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = IcarusControllerMenu.WIDTH; imageHeight = IcarusControllerMenu.HEIGHT;
    }
    private int data(int i) { return menu.data().get(i); }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override protected void init() {
        super.init();
        int x = leftPos + imageWidth - 70, y = topPos + PANEL_TOP;
        start = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.start"), b -> send(BUTTON_START)).bounds(x, y, 62, 20).build());
        cole = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.cole"), b -> send(BUTTON_COLE)).bounds(x, y + 23, 62, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.icarus.cole.tooltip"))).build());
        clip = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.clip").withStyle(ChatFormatting.RED), b -> {}).bounds(x, y + 46, 62, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.icarus.clip.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.blueprint"), b -> send(BUTTON_BLUEPRINT)).bounds(x, y + 69, 62, 20).build());
        int w = (imageWidth - 24) / 3, sx = leftPos + 8, sy = topPos + SLIDER_Y;
        output = addRenderableWidget(new Setting(sx, sy, w, OUTPUT_KW, (int) (IcarusPhysics.MIN_OUTPUT_MW * 1000), (int) (IcarusPhysics.MAX_OUTPUT_MW * 1000), 50,
                v -> Component.translatable("screen.frozendawn.icarus.output", String.format(Locale.ROOT, "%.2f", v / 1000.0))));
        shield = addRenderableWidget(new Setting(sx + w + 4, sy, w, SHIELD_SET, 200, 1000, 50,
                v -> Component.translatable("screen.frozendawn.icarus.shield_drive", v / 10)));
        feed = addRenderableWidget(new Setting(sx + 2 * (w + 4), sy, w, FEED, 0, 60, 1,
                v -> Component.translatable("screen.frozendawn.icarus.feed", v)));
    }
    private IcarusPhysics.State state() { return pick(IcarusPhysics.State.values(), data(STATE)); }
    private IcarusPhysics.Status status() { return pick(IcarusPhysics.Status.values(), data(STATUS)); }
    private static <E> E pick(E[] values, int i) { return values[Math.clamp(i, 0, values.length - 1)]; }
    private void sendSettings() {
        var pos = menu.pos();
        if (pos != null) PacketDistributor.sendToServer(new IcarusSettingsPayload(pos, output.current(), shield.current(), feed.current()));
    }

    @Override protected void containerTick() {
        super.containerTick();
        var s = state();
        start.active = s == IcarusPhysics.State.COLD || s == IcarusPhysics.State.CLIPPED;
        cole.active = s != IcarusPhysics.State.COLD && s != IcarusPhysics.State.DETONATED && s.ordinal() < IcarusPhysics.State.COLE_RAMP_DOWN.ordinal();
        clip.active = s.ordinal() >= IcarusPhysics.State.POWER_CHECK.ordinal() && s.ordinal() <= IcarusPhysics.State.RUNNING.ordinal();
        if (!clip.active) { clipHolding = false; clipTicks = 0; }
        if (clipHolding && ++clipTicks >= CLIP_HOLD_TICKS) { send(BUTTON_CLIP); clipHolding = false; clipTicks = 0; }
        output.sync(data(OUTPUT_KW)); shield.sync(data(SHIELD_SET)); feed.sync(data(FEED));
        remaining = menu.flag(F_VALID) ? List.of() : remaining();
    }
    /** What is still to build, read from the client's own copy of the blocks. */
    private List<Component> remaining() {
        var pos = menu.pos(); var level = minecraft == null ? null : minecraft.level;
        if (pos == null || level == null || !(level.getBlockState(pos).getBlock() instanceof IcarusControllerBlock)) return List.of();
        var lines = new ArrayList<Component>();
        for (var e : IcarusStructure.remaining(level, pos, level.getBlockState(pos).getValue(IcarusControllerBlock.FACING)).entrySet())
            lines.add(Component.translatable("message.frozendawn.icarus.blueprint_part", e.getValue(), e.getKey().getName()));
        return lines;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (clip.active && clip.isMouseOver(mx, my) && button == 0) { clipHolding = true; clipTicks = 0; setFocused(clip); return true; }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (clipHolding && button == 0) { clipHolding = false; clipTicks = 0; }
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        boolean activate = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
        if (getFocused() == clip && clip.active && activate) { if (!clipHolding) { clipHolding = true; clipTicks = 0; } return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean keyReleased(int key, int scan, int mods) {
        if (clipHolding && getFocused() == clip) { clipHolding = false; clipTicks = 0; }
        return super.keyReleased(key, scan, mods);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.fill(x + 6, y + PANEL_TOP, x + imageWidth - 76, y + PANEL_TOP + 4 + PANEL_ROWS * 10, PANEL);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, TEXT, false);
        var s = state();
        var stateText = Component.translatable("screen.frozendawn.icarus.state", Component.translatable("icarus.frozendawn.state." + s.name().toLowerCase(Locale.ROOT)));
        if (data(STEP_LEFT) > 0) stateText.append(Component.literal(" " + clock(data(STEP_LEFT))));
        int stateX = imageWidth - 8 - font.width(stateText);
        g.drawString(font, stateText, stateX, STATUS_Y, MUTED, false);
        String structure = structureLine().getString();
        if (font.width(structure) > stateX - 16) structure = font.plainSubstrByWidth(structure, stateX - 16 - font.width("...")) + "...";
        g.drawString(font, structure, 8, STATUS_Y, menu.flag(F_VALID) ? GREEN : AMBER, false);
        if (!menu.flag(F_VALID) && s == IcarusPhysics.State.COLD) {
            line(g, 0, Component.translatable("screen.frozendawn.icarus.remaining"), AMBER);
            for (int i = 0; i < Math.min(remaining.size(), PANEL_ROWS - 1); i++) line(g, i + 1, remaining.get(i), TEXT);
            return;
        }
        var status = status();
        line(g, 0, Component.translatable("icarus.frozendawn.status." + status.name().toLowerCase(Locale.ROOT)), statusColor(status));
        var alarm = pick(IcarusPhysics.Alarm.values(), data(ALARM)); var cascade = pick(IcarusPhysics.Cascade.values(), data(CASCADE));
        line(g, 1, Component.translatable("screen.frozendawn.icarus.cascade",
                Component.translatable("icarus.frozendawn.cascade." + cascade.name().toLowerCase(Locale.ROOT)),
                Component.translatable("icarus.frozendawn.alarm." + alarm.name().toLowerCase(Locale.ROOT))), alarmColor(alarm));
        line(g, 2, Component.translatable("screen.frozendawn.icarus.power", mw(GROSS), mw(SHIELD_DRAW), mw(NET)), CYAN);
        line(g, 3, Component.translatable("screen.frozendawn.icarus.request", data(REQUEST)), MUTED);
        int coreC = data(CORE_C);
        line(g, 4, Component.translatable("screen.frozendawn.icarus.core", coreC, (int) IcarusPhysics.RED_LINE_C),
                coreC >= IcarusPhysics.RED_LINE_C ? RED : coreC >= IcarusPhysics.RED_LINE_C * 0.85 ? AMBER : TEXT);
        line(g, 5, Component.translatable("screen.frozendawn.icarus.shield", String.format(Locale.ROOT, "%.1f", data(SHIELD) / 100.0),
                String.format(Locale.ROOT, "%.1f", data(RESERVE) / 10.0), (int) IcarusPhysics.RESERVE_MJ), data(SHIELD) < 3000 && s != IcarusPhysics.State.COLD ? AMBER : TEXT);
        line(g, 6, Component.translatable("screen.frozendawn.icarus.antimatter", String.format(Locale.ROOT, "%.2f", data(CHARGE) / 100.0), data(EMPTIES), data(MATTER)), TEXT);
        line(g, 7, Component.translatable("screen.frozendawn.icarus.eta.shield", eta(ETA_SHIELD), eta(ETA_RESERVE)), MUTED);
        line(g, 8, Component.translatable("screen.frozendawn.icarus.eta.core", eta(ETA_RED_LINE), eta(ETA_ANTIMATTER)), MUTED);
        long now = Math.round(data(CHARGE) / 100.0 * IcarusPhysics.CAPSULE), next = Math.round(data(NEXT_CHARGE) / 100.0 * IcarusPhysics.CAPSULE);
        line(g, 9, Component.translatable("screen.frozendawn.icarus.blast", (int) Math.round(IcarusBlast.radius(now)), (int) Math.round(IcarusBlast.radius(next))), AMBER);
        if (!menu.flag(F_COMMISSIONED)) line(g, 10, Component.translatable("screen.frozendawn.icarus.uncommissioned"), MUTED);
    }
    private static int statusColor(IcarusPhysics.Status s) {
        return switch (s) {
            case OK -> GREEN;
            case SAFE_TO_DISMANTLE -> CYAN;
            case MATTER_STARVED, RESERVE_IN_USE -> RED;
            default -> AMBER;
        };
    }
    private static int alarmColor(IcarusPhysics.Alarm a) {
        return switch (a) { case NONE -> GREEN; case CAUTION -> AMBER; case WARNING, CRITICAL -> RED; };
    }
    private void line(GuiGraphics g, int row, Component text, int color) { g.drawString(font, text, 10, PANEL_TOP + 4 + row * 10, color, false); }
    private String mw(int index) { return String.format(Locale.ROOT, "%.2f", data(index) / 1000.0); }
    private Component eta(int index) { int s = data(index); return s < 0 ? Component.translatable("screen.frozendawn.icarus.eta.none") : Component.literal(clock(s)); }
    private static String clock(int seconds) { return seconds >= 3600 ? String.format(Locale.ROOT, "%dh%02dm", seconds / 3600, seconds / 60 % 60) : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    private Component structureLine() {
        if (menu.flag(F_VALID)) return Component.translatable("screen.frozendawn.icarus.structure.valid");
        var problem = pick(IcarusStructure.Problem.values(), data(STRUCTURE));
        return Component.translatable("screen.frozendawn.icarus.structure.invalid",
                Component.translatable("message.frozendawn.icarus.stage." + problem.name().toLowerCase(Locale.ROOT)));
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (clipTicks > 0) g.fill(clip.getX(), clip.getY() + clip.getHeight(), clip.getX() + clip.getWidth() * clipTicks / CLIP_HOLD_TICKS, clip.getY() + clip.getHeight() + 2, RED);
        renderTooltip(g, mx, my);
    }

    /** One setting in its payload units, snapped to step; sent with the other two when released. */
    private final class Setting extends AbstractSliderButton {
        private final int index, min, max, step;
        private final IntFunction<Component> label;
        private boolean dragging;
        Setting(int x, int y, int w, int index, int min, int max, int step, IntFunction<Component> label) {
            super(x, y, w, 20, Component.empty(), 0);
            this.index = index; this.min = min; this.max = max; this.step = step; this.label = label; updateMessage();
        }
        int current() { return min + (int) Math.round(value * (max - min) / step) * step; }
        void sync(int server) {
            if (dragging || server == current()) return;
            value = Math.clamp((server - min) / (double) (max - min), 0, 1); updateMessage();
        }
        @Override protected void updateMessage() { setMessage(label.apply(current())); }
        @Override protected void applyValue() {}
        @Override public void onClick(double mx, double my) { dragging = true; super.onClick(mx, my); }
        @Override public void onRelease(double mx, double my) { dragging = false; super.onRelease(mx, my); sendSettings(); }
        @Override public boolean keyPressed(int key, int scan, int mods) {
            boolean moved = super.keyPressed(key, scan, mods);
            if (moved) sendSettings();
            return moved;
        }
    }
}
