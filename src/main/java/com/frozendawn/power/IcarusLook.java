package com.frozendawn.power;

import com.frozendawn.power.IcarusPhysics.Alarm;
import com.frozendawn.power.IcarusPhysics.State;

/** How the Icarus looks for a given simulation state: core and shield colours, how many stabilizer beams are lit and
 * how fast the core churns. Pure, so the renderers only draw what this decides. Colours are 0xRRGGBB. */
public final class IcarusLook {
    public static final int STABILIZERS = 4;
    /** Core colour stops by temperature: dark, dull orange, orange, hot orange, deep red, red. */
    private static final double[] CORE_C = {IcarusPhysics.AMBIENT_C, 1500, 3000, 5000, IcarusPhysics.RED_LINE_C, IcarusPhysics.CRITICAL_C};
    private static final int[] CORE_RGB = {0x1A1418, 0xC0501A, 0xFF8C2A, 0xE0441A, 0xB0200C, 0xD01008};
    private static final int CRITICAL_CORE = 0xE81808, SHIELD_CYAN = 0x3AD8FF, SHIELD_AMBER = 0xFFA628, SHIELD_RED = 0xFF2A1E;
    /** The shield starts turning amber this hot, or below half strength. */
    private static final double SHIELD_WARM_C = 4500, SHIELD_WARM_PCT = 50;

    private IcarusLook() {}

    /** Stabilizer beams lit: none before spin-up, one more each quarter of spin-up, all four while the field holds, and
     * one fewer each quarter of the Cole discharge. */
    public static int beamsLit(State state, int stepLeft) {
        return switch (state) {
            case COLD, POWER_CHECK, RESERVE_CHARGE, DETONATED -> 0;
            case STABILIZER_SPINUP -> Math.clamp(1 + (IcarusPhysics.SPINUP_S - stepLeft) * STABILIZERS / IcarusPhysics.SPINUP_S, 1, STABILIZERS);
            case COLE_DISCHARGE -> Math.clamp(STABILIZERS - (IcarusPhysics.DISCHARGE_S - stepLeft) * STABILIZERS / IcarusPhysics.DISCHARGE_S, 0, STABILIZERS);
            default -> STABILIZERS;
        };
    }

    /** Whether the plasma has taken the core block's place: from injection until the antimatter is back in its capsules. */
    public static boolean coreHidden(State state) {
        return switch (state) {
            case INJECTION, PRIMING, IGNITION, RAMP, RUNNING, CLIPPED, COLE_RAMP_DOWN, COLE_COOL, COLE_RECOVER, COLE_DISCHARGE -> true;
            default -> false;
        };
    }

    /** How close the reactor feels to the end, 0 to 1, for the escalating alarm, shake and screen warnings: nothing without
     * an alarm, a little at caution, tense at warning, and from 0.55 at critical up to 1 as the shield runs out, sooner if
     * the core is past the red line. */
    public static double danger(double coreC, double shieldPct, Alarm alarm) {
        double hot = Math.clamp((coreC - IcarusPhysics.RED_LINE_C) / (IcarusPhysics.CRITICAL_C - IcarusPhysics.RED_LINE_C), 0, 1);
        return switch (alarm) {
            case NONE -> 0;
            case CAUTION -> 0.05;
            case WARNING -> 0.2 + 0.3 * Math.max(hot, Math.clamp((30 - shieldPct) / 30, 0, 1));
            case CRITICAL -> 0.55 + 0.45 * Math.max(Math.clamp((15 - shieldPct) / 15, 0, 1), hot);
        };
    }
    public static boolean critical(double coreC, Alarm alarm) { return alarm == Alarm.CRITICAL || coreC >= IcarusPhysics.CRITICAL_C; }

    public static int coreColor(double coreC, Alarm alarm) {
        if (critical(coreC, alarm)) return CRITICAL_CORE;
        double c = Double.isFinite(coreC) ? coreC : IcarusPhysics.AMBIENT_C;
        if (c <= CORE_C[0]) return CORE_RGB[0];
        for (int i = 1; i < CORE_C.length; i++)
            if (c <= CORE_C[i]) return mix(CORE_RGB[i - 1], CORE_RGB[i], (c - CORE_C[i - 1]) / (CORE_C[i] - CORE_C[i - 1]));
        return CORE_RGB[CORE_RGB.length - 1];
    }

    /** Cyan while running, warming to amber with heat or a falling shield, red when critical. */
    public static int shieldColor(double coreC, double shieldPct, Alarm alarm) {
        if (critical(coreC, alarm)) return SHIELD_RED;
        double heat = (coreC - SHIELD_WARM_C) / (IcarusPhysics.RED_LINE_C - SHIELD_WARM_C);
        double strain = (SHIELD_WARM_PCT - shieldPct) / (SHIELD_WARM_PCT - 15);
        return mix(SHIELD_CYAN, SHIELD_AMBER, Math.clamp(Math.max(heat, strain), 0, 1));
    }

    /** Opacity of the shield shell: a faint outline before the stabilizers spin up, thicker the stronger it is. */
    public static double shieldAlpha(State state, double shieldPct) {
        return switch (state) {
            case DETONATED -> 0;
            case COLD, POWER_CHECK, RESERVE_CHARGE -> 0.03;
            default -> 0.12 + 0.28 * Math.clamp(shieldPct / 100, 0, 1);
        };
    }

    /** Core surface churn in turns per second: still when cold, faster with output and heat. */
    public static double churn(double grossMw, double coreC) {
        double out = Math.clamp(grossMw / IcarusPhysics.MAX_OUTPUT_MW, 0, 1);
        double heat = Math.clamp((coreC - IcarusPhysics.AMBIENT_C) / (IcarusPhysics.CRITICAL_C - IcarusPhysics.AMBIENT_C), 0, 1);
        return 0.15 * out + 0.35 * heat;
    }

    public static int mix(int a, int b, double t) {
        int r = (int) Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = (int) Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }
}
