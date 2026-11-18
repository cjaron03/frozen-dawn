package com.frozendawn.event;

import com.frozendawn.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives the Patchouli guide book and UN Evacuation Notice on first join.
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

    /**
     * Creates the UN Emergency Evacuation Notice — Book 1 of the lore series.
     * Given to every player on first join. Sets the scene immediately.
     */
    public static ItemStack createEvacuationNotice() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        List<Filterable<Component>> pages = new ArrayList<>();

        pages.add(page(
                "UNITED NATIONS\nEMERGENCY COUNCIL\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                "EVACUATION NOTICE\n\n" +
                "Classification: PUBLIC\nDate: [SYSTEM DATE ERROR]\n\n" +
                "THIS IS NOT A DRILL.\n\n" +
                "By order of the United Nations Emergency Council, " +
                "all civilians are to report to their nearest designated " +
                "evacuation point IMMEDIATELY."));

        pages.add(page(
                "SITUATION BRIEFING:\n\n" +
                "The Orbital Resonance Stabilization Authority (ORSA) " +
                "has confirmed that Earth's orbital trajectory has " +
                "deviated beyond correctable parameters.\n\n" +
                "Surface conditions will deteriorate rapidly.\n\n" +
                "Immediate evacuation is the ONLY option."));

        pages.add(page(
                "EVACUATION PROTOCOL:\n\n" +
                "1. Gather essential supplies\n" +
                "2. Proceed to the nearest\n   shuttle terminal\n" +
                "3. Follow all instructions\n   from ORSA personnel\n" +
                "4. Do NOT return home\n" +
                "5. Do NOT wait for others\n\n" +
                "PROJECT EXODUS is underway.\n" +
                "14 billion people. 72 hours."));

        pages.add(page(
                "DESTINATION:\n\n" +
                "All evacuees will be transported to Mars Colony " +
                "or Lunar Station facilities.\n\n" +
                "Capacity has been expanded. Your safety is assured.\n\n" +
                "Do not be alarmed.\nDo not panic.\nProceed calmly.\n\n" +
                "\u2014 UN Emergency Council\n" +
                "\u2014 ORSA Coordination Division"));

        pages.add(page(
                "\u00A7o[The last page is handwritten in shaky letters]\u00A7r\n\n" +
                "If you're reading this, we missed you.\n\n" +
                "I went back. Three times. I checked every shelter, " +
                "every basement, every locked door I could find.\n\n" +
                "I'm sorry.\n\n" +
                "\u2014 Kevin, Logistics Division"));

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("UN Emergency Evacuation Notice"),
                "United Nations Emergency Council",
                0, pages, true);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    private static Filterable<Component> page(String text) {
        return Filterable.passThrough(Component.literal(text));
    }
}
