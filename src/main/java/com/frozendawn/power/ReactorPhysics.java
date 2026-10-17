package com.frozendawn.power;

import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** CFU-7 Hearthstone core, stepped once per second. Game-level abstraction; all values are tuning targets.
 * Heat leaves only through linked radiators (to the outside) and the casing (to the reactor room).
 * Power out = radiator heat removed x (1 - T_cold / T_hot), in Kelvin. */
public final class ReactorPhysics {
    public static final int ROD_SLOTS = 4;
    public static final double ROD_KW = 35, DEEP_VACUUM_C = -200, NOMINAL_C = 300;
    /** 12 kW per radiator with a 300C core against deep vacuum. */
    public static final double RADIATOR_KW = 12, RADIATOR_KW_PER_C = RADIATOR_KW / (NOMINAL_C - DEEP_VACUUM_C);
    /** Core heat capacity: about 60 s from LOSS OF COOLANT to failure at full output. */
    public static final double HEAT_CAPACITY_KJ_PER_C = 84;
    /** Casing loss to the room. Exceeds peak decay heat above 700C, so a manual SCRAM always recovers. */
    public static final double CASING_KW_PER_C = 0.02, ROOM_SHARE = 0.1;
    public static final double WARNING_C = 350, AUTO_SCRAM_C = 450, DAMAGE_C = 600, LOSS_OF_COOLANT_C = 700, MELTDOWN_C = 800;
    /** Core damage above 600C permanently derates electrical output; it never reduces fission heat,
     * so a damaged core runs away exactly as fast as a healthy one. */
    public static final double DECAY_SHARE = 0.07, DAMAGE_PER_SECOND = 0.002, MAX_DAMAGE = 0.75;
    /** 7% fading to 1% over one in-game day (1200 s). */
    public static final double DECAY_PER_SECOND = Math.pow(1 / 7.0, 1 / 1200.0);
    public static final int ROD_LIFE_SECONDS = 4 * 1200, FREEZE_SECONDS = 1200, THAW_SECONDS = 180;
    public static final double THAW_ROOM_C = 5, LOAD_FOLLOW_SLEW = 0.02;
    /** Coolant pumps run while the core makes heat or is above this; an idle core settles to its room. */
    public static final double PUMP_C = 40;
    /** Linked radiators run hot, glow and shed snow, ice and frozen atmosphere once the coolant reaching them is
     * this warm. Covered radiators heat too, so frost on an idle plant clears itself before the core needs it. */
    public static final double RADIATOR_HOT_C = 150;
    /** While operating, coolant flow is throttled to hold the core in its working band: closed below 200C, full
     * above 280C. Oversized radiators therefore never chill a running core. After a SCRAM pumps run full to cool down. */
    public static final double HOLD_LOW_C = 200, HOLD_HIGH_C = 280;
    /** Prompt power excursion (Chernobyl): with automatic SCRAM disabled, a running core above 780C surges to
     * about twelve times its fission heat, covering the last 20C in a second or two. */
    public static final double EXCURSION_C = 780, EXCURSION_GAIN = 12;

    public enum State { NOMINAL, WARNING, SCRAM, DAMAGE, LOSS_OF_COOLANT, MELTDOWN }
    public enum Coolant { FLOWING, IDLE, FROZEN, LOST, WARMING }

    /** One second of inputs. radiatorSinks holds the outside temperature at each working radiator.
     * targetKw is the load-follow electrical target, or NaN in manual mode. */
    public record Inputs(int rods, List<Double> radiatorSinks, double roomC, boolean redstone, double targetKw) {}
    public record Readout(State state, Coolant coolant, double heatInKw, double heatRemovedKw, double radiatorKw,
            double powerKw, double efficiency, double climbCPerSecond, double secondsToFailure, double roomHeatKw, double burn) {}

    private double coreC = 20, insertion, residualKw, damage;
    private boolean scram, autoScram = true, loadFollow, frozen, meltdown;
    private int coldSeconds, thawSeconds;

    public double coreC() { return coreC; }
    public double insertion() { return insertion; }
    public double damage() { return damage; }
    public boolean scrammed() { return scram; }
    public boolean autoScram() { return autoScram; }
    public boolean loadFollow() { return loadFollow; }
    public boolean frozen() { return frozen; }
    public boolean meltdown() { return meltdown; }
    public boolean rodsLocked() { return coreC > WARNING_C; }
    public void setCoreC(double value) { if (Double.isFinite(value)) coreC = value; }
    public void setInsertion(double value) { if (Double.isFinite(value)) insertion = Math.clamp(value, 0, 1); }
    public void setLoadFollow(boolean value) { loadFollow = value; }
    public void setAutoScram(boolean value) { autoScram = value; }
    /** Manual SCRAM works in every state, including with automatic SCRAM disabled. */
    public void scram() { scram = true; }
    /** Reset never fights an active trip: refused under a redstone signal, and at or above the automatic trip point
     * while automatic SCRAM is enabled. With it disabled, restarting a hot core is the operator's call. */
    public boolean canReset(boolean redstone) { return scram && !meltdown && !redstone && (!autoScram || coreC < AUTO_SCRAM_C); }
    public boolean reset(boolean redstone) {
        if (!canReset(redstone)) return false;
        scram = false; insertion = 0; return true;
    }

    public static double efficiency(double hotC, double coldC) {
        double hot = hotC + 273.15, cold = coldC + 273.15;
        return hot <= cold || hot <= 0 ? 0 : 1 - cold / hot;
    }
    /** Highest fission heat the linked radiators hold at the nominal temperature. */
    public static double sustainableKw(List<Double> sinks) {
        return sinks.stream().mapToDouble(t -> RADIATOR_KW_PER_C * Math.max(0, NOMINAL_C - t)).sum();
    }

    public Readout step(Inputs in) {
        if (meltdown) return new Readout(State.MELTDOWN, Coolant.LOST, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        if (in.redstone()) scram = true;
        double ratedKw = in.rods() * ROD_KW;
        double sinkC = in.radiatorSinks().stream().mapToDouble(Double::doubleValue).average().orElse(DEEP_VACUUM_C);
        double casingKw = CASING_KW_PER_C * (coreC - in.roomC());
        if (loadFollow && !scram && !frozen && Double.isFinite(in.targetKw()) && ratedKw > 0) {
            // Aim at the equilibrium heat that yields the target, capped at what radiators hold at 300C.
            double effNominal = Math.max(0.05, efficiency(NOMINAL_C, sinkC) * (1 - damage));
            double wanted = Math.min(Math.max(0, in.targetKw()) / effNominal, sustainableKw(in.radiatorSinks()))
                    + CASING_KW_PER_C * Math.max(0, NOMINAL_C - in.roomC());
            // A cold core warms up at full insertion first; flow control keeps it from producing until it is hot.
            double goal = coreC < HOLD_LOW_C ? 1 : Math.min(1, wanted / ratedKw);
            insertion += Math.clamp(goal - insertion, -LOAD_FOLLOW_SLEW, LOAD_FOLLOW_SLEW);
        }
        double fission = scram || frozen ? 0 : ratedKw * insertion;
        boolean excursion = !autoScram && fission > 0 && coreC >= EXCURSION_C;
        if (excursion) fission *= EXCURSION_GAIN;
        residualKw = Math.max(residualKw * DECAY_PER_SECOND, DECAY_SHARE * fission);
        if (residualKw < 0.01) residualKw = 0;
        double heatIn = Math.max(fission, residualKw);
        boolean lost = coreC >= LOSS_OF_COOLANT_C, pumping = !frozen && !lost && (heatIn > 0.05 || coreC > PUMP_C);
        double flow = scram ? 1 : Math.clamp((coreC - HOLD_LOW_C) / (HOLD_HIGH_C - HOLD_LOW_C), 0, 1);
        double radiatorKw = !pumping ? 0
                : flow * in.radiatorSinks().stream().mapToDouble(t -> RADIATOR_KW_PER_C * Math.max(0, coreC - t)).sum();
        double efficiency = efficiency(coreC, sinkC) * (1 - damage), power = radiatorKw * efficiency;
        double climb = (heatIn - radiatorKw - casingKw) / HEAT_CAPACITY_KJ_PER_C;
        coreC += climb;
        if (coreC >= DAMAGE_C) damage = Math.min(MAX_DAMAGE, damage + DAMAGE_PER_SECOND);
        if (autoScram && coreC >= AUTO_SCRAM_C) scram = true;
        if (!autoScram && coreC >= MELTDOWN_C) { meltdown = true; insertion = 0; }
        coolant(in.roomC());
        double toFailure = climb > 0 && coreC >= WARNING_C ? (MELTDOWN_C - coreC) / climb : Double.POSITIVE_INFINITY;
        double burn = fission > 0 ? insertion : 0;
        State state = meltdown ? State.MELTDOWN : lost || coreC >= LOSS_OF_COOLANT_C ? State.LOSS_OF_COOLANT
                : coreC >= DAMAGE_C ? State.DAMAGE : scram ? State.SCRAM : coreC >= WARNING_C ? State.WARNING : State.NOMINAL;
        Coolant coolant = frozen ? Coolant.FROZEN : lost ? Coolant.LOST : radiatorKw > 0.05 ? Coolant.FLOWING
                : pumping && !in.radiatorSinks().isEmpty() && fission > 0 ? Coolant.WARMING : Coolant.IDLE;
        return new Readout(state, coolant, heatIn, radiatorKw + Math.max(0, casingKw), radiatorKw, power, efficiency,
                climb, toFailure, Math.max(0, casingKw) * ROOM_SHARE, burn);
    }

    private void coolant(double roomC) {
        if (frozen) {
            thawSeconds = roomC >= THAW_ROOM_C ? thawSeconds + 1 : 0;
            if (thawSeconds >= THAW_SECONDS) { frozen = false; thawSeconds = 0; coldSeconds = 0; }
            return;
        }
        coldSeconds = coreC < 0 ? coldSeconds + 1 : 0;
        if (coldSeconds >= FREEZE_SECONDS) { frozen = true; thawSeconds = 0; }
    }

    public void save(CompoundTag tag) {
        tag.putDouble("CoreC", coreC); tag.putDouble("Insertion", insertion); tag.putDouble("ResidualKw", residualKw);
        tag.putDouble("Damage", damage); tag.putBoolean("Scram", scram); tag.putBoolean("AutoScram", autoScram);
        tag.putBoolean("LoadFollow", loadFollow); tag.putBoolean("Frozen", frozen); tag.putBoolean("Meltdown", meltdown);
        tag.putInt("ColdSeconds", coldSeconds); tag.putInt("ThawSeconds", thawSeconds);
    }
    public void load(CompoundTag tag) {
        coreC = tag.contains("CoreC") ? finite(tag.getDouble("CoreC"), 20) : 20;
        insertion = Math.clamp(finite(tag.getDouble("Insertion"), 0), 0, 1);
        residualKw = Math.max(0, finite(tag.getDouble("ResidualKw"), 0));
        damage = Math.clamp(finite(tag.getDouble("Damage"), 0), 0, MAX_DAMAGE);
        scram = tag.getBoolean("Scram"); autoScram = !tag.contains("AutoScram") || tag.getBoolean("AutoScram");
        loadFollow = tag.getBoolean("LoadFollow"); frozen = tag.getBoolean("Frozen"); meltdown = tag.getBoolean("Meltdown");
        coldSeconds = Math.max(0, tag.getInt("ColdSeconds")); thawSeconds = Math.max(0, tag.getInt("ThawSeconds"));
    }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
}
