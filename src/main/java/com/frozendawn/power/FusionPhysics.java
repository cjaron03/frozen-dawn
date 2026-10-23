package com.frozendawn.power;

import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** ORSA SFR-1 Solstice Reactor, stepped once per second. Fusion never melts down: anything wrong ends the plasma
 * (a disruption), costs First Wall wear and needs a full restart. Startup is cooldown, fuel check, then a 10 s
 * ignition pulse paid by the network. Power out = radiator heat removed x Carnot efficiency x (1 - wear) - house load. */
public final class FusionPhysics {
    public static final double RATED_KW = 400, NOMINAL_C = 900, DISRUPT_C = 1100;
    /** 30 kW per High-Temperature Radiator with a 900C loop against deep vacuum; about 14 carry a full burn. */
    public static final double HT_RADIATOR_KW = 30, HT_RADIATOR_KW_PER_C = HT_RADIATOR_KW / (NOMINAL_C - ReactorPhysics.DEEP_VACUUM_C);
    /** House load (magnets and plasma heating) and the ash pump come out of gross output while burning. */
    public static final double HOUSE_KW = 40, ASH_PUMP_KW = 5, COOLDOWN_KW = 20, IGNITION_KW = 150, PUMP_KW = 10;
    public static final int COOLDOWN_SECONDS = 120, IGNITION_SECONDS = 10, ASH_FADE_SECONDS = 60;
    public static final double VACUUM_LIMIT = 0.05, WEAR_PER_DISRUPTION = 0.05, MAX_IGNITION_WEAR = 0.80;
    public static final int DEUTERIUM_SECONDS = 600, TRITIUM_SECONDS = 1200, TRITIUM_TANK = 8, IGNITION_TRITIUM = 1;
    public static final double BREEDING_RATIO = 1.05;
    /** Tuning starts: loop thermal mass, lowest sustained burn and the load-follow slew per second. */
    public static final double LOOP_HEAT_CAPACITY_KJ_PER_C = 100, MIN_BURN = 0.2, LOAD_FOLLOW_SLEW = 0.02;

    public enum State { OFF, COOLDOWN, READY, IGNITION, BURNING }
    public enum Status { IDLE, COOLDOWN, READY, TORUS_OPEN, NO_VACUUM, NO_FUEL, WALL_WORN, IGNITION, ABORTED, BURNING,
        DISRUPTION, MAGNET_QUENCH, ASH_POISONED }

    /** One second of inputs. powered: the previous second's request was paid in full. channelPressure is a fraction
     * of nominal. wear is the mean First Wall wear, 0 to 1. targetKw is the load-follow net target, or NaN in manual mode. */
    public record Inputs(List<Double> radiatorSinks, boolean powered, double channelPressure, boolean hatchClosed,
            boolean shellIntact, boolean roomVacuum, int deuterium, boolean pumping, double wear, boolean redstone, double targetKw) {}
    /** requestKw is what the reactor draws from the network next second. disrupted is true only in the second a
     * wear-adding disruption happens. progress is the cooldown or ignition fraction. */
    public record Readout(State state, Status status, double loopC, double heatKw, double removedKw, double grossKw,
            double netKw, double requestKw, int deuteriumUsed, double tritium, double progress, boolean disrupted) {}

    private State state = State.OFF;
    private Status status = Status.IDLE;
    private double loopC = 20, tritium, deuteriumProgress, burn = MIN_BURN, requestKw;
    private int progress, ashSeconds;
    private boolean loadFollow, startPending, stopPending;

    public State state() { return state; }
    public Status status() { return status; }
    public double loopC() { return loopC; }
    public void setLoopC(double value) { if (Double.isFinite(value)) loopC = value; }
    public double tritium() { return tritium; }
    public boolean loadFollow() { return loadFollow; }
    public void setLoadFollow(boolean value) { loadFollow = value; }

    /** Returns the units accepted; the tank holds TRITIUM_TANK. */
    public double addTritium(double units) {
        double accepted = Math.clamp(units, 0, TRITIUM_TANK - tritium);
        tritium += accepted; return accepted;
    }
    /** Removes one whole unit for a Tritium Canister. */
    public boolean extractTritium() {
        if (tritium < 1) return false;
        tritium -= 1; return true;
    }

    /** From OFF begins cooldown; from READY asks for ignition, checked against next second's inputs. */
    public void start() {
        if (state == State.OFF) { state = State.COOLDOWN; status = Status.COOLDOWN; progress = 0; }
        else if (state == State.READY) startPending = true;
    }
    /** Before burning this simply powers down. While burning it is a disruption, like a redstone stop. */
    public void stop() {
        if (state == State.BURNING) stopPending = true;
        else if (state != State.OFF) { state = State.OFF; status = Status.IDLE; progress = 0; startPending = false; }
    }
    public void setReadyForTest() { state = State.READY; status = Status.READY; progress = 0; }

    public Readout step(Inputs in) {
        double sinkC = in.radiatorSinks().stream().mapToDouble(Double::doubleValue).average().orElse(ReactorPhysics.DEEP_VACUUM_C);
        if (state == State.BURNING) return burn(in, sinkC);
        switch (state) {
            case COOLDOWN -> {
                if (!in.powered()) off(Status.IDLE);
                else {
                    progress += in.roomVacuum() ? 2 : 1;
                    if (progress >= COOLDOWN_SECONDS) { state = State.READY; status = Status.READY; progress = 0; }
                }
            }
            case READY -> {
                if (!in.powered()) off(Status.IDLE);
                else {
                    Status blocked = ignitionBlock(in);
                    if (startPending && blocked == null) { state = State.IGNITION; status = Status.IGNITION; progress = 1; }
                    else if (blocked != null) status = blocked;
                    else if (status != Status.ABORTED) status = Status.READY;
                    startPending = false;
                }
            }
            case IGNITION -> {
                if (!in.powered()) { state = State.READY; status = Status.ABORTED; progress = 0; }
                else if (++progress >= IGNITION_SECONDS) {
                    state = State.BURNING; status = Status.BURNING; progress = 0;
                    tritium -= IGNITION_TRITIUM; burn = MIN_BURN; ashSeconds = 0; deuteriumProgress = 0;
                }
            }
            default -> {}
        }
        double removed = removedKw(in.radiatorSinks());
        loopC -= removed / LOOP_HEAT_CAPACITY_KJ_PER_C;
        requestKw = switch (state) {
            case COOLDOWN, READY -> COOLDOWN_KW;
            case IGNITION -> IGNITION_KW;
            default -> 0;
        };
        double shown = state == State.COOLDOWN ? (double) progress / COOLDOWN_SECONDS
                : state == State.IGNITION ? (double) progress / IGNITION_SECONDS : 0;
        return new Readout(state, status, loopC, 0, removed, 0, -requestKw, requestKw, 0, tritium, shown, false);
    }

    private Readout burn(Inputs in, double sinkC) {
        boolean shortfall = requestKw > 0 && !in.powered();
        if (shortfall) return disrupt(Status.MAGNET_QUENCH, in);
        if (in.redstone() || stopPending || !in.hatchClosed() || !in.shellIntact() || in.channelPressure() > VACUUM_LIMIT)
            return disrupt(Status.DISRUPTION, in);
        double pumpKw = in.pumping() ? ASH_PUMP_KW : 0;
        double goal = 1;
        if (loadFollow && Double.isFinite(in.targetKw())) {
            double eff = Math.max(0.05, ReactorPhysics.efficiency(NOMINAL_C, sinkC) * (1 - in.wear()));
            double capacity = in.radiatorSinks().stream().mapToDouble(t -> HT_RADIATOR_KW_PER_C * Math.max(0, NOMINAL_C - t)).sum();
            goal = Math.min((Math.max(0, in.targetKw()) + HOUSE_KW + pumpKw) / eff, capacity) / RATED_KW;
        }
        goal = Math.clamp(goal, MIN_BURN, 1);
        burn += Math.clamp(goal - burn, -LOAD_FOLLOW_SLEW, LOAD_FOLLOW_SLEW);
        ashSeconds = in.pumping() ? 0 : ashSeconds + 1;
        if (ashSeconds >= ASH_FADE_SECONDS) {
            off(Status.ASH_POISONED);
            return idle(in, false);
        }
        double heat = RATED_KW * burn * (1 - (double) ashSeconds / ASH_FADE_SECONDS);
        double removed = removedKw(in.radiatorSinks());
        loopC += (heat - removed) / LOOP_HEAT_CAPACITY_KJ_PER_C;
        if (loopC > DISRUPT_C) return disrupt(Status.DISRUPTION, in);
        double gross = removed * ReactorPhysics.efficiency(loopC, sinkC) * (1 - Math.clamp(in.wear(), 0, 1));
        double net = gross - HOUSE_KW - pumpKw;
        int used = 0;
        deuteriumProgress += burn / DEUTERIUM_SECONDS;
        if (deuteriumProgress >= 1) {
            if (in.deuterium() <= 0) return disrupt(Status.DISRUPTION, in);
            deuteriumProgress -= 1; used = 1;
        }
        tritium = Math.min(TRITIUM_TANK, Math.max(0, tritium + burn / TRITIUM_SECONDS * (BREEDING_RATIO - 1)));
        requestKw = Math.max(0, -net);
        return new Readout(state, status, loopC, heat, removed, gross, net, requestKw, used, tritium, 0, false);
    }

    /** Plasma ends at once; the loop keeps its heat and cools through the radiators. */
    private Readout disrupt(Status why, Inputs in) {
        off(why);
        return idle(in, true);
    }
    private Readout idle(Inputs in, boolean disrupted) {
        double removed = removedKw(in.radiatorSinks());
        loopC -= removed / LOOP_HEAT_CAPACITY_KJ_PER_C;
        requestKw = 0;
        return new Readout(state, status, loopC, 0, removed, 0, 0, 0, 0, tritium, 0, disrupted);
    }

    private Status ignitionBlock(Inputs in) {
        if (!in.hatchClosed()) return Status.TORUS_OPEN;
        if (in.channelPressure() > VACUUM_LIMIT) return Status.NO_VACUUM;
        if (in.deuterium() <= 0 || tritium < IGNITION_TRITIUM) return Status.NO_FUEL;
        if (in.wear() > MAX_IGNITION_WEAR) return Status.WALL_WORN;
        return null;
    }

    private void off(Status why) { state = State.OFF; status = why; progress = 0; startPending = false; stopPending = false; }

    private double removedKw(List<Double> sinks) {
        return sinks.stream().mapToDouble(t -> HT_RADIATOR_KW_PER_C * Math.max(0, loopC - t)).sum();
    }

    public void save(CompoundTag tag) {
        tag.putString("State", state.name()); tag.putString("Status", status.name()); tag.putDouble("LoopC", loopC);
        tag.putDouble("Tritium", tritium); tag.putDouble("DeuteriumProgress", deuteriumProgress); tag.putDouble("Burn", burn); tag.putDouble("RequestKw", requestKw);
        tag.putInt("Progress", progress); tag.putInt("AshSeconds", ashSeconds); tag.putBoolean("LoadFollow", loadFollow);
        tag.putBoolean("StartPending", startPending); tag.putBoolean("StopPending", stopPending);
    }
    public void load(CompoundTag tag) {
        state = parse(State.class, tag.getString("State"), State.OFF);
        status = parse(Status.class, tag.getString("Status"), Status.IDLE);
        loopC = tag.contains("LoopC") ? finite(tag.getDouble("LoopC"), 20) : 20;
        tritium = Math.clamp(finite(tag.getDouble("Tritium"), 0), 0, TRITIUM_TANK);
        deuteriumProgress = Math.clamp(finite(tag.getDouble("DeuteriumProgress"), 0), 0, 1);
        burn = Math.clamp(tag.contains("Burn") ? finite(tag.getDouble("Burn"), MIN_BURN) : MIN_BURN, MIN_BURN, 1);
        requestKw = Math.max(0, finite(tag.getDouble("RequestKw"), 0));
        progress = Math.max(0, tag.getInt("Progress")); ashSeconds = Math.max(0, tag.getInt("AshSeconds"));
        loadFollow = tag.getBoolean("LoadFollow"); startPending = tag.getBoolean("StartPending"); stopPending = tag.getBoolean("StopPending");
    }
    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try { return Enum.valueOf(type, name); } catch (IllegalArgumentException e) { return fallback; }
    }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
}
