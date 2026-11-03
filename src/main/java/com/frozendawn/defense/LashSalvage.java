package com.frozendawn.defense;

/**
 * Where a survival player finds Lash Emitters: one or two on every Helion site's diagnostics bench, and one in the
 * maintenance bay of about one CFU-7 reactor site in four. Each roll is fixed by the world seed and the site's packed
 * position, independent of the builder's own random, so a site always rolls the same.
 */
public final class LashSalvage {
    public static final int REACTOR_BAY_ODDS = 4, MEMO_ODDS = 4;
    private LashSalvage() {}

    public static int helionEmitters(long seed, long site) { return 1 + (int) (roll(seed, site, 0x4C43345F48454CL) & 1); }
    public static boolean reactorBayEmitter(long seed, long site) { return Math.floorMod(roll(seed, site, 0x4C43345F424159L), REACTOR_BAY_ODDS) == 0; }
    /** The hellwhip requisition memo, filed at about one Helion site in four. */
    public static boolean helionMemo(long seed, long site) { return Math.floorMod(roll(seed, site, 0x4C43345F4D454DL), MEMO_ODDS) == 0; }

    /** SplitMix64 finaliser over the seed, the site and a per-roll salt. */
    private static long roll(long seed, long site, long salt) {
        long h = seed ^ site * 0x9E3779B97F4A7C15L ^ salt;
        h = (h ^ h >>> 30) * 0xBF58476D1CE4E5B9L;
        h = (h ^ h >>> 27) * 0x94D049BB133111EBL;
        return (h ^ h >>> 31) >>> 1;
    }
}
