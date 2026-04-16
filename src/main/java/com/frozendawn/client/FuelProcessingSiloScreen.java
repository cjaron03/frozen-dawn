package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.FuelProcessingSiloMultiblock;
import com.frozendawn.block.FuelProcessingSiloMenu;
import com.frozendawn.recipe.FuelProcessingSiloRecipes;
import com.frozendawn.recipe.FuelProcessingSiloRecipes.FuelProcessingSiloRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public class FuelProcessingSiloScreen extends AbstractContainerScreen<FuelProcessingSiloMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/gui/fuel_processing_silo.png");

    private static final int GUI_W = 196;
    private static final int GUI_H = 222;
    private static final int PROGRESS_X = 78;
    private static final int PROGRESS_Y = 39;
    private static final int PROGRESS_W = 38;
    private static final int PROGRESS_H = 8;
    private static final float HELPER_TEXT_SCALE = 0.75f;

    public static final int RECIPE_CLICK_X = PROGRESS_X - 2;
    public static final int RECIPE_CLICK_Y = PROGRESS_Y - 2;
    public static final int RECIPE_CLICK_W = PROGRESS_W + 4;
    public static final int RECIPE_CLICK_H = PROGRESS_H + 4;

    public FuelProcessingSiloScreen(FuelProcessingSiloMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_W;
        imageHeight = GUI_H;
        inventoryLabelY = 124;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Absolute-positioned labels rendered in renderBg.
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        var data = menu.getData();
        boolean processing = data.get(1) != 0;
        boolean structureValid = data.get(2) != 0;
        boolean heaterPresent = data.get(3) != 0;
        boolean heaterLit = data.get(4) != 0;
        int speedUnits = data.get(5);
        int heaterEtaMinutes = data.get(6);
        int heaterTierCode = data.get(7);
        boolean heaterCapacitor = data.get(8) != 0;
        int progressPct = data.get(0);

        FuelProcessingSiloRecipe recipe = FuelProcessingSiloRecipes.findMatch(menu.getInputStacks(), menu.getOutputStack());

        graphics.drawString(font, title, x + (GUI_W - font.width(title)) / 2, y + 6, 0xFFD7DCCF, false);

        if (progressPct > 0) {
            int fillW = Math.max(1, progressPct * PROGRESS_W / 100);
            int fillColor = processing ? 0xFF4FD9F2 : 0xFF6D8F9C;
            graphics.fill(x + PROGRESS_X + 1, y + PROGRESS_Y + 1,
                    x + PROGRESS_X + 1 + fillW, y + PROGRESS_Y + 1 + PROGRESS_H, fillColor);
        }

        String progressText = processing ? progressPct + "%" : "IDLE";
        graphics.drawString(font, progressText,
                x + PROGRESS_X + (PROGRESS_W - font.width(progressText)) / 2,
                y + 39,
                0xFFDDE5E8,
                false);

        int rowX = x + 78;
        int valueX = x + 122;
        int rowY = y + 60;
        drawStatusRow(graphics, rowX, valueX, rowY, "Shell", structureValid ? "Locked" : "Invalid",
                structureValid ? 0xFF84D8A8 : 0xFFFF6D7C);

        String heaterLabel = !heaterPresent
                ? "Missing"
                : FuelProcessingSiloMultiblock.tierLabel(heaterTierCode) + (heaterCapacitor ? "+C" : "");
        int heaterColor = heaterPresent ? (heaterLit ? 0xFF8CD9FF : 0xFFF2C27C) : 0xFFFF6D7C;
        drawStatusRow(graphics, rowX, valueX, rowY + 11, "Heater", heaterLabel, heaterColor);
        drawStatusRow(graphics, rowX, valueX, rowY + 22, "Speed", FuelProcessingSiloMultiblock.formatSpeed(speedUnits),
                speedUnits > 0 ? 0xFFDDE5E8 : 0xFF839AA7);
        drawStatusRow(graphics, rowX, valueX, rowY + 33, "Fuel", heaterPresent && heaterLit ? heaterEtaMinutes + " min" : "Offline",
                heaterPresent && heaterLit ? 0xFFF0D6A6 : 0xFF839AA7);

        String helperText;
        int helperColor;
        if (!structureValid) {
            helperText = "3x3x4 open-top shell. Controller on wall.";
            helperColor = 0xFFF19784;
        } else if (!heaterPresent) {
            helperText = "Touch lit heater to shell.";
            helperColor = 0xFFF19784;
        } else if (!heaterLit) {
            helperText = "Light attached heater.";
            helperColor = 0xFFF2C27C;
        } else if (recipe != null) {
            helperText = processing ? "Processing." : "Ready to process.";
            helperColor = processing ? 0xFF8CD9FF : 0xFF9FD8A7;
        } else {
            helperText = "Load valid ingredients.";
            helperColor = 0xFF8AA0AC;
        }
        drawWrappedText(graphics, helperText, x + 12, y + 104, 172, helperColor);

        graphics.drawString(font, playerInventoryTitle, x + 8, y + inventoryLabelY, 0xFF7F96A5, false);
    }

    private void drawStatusRow(GuiGraphics graphics, int labelX, int valueX, int y, String label, String value, int valueColor) {
        graphics.drawString(font, label, labelX, y, 0xFF7F96A5, false);
        graphics.drawString(font, value, valueX, y, valueColor, false);
    }

    private void drawWrappedText(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        int lineY = Math.round(y / HELPER_TEXT_SCALE);
        int scaledX = Math.round(x / HELPER_TEXT_SCALE);
        int scaledWidth = Math.round(width / HELPER_TEXT_SCALE);
        graphics.pose().pushPose();
        graphics.pose().scale(HELPER_TEXT_SCALE, HELPER_TEXT_SCALE, 1.0f);
        int lines = 0;
        for (FormattedCharSequence seq : font.split(Component.literal(text), scaledWidth)) {
            graphics.drawString(font, seq, scaledX, lineY, color, false);
            lineY += 10;
            lines++;
            if (lines >= 2) {
                break;
            }
        }
        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(RECIPE_CLICK_X, RECIPE_CLICK_Y, RECIPE_CLICK_W, RECIPE_CLICK_H, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("View Fuel Processing recipes"),
                    Component.literal("Click to open JEI").withStyle(ChatFormatting.GRAY)
            ), Optional.empty(), mouseX, mouseY);
        }
    }
}
