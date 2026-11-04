package com.frozendawn.shepherd;

import com.frozendawn.data.HelionSiteState;
import com.frozendawn.defense.TerminusLedger;
import com.frozendawn.defense.TerminusLog;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.vault.TerminusVaultBuilder;
import com.frozendawn.vault.VaultCradleBlockEntity;
import net.minecraft.server.level.ServerPlayer;

/** What she can truthfully say about the Terminus: she can be woken long after it has been used, or after the log has turned. */
public final class ShepherdWand {
    public enum Where {
        /** Found as it was, in the cradle. */
        CRADLE,
        /** Found as it was, on the speaker. */
        HAND,
        /** Anything else: "Transfers logged: 1" would be untrue. */
        OTHER
    }

    private ShepherdWand() {}

    /** {@code inHand} is the speaker's wand's log, or null. */
    public static Where where(boolean cradled, TerminusLog inHand, boolean turned) {
        if (turned) return Where.OTHER;
        if (cradled) return Where.CRADLE;
        return inHand != null && inHand.logged() == TerminusLog.FOUND.logged() ? Where.HAND : Where.OTHER;
    }

    static Where of(ServerPlayer player) {
        var server = player.getServer();
        var hall = HelionSiteState.get(server).vault();
        boolean cradled = hall != null && server.overworld().getBlockEntity(hall.offset(TerminusVaultBuilder.CRADLE)) instanceof VaultCradleBlockEntity cradle && cradle.holding();
        return where(cradled, held(player), TerminusLedger.turned(server));
    }

    private static TerminusLog held(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.is(ModItems.TERMINUS.get())) return stack.getOrDefault(ModDataComponents.TERMINUS_LOG.get(), TerminusLog.FOUND);
        }
        return null;
    }
}
