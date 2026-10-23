package com.frozendawn.client;

import com.frozendawn.event.SuffocationStage;

/** Chooses one oxygen message, including danger during recovery, before rendering. */
public final class AtmosphericActionBarPolicy {
    private AtmosphericActionBarPolicy() {}

    public static boolean allowsDeviceNotice(boolean activeRoomNotice,SuffocationStage stage) {
        return !activeRoomNotice && stage == SuffocationStage.NONE;
    }

    /** Direct hatch feedback may replace a completed room notice, never an oxygen warning. */
    public static boolean allowsDoorNotice(String roomKey,SuffocationStage stage) {
        return stage==SuffocationStage.NONE && (roomKey==null
                ||roomKey.equals("ui.frozendawn.room.air_restored")||roomKey.equals("ui.frozendawn.room.seal_verified"));
    }
    public record Notice(String primaryKey, String secondaryKey, boolean danger) {}

    public static Notice select(String roomKey, boolean suitHud, SuffocationStage stage) {
        // Successful door openings stay silent in both the helmet and action bar.
        if("message.frozendawn.airlock.door_open_vacuum".equals(roomKey)
                ||"message.frozendawn.airlock.door_open_matched".equals(roomKey))roomKey=null;
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
