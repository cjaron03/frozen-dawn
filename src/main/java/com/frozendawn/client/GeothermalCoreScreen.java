package com.frozendawn.client;

import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.GeothermalCoreMenu;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.O2TankItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Original ORSA machine panel: output, allocation, upgrades and refill share one frame. */
public class GeothermalCoreScreen extends AbstractContainerScreen<GeothermalCoreMenu> {
    private static final int HEAT = 0xFFE5A064;
    private static final int OXYGEN = 0xFF64C9CC;
    private static final int POWER = 0xFF9CD5A5;
    private static final int TEXT = 0xFFE0E6E8;
    private static final int MUTED = 0xFF9BAAB1;
    private AllocationSlider allocation;
    private AllocationSlider powerAllocation;

    public GeothermalCoreScreen(GeothermalCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GeothermalCoreMenu.UI_WIDTH;
        imageHeight = GeothermalCoreMenu.UI_HEIGHT;
        inventoryLabelX = 80;
        inventoryLabelY = 147;
    }

    @Override
    protected void init() {
        super.init();
        powerAllocation = addRenderableWidget(new AllocationSlider(leftPos + 168, topPos + 108, true));
        allocation = addRenderableWidget(new AllocationSlider(leftPos + 8, topPos + 108, false));
        Button reset = addRenderableWidget(Button.builder(Component.literal("Default"), button -> {
            powerAllocation.preview(0); allocation.preview(0.6);
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 400);
        }).bounds(leftPos + 264, topPos + 129, 48, 20).build());
        reset.setTooltip(Tooltip.create(Component.literal("60% heat / 40% O2 / 0% power")));
    }

    /** All widgets now fit within JEI's ordinary container rectangle. */
    public List<Rect2i> getJeiExtraAreas() {
        return List.of();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Absolute coordinates keep the custom panel and vanilla slots aligned.
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        frame(graphics, x, y, imageWidth, imageHeight);
        graphics.drawString(font, title, x + (imageWidth - font.width(title)) / 2, y + 6, HEAT, false);
        var data = menu.getData();

        // Recessed display reports server-authoritative production, not stored gas or room temperature.
        well(graphics, x + 8, y + 20, 216, 68);
        graphics.drawString(font, "CORE OUTPUT", x + 14, y + 25, MUTED, false);
        graphics.drawString(font, "Heat", x + 14, y + 39, HEAT, false);
        right(graphics, data.get(4) + " units/s", x + 218, y + 39, TEXT);
        graphics.drawString(font, "O2", x + 14, y + 51, OXYGEN, false);
        right(graphics, String.format(Locale.ROOT, "%.1f supply", data.get(5) / 100.0), x + 218, y + 51, TEXT);
        graphics.drawString(font, "Power", x + 14, y + 63, POWER, false);
        right(graphics, String.format(Locale.ROOT, "%.2f kW", data.get(7) / 100.0), x + 218, y + 63, TEXT);
        boolean surface = menu.getCorePos() != null
                && GeothermalCoreBlockEntity.hasSurfaceWarmthPenalty(menu.getCorePos());
        graphics.drawString(font, surface ? "Surface heat: 50%" : "Underground: full heat", x + 14, y + 76, MUTED, false);

        float range = GeothermalCoreBlockEntity.BASE_RANGE + data.get(0);
        float supply = GeothermalCoreBlockEntity.BASE_TEMP + data.get(1) * 5.0f;
        if (menu.getCorePos() != null) {
            range = GeothermalCoreBlockEntity.applySurfaceWarmthPenalty(range, menu.getCorePos());
            supply = GeothermalCoreBlockEntity.applySurfaceWarmthPenalty(supply, menu.getCorePos());
        }
        int reach = switch (data.get(2)) {
            case 1 -> 20;
            case 2 -> 26;
            case 3 -> GeothermalCoreBlockEntity.MAX_O2_RANGE;
            default -> GeothermalCoreBlockEntity.BASE_O2_RANGE;
        };
        upgrade(graphics, menu.slots.get(0), "Range", number(range) + " blk", data.get(0),
                GeothermalCoreBlockEntity.MAX_RANGE_LEVEL, 0xFF79A9CF);
        upgrade(graphics, menu.slots.get(1), "Temp cap", number(supply) + " C", data.get(1),
                GeothermalCoreBlockEntity.MAX_TEMP_LEVEL, HEAT);
        upgrade(graphics, menu.slots.get(2), "O2 reach", reach + " blk", data.get(2),
                GeothermalCoreBlockEntity.MAX_O2_LEVEL, OXYGEN);

        int power = powerAllocation == null ? data.get(6) : powerAllocation.percent();
        int heat = allocation == null ? data.get(3) : allocation.heatAt(power);
        graphics.drawString(font, "Power " + power + "%", x + 168, y + 95, POWER, false);
        right(graphics, String.format(Locale.ROOT, "%.2f kW", power * 0.2), x + 312, y + 95, POWER);
        graphics.drawString(font, "Heat " + heat + "%", x + 8, y + 95, HEAT, false);
        right(graphics, "O2 " + (100 - power - heat) + "%", x + 152, y + 95, OXYGEN);
        drawRefill(graphics);
        graphics.drawString(font, playerInventoryTitle, x + inventoryLabelX, y + inventoryLabelY, MUTED, false);
        for (int i = 4; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            well(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
    }

    private void upgrade(GuiGraphics graphics, Slot slot, String label, String value, int level, int maximum, int color) {
        int x = leftPos + slot.x;
        int y = topPos + slot.y;
        well(graphics, x - 1, y - 1, 18, 18);
        graphics.drawString(font, label, x + 24, y - 2, MUTED, false);
        graphics.drawString(font, value, x + 24, y + 9, color, false);
        graphics.fill(x + 24, y + 19, x + 69, y + 21, 0xFF29353C);
        int fill = Math.clamp(Math.round(45f * level / maximum), 0, 45);
        if (fill > 0) graphics.fill(x + 24, y + 19, x + 24 + fill, y + 21, color);
    }

    private void drawRefill(GuiGraphics graphics) {
        Slot slot = menu.slots.get(3);
        int x = leftPos + slot.x;
        int y = topPos + slot.y;
        well(graphics, x - 1, y - 1, 18, 18);
        graphics.drawString(font, "Canister refill", leftPos + 36, topPos + 127, OXYGEN, false);
        ItemStack stack = slot.getItem();
        boolean canister = !stack.isEmpty() && stack.getItem() instanceof O2TankItem;
        int percent = canister ? Math.clamp(Math.round(100f * stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0)
                / ((O2TankItem) stack.getItem()).getMaxO2()), 0, 100) : 0;
        graphics.fill(leftPos + 36, topPos + 138, leftPos + 220, topPos + 143, 0xFF29353C);
        if (percent > 0) graphics.fill(leftPos + 36, topPos + 138, leftPos + 36 + 184 * percent / 100,
                topPos + 143, OXYGEN);
        right(graphics, canister ? percent + "%" : "Empty slot", leftPos + 256, topPos + 131, MUTED);
    }

    private void right(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, value, x - font.width(value), y, color, false);
    }

    private static void well(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF10181D);
        graphics.fill(x, y, x + width, y + 1, 0xFF070C10);
        graphics.fill(x, y, x + 1, y + height, 0xFF070C10);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, 0xFF53636D);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, 0xFF53636D);
    }

    private static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF53636D);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF29353C);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 17, 0xFF1A252C);
        graphics.fill(x, y, x + width, y + 1, 0xFF87939A);
        graphics.fill(x, y, x + 1, y + height, 0xFF87939A);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, 0xFF10181D);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, 0xFF10181D);
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        powerAllocation.synchronize();
        allocation.synchronize();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (int i = 0; i < 3; i++) {
            Slot slot = menu.slots.get(i);
            if (!inside(mouseX, mouseY, slot.x - 1, slot.y - 3, 74, 25)) continue;
            if (inside(mouseX, mouseY, slot.x - 1, slot.y - 1, 18, 18) && slot.hasItem()) return;
            List<Component> lines = switch (i) {
                case 0 -> List.of(Component.literal("Heat range upgrade"), Component.literal("Obsidian: +1 block; diamond block: +4"),
                        Component.literal("Outdoor warmth range. Surface range is halved."));
                case 1 -> List.of(Component.literal("Supply temperature upgrade"), Component.literal("Blaze powder: +5 C; Thermal Core: +10 C"),
                        Component.literal("Raises the source temperature cap and heat transfer."),
                        Component.literal("Room temperature still comes from stored air and wall heat."));
                default -> List.of(Component.literal("O2 reach upgrade"), Component.literal("Nether Star: +1 level (16 / 20 / 26 / 32 blocks)"),
                        Component.literal("Reach changes; production follows the allocation."));
            };
            graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }
        Slot refill = menu.slots.get(3);
        if (inside(mouseX, mouseY, 8, 126, 248, 20)
                && (!inside(mouseX, mouseY, refill.x - 1, refill.y - 1, 18, 18) || !refill.hasItem())) {
            graphics.renderTooltip(font, List.of(Component.literal("O2 canister refill"),
                    Component.literal("Place a canister in the slot. Only spare room production charges it."),
                    Component.literal("Refill shares O2 with reservoirs, suits and airlocks.")), Optional.empty(), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, 168, 92, 144, 32)) {
            graphics.renderTooltip(font, List.of(Component.literal("Reserve part of the fixed Core budget for electricity."),
                    Component.literal("100% power = 20 kW. Heat and O2 share what remains."),
                    Component.literal("Network load never changes these settings.")), Optional.empty(), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, 8, 92, 144, 32)) {
            graphics.renderTooltip(font, List.of(Component.literal("Balance the remaining budget between heat and O2.")),
                    Optional.empty(), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, 8, 20, 216, 68)) {
            graphics.renderTooltip(font, List.of(Component.literal("Core production at the current allocation"),
                    Component.literal("One O2 supply unit sustains one player's breathing rate."),
                    Component.literal("Heat is calibrated game energy; stored warmth changes gradually.")), Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0) for (var slider : List.of(powerAllocation, allocation))
            if (slider.dragging) return slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) for (var slider : List.of(powerAllocation, allocation)) if (slider.dragging) {
            slider.mouseReleased(mouseX, mouseY, button); setDragging(false); return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private double heatBalance() {
        return menu.getData().get(8) / 10000.0;
    }

    private final class AllocationSlider extends AbstractSliderButton {
        private final boolean power;
        private boolean dragging;
        private int submitted = -1;
        private long submittedAt;
        AllocationSlider(int x, int y, boolean power) {
            super(x, y, 144, 14, Component.empty(), power ? menu.getData().get(6) / 100.0 : heatBalance());
            this.power = power; updateMessage();
        }
        int percent() { return (int)Math.round(value * 100); }
        int heatAt(int powerShare) { return (int)Math.round(value * (100 - powerShare)); }
        void preview(double value) {
            this.value = value; submitted = power ? percent() : heatAt(0);
            submittedAt = net.minecraft.Util.getMillis(); updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.literal(power ? "Power " + percent() + "%"
                    : "Heat " + heatAt(menu.getData().get(6)) + "% / O2 "
                    + (100 - menu.getData().get(6) - heatAt(menu.getData().get(6))) + "%"));
        }
        @Override protected void applyValue() {
            value = Math.round(value * 100) / 100.0;
            int share = power ? percent() : heatAt(menu.getData().get(6));
            updateMessage();
            if (share != submitted && minecraft != null && minecraft.gameMode != null) {
                submitted = share; submittedAt = net.minecraft.Util.getMillis();
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, power ? 200 + share : share);
            }
        }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(); well(graphics, x, y, width, height);
            int split = Math.round((width - 4) * (float)value);
            graphics.fill(x + 2, y + 4, x + 2 + split, y + 10, power ? POWER : HEAT);
            graphics.fill(x + 2 + split, y + 4, x + width - 2, y + 10, power ? 0xFF29353C : OXYGEN);
            int thumb = x + 1 + Math.round((width - 6) * (float)value);
            graphics.fill(thumb, y + 1, thumb + 4, y + height - 1, isHoveredOrFocused() ? TEXT : MUTED);
        }
        @Override public void onClick(double x, double y) { dragging = true; super.onClick(x, y); }
        @Override public boolean mouseReleased(double x, double y, int button) {
            boolean result = super.mouseReleased(x, y, button); dragging = false; return result;
        }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
                value = Math.clamp(value + (key == GLFW.GLFW_KEY_LEFT ? -0.01 : 0.01), 0, 1);
                applyValue(); return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        void synchronize() {
            if (dragging) return;
            int share = menu.getData().get(power ? 6 : 3);
            if (submitted >= 0 && share != submitted && net.minecraft.Util.getMillis() - submittedAt < 1000) return;
            value = power ? share / 100.0 : heatBalance(); submitted = -1; updateMessage();
        }
    }

    private static String number(float value) {
        return Math.abs(value - Math.round(value)) < 0.001f ? Integer.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
