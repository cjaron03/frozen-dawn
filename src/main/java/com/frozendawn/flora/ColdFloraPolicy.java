package com.frozendawn.flora;

/**
 * Pure Cold Flora rules (Notion "Cold Flora: Frostcap and Rimewood", decided 2026-10-14, and the Rimewood section of
 * "Blind Playtest, October 7"). Both grow only at -20C or colder; warmth pauses them, and Frostcap dies above 0C.
 */
public final class ColdFloraPolicy {
    private ColdFloraPolicy() { }

    /** Growth needs this cold or colder. */
    public static final float GROW_AT_OR_BELOW = -20f;
    /** Frostcap dies above this. */
    public static final float FROSTCAP_DIES_ABOVE = 0f;

    /**
     * Rimewood sapling: each random tick at -20C or colder advances it with probability 1 in this. With the default
     * random tick speed a block is ticked about every 68 s, so two stages take about 25 minutes, inside the 20-30
     * minute target. Bone meal keeps vanilla's 45% per use: about 4.4 uses for two stages, inside the 4-6 target.
     */
    public static final int SAPLING_ADVANCE_ONE_IN = 11;

    /** A sapling grows only with open sky and at -20C or colder; sunlight is not needed. */
    public static boolean saplingGrows(boolean sky, float celsius) { return sky && celsius <= GROW_AT_OR_BELOW; }

    /**
     * Frostcap growth multiplier: 1x at -20C rising linearly to 2x at -80C, capped there; 0 when too warm to grow.
     */
    public static double frostcapGrowth(float celsius) {
        if (celsius > GROW_AT_OR_BELOW) return 0;
        return 1 + Math.min(1, (GROW_AT_OR_BELOW - celsius) / 60.0);
    }

    /** Per random tick at 1x: ripen one age step (of two) with this chance, then try to spread with the next. */
    public static final double FROSTCAP_RIPEN_CHANCE = 1 / 6.0, FROSTCAP_SPREAD_CHANCE = 1 / 25.0;
    /** Spread is capped like vanilla mushrooms: at most this many Frostcaps in the 9x3x9 box around one. */
    public static final int FROSTCAP_AREA_CAP = 5, FROSTCAP_AREA_RADIUS = 4;

    /** Frozen Frostcap thaws after 5 minutes above 0C; Thawed Frostcap turns to mush after another 5. Seconds. */
    public static final int THAW_SECONDS = 300;

    /** A warm stack's next state: frozen -> thawed -> mush; mush stays mush. Thawing is one-way (no refreeze). */
    public enum Cap { FROZEN, THAWED, MUSH }
    public static Cap next(Cap cap) { return cap == Cap.FROZEN ? Cap.THAWED : Cap.MUSH; }
}
