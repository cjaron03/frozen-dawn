package com.frozendawn.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Gives the Patchouli guide book on first join.
 * All content (UN notice, timeline, crafting recipes) is in the Patchouli book.
 */
public final class StarterBooks {

    private StarterBooks() {}

    /**
     * Returns the Patchouli guide book if Patchouli is loaded, otherwise null.
     */
    public static ItemStack createGuideBook() {
        if (!net.neoforged.fml.ModList.get().isLoaded("patchouli")) return null;
        try {
            return vazkii.patchouli.api.PatchouliAPI.get().getBookStack(
                    ResourceLocation.fromNamespaceAndPath("frozendawn", "frozen_dawn_guide"));
        } catch (Exception e) {
            return null;
        }
    }
}
