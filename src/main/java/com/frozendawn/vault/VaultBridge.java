package com.frozendawn.vault;

/** The backward bridge's reach. A frozen walkway alone would let a player wait with their back turned and then walk over
 * facing forward, so a new cell comes only for someone standing near its end: it reaches two cells past them. */
public final class VaultBridge {
    public static final int SPAN = 9, REACH = 2;
    private VaultBridge() { }

    /** @param out cells already out. @param standing the cell a player stands on; the ledge is -1 and -2. */
    public static boolean wants(int out, int standing) {
        return out < SPAN && out - standing <= REACH;
    }
}
