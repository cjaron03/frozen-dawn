package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Pure timing and consequence policy for the Heart's per-player Echo. */
public final class HeartEchoPolicy {
    public static final float SPAWN_LOAD = 50.0F;
    public static final int GAZE_TICKS = 40;
    public static final int PATIENCE_TICKS = 160;
    public static final int NODE_EXPOSURE_TICKS = 200;
    public static final int CLARITY_TICKS = 60;
    public static final float ACKNOWLEDGEMENT_RELIEF = 20.0F;
    public static final float SCREAM_LOAD = 22.5F;
    public static final float VIOLENCE_LOAD = 8.0F;
    public static final float VIOLENCE_FLOOR_STEP = 5.0F;
    public static final float MAX_VIOLENCE_FLOOR = 30.0F;

    private HeartEchoPolicy() {
    }

    public static boolean canSpawn(float load, boolean heartLive, int nextNode) {
        return heartLive && load >= SPAWN_LOAD && nextNode >= 0;
    }

    /** Sparse surviving archives offer fewer chances to recover clarity. */
    public static int respawnCooldownTicks(float fieldStrength, int destroyedNodes) {
        float strength = Mth.clamp(fieldStrength, 0.0F, 1.0F);
        return Math.max(60, Math.round(260.0F - strength * 140.0F)
                - Math.max(0, destroyedNodes) * 20);
    }

    public static float nextViolenceFloor(float currentFloor) {
        return Mth.clamp(currentFloor + VIOLENCE_FLOOR_STEP,
                0.0F, MAX_VIOLENCE_FLOOR);
    }
}
