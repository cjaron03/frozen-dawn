package com.frozendawn.world;

/** Independently paced post-Maeve encounters. */
public enum PostMaeveEncounterType {
    RIMEBOUND(7_200L, 12_000L),
    RESONANT(9_600L, 14_400L),
    FROSTWRITHE(12_000L, 18_000L),
    UNDONE(7_200L, 12_000L),
    UNDONE_ARCHITECT(18_000L, 30_000L),
    BLOOMBOUND(7_200L, 12_000L),
    REMNANT(42_000L, 60_000L),
    ARCHIVIST(72_000L, 108_000L);

    private final long minimumIntervalTicks;
    private final long guaranteedIntervalTicks;

    PostMaeveEncounterType(long minimumIntervalTicks,
                           long guaranteedIntervalTicks) {
        this.minimumIntervalTicks = minimumIntervalTicks;
        this.guaranteedIntervalTicks = guaranteedIntervalTicks;
    }

    public long minimumIntervalTicks() {
        return minimumIntervalTicks;
    }

    public long guaranteedIntervalTicks() {
        return guaranteedIntervalTicks;
    }
}
