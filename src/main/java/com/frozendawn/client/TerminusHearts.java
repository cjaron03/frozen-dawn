package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.defense.Terminus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The hearts a Terminus discharge has taken, drawn like Fallout's radiation at the end of the health bar (owner's
 * mockup, docs/art/terminus/taken-hearts-mockup.png): ash grey with one pale mote, not health, coming back half a
 * heart at a time. The count is the player's own toll modifier, which the server keeps in step.
 */
public final class TerminusHearts {
    private static final ResourceLocation TAKEN = sprite("terminus_taken"), HALF = sprite("terminus_taken_half");

    /** After vanilla has drawn {@code maxHealth} and {@code absorption}, on its rows and in its slots. */
    public static void render(GuiGraphics graphics, Player player, int x, int y, int rowHeight, float maxHealth, int absorption) {
        int taken = Terminus.taken(player);
        if (taken <= 0) return;
        // An odd toll leaves vanilla's last heart a half heart of maximum: the half sprite covers its right half.
        int last = Mth.ceil(maxHealth / 2) - 1;
        if (taken % 2 == 1 && last >= 0) draw(graphics, HALF, x, y, rowHeight, last);
        int first = Mth.ceil(maxHealth / 2) + Mth.ceil(absorption / 2.0);
        for (int n = 0; n < taken / 2; n++) draw(graphics, TAKEN, x, y, rowHeight, first + n);
    }

    private static void draw(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int rowHeight, int slot) {
        graphics.blitSprite(sprite, x + slot % 10 * 8, y - slot / 10 * rowHeight, 9, 9);
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "hud/heart/" + name);
    }

    private TerminusHearts() { }
}
