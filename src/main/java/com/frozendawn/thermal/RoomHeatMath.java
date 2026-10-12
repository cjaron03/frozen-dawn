package com.frozendawn.thermal;

/** Heat units are deliberately game-scaled. Energy uses Kelvin so vent/removal budgets remain explicit. */
public final class RoomHeatMath {
    public static final double ABSOLUTE_ZERO = -273.15;
    public static final double AIR_CAPACITY = 1.0;
    public static final double MATERIAL_CAPACITY = 12.0;
    public static final double VACUUM_HEATER_EFFICIENCY = 0.25;
    public static final double RADIANT_FEEL_WEIGHT = 0.20;
    private RoomHeatMath() {}
    public static double energy(double capacity, double celsius) {
        return Math.max(0, capacity) * Math.max(0, celsius - ABSOLUTE_ZERO);
    }
    public static double temperature(double capacity, double energy) {
        return capacity > 0 ? Math.max(0, energy) / capacity + ABSOLUTE_ZERO : ABSOLUTE_ZERO;
    }
    /** Exact exchange between two finite reservoirs; positive means air -> structure. */
    public static double exchange(double airCapacity, double structureCapacity, double airTemperature,
            double structureTemperature, double conductance, double seconds) {
        if (airCapacity <= 0 || structureCapacity <= 0 || conductance <= 0 || seconds <= 0) return 0;
        double reduced = airCapacity * structureCapacity / (airCapacity + structureCapacity);
        return reduced * (airTemperature - structureTemperature)
                * -Math.expm1(-conductance * seconds / reduced);
    }
    /** Exact loss to a fixed reservoir; negative means environmental heat entering the material. */
    public static double reservoirLoss(double capacity, double temperature, double outside,
            double conductance, double seconds) {
        if (capacity <= 0 || conductance <= 0 || seconds <= 0) return 0;
        return capacity * (temperature - outside) * -Math.expm1(-conductance * seconds / capacity);
    }
    public static double seriesConductance(double... tiers) {
        double resistance = 0;
        for (double tier : tiers) if (tier > 0) resistance += 1.0 / tier;
        return resistance > 0 ? 1.0 / resistance : 0;
    }
}
