package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * Archive messages and entry links for the ORSA Field Manual (Patchouli book
 * {@code frozendawn:frozen_dawn_guide}).
 */
public final class FieldManual {

    public static final ResourceLocation BOOK =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frozen_dawn_guide");

    private FieldManual() {
    }

    /** Chat line naming the manual entry a document was filed under. */
    public static void announceArchived(ServerPlayer player, String entryName) {
        player.displayClientMessage(
                Component.literal("§7[§6Frozen Dawn§7] §fArchived in your §6ORSA Field Manual§f: §e"
                        + entryName + "§f."),
                false);
    }

    /**
     * Opens the manual on an entry. Patchouli will not open a locked entry, so any
     * advancement just granted is sent to the client first; the client applies it
     * before it handles the open request.
     */
    public static void openEntry(ServerPlayer player, String entryPath) {
        player.getAdvancements().flushDirty(player);
        PatchouliAPI.get().openBookEntry(player, BOOK,
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, entryPath), 0);
    }
}
