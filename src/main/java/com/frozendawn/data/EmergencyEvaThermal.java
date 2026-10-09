package com.frozendawn.data;

/** Worn service age and recoverable internal heat; independent of oxygen debit and frame rate. */
public final class EmergencyEvaThermal {
    public static final int COOLANT_LIFE_TICKS = 5 * 60 * 20;
    public static final int MAX_HEAT = 1200;
    public static final int HIGH_HEAT = MAX_HEAT / 2;
    private int wornTicks;
    private int heat;

    public EmergencyEvaThermal(int wornTicks, int heat) {
        this.wornTicks = Math.clamp(wornTicks, 0, EmergencyEvaState.SERVICE_TICKS);
        this.heat = Math.clamp(heat, 0, MAX_HEAT);
    }

    public int wornTicks() { return wornTicks; }
    public int heat() { return heat; }
    public float intensity() { return heat / (float) MAX_HEAT; }
    public boolean coolingDegraded() { return wornTicks >= COOLANT_LIFE_TICKS; }
    public boolean highHeat() { return heat >= HIGH_HEAT; }

    public void tickWorn(boolean running, boolean sealed) {
        // The first five minutes can remove sprint heat. Reduced cooling then
        // worsens in two thirty-second steps, rather than an instant heat spike.
        if (coolingDegraded() && sealed && running) {
            int rate = 1 + Math.min(2, (wornTicks - COOLANT_LIFE_TICKS) / (30 * 20));
            heat = Math.min(MAX_HEAT, heat + rate);
        } else {
            recover();
        }
        wornTicks = Math.min(EmergencyEvaState.SERVICE_TICKS, wornTicks + 1);
    }

    public void recover() { heat = Math.max(0, heat - 2); }
}
