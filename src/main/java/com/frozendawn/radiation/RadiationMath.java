package com.frozendawn.radiation;

/** Pure radiation rules in RU (radiation units). Rates are RU per second; dose is accumulated RU.
 * All values are starting points for tuning; see Notion "Radiation". */
public final class RadiationMath {
    /** Sickness thresholds on accumulated dose: I, II, III, lethal. */
    public static final double STAGE_I = 100, STAGE_II = 300, STAGE_III = 600, LETHAL = 1000;
    /** Outside radiation the dose falls about 1 RU every 10 seconds. */
    public static final double RECOVERY_PER_SECOND = 0.1, BACKGROUND = 0.05;
    /** Symptoms lag the dose (latent period): the felt dose follows the real one with this time constant. */
    public static final double LATENT_SECONDS = 30;
    /** Source strengths: RU/s at one block, before shielding. */
    public static final double SPENT_ROD = 60, CORE_PER_KW = 0.1, CORIUM = 700, MOLTEN_CORIUM = 700,
            FALLOUT = 0.08, FALLOUT_SEVERE = 0.4, DEBRIS = 150, RADIOACTIVE_ASH = 1, FRESH_ROD = 0.05, PELLET = 0.01, RAW_URANIUM = 0.004, ORE = 0.03;
    public static final double CASK_SHIELD = 0.01;

    private RadiationMath() {}

    public static int stage(double dose) {
        return dose >= LETHAL ? 4 : dose >= STAGE_III ? 3 : dose >= STAGE_II ? 2 : dose >= STAGE_I ? 1 : 0;
    }
    /** Inverse-square, never stronger than at one block. */
    public static double rate(double strength, double distance, double shielding) {
        return strength * shielding / Math.max(1, distance * distance);
    }
    /** One second of dose: exposure adds, clean air recovers. Never negative. */
    public static double nextDose(double dose, double rate, double treatment) {
        double next = dose + rate - treatment;
        if (rate < BACKGROUND) next -= RECOVERY_PER_SECOND;
        return Math.max(0, next);
    }
    public static double nextFelt(double felt, double dose) {
        return felt + (dose - felt) * (1 - Math.exp(-1 / LATENT_SECONDS));
    }
    /** Geiger click rate for a dose rate: silent at background, saturating near 40 clicks per second. */
    /** Soot shown over the view: visor grime only while a sealed suit's visor is on, or skin contamination. */
    public static float soot(float grime, boolean sealed, float contamination) {
        return Math.max(sealed ? grime : 0f, Math.min(1f, contamination / 2f));
    }
    public static double clicksPerSecond(double rate) {
        return rate < 0.02 ? 0 : Math.min(40, 1.5 + 6 * Math.sqrt(rate));
    }
}
