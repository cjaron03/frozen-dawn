package com.frozendawn.homo;

/** Pure visibility, identity, and progress rules for the Master Architect boss bar. */
public final class MasterArchitectBossBarPolicy {
    public static final String NAME_KEY = "bossbar.frozendawn.master_architect";

    private MasterArchitectBossBarPolicy() {
    }

    public static boolean shouldReveal(
            boolean hostileAtHearth, boolean directlyDamagedByPlayer) {
        return hostileAtHearth || directlyDamagedByPlayer;
    }

    public static float progress(float health, float maxHealth) {
        if (maxHealth <= 0.0F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, health / maxHealth));
    }
}
