package com.frozendawn.maeve;

import java.util.List;

/** Provisional §14 tuning. History is lifetime; pressure is recent. Same values in every tier. */
final class ConvergencePolicy {
    static final int RADIUS = 24, MIN_DEATHS = 6, MIN_ENCOUNTERS = 3;
    static final int MAX_HOTSPOTS = 32, MAX_EVIDENCE = 16, MAX_RESULTS = 8;
    static final long QUIET = 600, HALF_LIFE = 24000, COOLDOWN = 12000;
    static final long WARNING = 240, TRAVEL_LIMIT = 2400, ENGAGEMENT_LIMIT = 1200;
    static final double ACTIVATION_WEIGHT = 3.0;
    static final int MESSAGE_RADIUS = 48, MESSAGE_LIMIT = 3;
    static double decay(double weight, long from, long now) {
        return weight * Math.pow(.5, Math.max(0, now - from) / (double) HALF_LIFE);
    }
    static int groupSize(double weight) { return weight >= 8 ? 3 : 2; }
    static int populationLimit(int eligiblePlayers) { return Math.clamp(eligiblePlayers * 3, 3, 12); }
    static List<StrategySelector.Score> scores(boolean avoid, int wipes, double weight) {
        return List.of(new StrategySelector.Score("CONVERGE", avoid ? -10 : 1, 0,
                        Math.min(1, weight / 8), 0, 0, wipes * .2, .2),
                new StrategySelector.Score("AVOID", 0, avoid ? 4 : .25 + wipes * .2, 0, 0, 0, 0, 0));
    }
    static int increment(int value) { return value == Integer.MAX_VALUE ? value : value + 1; }
    private ConvergencePolicy() { }
}
