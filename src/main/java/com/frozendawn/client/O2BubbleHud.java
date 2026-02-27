package com.frozendawn.client;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.O2TankItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Custom O2 bubble bar HUD. Shows 10 bubbles above the hunger bar (right side)
 * when in phase 6 late with full EVA suit and an O2 tank in inventory.
 * Tier-colored bubbles with glow, pulse, and pop animations.
 */
public class O2BubbleHud {

    private static final int BUBBLE_COUNT = 10;
    private static final int BUBBLE_SIZE = 9;
    private static final int BUBBLE_SPACING = 1;

    // Pop animation state
    private static int prevBubblesFull = BUBBLE_COUNT;
    private static int popBubbleIndex = -1;
    private static int popTimer = 0;
    private static final int POP_DURATION = 8;

    // Pulse state for low O2
    private static long tickCounter = 0;

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        if (phase < 6 || progress < 0.85f) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isCreative() || player.isSpectator()) return;
        if (mc.options.hideGui) return;

        // Sum O2 across all tanks in inventory; use highest tier for color
        int totalO2 = 0;
        int totalMaxO2 = 0;
        int bestTier = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof O2TankItem tankItem) {
                totalO2 += stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0);
                totalMaxO2 += tankItem.getMaxO2();
                bestTier = Math.max(bestTier, tankItem.getTier());
            }
        }
        if (bestTier == 0) return; // no tanks in inventory

        int o2Level = totalO2;
        int tier = bestTier;
        int maxO2 = totalMaxO2;

        tickCounter++;

        int o2PerBubble = maxO2 / BUBBLE_COUNT;

        // Position: right side, above hunger bar
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int baseX = screenWidth / 2 + 91;
        int baseY = screenHeight - 49;

        // Calculate full/partial bubbles
        int fullBubbles;
        float partialFraction;
        if (o2Level <= 0) {
            fullBubbles = 0;
            partialFraction = 0f;
        } else {
            fullBubbles = o2Level / o2PerBubble;
            int remainder = o2Level % o2PerBubble;
            partialFraction = (float) remainder / o2PerBubble;
        }

        int bubblesFull = o2Level <= 0 ? 0 : Math.min(BUBBLE_COUNT, (o2Level + o2PerBubble - 1) / o2PerBubble);

        // Pop animation: detect when a bubble just emptied
        if (bubblesFull < prevBubblesFull && prevBubblesFull > 0) {
            popBubbleIndex = bubblesFull;
            popTimer = POP_DURATION;
        }
        prevBubblesFull = bubblesFull;
        if (popTimer > 0) popTimer--;

        // Low O2 pulse (≤20%)
        float o2Ratio = (float) o2Level / maxO2;
        boolean lowO2 = o2Ratio <= 0.2f && o2Level > 0;
        float pulseAlpha = 1.0f;
        if (lowO2) {
            // Oscillate between 0.4 and 1.0
            pulseAlpha = 0.7f + 0.3f * Mth.sin(tickCounter * 0.3f);
        }

        // Tier colors
        int[] tierColors = getTierColors(1); // always cyan

        // Draw bubbles right-to-left
        for (int i = 0; i < BUBBLE_COUNT; i++) {
            int bx = baseX - (i + 1) * (BUBBLE_SIZE + BUBBLE_SPACING);
            int by = baseY;

            if (i < fullBubbles) {
                drawRoundBubble(graphics, bx, by, 1.0f, tierColors, pulseAlpha);
            } else if (i == fullBubbles && partialFraction > 0f) {
                drawRoundBubble(graphics, bx, by, partialFraction, tierColors, pulseAlpha);
            } else if (i == popBubbleIndex && popTimer > 0) {
                drawPopBubble(graphics, bx, by, popTimer, tierColors);
            } else {
                drawEmptyBubble(graphics, bx, by);
            }
        }
    }

    /** Returns {outline, fill, highlight, glow} colors for a tier. */
    private static int[] getTierColors(int tier) {
        return switch (tier) {
            case 3 -> new int[]{
                    0xFF3A1060, // outline — dark purple
                    0xFFA040E0, // fill — purple/violet
                    0xFFD080FF, // highlight — light violet
                    0x40A040E0  // glow — semi-transparent purple
            };
            case 2 -> new int[]{
                    0xFF0A3066, // outline — dark blue
                    0xFF2080E0, // fill — blue
                    0xFF60B0FF, // highlight — light blue
                    0x402080E0  // glow — semi-transparent blue
            };
            default -> new int[]{
                    0xFF005566, // outline — dark cyan
                    0xFF00CCCC, // fill — cyan
                    0xFF66FFFF, // highlight — light cyan
                    0x4000CCCC  // glow — semi-transparent cyan
            };
        };
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static void drawRoundBubble(GuiGraphics g, int x, int y, float fillFraction,
                                         int[] colors, float pulseAlpha) {
        int outline = applyAlpha(colors[0], pulseAlpha);
        int fill = applyAlpha(colors[1], pulseAlpha);
        int highlight = applyAlpha(colors[2], pulseAlpha);
        int glow = applyAlpha(colors[3], pulseAlpha);
        int dark = applyAlpha(0xFF0A1820, pulseAlpha);

        // Glow halo (1px border around the bubble)
        if (fillFraction > 0.5f) {
            g.fill(x + 1, y - 1, x + 8, y, glow);         // top glow
            g.fill(x + 1, y + 9, x + 8, y + 10, glow);    // bottom glow
            g.fill(x - 1, y + 1, x, y + 8, glow);          // left glow
            g.fill(x + 9, y + 1, x + 10, y + 8, glow);     // right glow
        }

        // Rounded outline: 9x9 with narrower top/bottom rows
        // Top row (3 wide, centered)
        g.fill(x + 2, y, x + 7, y + 1, outline);
        // Second row (wider)
        g.fill(x + 1, y + 1, x + 8, y + 2, outline);
        // Left/right sides for rows 2-7
        g.fill(x, y + 2, x + 1, y + 7, outline);
        g.fill(x + 8, y + 2, x + 9, y + 7, outline);
        // Second-to-last row
        g.fill(x + 1, y + 7, x + 8, y + 8, outline);
        // Bottom row (3 wide, centered)
        g.fill(x + 2, y + 8, x + 7, y + 9, outline);

        // Interior fill based on fraction (liquid draining from top)
        int interiorTop = y + 2;
        int interiorBottom = y + 7;
        int interiorHeight = interiorBottom - interiorTop; // 5 pixels
        int fillHeight = Math.max(0, Math.round(interiorHeight * fillFraction));
        int emptyHeight = interiorHeight - fillHeight;

        // Empty part (dark)
        if (emptyHeight > 0) {
            g.fill(x + 1, interiorTop, x + 8, interiorTop + emptyHeight, dark);
        }
        // Filled part
        if (fillHeight > 0) {
            g.fill(x + 1, interiorTop + emptyHeight, x + 8, interiorBottom, fill);
        }

        // Fill the second row interior and second-to-last row interior
        if (fillFraction >= 1.0f) {
            g.fill(x + 2, y + 1, x + 7, y + 2, fill);
            g.fill(x + 2, y + 7, x + 7, y + 8, fill);
        } else {
            g.fill(x + 2, y + 1, x + 7, y + 2, dark);
            if (fillFraction > 0.8f) {
                g.fill(x + 2, y + 7, x + 7, y + 8, fill);
            } else {
                g.fill(x + 2, y + 7, x + 7, y + 8, dark);
            }
        }

        // Specular highlight (top-left)
        if (fillFraction > 0.5f) {
            g.fill(x + 2, y + 2, x + 4, y + 3, highlight);
            g.fill(x + 2, y + 3, x + 3, y + 4, highlight);
        }
    }

    private static void drawEmptyBubble(GuiGraphics g, int x, int y) {
        int outline = 0xFF223344;
        int dark = 0xFF0A1820;

        // Same rounded shape, dim
        g.fill(x + 2, y, x + 7, y + 1, outline);
        g.fill(x + 1, y + 1, x + 8, y + 2, outline);
        g.fill(x, y + 2, x + 1, y + 7, outline);
        g.fill(x + 8, y + 2, x + 9, y + 7, outline);
        g.fill(x + 1, y + 7, x + 8, y + 8, outline);
        g.fill(x + 2, y + 8, x + 7, y + 9, outline);

        // Dark interior
        g.fill(x + 2, y + 1, x + 7, y + 2, dark);
        g.fill(x + 1, y + 2, x + 8, y + 7, dark);
        g.fill(x + 2, y + 7, x + 7, y + 8, dark);
    }

    private static void drawPopBubble(GuiGraphics g, int x, int y, int timer, int[] colors) {
        float t = (float) timer / POP_DURATION;
        int alpha = (int) (200 * t);
        int ringColor = (alpha << 24) | (colors[1] & 0x00FFFFFF);

        // Expanding ring effect
        int expand = (int) (3 * (1 - t));

        // Ring corners expanding outward
        g.fill(x + 1 - expand, y + 1 - expand, x + 3 - expand, y + 2 - expand, ringColor);
        g.fill(x + 6 + expand, y + 1 - expand, x + 8 + expand, y + 2 - expand, ringColor);
        g.fill(x + 1 - expand, y + 7 + expand, x + 3 - expand, y + 8 + expand, ringColor);
        g.fill(x + 6 + expand, y + 7 + expand, x + 8 + expand, y + 8 + expand, ringColor);

        // Center sparkle (fading)
        if (t > 0.5f) {
            int sparkleAlpha = (int) (255 * (t - 0.5f) * 2);
            int sparkle = (sparkleAlpha << 24) | (colors[2] & 0x00FFFFFF);
            g.fill(x + 3, y + 3, x + 6, y + 6, sparkle);
        }

        // Empty bubble underneath
        drawEmptyBubble(g, x, y);
    }

    /** Reset state when changing worlds. */
    public static void reset() {
        prevBubblesFull = BUBBLE_COUNT;
        popBubbleIndex = -1;
        popTimer = 0;
        tickCounter = 0;
    }
}
