package com.frozendawn.client;

import com.frozendawn.event.SuffocationStage;

/** Chooses one oxygen message, including danger during recovery, before rendering. */
public final class AtmosphericActionBarPolicy {
    private AtmosphericActionBarPolicy() {}

    public record Notice(String primaryKey, String secondaryKey, boolean danger) {}

    public static Notice select(String roomKey, boolean suitHud, SuffocationStage stage) {
        boolean danger = stage != SuffocationStage.NONE;
        if (suitHud || roomKey == null) {
            return danger ? new Notice(stage.translationKey(), null, true) : null;
        }
        // A room transition can arrive before the player's protection refresh. Never
        // briefly announce safety over authoritative, ongoing suffocation.
        if (danger && roomKey.equals("ui.frozendawn.room.air_restored"))
            return new Notice(stage.translationKey(), null, true);
        return new Notice(roomKey, danger ? "ui.frozendawn.room.still_suffocating" : null, danger);
    }
}
