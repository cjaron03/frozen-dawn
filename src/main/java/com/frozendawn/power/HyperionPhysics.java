package com.frozendawn.power;

import net.minecraft.nbt.CompoundTag;

/** SMS-1 Hyperion superconducting cells, one step per second. The cryocooler holds the cells at OPERATING_C and is
 * paid from the battery's own charge: that draw is the self-discharge. When the heat load beats the coolers (or the
 * charge cannot pay them) the cells warm toward QUENCH_C over the warning time; reaching it with charge quenches,
 * reaching it empty only leaves the cells warm. Warm cells hold nothing until cooled back down. Pure: no world access. */
public final class HyperionPhysics {
    public static final long CELL_JOULES = 20_000_000L;
    public static final int PORT_WATTS = 500_000;
    public static final double QUENCH_C = -180, OPERATING_C = -200;
    /** Cell-degrees above QUENCH_C one Cryocooler removes. */
    public static final double COOLER_CAPACITY = 12_500, HOT_NEIGHBOR_LOAD = 60_000;
    public static final int WARN_COLD_S = 900, WARN_ROOM_S = 300, WARN_HOT_S = 90, FINAL_S = 25;
    public static final double RECOVER_C_PER_S = 1.0, COOLDOWN_C_PER_S = 4.0;
    public static final int COOLDOWN_WATTS_PER_CELL = 1000;
    private static final double EPS = 1e-9;
    /** Self-discharge in % of capacity per hour, at these surroundings; linear between, and past the warm end. */
    private static final double[][] DISCHARGE = {{-180, 0}, {-120, 0.1}, {-45, 0.5}, {20, 2.0}};

    public enum Stage { COLD, CHIRP, WARNING, FINAL, WARM }
    /** cooldownJoules is what the grid granted this second for cooling warm cells back down. */
    public record Inputs(int cells, int coolers, double surroundingsC, int hotNeighbors, long cooldownJoules) {}
    /** coolerJoules is what the caller takes from the charge this second. secondsToQuench is infinite unless warming. */
    public record Readout(Stage stage, double cellC, long coolerJoules, double secondsToQuench, boolean quenched, boolean allClear, int secondsInStage) {}

    private double cellC = OPERATING_C;
    private Stage stage = Stage.COLD;
    private int secondsInStage;

    public static long capacity(int cells) { return cells * CELL_JOULES; }
    public static double selfDischargePercentPerHour(double surroundingsC) {
        if (surroundingsC <= DISCHARGE[0][0]) return 0;
        for (int i = 1; i < DISCHARGE.length; i++) if (surroundingsC <= DISCHARGE[i][0]) return lerp(DISCHARGE[i - 1], DISCHARGE[i], surroundingsC);
        return lerp(DISCHARGE[DISCHARGE.length - 2], DISCHARGE[DISCHARGE.length - 1], surroundingsC);
    }
    private static double lerp(double[] a, double[] b, double x) { return a[1] + (b[1] - a[1]) * (x - a[0]) / (b[0] - a[0]); }
    public static long coolerJoules(long capacity, double surroundingsC) { return Math.round(capacity * selfDischargePercentPerHour(surroundingsC) / 100 / 3600); }
    public static double heatLoad(int cells, double surroundingsC, int hotNeighbors) { return cells * Math.max(0, surroundingsC - QUENCH_C) + hotNeighbors * HOT_NEIGHBOR_LOAD; }
    public static int warningSeconds(double surroundingsC, int hotNeighbors) {
        if (hotNeighbors > 0) return WARN_HOT_S;
        if (surroundingsC <= 0) return WARN_COLD_S;
        if (surroundingsC >= 20) return WARN_ROOM_S;
        return (int) Math.round(WARN_COLD_S + (WARN_ROOM_S - WARN_COLD_S) * surroundingsC / 20);
    }
    public static long cooldownWatts(int cells) { return (long) cells * COOLDOWN_WATTS_PER_CELL; }

    public double cellC() { return cellC; }
    public Stage stage() { return stage; }
    /** A freshly formed battery starts at its surroundings, never colder than the cooler holds it. */
    public void reset(double surroundingsC) {
        cellC = Math.max(OPERATING_C, surroundingsC);
        stage = cellC > QUENCH_C ? Stage.WARM : Stage.COLD; secondsInStage = 0;
    }

    public Readout step(Inputs in, long stored) {
        Stage before = stage; boolean quenched = false; long cooler = 0; double toQuench = Double.POSITIVE_INFINITY;
        if (stage != Stage.WARM) {
            long cost = coolerJoules(capacity(in.cells()), in.surroundingsC());
            boolean unpaid = stored < cost;
            cooler = Math.min(Math.max(0, stored), cost);
            double load = heatLoad(in.cells(), in.surroundingsC(), in.hotNeighbors());
            boolean overloaded = load > in.coolers() * COOLER_CAPACITY || unpaid && load > 0;
            double rate = (QUENCH_C - OPERATING_C) / warningSeconds(in.surroundingsC(), in.hotNeighbors());
            if (overloaded) cellC += rate;
            else cellC = Math.max(OPERATING_C, cellC - RECOVER_C_PER_S);
            if (cellC >= QUENCH_C - EPS) {
                // The cooler's last joules went first, so only charge left after paying it can quench.
                if (stored - cooler > 0) { quenched = true; cellC = Math.max(in.surroundingsC(), QUENCH_C + 20); }
                stage = Stage.WARM; cooler = quenched ? 0 : cooler;
            } else if (overloaded) {
                toQuench = (QUENCH_C - cellC) / rate;
                int warn = warningSeconds(in.surroundingsC(), in.hotNeighbors());
                stage = toQuench <= FINAL_S + EPS ? Stage.FINAL : toQuench <= warn / 3.0 + EPS ? Stage.WARNING : Stage.CHIRP;
            } else stage = Stage.COLD;
        } else {
            double share = in.surroundingsC() <= QUENCH_C ? 1 : Math.min(1, in.cooldownJoules() / (double) cooldownWatts(in.cells()));
            cellC = Math.max(OPERATING_C, cellC - COOLDOWN_C_PER_S * share);
            if (cellC <= OPERATING_C + EPS) { cellC = OPERATING_C; stage = Stage.COLD; }
        }
        boolean allClear = stage == Stage.COLD && (before == Stage.CHIRP || before == Stage.WARNING || before == Stage.FINAL);
        secondsInStage = stage == before ? secondsInStage + 1 : 0;
        return new Readout(stage, cellC, cooler, toQuench, quenched, allClear, secondsInStage);
    }

    public void save(CompoundTag tag) { tag.putDouble("CellC", cellC); tag.putString("Stage", stage.name()); tag.putInt("StageSeconds", secondsInStage); }
    public void load(CompoundTag tag) {
        cellC = tag.contains("CellC") ? tag.getDouble("CellC") : OPERATING_C;
        try { stage = Stage.valueOf(tag.getString("Stage")); } catch (IllegalArgumentException e) { stage = cellC > QUENCH_C ? Stage.WARM : Stage.COLD; }
        secondsInStage = tag.getInt("StageSeconds");
    }
}
