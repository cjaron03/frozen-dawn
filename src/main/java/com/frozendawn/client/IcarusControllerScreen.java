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
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.frozendawn.block.IcarusControllerBlockEntity.*;

/** AMR-1 Icarus Controller. From the top: the current step with its clock and progress bar; a box with the status and
 * what to do next; shield, core and reserve gauges; the readout lines; the Start, Cole Protocol, CLIP and Blueprint row;
 * and the three settings sliders. CLIP fires only after its button is held for a second. While the structure is
 * incomplete the readout lists what is still to build. Every line is fitted to the panel width. */
public final class IcarusControllerScreen extends AbstractContainerScreen<IcarusControllerMenu> {
    private static final int BG = 0xFF28353D, PANEL = 0xFF152128, TEXT = 0xFFE0E6E8, MUTED = 0xFF9BAAB1, GREEN = 0xFF9CD5A5,
            AMBER = 0xFFE5A064, RED = 0xFFE5534B, CYAN = 0xFF64C9CC, TRACK = 0xFF0B1418, CLIP_HOLD_TICKS = 20;
    private static final int LEFT = 10, TEXT_WIDTH = 236, STEP_Y = 20, BAR_Y = 31, BOX_TOP = 38, BOX_BOTTOM = 71,
            READOUT_TOP = 74, READOUT_BOTTOM = 161, LINES_Y = 112, BUTTON_Y = 166, SLIDER_Y = 192, GAUGE_X = 132, GAUGE_W = 112;
    private Button start, cole, clip;
    private Setting output, shield, feed;
    private boolean clipHolding;
    private int clipTicks, stepTotal;
    private IcarusPhysics.State shownState;
    private List<Component> remaining = List.of();

    public IcarusControllerScreen(IcarusControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = IcarusControllerMenu.WIDTH; imageHeight = IcarusControllerMenu.HEIGHT;
    }
    private int data(int i) { return menu.data().get(i); }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override protected void init() {
        super.init();
        // Start 44, Cole Protocol 74, CLIP 44, Blueprint 58, 6 apart: 238 wide, centred.
        int x = leftPos + (imageWidth - 238) / 2, y = topPos + BUTTON_Y;
        start = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.start"), b -> send(BUTTON_START)).bounds(x, y, 44, 20).build());
        cole = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.cole"), b -> send(BUTTON_COLE)).bounds(x + 50, y, 74, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.icarus.cole.tooltip"))).build());
        clip = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.clip").withStyle(ChatFormatting.RED), b -> {}).bounds(x + 130, y, 44, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.icarus.clip.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.blueprint"), b -> send(BUTTON_BLUEPRINT)).bounds(x + 180, y, 58, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.icarus.blueprint.tooltip"))).build());
        int w = (imageWidth - 24) / 3, sx = leftPos + (imageWidth - 3 * w - 8) / 2, sy = topPos + SLIDER_Y;
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
        // A step seen starting runs from its own clock (a restart's steps are shorter); one already under way when the
        // screen opens is measured against its nominal length.
        int left = data(STEP_LEFT);
        if (s != shownState) { stepTotal = shownState == null ? Math.max(left, nominal(s)) : left; shownState = s; }
        else stepTotal = Math.max(stepTotal, left);
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
        g.fill(x + 6, y + BOX_TOP, x + imageWidth - 6, y + BOX_BOTTOM, PANEL);
        g.fill(x + 6, y + READOUT_TOP, x + imageWidth - 6, y + READOUT_BOTTOM, PANEL);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, fit(title), LEFT, 6, TEXT, false);
        var s = state();
        renderStep(g, s);
        boolean building = !menu.flag(F_VALID) && s == IcarusPhysics.State.COLD;
        var status = status();
        Component head = s == IcarusPhysics.State.COLD ? structureLine()
                : Component.translatable("icarus.frozendawn.status." + status.name().toLowerCase(Locale.ROOT));
        int headColor = s == IcarusPhysics.State.COLD ? (menu.flag(F_VALID) ? GREEN : AMBER) : statusColor(status);
        g.drawString(font, fit(head), LEFT, BOX_TOP + 4, headColor, false);
        var hint = font.split(hint(s, status), TEXT_WIDTH);
        for (int i = 0; i < Math.min(2, hint.size()); i++) g.drawString(font, hint.get(i), LEFT, BOX_TOP + 15 + i * 10, TEXT, false);
        if (building) {
            line(g, READOUT_TOP + 4, Component.translatable("screen.frozendawn.icarus.remaining"), AMBER);
            for (int i = 0; i < Math.min(remaining.size(), 7); i++) line(g, READOUT_TOP + 16 + i * 10, remaining.get(i), TEXT);
            return;
        }
        int shieldPct = data(SHIELD), coreC = data(CORE_C), reserve = data(RESERVE);
        boolean live = s != IcarusPhysics.State.COLD;
        gauge(g, READOUT_TOP + 5, Component.translatable("screen.frozendawn.icarus.gauge.shield", String.format(Locale.ROOT, "%.1f", shieldPct / 100.0)),
                shieldPct / 10000.0, !live ? MUTED : shieldPct >= 6000 ? GREEN : shieldPct >= 3000 ? AMBER : RED);
        gauge(g, READOUT_TOP + 17, Component.translatable("screen.frozendawn.icarus.gauge.core", coreC, (int) IcarusPhysics.RED_LINE_C),
                coreC / IcarusPhysics.RED_LINE_C, coreC >= IcarusPhysics.RED_LINE_C ? RED : coreC >= IcarusPhysics.RED_LINE_C * 0.85 ? AMBER : CYAN);
        var reserveText = Component.translatable("screen.frozendawn.icarus.gauge.reserve", String.format(Locale.ROOT, "%.0f", reserve / 10.0), (int) IcarusPhysics.RESERVE_MJ);
        if (data(ETA_RESERVE) >= 0) reserveText.append(" ").append(Component.translatable("screen.frozendawn.icarus.gauge.out", eta(ETA_RESERVE)));
        gauge(g, READOUT_TOP + 29, reserveText, reserve / 10.0 / IcarusPhysics.RESERVE_MJ, data(ETA_RESERVE) >= 0 ? RED : CYAN);
        var alarm = pick(IcarusPhysics.Alarm.values(), data(ALARM)); var cascade = pick(IcarusPhysics.Cascade.values(), data(CASCADE));
        line(g, LINES_Y - 2, Component.translatable("screen.frozendawn.icarus.cascade",
                Component.translatable("icarus.frozendawn.cascade." + cascade.name().toLowerCase(Locale.ROOT)),
                Component.translatable("icarus.frozendawn.alarm." + alarm.name().toLowerCase(Locale.ROOT))), live ? alarmColor(alarm) : MUTED);
        line(g, LINES_Y + 8, Component.translatable("screen.frozendawn.icarus.power", mw(GROSS), mw(SHIELD_DRAW), mw(NET)), CYAN);
        line(g, LINES_Y + 18, Component.translatable("screen.frozendawn.icarus.supplies", data(REQUEST),
                String.format(Locale.ROOT, "%.2f", data(CHARGE) / 100.0), data(MATTER)), TEXT);
        line(g, LINES_Y + 28, Component.translatable("screen.frozendawn.icarus.etas", eta(ETA_SHIELD), eta(ETA_RED_LINE), eta(ETA_ANTIMATTER)), MUTED);
        long now = Math.round(data(CHARGE) / 100.0 * IcarusPhysics.CAPSULE), next = Math.round(data(NEXT_CHARGE) / 100.0 * IcarusPhysics.CAPSULE);
        line(g, LINES_Y + 38, Component.translatable("screen.frozendawn.icarus.blast", (int) Math.round(IcarusBlast.radius(now)), (int) Math.round(IcarusBlast.radius(next))), AMBER);
    }
    /** The step line, its clock on the right, and a bar for any timed step. */
    private void renderStep(GuiGraphics g, IcarusPhysics.State s) {
        var name = Component.translatable("icarus.frozendawn.state." + s.name().toLowerCase(Locale.ROOT));
        int number = step(s);
        Component text = number > 0 ? Component.translatable("screen.frozendawn.icarus.step", number, name) : Component.translatable("screen.frozendawn.icarus.state", name);
        int left = data(STEP_LEFT), clockW = left > 0 ? font.width(clock(left)) + 6 : 0;
        if (left > 0) g.drawString(font, clock(left), LEFT + TEXT_WIDTH - font.width(clock(left)), STEP_Y, TEXT, false);
        g.drawString(font, fit(text, TEXT_WIDTH - clockW), LEFT, STEP_Y, MUTED, false);
        g.fill(LEFT, BAR_Y, LEFT + TEXT_WIDTH, BAR_Y + 3, TRACK);
        if (left > 0 && stepTotal > 0) g.fill(LEFT, BAR_Y, LEFT + TEXT_WIDTH * (stepTotal - left) / stepTotal, BAR_Y + 3, CYAN);
        else if (s == IcarusPhysics.State.RUNNING) g.fill(LEFT, BAR_Y, LEFT + TEXT_WIDTH, BAR_Y + 3, GREEN);
    }
    private static int step(IcarusPhysics.State s) {
        return switch (s) {
            case POWER_CHECK -> 1; case RESERVE_CHARGE -> 2; case STABILIZER_SPINUP, COMMISSIONING -> 3; case INJECTION -> 4;
            case PRIMING -> 5; case IGNITION -> 6; case RAMP -> 7; default -> 0;
        };
    }
    private static int nominal(IcarusPhysics.State s) {
        return switch (s) {
            case POWER_CHECK -> IcarusPhysics.POWER_CHECK_S; case RESERVE_CHARGE -> IcarusPhysics.RESERVE_CHARGE_S;
            case STABILIZER_SPINUP -> IcarusPhysics.SPINUP_S; case COMMISSIONING -> IcarusPhysics.COMMISSIONING_S;
            case INJECTION -> IcarusPhysics.INJECTION_S; case PRIMING -> IcarusPhysics.PRIMING_S; case IGNITION -> IcarusPhysics.IGNITION_S;
            case RAMP -> IcarusPhysics.RAMP_S; case COLE_RAMP_DOWN -> IcarusPhysics.COLE_RAMP_DOWN_S; case COLE_COOL -> IcarusPhysics.COLE_COOL_S;
            case COLE_RECOVER -> IcarusPhysics.COLE_RECOVER_S; case COLE_DISCHARGE -> IcarusPhysics.DISCHARGE_S; default -> 0;
        };
    }
    /** What the player should do now, or what the reactor is doing. */
    private Component hint(IcarusPhysics.State s, IcarusPhysics.Status status) {
        String key = switch (status) {
            case HOLD_STRUCTURE -> "build"; case HOLD_POWER -> "power"; case HOLD_CAPSULES -> "capsules"; case HOLD_MATTER -> "matter";
            case HOLD_CAPSULE_SPACE -> "capsule_space"; case MATTER_STARVED -> "starved"; case RESERVE_IN_USE -> "reserve";
            case SAFE_TO_DISMANTLE -> "dismantle";
            default -> switch (s) {
                case COLD -> !menu.flag(F_VALID) ? "build"
                        : data(NEXT_CHARGE) < IcarusPhysics.MIN_START_CAPSULES * 100 ? "capsules"
                        : data(MATTER) <= 0 ? "matter" : menu.flag(F_COMMISSIONED) ? "ready" : "ready_first";
                case COLE_RAMP_DOWN, COLE_COOL, COLE_RECOVER, COLE_DISCHARGE -> "cole";
                case DETONATED -> "detonated";
                default -> s.name().toLowerCase(Locale.ROOT);
            };
        };
        return Component.translatable("screen.frozendawn.icarus.hint." + key);
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
    private void gauge(GuiGraphics g, int y, Component label, double fraction, int color) {
        g.drawString(font, fit(label, GAUGE_X - LEFT - 4), LEFT, y, TEXT, false);
        g.fill(GAUGE_X, y + 1, GAUGE_X + GAUGE_W, y + 7, TRACK);
        g.fill(GAUGE_X, y + 1, GAUGE_X + (int) Math.round(GAUGE_W * Math.clamp(fraction, 0, 1)), y + 7, color);
    }
    private void line(GuiGraphics g, int y, Component text, int color) { g.drawString(font, fit(text), LEFT, y, color, false); }
    private FormattedCharSequence fit(Component text) { return fit(text, TEXT_WIDTH); }
    /** text cut to width with an ellipsis, so no line runs off the panel. */
    private FormattedCharSequence fit(Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        return Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(text, width - font.width("...")), Component.literal("...")));
    }
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
