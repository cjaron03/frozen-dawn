package com.frozendawn.world;

import com.frozendawn.phase.SurfaceTemperatureCurve;
import java.util.LinkedHashMap;
import java.util.Map;

/** Depth/time diffusion of the canonical surface curve. Never queries or loads chunks. */
public final class GroundTemperatureModel {
    public static final int SURFACE_Y = 64;
    public static final int MAX_DEPTH = 128;
    public static final int PROGRESS_STEPS = 1000;
    public static final double DEFAULT_DIFFUSIVITY = 8000;
    public static final double BRUTAL_DIFFUSIVITY = 12000;
    public static final double GRADIENT = .30;

    // Two preset kernels and at most two custom kernels; independent of world/save lifetime.
    private static final Map<Double, ResponseTable> TABLES = new LinkedHashMap<>(4, .75f, true);

    private GroundTemperatureModel() {}

    public static double temperature(double y, double progress, double geothermalStrength,
                                     double surfaceScale, double diffusivity) {
        if (!Double.isFinite(y) || !Double.isFinite(progress)
                || !Double.isFinite(geothermalStrength) || geothermalStrength < 0
                || !Double.isFinite(surfaceScale) || surfaceScale < 0
                || !Double.isFinite(diffusivity) || diffusivity <= 0) {
            throw new IllegalArgumentException("Ground temperature requires finite nonnegative parameters and positive diffusivity");
        }
        double depth = Math.clamp(SURFACE_Y - y, 0, MAX_DEPTH);
        progress = Math.clamp(progress, 0, 1);
        if (depth == 0) return SurfaceTemperatureCurve.temperature((float) progress, (float) surfaceScale);
        ResponseTable table = table(diffusivity);
        return GRADIENT * depth * geothermalStrength
                + table.sample(table.cold, depth, progress) * surfaceScale
                + table.sample(table.rebound, depth, progress);
    }

    private static synchronized ResponseTable table(double diffusivity) {
        ResponseTable existing = TABLES.get(diffusivity);
        if (existing != null) return existing;
        ResponseTable created = new ResponseTable(diffusivity);
        if (TABLES.size() == 4) TABLES.remove(TABLES.keySet().iterator().next());
        TABLES.put(diffusivity, created);
        return created;
    }

    private static final class ResponseTable {
        private final float[][] cold = new float[MAX_DEPTH + 1][PROGRESS_STEPS + 1];
        private final float[][] rebound = new float[MAX_DEPTH + 1][PROGRESS_STEPS + 1];

        private ResponseTable(double diffusivity) {
            double[] coldSteps = new double[PROGRESS_STEPS + 1];
            double[] reboundSteps = new double[PROGRESS_STEPS + 1];
            for (int i = 0; i <= PROGRESS_STEPS; i++) {
                float progress = (float) i / PROGRESS_STEPS;
                cold[0][i] = SurfaceTemperatureCurve.base(progress);
                rebound[0][i] = SurfaceTemperatureCurve.rebound(progress);
                if (i > 0) {
                    coldSteps[i] = cold[0][i] - cold[0][i - 1];
                    reboundSteps[i] = rebound[0][i] - rebound[0][i - 1];
                }
            }
            for (int z = 1; z <= MAX_DEPTH; z++) {
                double[] kernel = new double[PROGRESS_STEPS + 1];
                for (int lag = 1; lag <= PROGRESS_STEPS; lag++) {
                    // Surface increments centered in their interval reduce time-discretization bias.
                    double elapsed = (lag - .5) / PROGRESS_STEPS;
                    kernel[lag] = erfc(z / (2 * Math.sqrt(diffusivity * elapsed)));
                }
                for (int i = 1; i <= PROGRESS_STEPS; i++) {
                    double coldSum = 0, reboundSum = 0;
                    for (int k = 1; k <= i; k++) {
                        double response = kernel[i - k + 1];
                        coldSum += coldSteps[k] * response;
                        reboundSum += reboundSteps[k] * response;
                    }
                    cold[z][i] = (float) coldSum;
                    rebound[z][i] = (float) reboundSum;
                }
            }
        }

        private double sample(float[][] values, double depth, double progress) {
            int z0 = (int) depth, z1 = Math.min(MAX_DEPTH, z0 + 1);
            double clock = progress * PROGRESS_STEPS;
            int p0 = (int) clock, p1 = Math.min(PROGRESS_STEPS, p0 + 1);
            double t = clock - p0;
            double low = values[z0][p0] + t * (values[z0][p1] - values[z0][p0]);
            double high = values[z1][p0] + t * (values[z1][p1] - values[z1][p0]);
            return low + (depth - z0) * (high - low);
        }
    }

    /** Abramowitz & Stegun 7.1.26, evaluated directly as erfc to avoid cancellation.
     * Mathematical coefficients, not adapted implementation code. Maximum absolute error 1.5e-7.
     * Reference: https://web.maths.unsw.edu.au/~rsw/optnotes.pdf, equation 9.1.7 (printed p.190).
     */
    private static double erfc(double x) {
        if (x == 0) return 1;
        double t = 1 / (1 + .3275911 * x);
        return t * (.254829592 + t * (-.284496736 + t * (1.421413741
                + t * (-1.453152027 + t * 1.061405429)))) * Math.exp(-x * x);
    }
}
