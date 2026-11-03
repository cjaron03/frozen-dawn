package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheronForgeBlockEntity;
import com.frozendawn.block.AcheronForgeMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AcheronForgeScreen extends AbstractContainerScreen<AcheronForgeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/gui/acheron_forge.png");
    private static final int PROGRESS_BAR_X = 73, PROGRESS_BAR_Y = 35, PROGRESS_BAR_W = 24, PROGRESS_BAR_H = 16;
    private Button mode;

    public AcheronForgeScreen(AcheronForgeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }
    @Override protected void init() {
        super.init();
        mode = addRenderableWidget(Button.builder(modeLabel(), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, (menu.getData().get(3) + 1) % 3);
        }).bounds(leftPos + 16, topPos + 17, 144, 16).build());
        mode.setTooltip(Tooltip.create(Component.translatable("screen.frozendawn.forge.mode_hint")));
    }
    private Component modeLabel() {
        String key = switch (AcheronForgeBlockEntity.Mode.fromId(menu.getData().get(3))) {
            case AUTO -> "auto"; case ELECTRIC -> "electric"; case HEAT -> "heat";
        };
        return Component.translatable("screen.frozendawn.forge.mode_" + key);
    }
    @Override protected void containerTick() { super.containerTick(); mode.setMessage(modeLabel()); }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 5, 0x40A0B4, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x607080, false);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int progress = menu.getData().get(0);
        if (progress > 0) {
            int width = PROGRESS_BAR_W * progress / AcheronForgeBlockEntity.TOTAL_WORK;
            graphics.blit(TEXTURE, leftPos + PROGRESS_BAR_X, topPos + PROGRESS_BAR_Y, 176, 0, width, PROGRESS_BAR_H);
        }
        int status = menu.getData().get(5);
        String key = switch (status) {
            case 1 -> "electric_work"; case 2 -> "heat_work"; case 3 -> "waiting";
            case 4 -> "depth"; case 5 -> "no_heat"; case 6 -> "blocked"; case 7 -> "input"; default -> "ready";
        };
        Component text = Component.translatable("screen.frozendawn.forge." + key);
        graphics.drawString(font, text, leftPos + (imageWidth - font.width(text)) / 2, topPos + 58,
                status >= 3 ? 0xFFAA55 : status > 0 ? 0x55FFAA : 0x40A0B4, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= leftPos + PROGRESS_BAR_X && mouseX < leftPos + PROGRESS_BAR_X + PROGRESS_BAR_W
                && mouseY >= topPos + PROGRESS_BAR_Y && mouseY < topPos + PROGRESS_BAR_Y + PROGRESS_BAR_H) {
            List<Component> tooltip = new ArrayList<>();
            for (String key : List.of("recipes", "electric_hint", "heat_hint", "waste_hint", "noise_hint"))
                tooltip.add(Component.translatable("screen.frozendawn.forge." + key));
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
