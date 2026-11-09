package com.frozendawn.defense;

import com.frozendawn.lore.PayoffState;
import net.minecraft.server.MinecraftServer;

/**
 * Whether the world's wands log terminations rather than transfers, and kill rather than discard. It reads
 * {@link PayoffState#terminusTurned}: it turns on the CH-18 trigger, a player's first death after the Heart falls with
 * Maeve erased, in every world, never in an E11 world, and never back.
 */
public final class TerminusLedger {
    public static boolean turned(MinecraftServer server) { return PayoffState.terminusTurned(server); }
    public static boolean clientTurned() { return PayoffState.clientTerminusTurned(); }

    /** GameTests and the playtest command only: in play the switch never reverts. */
    public static void setForTest(MinecraftServer server, boolean turned) { PayoffState.setTerminusTurnedForTest(server, turned); }

    private TerminusLedger() { }
}
