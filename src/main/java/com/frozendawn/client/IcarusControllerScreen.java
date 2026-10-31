package com.frozendawn.client;

import com.frozendawn.block.IcarusControllerBlock;
import com.frozendawn.block.IcarusControllerMenu;
import com.frozendawn.block.IcarusStructure;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.IcarusSettingsPayload;
import com.frozendawn.power.IcarusBlast;
import com.frozendawn.power.IcarusPhysics;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.frozendawn.block.IcarusControllerBlockEntity.*;

/** AMR-1 Icarus Controller. From the top: the current step with its clock and progress bar; a box with the status, the
 * cascade and what to do next; shield, core and reserve gauges with their values; a grid of the key numbers and the
 * predictions; the Start, Cole Protocol, CLIP and Blueprint row; and the three settings sliders. The finer figures sit in
 * tooltips on the gauges and cells. CLIP fires only after its button is held for a second. While the structure is
 * incomplete the gauges and grid give way to what is still to build. Every line is fitted to its space. */
public final class IcarusControllerScreen extends AbstractContainerScreen<IcarusControllerMenu> {
    private static final int BG = 0xFF28353D, PANEL = 0xFF152128, TEXT = 0xFFE0E6E8, MUTED = 0xFF9BAAB1, GREEN = 0xFF9CD5A5,
            AMBER = 0xFFE5A064, RED = 0xFFE5534B, CYAN = 0xFF64C9CC, TRACK = 0xFF0B1418, CLIP_HOLD_TICKS = 20;
    private static final int LEFT = 10, TEXT_WIDTH = 236, STEP_Y = 20, BAR_Y = 31, BOX_TOP = 38, BOX_BOTTOM = 71,
            GAUGE_TOP = 74, GAUGE_BOTTOM = 116, GAUGE_ROW = 13, GRID_TOP = 119, GRID_BOTTOM = 161, GRID_ROW = 21, CELL_W = 59,
            BUTTON_Y = 166, SLIDER_Y = 192, BAR_X = 56, BAR_W = 134, PREVIEW = 34, TOP_WIDTH = TEXT_WIDTH - PREVIEW - 6;
    private static final int PREVIEW_MATTER = 0xFFB878, PREVIEW_ANTI = 0xB47CFF, PREVIEW_SPECK = 0xB45CFF;
    private Button start, cole, clip;
    private Setting output, shield, feed;
    private boolean clipHolding;
    private int clipTicks, stepTotal;
    private SoundInstance clipArm;
    private IcarusPhysics.State shownState;
    private float previewGlow, previewGlowO;
    private List<Component> remaining = List.of();

    public IcarusControllerScreen(IcarusControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = IcarusControllerMenu.WIDTH; imageHeight = IcarusControllerMenu.HEIGHT;
    }
    private int data(int i) { return menu.data().get(i); }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    /** A command or setting the Controller takes: the button's own click, then its confirm chirp. */
    private void confirmed(int id) { send(id); ui(ModSounds.ICARUS_UI_CONFIRM.get(), 1.0f); }
    private SoundInstance ui(SoundEvent sound, float pitch) {
        var instance = SimpleSoundInstance.forUI(sound, pitch, 0.6f);
        if (minecraft != null) minecraft.getSoundManager().play(instance);
        return instance;
    }
    /** Holding CLIP arms it: the rising arming tone plays for the one-second hold and stops if the hold is let go. */
    private void holdClip() { clipHolding = true; clipTicks = 0; stopArming(); clipArm = ui(ModSounds.ICARUS_CLIP_ARM.get(), 1.0f); }
    private void dropClip() { clipHolding = false; clipTicks = 0; stopArming(); }
    private void stopArming() { if (clipArm != null && minecraft != null) minecraft.getSoundManager().stop(clipArm); clipArm = null; }
    @Override public void removed() { stopArming(); super.removed(); }

    @Override protected void init() {
        super.init();
        // Start 44, Cole Protocol 74, CLIP 44, Blueprint 58, 6 apart: 238 wide, centred.
        int x = leftPos + (imageWidth - 238) / 2, y = topPos + BUTTON_Y;
        start = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.start"), b -> confirmed(BUTTON_START)).bounds(x, y, 44, 20).build());
        cole = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.cole"), b -> confirmed(BUTTON_COLE)).bounds(x + 50, y, 74, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.icarus.cole.tooltip"))).build());
        clip = addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.clip").withStyle(ChatFormatting.RED), b -> {}).bounds(x + 130, y, 44, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.frozendawn.icarus.clip.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.frozendawn.icarus.blueprint"), b -> confirmed(BUTTON_BLUEPRINT)).bounds(x + 180, y, 58, 20)
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
        if (pos == null) return;
        PacketDistributor.sendToServer(new IcarusSettingsPayload(pos, output.current(), shield.current(), feed.current()));
        ui(ModSounds.ICARUS_UI_CONFIRM.get(), 1.25f);
    }

    @Override protected void containerTick() {
        super.containerTick();
        var s = state();
        start.active = s == IcarusPhysics.State.COLD || s == IcarusPhysics.State.CLIPPED;
        cole.active = s != IcarusPhysics.State.COLD && s != IcarusPhysics.State.DETONATED && s.ordinal() < IcarusPhysics.State.COLE_RAMP_DOWN.ordinal();
        clip.active = s.ordinal() >= IcarusPhysics.State.POWER_CHECK.ordinal() && s.ordinal() <= IcarusPhysics.State.RUNNING.ordinal();
        if (!clip.active && clipHolding) dropClip();
        if (!clip.active) clipTicks = 0;
        if (clipHolding && ++clipTicks >= CLIP_HOLD_TICKS) { send(BUTTON_CLIP); clipHolding = false; clipTicks = 0; clipArm = null; }
        output.sync(data(OUTPUT_KW)); shield.sync(data(SHIELD_SET)); feed.sync(data(FEED));
        previewGlowO = previewGlow;
        double heat = Math.clamp((data(CORE_C) - IcarusPhysics.AMBIENT_C) / (IcarusPhysics.CRITICAL_C - IcarusPhysics.AMBIENT_C), 0, 1);
        double out = Math.clamp(data(GROSS) / 1000.0 / IcarusPhysics.MAX_OUTPUT_MW, 0, 1);
        float target = (float) switch (s) {
            case IGNITION -> 1;
            case RAMP, COLE_RAMP_DOWN -> 0.3 + 0.5 * out + 0.2 * heat;
            case RUNNING -> 0.06 + Math.clamp(data(GROSS) / 250.0, 0, 1) * (0.24 + 0.5 * out) + 0.2 * heat;
            case COLE_COOL, COLE_RECOVER -> 0.1 + 0.5 * heat;
            default -> 0;
        };
        previewGlow += (target - previewGlow) * 0.08f;
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
        if (clip.active && clip.isMouseOver(mx, my) && button == 0) { holdClip(); setFocused(clip); return true; }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (clipHolding && button == 0) dropClip();
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        boolean activate = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
        if (getFocused() == clip && clip.active && activate) { if (!clipHolding) holdClip(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean keyReleased(int key, int scan, int mods) {
        if (clipHolding && getFocused() == clip) dropClip();
        return super.keyReleased(key, scan, mods);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.fill(x + 6, y + BOX_TOP, x + imageWidth - 6, y + BOX_BOTTOM, PANEL);
        g.fill(x + 6, y + GAUGE_TOP, x + imageWidth - 6, y + GAUGE_BOTTOM, PANEL);
        g.fill(x + 6, y + GRID_TOP, x + imageWidth - 6, y + GRID_BOTTOM, PANEL);
        renderPreview(g, x + LEFT + TEXT_WIDTH - PREVIEW, y + 2, partial);
    }
    /** A live miniature of the core in the top corner: the shield's ring, the plasma's glow with its white heart, the two
     * rings turning, or the violet speck before ignition. It eases like the real core but reads only the synced data. */
    private void renderPreview(GuiGraphics g, int x, int y, float partial) {
        g.fill(x, y, x + PREVIEW, y + PREVIEW, PANEL);
        var s = state();
        float time = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partial;
        float glow = Mth.lerp(partial, previewGlowO, previewGlow), cx = x + PREVIEW / 2f, cy = y + PREVIEW / 2f;
        double shieldK = s == IcarusPhysics.State.COLD || s == IcarusPhysics.State.DETONATED ? 0 : Math.clamp(data(SHIELD) / 10000.0, 0, 1);
        int shieldRgb = data(SHIELD) >= 6000 ? 0x64C9CC : data(SHIELD) >= 3000 ? 0xE5A064 : 0xE5534B;
        if (shieldK > 0) for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            dot(g, cx + 15 * Math.cos(a), cy + 15 * Math.sin(a), shieldRgb, 0.35 + 0.5 * shieldK);
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        if (glow > 0.02f) {
            float heart = (float) Math.exp(-Math.pow(((time % 36) / 36 - 0.08) / 0.05, 2));
            sprite(g, cx, cy, 6 + 22 * glow, PREVIEW_MATTER, 0.9f * glow);
            sprite(g, cx, cy, 4 + 12 * glow, PREVIEW_ANTI, 0.8f * glow);
            sprite(g, cx, cy, 4 + 4 * glow + 2 * heart, 0xFFFFFF, Math.min(1, 0.7f + glow));
        } else if (s.ordinal() >= IcarusPhysics.State.INJECTION.ordinal() && s != IcarusPhysics.State.DETONATED && data(CHARGE) > 0) {
            sprite(g, cx, cy, 7 + Mth.sin(time * 0.4f), PREVIEW_SPECK, 0.9f);
        }
        g.setColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        if (glow > 0.02f) for (int ring = 0; ring < 2; ring++) {
            double tilt = ring == 0 ? 0.35 : -0.5, turn = time * (ring == 0 ? 0.05 : -0.07);
            for (int i = 0; i < 28; i++) {
                double a = turn + i * Math.PI * 2 / 28, ex = 12 * Math.cos(a), ey = 12 * Math.sin(a) * 0.3;
                dot(g, cx + ex * Math.cos(tilt) - ey * Math.sin(tilt), cy + ex * Math.sin(tilt) + ey * Math.cos(tilt), ring == 0 ? PREVIEW_MATTER : PREVIEW_ANTI, 0.8 * glow);
            }
        }
    }
    private static void dot(GuiGraphics g, double x, double y, int rgb, double alpha) {
        int px = (int) Math.floor(x), py = (int) Math.floor(y);
        g.fill(px, py, px + 1, py + 1, (int) (Math.clamp(alpha, 0, 1) * 255) << 24 | rgb);
    }
    private static void sprite(GuiGraphics g, float cx, float cy, float size, int rgb, float alpha) {
        g.setColor((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, Math.clamp(alpha, 0, 1));
        int half = Math.round(size / 2);
        g.blit(IcarusDraw.GLOW, Math.round(cx) - half, Math.round(cy) - half, half * 2, half * 2, 0, 0, 64, 64, 64, 64);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, fit(title, TOP_WIDTH), LEFT, 6, TEXT, false);
        var s = state();
        renderStep(g, s);
        boolean building = !menu.flag(F_VALID) && s == IcarusPhysics.State.COLD;
        var status = status();
        Component head = s == IcarusPhysics.State.COLD ? structureLine()
                : Component.translatable("icarus.frozendawn.status." + status.name().toLowerCase(Locale.ROOT));
        int headColor = s == IcarusPhysics.State.COLD ? (menu.flag(F_VALID) ? GREEN : AMBER) : statusColor(status);
        boolean live = s != IcarusPhysics.State.COLD;
        var alarm = pick(IcarusPhysics.Alarm.values(), data(ALARM)); var cascade = pick(IcarusPhysics.Cascade.values(), data(CASCADE));
        int chipW = 0;
        if (live && s != IcarusPhysics.State.DETONATED) {
            var chip = Component.translatable("screen.frozendawn.icarus.cascade_chip", Component.translatable("icarus.frozendawn.cascade." + cascade.name().toLowerCase(Locale.ROOT)));
            if (alarm != IcarusPhysics.Alarm.NONE) chip.append(" | ").append(Component.translatable("icarus.frozendawn.alarm." + alarm.name().toLowerCase(Locale.ROOT)));
            chipW = font.width(chip) + 8;
            g.drawString(font, chip, LEFT + TEXT_WIDTH - font.width(chip), BOX_TOP + 4, alarmColor(alarm), false);
        }
        g.drawString(font, fit(head, TEXT_WIDTH - chipW), LEFT, BOX_TOP + 4, headColor, false);
        var hint = font.split(hint(s, status), TEXT_WIDTH);
        for (int i = 0; i < Math.min(2, hint.size()); i++) g.drawString(font, hint.get(i), LEFT, BOX_TOP + 15 + i * 10, TEXT, false);
        if (building) {
            line(g, GAUGE_TOP + 4, Component.translatable("screen.frozendawn.icarus.remaining"), AMBER);
            for (int i = 0; i < Math.min(remaining.size(), 7); i++) line(g, GAUGE_TOP + 16 + i * 10, remaining.get(i), TEXT);
            return;
        }
        int shieldPct = data(SHIELD), coreC = data(CORE_C), reserve = data(RESERVE);
        gauge(g, 0, "screen.frozendawn.icarus.label.shield", shieldPct / 10000.0, !live ? MUTED : shieldPct >= 6000 ? GREEN : shieldPct >= 3000 ? AMBER : RED,
                String.format(Locale.ROOT, "%.1f%%", shieldPct / 100.0));
        gauge(g, 1, "screen.frozendawn.icarus.label.core", coreC / IcarusPhysics.RED_LINE_C,
                coreC >= IcarusPhysics.RED_LINE_C ? RED : coreC >= IcarusPhysics.RED_LINE_C * 0.85 ? AMBER : CYAN, coreC + " C");
        gauge(g, 2, "screen.frozendawn.icarus.label.reserve", reserve / 10.0 / IcarusPhysics.RESERVE_MJ, data(ETA_RESERVE) >= 0 ? RED : CYAN,
                String.format(Locale.ROOT, "%.0f MJ", reserve / 10.0));
        cell(g, 0, 0, "net", Component.literal(mw(NET) + " MW"), CYAN);
        cell(g, 1, 0, "capsules", Component.literal(String.format(Locale.ROOT, "%.2f", data(CHARGE) / 100.0)), TEXT);
        cell(g, 2, 0, "matter", Component.literal(String.valueOf(data(MATTER))), data(MATTER) > 0 ? TEXT : AMBER);
        cell(g, 3, 0, "blast", Component.literal(blastNow() + " m"), AMBER);
        cell(g, 0, 1, "shield_out", eta(ETA_SHIELD), data(ETA_SHIELD) >= 0 ? AMBER : TEXT);
        cell(g, 1, 1, "red_line", eta(ETA_RED_LINE), data(ETA_RED_LINE) >= 0 ? AMBER : TEXT);
        cell(g, 2, 1, "fuel_out", eta(ETA_ANTIMATTER), TEXT);
        cell(g, 3, 1, "reserve_out", eta(ETA_RESERVE), data(ETA_RESERVE) >= 0 ? RED : TEXT);
    }
    private int blastNow() { return (int) Math.round(IcarusBlast.radius(Math.round(data(CHARGE) / 100.0 * IcarusPhysics.CAPSULE))); }
    private int blastNext() { return (int) Math.round(IcarusBlast.radius(Math.round(data(NEXT_CHARGE) / 100.0 * IcarusPhysics.CAPSULE))); }
    /** The finer figures behind a gauge or cell under the mouse, in panel coordinates; empty elsewhere. */
    private List<Component> details(int x, int y) {
        if (!menu.flag(F_VALID) && state() == IcarusPhysics.State.COLD) return List.of();
        if (x < LEFT || x >= LEFT + TEXT_WIDTH) return List.of();
        if (y >= BOX_TOP && y < BOX_TOP + 13 && x > LEFT + TEXT_WIDTH / 2) {
            var alarm = pick(IcarusPhysics.Alarm.values(), data(ALARM)); var cascade = pick(IcarusPhysics.Cascade.values(), data(CASCADE));
            return List.of(Component.translatable("screen.frozendawn.icarus.cascade",
                    Component.translatable("icarus.frozendawn.cascade." + cascade.name().toLowerCase(Locale.ROOT)),
                    Component.translatable("icarus.frozendawn.alarm." + alarm.name().toLowerCase(Locale.ROOT))));
        }
        if (y >= GAUGE_TOP && y < GAUGE_BOTTOM) {
            return switch ((y - GAUGE_TOP - 2) / GAUGE_ROW) {
                case 0 -> List.of(Component.translatable("screen.frozendawn.icarus.power", mw(GROSS), mw(SHIELD_DRAW), mw(NET)));
                case 1 -> List.of(Component.translatable("screen.frozendawn.icarus.gauge.core", data(CORE_C), (int) IcarusPhysics.RED_LINE_C));
                case 2 -> List.of(Component.translatable("screen.frozendawn.icarus.gauge.reserve", String.format(Locale.ROOT, "%.0f", data(RESERVE) / 10.0), (int) IcarusPhysics.RESERVE_MJ));
                default -> List.of();
            };
        }
        if (y >= GRID_TOP && y < GRID_BOTTOM && y < GRID_TOP + GRID_ROW) {
            return switch ((x - LEFT) / CELL_W) {
                case 0 -> List.of(Component.translatable("screen.frozendawn.icarus.power", mw(GROSS), mw(SHIELD_DRAW), mw(NET)),
                        Component.translatable("screen.frozendawn.icarus.grid_draw", data(REQUEST)));
                case 1, 3 -> List.of(Component.translatable("screen.frozendawn.icarus.blast", blastNow(), blastNext()));
                default -> List.of();
            };
        }
        return List.of();
    }
    /** The step line, its clock on the right, and a bar for any timed step. */
    private void renderStep(GuiGraphics g, IcarusPhysics.State s) {
        var name = Component.translatable("icarus.frozendawn.state." + s.name().toLowerCase(Locale.ROOT));
        int number = step(s);
        Component text = number > 0 ? Component.translatable("screen.frozendawn.icarus.step", number, name) : Component.translatable("screen.frozendawn.icarus.state", name);
        int left = data(STEP_LEFT), clockW = left > 0 ? font.width(clock(left)) + 6 : 0;
        if (left > 0) g.drawString(font, clock(left), LEFT + TOP_WIDTH - font.width(clock(left)), STEP_Y, TEXT, false);
        g.drawString(font, fit(text, TOP_WIDTH - clockW), LEFT, STEP_Y, MUTED, false);
        g.fill(LEFT, BAR_Y, LEFT + TOP_WIDTH, BAR_Y + 3, TRACK);
        if (left > 0 && stepTotal > 0) g.fill(LEFT, BAR_Y, LEFT + TOP_WIDTH * (stepTotal - left) / stepTotal, BAR_Y + 3, CYAN);
        else if (s == IcarusPhysics.State.RUNNING) g.fill(LEFT, BAR_Y, LEFT + TOP_WIDTH, BAR_Y + 3, GREEN);
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
    /** A gauge row: its name, a bar, and its value on the right. */
    private void gauge(GuiGraphics g, int row, String label, double fraction, int color, String value) {
        int y = GAUGE_TOP + 4 + row * GAUGE_ROW;
        g.drawString(font, fit(Component.translatable(label), BAR_X - LEFT - 4), LEFT, y, MUTED, false);
        g.fill(BAR_X, y + 1, BAR_X + BAR_W, y + 7, TRACK);
        g.fill(BAR_X, y + 1, BAR_X + (int) Math.round(BAR_W * Math.clamp(fraction, 0, 1)), y + 7, color);
        g.drawString(font, value, LEFT + TEXT_WIDTH - font.width(value), y, TEXT, false);
    }
    /** One cell of the grid: a muted name over its value. */
    private void cell(GuiGraphics g, int column, int row, String key, Component value, int color) {
        int x = LEFT + column * CELL_W, y = GRID_TOP + 3 + row * GRID_ROW;
        g.drawString(font, fit(Component.translatable("screen.frozendawn.icarus.label." + key), CELL_W - 4), x, y, MUTED, false);
        g.drawString(font, fit(value, CELL_W - 4), x, y + 10, color, false);
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
        if (clipTicks > 0) {
            // The held CLIP glows red, deeper and pulsing faster as the hold nears its end, with the fill bar beneath.
            float k = Math.min(1, (clipTicks + (clipHolding ? partial : 0)) / CLIP_HOLD_TICKS);
            float pulse = 0.5f + 0.5f * Mth.sin((clipTicks + partial) * (0.6f + 1.4f * k));
            int a = (int) (40 + 150 * k * (0.6f + 0.4f * pulse));
            int x0 = clip.getX(), y0 = clip.getY(), x1 = x0 + clip.getWidth(), y1 = y0 + clip.getHeight();
            g.fill(x0, y0, x1, y1, a << 24 | 0xFF2A1A);
            int edge = (int) (120 + 135 * k) << 24 | 0xFF5040;
            g.fill(x0 - 1, y0 - 1, x1 + 1, y0, edge); g.fill(x0 - 1, y1, x1 + 1, y1 + 1, edge);
            g.fill(x0 - 1, y0, x0, y1, edge); g.fill(x1, y0, x1 + 1, y1, edge);
            g.drawCenteredString(font, clip.getMessage(), (x0 + x1) / 2, y0 + (clip.getHeight() - 8) / 2, 0xFFFFE0D8);
            g.fill(x0, y1 + 1, x0 + (int) (clip.getWidth() * k), y1 + 3, RED);
        }
        var details = details(mx - leftPos, my - topPos);
        if (!details.isEmpty()) g.renderComponentTooltip(font, details, mx, my);
        else renderTooltip(g, mx, my);
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
