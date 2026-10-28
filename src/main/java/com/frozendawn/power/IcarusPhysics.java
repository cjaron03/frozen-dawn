package com.frozendawn.power;

import net.minecraft.nbt.CompoundTag;

/** ORSA AMR-1 Icarus, stepped once per second. A Halsey Cascade burns ordinary matter against injected antimatter inside a
 * stabilizer-held shield. Antimatter is counted in micro-capsules and spent per megajoule produced, never per second. The
 * startup is a staged sequence that holds, never fails, on a missing input; a charged core whose shield reaches zero
 * detonates, so the controller acts on Readout.detonate. */
public final class IcarusPhysics {
    public static final long CAPSULE = 1_000_000L;
    public static final int MAX_CAPSULES = 64, MIN_START_CAPSULES = 2;
    public static final double MIN_OUTPUT_MW = 1.0, MAX_OUTPUT_MW = 2.5, STARTUP_DRAW_KW = 1000;
    /** The reserve bank holds the shield about ten minutes at the Minimum draw; startup puts the first RESERVE_BOOT_MJ in from the grid. */
    public static final double RESERVE_MJ = 240, RESERVE_BOOT_MJ = 20;
    public static final double AMBIENT_C = 20, RED_LINE_C = 6500, CRITICAL_C = 8000;
    /** One block of ordinary matter carries 5 MJ of gross output, so 1 MW eats 12 blocks a minute. */
    public static final double MJ_PER_MATTER_BLOCK = 5;
    /** The reserve refills only from the reactor's own surplus, at this rate, never from the grid. */
    public static final double RESERVE_RECHARGE_KW = 200;
    /** Shield draw = setting x (SHIELD_BASE_MW + SHIELD_MW_PER_GROSS x gross): 0.4 MW at Minimum, 1.3 MW at full. */
    public static final double SHIELD_BASE_MW = 0.244, SHIELD_MW_PER_GROSS = 0.422;
    /** Shield percent per second: a powered shield regenerates SHIELD_REGEN x setting; containment always decays SHIELD_DECAY,
     * plus a heat term that steepens sharply past the red line. */
    public static final double SHIELD_REGEN = 1.5, SHIELD_DECAY = 0.2;
    /** Equilibrium core temperature: AMBIENT + CORE_C_FIRST_MW x min(1, gross) + CORE_C_PER_MW x gross x reactivity
     * + OVERFEED_C_PER_BLOCK x surplus blocks a minute. Reactivity grows by DEPLETION_REACTIVITY as the load is spent. */
    public static final double CORE_C_FIRST_MW = 1500, CORE_C_PER_MW = 1400, OVERFEED_C_PER_BLOCK = 40, DEPLETION_REACTIVITY = 0.6;
    public static final double CORE_TIME_CONSTANT_S = 30;
    /** Gross output slews this fast while ramping up, so RAMP reaches MIN_OUTPUT_MW in its 20 s. */
    public static final double RAMP_MW_PER_SECOND = 0.05;
    public static final int POWER_CHECK_S = 10, RESERVE_CHARGE_S = 20, SPINUP_S = 30, COMMISSIONING_S = 60,
            INJECTION_S = 20, PRIMING_S = 15, IGNITION_S = 5, RAMP_S = 20, DISCHARGE_S = 20;

    /** Megajoules a capsule yields: a Minimum burn gets the most out of it. */
    public static double mjPerCapsule(double grossMw) {
        return 7200 - 1800 * (Math.clamp(Double.isFinite(grossMw) ? grossMw : 0, MIN_OUTPUT_MW, MAX_OUTPUT_MW) - MIN_OUTPUT_MW);
    }
    static double shieldDrainPerSecond(double coreC) {
        double t = Math.max(0, coreC);
        return t <= RED_LINE_C ? SHIELD_DECAY + 0.3 * t / RED_LINE_C : SHIELD_DECAY + 0.3 + 2.4 * (t - RED_LINE_C) / 1000;
    }
    /** Running out of antimatter is a safe end and only ever cautions. Reaching the red line warns. Only the shield, or the
     * reserve that keeps it powered, can go critical. */
    static Alarm alarm(Status status, double coreC, double shieldPct, double shieldEta, double redLineEta, double reserveEta, double antimatterEta) {
        double danger = Math.min(shieldEta, reserveEta);
        if (shieldPct < 15 || danger < 60) return Alarm.CRITICAL;
        if (danger < 180 || redLineEta < 180 || coreC >= RED_LINE_C) return Alarm.WARNING;
        if (Math.min(danger, Math.min(redLineEta, antimatterEta)) < 600 || status == Status.MATTER_STARVED || status == Status.RESERVE_IN_USE)
            return Alarm.CAUTION;
        return Alarm.NONE;
    }
    static Cascade cascade(double coreC, double shieldPct, boolean shieldFalling) {
        if (coreC >= CRITICAL_C || shieldPct < 15) return Cascade.RUNAWAY;
        if (coreC >= RED_LINE_C || shieldFalling) return Cascade.STRAINED;
        return Cascade.STABLE;
    }

    /** Synced by ordinal: append new values only. */
    public enum State { COLD, POWER_CHECK, RESERVE_CHARGE, STABILIZER_SPINUP, COMMISSIONING, INJECTION, PRIMING, IGNITION, RAMP,
        RUNNING, CLIPPED, COLE_RAMP_DOWN, COLE_COOL, COLE_RECOVER, COLE_DISCHARGE, DETONATED }
    public enum Status { OK, HOLD_STRUCTURE, HOLD_POWER, HOLD_CAPSULES, HOLD_MATTER, HOLD_CAPSULE_SPACE, MATTER_STARVED, RESERVE_IN_USE, SAFE_TO_DISMANTLE }
    public enum Alarm { NONE, CAUTION, WARNING, CRITICAL }
    public enum Cascade { STABLE, STRAINED, RUNAWAY }
    /** outputMw is the gross target, shield the stabilizer drive (0.2 to 1) and feedPerMinute the matter feed in blocks. */
    public record Settings(double outputMw, double shield, int feedPerMinute) {
        public static final Settings MINIMUM = new Settings(1.0, 0.6, 12);
        public Settings {
            outputMw = Double.isFinite(outputMw) ? Math.clamp(outputMw, MIN_OUTPUT_MW, MAX_OUTPUT_MW) : MIN_OUTPUT_MW;
            shield = Double.isFinite(shield) ? Math.clamp(shield, 0.2, 1) : 0.6;
            feedPerMinute = Math.clamp(feedPerMinute, 0, 60);
        }
    }
    /** powered: last second's request was paid. dockCharge: sealed charge the dock can inject now (whole capsules, within the cap).
     * dockEmpties: empty casings in the dock. matter: plain blocks in the matter port. */
    public record Inputs(boolean structureValid, boolean powered, long dockCharge, int dockEmpties, int matter) {}
    /** injected, recovered and matterUsed are this second's movements, for the controller to apply to the dock and matter port.
     * ETAs are seconds, +Infinity when nothing is running out. */
    public record Readout(State state, Status status, int stepSecondsLeft, double grossMw, double shieldDrawMw, double netMw,
        double requestKw, double coreC, double shieldPct, double reserveMJ, long coreCharge, long injected, long recovered,
        int matterUsed, double shieldEtaS, double redLineEtaS, double reserveEtaS, double antimatterEtaS,
        Alarm alarm, Cascade cascade, boolean detonate) {}

    private State state = State.COLD;
    private Status status = Status.OK;
    private Settings settings = Settings.MINIMUM;
    private int left;
    private boolean commissioned;
    private long coreCharge, loadedCharge, used;
    private double grossMw, coreC = AMBIENT_C, shieldPct, reserveMJ, matterProgress, useRemainder;

    public State state() { return state; }
    public long coreCharge() { return coreCharge; }
    public boolean commissioned() { return commissioned; }
    public Settings settings() { return settings; }
    public void settings(Settings s) { if (s != null) settings = s; }
    void setCoreChargeForTest(long c) { coreCharge = Math.clamp(c, 0, MAX_CAPSULES * CAPSULE); }
    void setCommissionedForTest(boolean b) { commissioned = b; }
    /** Clears the charge after the controller has queued the blast. */
    public void detonated() { coreCharge = 0; state = State.DETONATED; grossMw = 0; }

    public boolean start() {
        if (state != State.COLD) return false;
        enter(State.POWER_CHECK); status = Status.OK; return true;
    }
    /** Before injection, aborts to COLD. */
    public boolean cole() {
        if (state == State.COLD || state == State.DETONATED) return false;
        if (coreCharge == 0) { cold(Status.OK); return true; }
        return false;
    }

    public Readout step(Inputs in) {
        long injected = 0;
        int matterUsed = 0;
        double requestKw = 0;
        boolean startup = false, burning = false, starved = false;
        if (state != State.COLD) status = Status.OK;
        switch (state) {
            case POWER_CHECK, RESERVE_CHARGE, STABILIZER_SPINUP, COMMISSIONING, INJECTION, PRIMING, IGNITION -> {
                startup = true;
                requestKw = STARTUP_DRAW_KW;
                Status hold = startupHold(in);
                if (hold != null) status = hold;
                else {
                    if (state == State.RESERVE_CHARGE) reserveMJ = Math.min(RESERVE_MJ, reserveMJ + STARTUP_DRAW_KW / 1000);
                    if (state == State.STABILIZER_SPINUP) shieldPct = Math.min(100, shieldPct + 100.0 / SPINUP_S);
                    if (state == State.PRIMING) matterUsed = feed(in);
                    left--;
                    if (state == State.INJECTION && left == 0) {
                        injected = Math.min(in.dockCharge(), MAX_CAPSULES * CAPSULE - coreCharge);
                        coreCharge += injected; loadedCharge = coreCharge;
                    }
                }
            }
            case RAMP, RUNNING -> {
                burning = true;
                starved = settings.feedPerMinute() > 0 && in.matter() <= 0;
                matterUsed = feed(in);
                double target = Math.min(settings.outputMw(), (starved ? 0 : settings.feedPerMinute()) * MJ_PER_MATTER_BLOCK / 60);
                grossMw += Math.clamp(target - grossMw, -RAMP_MW_PER_SECOND, RAMP_MW_PER_SECOND);
                if (state == State.RAMP) left--;
            }
            case COLE_DISCHARGE -> left--;
            default -> {}
        }
        double perSecond = burning ? grossMw / mjPerCapsule(grossMw) * CAPSULE : 0;
        if (burning) {
            useRemainder += perSecond;
            long spent = Math.min(coreCharge, (long) useRemainder);
            useRemainder -= spent; coreCharge -= spent; used += spent;
        }
        double feedSurplus = burning ? Math.max(0, (starved ? 0 : settings.feedPerMinute()) - grossMw * 60 / MJ_PER_MATTER_BLOCK) : 0;
        double reactivity = loadedCharge > 0 ? 1 + DEPLETION_REACTIVITY * (1 - (double) coreCharge / loadedCharge) : 1;
        double equilibriumC = AMBIENT_C + CORE_C_FIRST_MW * Math.min(1, grossMw) + CORE_C_PER_MW * grossMw * reactivity + OVERFEED_C_PER_BLOCK * feedSurplus;
        coreC += (equilibriumC - coreC) / CORE_TIME_CONSTANT_S;

        boolean charged = coreCharge > 0;
        double shieldDraw = charged ? settings.shield() * (SHIELD_BASE_MW + SHIELD_MW_PER_GROSS * grossMw) : 0;
        double deficit = Math.max(0, shieldDraw - grossMw), recharge = 0;
        boolean supplied = deficit == 0, reserveInUse = false;
        if (!supplied) {
            if (!startup) requestKw += deficit * 1000;
            if (in.powered()) supplied = true;
            else if (reserveMJ >= deficit) { reserveMJ -= deficit; supplied = true; reserveInUse = true; }
            else reserveMJ = 0;
        } else if (burning) {
            recharge = Math.min(RESERVE_RECHARGE_KW / 1000, Math.min(grossMw - shieldDraw, RESERVE_MJ - reserveMJ));
            reserveMJ += recharge;
        }
        double shieldRate = charged ? (supplied ? SHIELD_REGEN * settings.shield() : 0) - shieldDrainPerSecond(coreC) : 0;
        if (charged) shieldPct = Math.clamp(shieldPct + shieldRate, 0, 100);
        boolean detonate = charged && shieldPct <= 0;
        if (detonate) { state = State.DETONATED; grossMw = 0; left = 0; }
        else if (state == State.DETONATED) detonate = charged;
        double netMw = burning || !startup ? grossMw - shieldDraw - recharge : 0;

        if (reserveInUse) status = Status.RESERVE_IN_USE;
        else if (starved && status == Status.OK) status = Status.MATTER_STARVED;
        boolean falling = charged && shieldRate < 0 && shieldPct < 100;
        double shieldEta = falling ? shieldPct / -shieldRate : Double.POSITIVE_INFINITY;
        double redLineEta = !charged ? Double.POSITIVE_INFINITY : coreC >= RED_LINE_C ? 0
                : equilibriumC > RED_LINE_C ? CORE_TIME_CONSTANT_S * Math.log((equilibriumC - coreC) / (equilibriumC - RED_LINE_C)) : Double.POSITIVE_INFINITY;
        double reserveEta = reserveInUse ? reserveMJ / deficit : Double.POSITIVE_INFINITY;
        double antimatterEta = perSecond > 0 ? coreCharge / perSecond : Double.POSITIVE_INFINITY;
        Alarm alarm = charged ? alarm(status, coreC, shieldPct, shieldEta, redLineEta, reserveEta, antimatterEta) : Alarm.NONE;
        Cascade cascade = charged ? cascade(coreC, shieldPct, falling) : Cascade.STABLE;
        Readout r = new Readout(state, status, left, grossMw, shieldDraw, netMw, requestKw, coreC, shieldPct, reserveMJ, coreCharge,
                injected, 0, matterUsed, shieldEta, redLineEta, reserveEta, antimatterEta, alarm, cascade, detonate);
        if (burning && coreCharge == 0) { grossMw = 0; enter(State.COLE_DISCHARGE); }
        else if (left == 0 && status != Status.HOLD_STRUCTURE && status != Status.HOLD_POWER && status != Status.HOLD_CAPSULES
                && status != Status.HOLD_MATTER) advance();
        return r;
    }

    private Status startupHold(Inputs in) {
        if (!in.structureValid()) return Status.HOLD_STRUCTURE;
        if (!in.powered()) return Status.HOLD_POWER;
        if (state == State.INJECTION && coreCharge + Math.min(in.dockCharge(), MAX_CAPSULES * CAPSULE - coreCharge) < MIN_START_CAPSULES * CAPSULE)
            return Status.HOLD_CAPSULES;
        if (state == State.PRIMING && in.matter() <= 0) return Status.HOLD_MATTER;
        return null;
    }

    /** Blocks fed this second at the set rate, limited to what the matter port holds. */
    private int feed(Inputs in) {
        matterProgress += settings.feedPerMinute() / 60.0;
        int want = (int) matterProgress;
        int used = Math.min(want, Math.max(0, in.matter()));
        matterProgress -= want;
        return used;
    }

    private void advance() {
        switch (state) {
            case POWER_CHECK -> enter(State.RESERVE_CHARGE);
            case RESERVE_CHARGE -> enter(State.STABILIZER_SPINUP);
            case STABILIZER_SPINUP -> enter(commissioned ? State.INJECTION : State.COMMISSIONING);
            case COMMISSIONING -> { commissioned = true; enter(State.INJECTION); }
            case INJECTION -> enter(State.PRIMING);
            case PRIMING -> enter(State.IGNITION);
            case IGNITION -> enter(State.RAMP);
            case RAMP -> enter(State.RUNNING);
            case COLE_DISCHARGE -> cold(Status.SAFE_TO_DISMANTLE);
            default -> {}
        }
    }

    private void enter(State next) {
        state = next;
        left = switch (next) {
            case POWER_CHECK -> POWER_CHECK_S;
            case RESERVE_CHARGE -> RESERVE_CHARGE_S;
            case STABILIZER_SPINUP -> SPINUP_S;
            case COMMISSIONING -> COMMISSIONING_S;
            case INJECTION -> INJECTION_S;
            case PRIMING -> PRIMING_S;
            case IGNITION -> IGNITION_S;
            case RAMP -> RAMP_S;
            case COLE_DISCHARGE -> DISCHARGE_S;
            default -> 0;
        };
    }

    private void cold(Status why) {
        state = State.COLD; status = why; left = 0; grossMw = 0; shieldPct = 0; matterProgress = 0; useRemainder = 0; loadedCharge = 0;
    }

    public void save(CompoundTag t) {
        t.putString("State", state.name()); t.putString("Status", status.name()); t.putInt("Left", left);
        t.putBoolean("Commissioned", commissioned); t.putLong("CoreCharge", coreCharge); t.putLong("LoadedCharge", loadedCharge); t.putLong("Used", used);
        t.putDouble("UseRemainder", useRemainder);
        t.putDouble("GrossMw", grossMw); t.putDouble("CoreC", coreC); t.putDouble("ShieldPct", shieldPct);
        t.putDouble("ReserveMJ", reserveMJ); t.putDouble("MatterProgress", matterProgress);
        t.putDouble("OutputMw", settings.outputMw()); t.putDouble("Shield", settings.shield()); t.putInt("Feed", settings.feedPerMinute());
    }
    public void load(CompoundTag t) {
        state = parse(State.class, t.getString("State"), State.COLD);
        status = parse(Status.class, t.getString("Status"), Status.OK);
        left = Math.max(0, t.getInt("Left"));
        commissioned = t.getBoolean("Commissioned");
        coreCharge = Math.clamp(t.getLong("CoreCharge"), 0, MAX_CAPSULES * CAPSULE);
        loadedCharge = Math.clamp(t.getLong("LoadedCharge"), coreCharge, MAX_CAPSULES * CAPSULE);
        used = Math.max(0, t.getLong("Used"));
        useRemainder = Math.clamp(finite(t.getDouble("UseRemainder"), 0), 0, 1);
        grossMw = Math.clamp(finite(t.getDouble("GrossMw"), 0), 0, MAX_OUTPUT_MW);
        coreC = t.contains("CoreC") ? finite(t.getDouble("CoreC"), AMBIENT_C) : AMBIENT_C;
        shieldPct = Math.clamp(finite(t.getDouble("ShieldPct"), 0), 0, 100);
        reserveMJ = Math.clamp(finite(t.getDouble("ReserveMJ"), 0), 0, RESERVE_MJ);
        matterProgress = Math.clamp(finite(t.getDouble("MatterProgress"), 0), 0, 1);
        settings = t.contains("OutputMw") ? new Settings(t.getDouble("OutputMw"), t.getDouble("Shield"), t.getInt("Feed")) : Settings.MINIMUM;
    }
    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try { return Enum.valueOf(type, name); } catch (IllegalArgumentException e) { return fallback; }
    }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
}
