package com.frozendawn.event;

import com.frozendawn.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Gives the Patchouli guide book, the UN Evacuation Notice and Kevin's blanket on first join.
 */
public final class StarterBooks {

    private StarterBooks() {}

    /** The flag that marks a player's first join as handled; a player who has it never receives anything again. */
    public static final String RECEIVED_FLAG = "frozendawn:received_books";

    /**
     * A new player's first join: the field manual, then Kevin's Evacuation Notice and the blanket he left (CH-38).
     * What does not fit in a full inventory drops at the player's feet.
     */
    public static void grantFirstJoin(ServerPlayer player) {
        var data = player.getPersistentData();
        if (data.getBoolean(RECEIVED_FLAG)) return;
        data.putBoolean(RECEIVED_FLAG, true);
        ItemStack guide = createGuideBook();
        if (guide != null) player.getInventory().add(guide);
        player.getInventory().placeItemBackInInventory(createEvacuationNotice());
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.ORSA_THERMAL_BLANKET.get()));
    }

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

    /** The UN Emergency Evacuation Notice (CH-38): its words and layout live in {@link com.frozendawn.item.EvacuationNotice}. */
    public static ItemStack createEvacuationNotice() {
        return new ItemStack(ModItems.EVACUATION_NOTICE.get());
    }
}
