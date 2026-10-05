package com.frozendawn.data;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** One issued recovery kit. Its lease survives saves but never another death. */
public final class EmergencyEvaState implements INBTSerializable<CompoundTag> {
    public static final int SERVICE_TICKS = 15 * 60 * 20;
    public static final int OXYGEN_TICKS = 10 * 60 * 20;
    public static final UUID NO_ISSUE = new UUID(0, 0);
    public static final int ACTIVE = 0, HANDOFF = 1, EXPIRED = 2;
    private UUID issue = NO_ISSUE;
    private int remainingTicks;
    private int oxygenTicks;
    private int retirement;
    private EmergencyEvaAirIntake intake = new EmergencyEvaAirIntake(0);
    private int lastSyncedMask = -1;
    private EmergencyEvaExertion exertion = new EmergencyEvaExertion();
    private EmergencyEvaThermal thermal = new EmergencyEvaThermal(0, 0);

    public EmergencyEvaState() {}
    public EmergencyEvaState(UUID issue, int remainingTicks) {
        this.issue = issue;
        this.remainingTicks = Math.clamp(remainingTicks, 0, SERVICE_TICKS);
        oxygenTicks = Math.min(this.remainingTicks, OXYGEN_TICKS);
        thermal = new EmergencyEvaThermal(SERVICE_TICKS - this.remainingTicks, 0);
    }
    public EmergencyEvaState(UUID issue, int remainingTicks, int load) {
        this(issue, remainingTicks);
        exertion = new EmergencyEvaExertion(load, 0);
    }
    public EmergencyEvaState(UUID issue, int remainingTicks, int load, int wornTicks, int heat) {
        this(issue, remainingTicks, load);
        thermal = new EmergencyEvaThermal(wornTicks, heat);
    }
    public EmergencyEvaState(UUID issue, int remainingTicks, int oxygenTicks, int load,
                             int wornTicks, int heat, boolean ambient, int retirement) {
        this(issue, remainingTicks, load, wornTicks, heat);
        this.oxygenTicks = Math.clamp(oxygenTicks, 0, OXYGEN_TICKS);
        this.retirement = Math.clamp(retirement, ACTIVE, EXPIRED);
        intake = new EmergencyEvaAirIntake(ambient ? EmergencyEvaAirIntake.STABLE_TICKS : 0);
    }
    public EmergencyEvaState copy() {
        var copy = new EmergencyEvaState(issue, remainingTicks);
        copy.exertion = new EmergencyEvaExertion(exertion.load(), exertion.fractionalDebit());
        copy.thermal = new EmergencyEvaThermal(thermal.wornTicks(), thermal.heat());
        copy.oxygenTicks = oxygenTicks;
        copy.retirement = retirement;
        copy.intake = new EmergencyEvaAirIntake(intake.stableTicks());
        return copy;
    }
    public int exertionLoad() { return exertion.load(); }
    public float exertionIntensity() { return exertion.intensity(); }
    public int wornTicks() { return thermal.wornTicks(); }
    public int thermalLoad() { return thermal.heat(); }
    public float thermalIntensity() { return thermal.intensity(); }
    public boolean coolingDegraded() { return thermal.coolingDegraded(); }
    public boolean highThermalLoad() { return thermal.highHeat(); }
    public int oxygenTicks() { return oxygenTicks; }
    public boolean ambientIntake() { return intake.isOpen(); }
    public int retirement() { return retirement; }
    public boolean retired() { return retirement != ACTIVE; }
    public void retire(int reason) {
        retirement = reason;
        remainingTicks = 0;
        oxygenTicks = 0;
        intake.tick(false);
    }
    public void recoverUnworn() { exertion.advance(false); thermal.recover(); intake.tick(false); }
    public UUID issue() { return issue; }
    public int remainingTicks() { return remainingTicks; }
    public void tickWorn() { tickWorn(false); }
    public void tickWorn(boolean running) {
        tickWorn(running, true);
    }
    public void tickWorn(boolean running, boolean sealed) {
        tickWorn(running, sealed, false);
    }
    public void tickWorn(boolean running, boolean sealed, boolean breathable) {
        if (remainingTicks <= 0 || retired()) return;
        intake.tick(breathable && sealed);
        exertion.advance(running);
        thermal.tickWorn(running, sealed);
        remainingTicks--;
        if (!ambientIntake()) oxygenTicks = Math.max(0, oxygenTicks - exertion.debit());
    }
    public boolean equipmentChanged(int mask) {
        if (mask == lastSyncedMask) return false;
        lastSyncedMask = mask;
        return true;
    }
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putUUID("issue", issue);
        tag.putInt("remainingTicks", remainingTicks);
        tag.putInt("oxygenTicks", oxygenTicks);
        tag.putInt("retirement", retirement);
        tag.putInt("ambientTicks", intake.stableTicks());
        tag.putInt("exertionLoad", exertion.load());
        tag.putInt("fractionalDebit", exertion.fractionalDebit());
        tag.putInt("wornTicks", thermal.wornTicks());
        tag.putInt("thermalLoad", thermal.heat());
        return tag;
    }
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        issue = tag.hasUUID("issue") ? tag.getUUID("issue") : NO_ISSUE;
        remainingTicks = Math.clamp(tag.getInt("remainingTicks"), 0, SERVICE_TICKS);
        oxygenTicks = Math.clamp(tag.contains("oxygenTicks") ? tag.getInt("oxygenTicks") : remainingTicks, 0, OXYGEN_TICKS);
        retirement = Math.clamp(tag.getInt("retirement"), ACTIVE, EXPIRED);
        intake = new EmergencyEvaAirIntake(tag.getInt("ambientTicks"));
        exertion = new EmergencyEvaExertion(tag.getInt("exertionLoad"), tag.getInt("fractionalDebit"));
        // Pre-clock saves used a ten-minute shared budget. Keep that historical
        // age and balance; the longer limit applies only to newly issued kits.
        thermal = new EmergencyEvaThermal(tag.contains("wornTicks") ? tag.getInt("wornTicks")
                : Math.max(0, OXYGEN_TICKS - remainingTicks), tag.getInt("thermalLoad"));
        lastSyncedMask = -1;
    }
}
