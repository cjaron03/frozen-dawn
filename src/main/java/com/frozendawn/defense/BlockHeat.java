package com.frozendawn.defense;

/**
 * Heat a held beam builds in one block: each tick it lands adds {@link #PER_TICK}, and each idle tick sheds one, so a
 * sweep past leaves a block and a held beam gives way after its dwell. Several rays landing in one tick heat it once.
 */
public final class BlockHeat {
    public static final int PER_TICK = 2;
    /** Ticks of held beam before each gives way. Snow and plain ice go at once. */
    public static final int STONE_TICKS = 10, PACKED_ICE_TICKS = 20, BLUE_ICE_TICKS = 40;
    private BlockHeat() {}

    public static int land(int heat, long last, long now) {
        if (heat <= 0) return PER_TICK;
        if (now == last) return heat;
        return (int) Math.max(0, heat - Math.max(0, now - last - 1)) + PER_TICK;
    }
    public static boolean gives(int heat, int dwellTicks) { return heat >= dwellTicks * PER_TICK; }
    public static boolean cold(int heat, long last, long now) { return heat - (now - last) <= 0; }
}
