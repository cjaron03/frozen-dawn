package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Random;

/**
 * Client-side frost visuals: tooltip text + inventory overlay.
 * Draws frost crystals and blue tint over food items in all container screens.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class FoodFrostClientHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.has(DataComponents.FOOD)) return;

        Integer frostTicks = stack.get(ModDataComponents.FROST_TICKS.get());
        if (frostTicks == null || frostTicks <= 0) return;

        if (frostTicks >= 6000) {
            event.getToolTip().add(Component.literal("\u2726 Frost-Ruined")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
            event.getToolTip().add(Component.literal("  Permanently damaged. Inedible.")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (frostTicks >= 2400) {
            event.getToolTip().add(Component.literal("\u2744 Frozen")
                    .withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
            event.getToolTip().add(Component.literal("  2x eating time, half hunger restored")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else if (frostTicks >= 600) {
            event.getToolTip().add(Component.literal("\u2744 Chilled")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * Draw frost crystal overlays on food items in container screens.
     * Fires during renderLabels — above items, below tooltips.
     */
    @SubscribeEvent
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        for (Slot slot : event.getContainerScreen().getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) continue;

            Integer frost = stack.get(ModDataComponents.FROST_TICKS.get());
            if (frost == null || frost <= 0) continue;

            renderFrostOverlay(event.getGuiGraphics(), slot.x, slot.y, frost);
        }
    }

    private static void renderFrostOverlay(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int frost) {
        // Deterministic random based on slot position so crystals don't dance
        Random rng = new Random(x * 31L + y * 17L);

        if (frost >= 6000) {
            // Frost-Ruined: heavy blue-grey overlay, nearly opaque
            graphics.fill(x, y, x + 16, y + 16, 0xA04466AA);
            for (int c = 0; c < 14; c++) {
                int cx = x + rng.nextInt(14) + 1;
                int cy = y + rng.nextInt(14) + 1;
                int size = rng.nextInt(2) + 1;
                graphics.fill(cx, cy, cx + size, cy + size, 0xD08899BB);
            }
        } else if (frost >= 2400) {
            // Frozen: noticeable blue overlay + scattered crystals
            graphics.fill(x, y, x + 16, y + 16, 0x506688CC);
            for (int c = 0; c < 8; c++) {
                int cx = x + rng.nextInt(14) + 1;
                int cy = y + rng.nextInt(14) + 1;
                int size = rng.nextInt(2) + 1;
                graphics.fill(cx, cy, cx + size, cy + size, 0xC0BBDDFF);
            }
        } else {
            // Chilled: subtle frost crystals, no background tint
            for (int c = 0; c < 4; c++) {
                int cx = x + rng.nextInt(14) + 1;
                int cy = y + rng.nextInt(14) + 1;
                graphics.fill(cx, cy, cx + 1, cy + 1, 0xB0AACCFF);
            }
        }
    }
}
