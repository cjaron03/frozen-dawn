package com.frozendawn.vault;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Morgue Gate, without a world. The store's A/C keeps the cold room cold, and its scanner passes the dead and nobody else, by
 * pulse and temperature. Standing in the blast at MAX takes a body past the line, at a cost in hearts, and it warms only slowly
 * after: long enough to walk to the plate, where the scanner opens for a few seconds. Anyone warm on the plate shuts it.
 */
public final class MorgueGate {
    /** Degrees: a body, the line at or below which the scanner reads nobody, what it reads as cooling, and the store itself. */
    public static final float BODY = 37, LINE = 12, COOLING = 30, COLDEST = 4;
    /** Degrees a tick. */
    public static final float COOL = .08F, WARM = .01F;
    /** Passed around for no reading. */
    public static final float NOBODY = -1;
    public static final int OPEN_TICKS = 5 * 20, BITE_EVERY = 2 * 20;
    public static final float BITE_DAMAGE = 2;

    private final Map<UUID, Float> bodies = new HashMap<>();
    private int open;
    private float reading = NOBODY;

    public static boolean bites(long gameTime) { return gameTime % BITE_EVERY == 0; }

    public boolean open() { return open > 0; }
    /** What the panel shows, or {@link #NOBODY}. */
    public float reading() { return reading; }
    public float temperature(UUID body) { return bodies.getOrDefault(body, BODY); }
    /** How many bodies are still cold. */
    public int bodies() { return bodies.size(); }

    /** Each tick: those in the blast cool, and everyone else warms back toward a living body. */
    public void expose(Set<UUID> blast) {
        for (var id : blast) bodies.putIfAbsent(id, BODY);
        bodies.replaceAll((id, degrees) -> blast.contains(id) ? Math.max(COLDEST, degrees - COOL) : degrees + WARM);
        bodies.values().removeIf(degrees -> degrees >= BODY);
    }

    /** A body that died starts its next life warm. */
    public void forget(UUID body) { bodies.remove(body); }

    /** @param plate everyone on the plate. The warmest is what it reads. */
    public void tick(Collection<UUID> plate) {
        if (plate.isEmpty()) {
            if (open > 0) open--;
            if (open == 0) reading = NOBODY;
            return;
        }
        reading = NOBODY;
        for (var id : plate) reading = Math.max(reading, temperature(id));
        open = reading <= LINE ? OPEN_TICKS : 0;
    }

    /** Playtest only. */
    public void reset() {
        bodies.clear();
        open = 0;
        reading = NOBODY;
    }
}
