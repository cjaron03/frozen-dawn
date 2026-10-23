package com.frozendawn.airlock;

/** Integer O2 bookkeeping, in existing item units rather than physical liters. */
public final class AirlockCycle {
    public static final int UNITS_PER_CELL = 100, RESERVE_CAPACITY = 6400, DURATION = 80;
    public enum Mode { IDLE, PRESSURIZING, DEPRESSURIZING }
    private final int capacity;
    private int reserve, air, elapsed, planned, moved, recovered, lost;
    private Mode mode = Mode.IDLE;

    public AirlockCycle(int cells, int initialAir) {
        if (cells < 1 || cells > 32) throw new IllegalArgumentException("Invalid airlock volume");
        capacity = cells * UNITS_PER_CELL;
        air = Math.clamp(initialAir, 0, capacity);
    }
    public int capacity() { return capacity; }
    public int reserve() { return reserve; }
    public int air() { return air; }
    public int elapsed() { return elapsed; }
    public int lost() { return lost; }
    public Mode mode() { return mode; }
    public boolean breathable() { return air >= capacity && mode == Mode.IDLE; }
    public boolean cycling() { return mode != Mode.IDLE; }
    public double density(){return (double)air/capacity;}
    static AirlockCycle reconfigured(int cells,int air,int reserve) {
        var gas=new AirlockCycle(cells,0);gas.air=Math.max(0,air);gas.fill(reserve);return gas;
    }
    public int fill(int offered) {
        int accepted = Math.clamp(offered, 0, RESERVE_CAPACITY-reserve);
        reserve += accepted; return accepted;
    }
    public boolean start(boolean pressurize) {
        if (pressurize && air >= capacity) return false;
        return startTo(pressurize ? capacity : 0);
    }
    /** Match a measured destination density; the existing snapshot retains the remaining transfer. */
    public boolean startTo(int targetAir) {
        if (cycling() || targetAir < 0 || targetAir == air) return false;
        boolean pressurize = targetAir > air;
        int amount = pressurize ? targetAir-air : air-targetAir;
        if (pressurize && reserve < amount) return false;
        mode = pressurize ? Mode.PRESSURIZING : Mode.DEPRESSURIZING;
        planned = amount; elapsed = moved = recovered = lost = 0;
        return true;
    }
    public boolean tick() {
        if (!cycling()) return false;
        int target = (int)((long)planned * ++elapsed / DURATION);
        int delta = target-moved;
        if (mode == Mode.PRESSURIZING) { reserve -= delta; air += delta; }
        else {
            air -= delta;
            int reclaim = (int)((long)target * 9 / 10) - recovered;
            int accepted = fill(reclaim);
            lost += delta-accepted;
            recovered += reclaim;
        }
        moved = target;
        if (elapsed == DURATION) { interrupt(); return true; }
        return false;
    }
    public void interrupt() { mode = Mode.IDLE; elapsed = planned = moved = recovered = 0; }
    public void vent() { air = 0; interrupt(); }
    public void discardReserve() { reserve = 0; interrupt(); }

    public int[] snapshot() { return new int[]{capacity,reserve,air,mode.ordinal(),elapsed,planned,moved,recovered,lost}; }
    public static AirlockCycle restore(int cells,int[] data) {
        var c = new AirlockCycle(cells,0);
        if (data.length != 9 || data[0] != c.capacity || data[1] < 0 || data[1] > RESERVE_CAPACITY
                || data[2] < 0 || data[3] < 0 || data[3] >= Mode.values().length
                || data[4] < 0 || data[4] >= DURATION || data[5] < 0
                || data[6] < 0 || data[6] > data[5] || data[7] < 0 || data[7] > data[6] || data[8] < 0)
            return c;
        c.reserve=data[1];c.air=data[2];c.mode=Mode.values()[data[3]];c.elapsed=data[4];
        c.planned=data[5];c.moved=data[6];c.recovered=data[7];c.lost=data[8];
        if (c.cycling() && (c.moved != (long)c.planned*c.elapsed/DURATION
                || c.mode == Mode.PRESSURIZING && (c.reserve < c.planned-c.moved || c.air < c.moved)
                || c.mode == Mode.DEPRESSURIZING && (c.air < c.planned-c.moved || c.recovered != (long)c.moved*9/10)))
            c.interrupt();
        return c;
    }
}
