package com.frozendawn.data;

/** Gradual metabolic load plus exact fractional reserve debit. Independent of rendering/frame rate. */
public final class EmergencyEvaExertion {
    public static final int MAX_LOAD = 200;
    private static final int EXTRA_UNIT = MAX_LOAD * 4;
    private int load;
    private int fractionalDebit;

    public EmergencyEvaExertion() {}
    public EmergencyEvaExertion(int load, int fractionalDebit) {
        this.load = Math.clamp(load, 0, MAX_LOAD);
        this.fractionalDebit = Math.clamp(fractionalDebit, 0, EXTRA_UNIT - 1);
    }
    public int load() { return load; }
    public int fractionalDebit() { return fractionalDebit; }
    public float intensity() { return load / (float) MAX_LOAD; }
    public void advance(boolean running) {
        // 2.5 seconds to steady high draw; up to 10 seconds to settle after stopping.
        load = Math.clamp(load + (running ? 4 : -1), 0, MAX_LOAD);
    }
    public int debit() {
        fractionalDebit += load;
        int extra = fractionalDebit / EXTRA_UNIT;
        fractionalDebit %= EXTRA_UNIT;
        return 1 + extra;
    }
}
