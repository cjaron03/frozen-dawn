package com.frozendawn.maeve;

/** §9.4a Pyro tunables (owner, 2026-10-22: accept the proposed defaults). */
final class PyroPolicy {
    /** One Pyro at a time; the next may be designated five in-game days after the last one ended. */
    static final long COOLDOWN = 5 * 24000L;
    /** A claim that never resolves (unloaded, lost) still ends, so it cannot block the next Pyro forever. */
    static final long LIFETIME = 24000L;
    /** Thermal sight range from the Pyro's eyes. */
    static final int SIGHT = 64;
    /** The area read around the suspected home: horizontal radius and half height. */
    static final int SCAN_RADIUS = 24, SCAN_HEIGHT = 16;
    /** Heat already believed this near the suspected home leaves nothing for a Pyro to find out. */
    static final int HOME_RADIUS = 32;
    /** A look sees the home when its line toward the home first strikes within this many blocks of it: the home's own walls. */
    static final int HOME_VIEW = 12;
    static final double KNOWN_HEAT = CommitmentPolicy.THRESHOLD;
    /** Seen directly or leaking through an opening; a thin wall only glows faintly. */
    static final double SEEN = .2, GLOW = .1;

    private PyroPolicy() { }

    static String gate(String lifecycle, boolean active, long lastEnded, long now, boolean home, boolean warm) {
        if (!"ACTIVE".equals(lifecycle)) return "LIFECYCLE_" + lifecycle;
        if (active) return "PYRO_ALREADY_ACTIVE";
        if (lastEnded >= 0 && now >= lastEnded && now - lastEnded < COOLDOWN) return "COOLDOWN";
        if (!home) return "NO_SUSPECTED_HOME";
        return warm ? "HOME_ALREADY_WARM" : "ELIGIBLE";
    }
}
