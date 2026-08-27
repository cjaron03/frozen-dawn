package com.frozendawn.client;

import com.frozendawn.item.O2EfficiencyModuleItem;
import com.frozendawn.item.O2TankItem;

/** Pure ETA math for the EVA tank readout. */
public final class AirStatusEtaPolicy {
    public static final int NO_ACTIVE_DRAIN = -1;

    private AirStatusEtaPolicy() {
    }

    public static int estimateSeconds(
            int totalO2,
            int maxO2,
            boolean activeDrain,
            int punctures,
            int punctureVentSeconds,
            boolean visorRig,
            boolean efficiencyModule,
            double externalConsumptionMultiplier,
            double diseaseConsumptionMultiplier) {
        if (!activeDrain || maxO2 <= 0) {
            return NO_ACTIVE_DRAIN;
        }
        if (totalO2 <= 0) {
            return 0;
        }

        double moduleMultiplier = efficiencyModule
                ? O2EfficiencyModuleItem.CONSUMPTION_MULTIPLIER
                : 1.0D;
        double unitsPerTick;
        if (punctures > 0) {
            int ventTicks = Math.max(1, punctureVentSeconds * 20);
            unitsPerTick = maxO2 / (double) ventTicks
                    * punctures
                    * moduleMultiplier;
        } else {
            int interval = O2TankItem.BASE_CONSUMPTION_INTERVAL_TICKS
                    * (visorRig ? 2 : 1);
            unitsPerTick = moduleMultiplier
                    * Math.max(1.0D, externalConsumptionMultiplier)
                    * Math.max(1.0D, diseaseConsumptionMultiplier)
                    / interval;
        }
        if (unitsPerTick <= 0.0D) {
            return NO_ACTIVE_DRAIN;
        }
        return Math.max(1, (int) Math.ceil(totalO2 / (unitsPerTick * 20.0D)));
    }

    public static String format(int seconds) {
        if (seconds < 0) {
            return "--:--";
        }
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes, remainder);
    }
}
