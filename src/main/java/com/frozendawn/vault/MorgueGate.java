package com.frozendawn.vault;

/**
 * The Morgue Gate, without a world. The scanner lets the dead through and nobody else: at or below one heart it reads no
 * vitals and opens for a few seconds. Anyone on the plate above that line shuts it. The vent is what gets you there.
 */
public final class MorgueGate {
    /** Health at or below which the scanner reads nobody. */
    public static final float LINE = 2;
    /** Passed to {@link #tick} for an empty plate. */
    public static final float NOBODY = -1;
    public static final int OPEN_TICKS = 5 * 20, VENT_EVERY = 2 * 20, MOST_HEARTS = 10;
    public static final float VENT_DAMAGE = 2;

    private int open, hearts;

    public static boolean bites(long gameTime) { return gameTime % VENT_EVERY == 0; }

    public boolean open() { return open > 0; }
    /** Hearts for the panel, or nought for nothing to show. */
    public int hearts() { return hearts; }

    /** @param health the healthiest body on the plate, or {@link #NOBODY}. */
    public void tick(float health) {
        if (health == NOBODY) {
            if (open > 0) open--;
            if (open == 0) hearts = 0;
            return;
        }
        hearts = Math.min(MOST_HEARTS, (int) Math.ceil(health / 2));
        open = health <= LINE ? OPEN_TICKS : 0;
    }

    /** Playtest only. */
    public void reset() { open = 0; hearts = 0; }
}
