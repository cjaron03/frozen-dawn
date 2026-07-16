package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.MasterArchitectCombatAction;

/** Pure eligibility and timing rules for the one-time Master Architect perception moment. */
public final class MasterArchitectFourthWallPolicy {
    public static final int EYE_CONTACT_TICKS = 60;
    public static final int OVERLAY_TICKS = 100;
    public static final int AUDIO_FADE_TICKS = 20;
    public static final int AUDIO_RESTORE_START_TICK = 70;
    public static final double CAMERA_DOT_THRESHOLD = 0.90D;

    private MasterArchitectFourthWallPolicy() {
    }

    public static boolean isPeacefulWatchEligible(
            boolean masterArchitect,
            boolean alive,
            int deathTicks,
            int combatAction,
            ReturnedHearthSavedData.HiveRelationship relationship,
            double distanceSquared) {
        return masterArchitect
                && alive
                && deathTicks <= 0
                && combatAction == MasterArchitectCombatAction.IDLE
                && !HearthMasterArchitectPolicy.isHostileRelationship(relationship)
                && distanceSquared <= (double) HearthMasterArchitectPolicy.WATCH_DISTANCE
                        * HearthMasterArchitectPolicy.WATCH_DISTANCE;
    }

    public static boolean hasCameraEyeContact(double lookDot, boolean unobstructed) {
        return unobstructed && lookDot >= CAMERA_DOT_THRESHOLD;
    }

    public static boolean contactComplete(int eyeContactTicks) {
        return eyeContactTicks >= EYE_CONTACT_TICKS;
    }

    public static boolean shouldTrackCamera(
            boolean completed,
            boolean eligible,
            boolean trackedMaster,
            boolean thirdPerson,
            boolean peacefulWatch) {
        return (completed || eligible)
                && trackedMaster
                && thirdPerson
                && peacefulWatch;
    }

    public static float weatherAudioMultiplier(int overlayTick) {
        if (overlayTick < 0 || overlayTick >= OVERLAY_TICKS) {
            return 1.0F;
        }
        if (overlayTick < AUDIO_FADE_TICKS) {
            return 1.0F - overlayTick / (float) AUDIO_FADE_TICKS;
        }
        if (overlayTick < AUDIO_RESTORE_START_TICK) {
            return 0.0F;
        }
        return Math.min(1.0F, (overlayTick - AUDIO_RESTORE_START_TICK)
                / (float) (OVERLAY_TICKS - AUDIO_RESTORE_START_TICK));
    }
}
