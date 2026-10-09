package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lab only (-Dfrozendawn.labCommands): a fixed §9.4b belief set and a cleared cooldown, so the Scribe can be checked
 * by eye without days of play. Production never registers the commands that reach this.
 */
public final class ScribeLab {
    public static final boolean ENABLED = Boolean.getBoolean("frozendawn.labCommands");
    private static final UUID WITNESS = new UUID(0x5C81BEL, 2);

    /** One line of each certainty, plus one never seen. Two are confident, so bad luck protection decides each roll. */
    private record Seed(String pattern, int supports, int contradictions) { }
    private static final List<Seed> SEEDS = List.of(
            new Seed(BeliefStore.SWORD, 5, 0),            // ALWAYS
            new Seed("RETREAT_BEARING_E", 4, 0),          // FLAT
            new Seed(BeliefStore.RECOVERY, 2, 0),         // HEDGED
            new Seed("RETREAT_BEARING_W", 2, 2),          // INCONCLUSIVE
            new Seed(BeliefStore.RANGED, 0, 3));          // never written

    private ScribeLab() { }

    /** Encounter-separated witnessed evidence through the real store, ending now. Refuses a Maeve that already holds beliefs. */
    public static String seed(ServerPlayer player) {
        var data = MaeveSavedData.get(player.server);
        if (data.store() == null || data.scribe() == null) return "Maeve is not awake.";
        if (!MaeveDirector.snapshot(player.server, player.getUUID()).beliefs().isEmpty())
            return "Maeve already holds beliefs about you. Start from an empty Maeve.";
        long step = BeliefPolicy.ENCOUNTER_GAP + 1, now = player.server.overworld().getGameTime();
        long tick = now - step * SEEDS.stream().mapToInt(s -> s.supports() + s.contradictions()).sum();
        if (tick < 0) return "This world is too young to hold that much history.";
        String dimension = player.level().dimension().location().toString();
        for (var seed : SEEDS) {
            for (int i = 0; i < seed.supports() + seed.contradictions(); i++) {
                tick += step;
                data.store().record(player.getUUID(), WITNESS, dimension, player.blockPosition(), tick, seed.pattern(),
                        i < seed.supports(), "LAB_SEEDED");
            }
        }
        data.setDirty();
        return "Seeded: sword always, east exit, recovery perhaps, west exit unsettled, ranged never.";
    }

    /** Clears the cooldown only; the miss count and any active Scribe are left as they are. */
    public static String clearCooldown(MinecraftServer server) {
        var data = MaeveSavedData.get(server);
        if (data.scribe() == null) return "Maeve is not awake.";
        if (data.scribe().active != null) return "A Scribe is still out.";
        data.scribe().lastEnded = -1;
        data.setDirty();
        return "Scribe cooldown cleared.";
    }
}
