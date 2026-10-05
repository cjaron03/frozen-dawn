package com.frozendawn.data;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** One issued recovery kit. Its lease survives saves but never another death. */
public final class EmergencyEvaState implements INBTSerializable<CompoundTag> {
    public static final int SERVICE_TICKS = 10 * 60 * 20;
    public static final UUID NO_ISSUE = new UUID(0, 0);
    private UUID issue = NO_ISSUE;
    private int remainingTicks;
    private int lastSyncedMask = -1;
    private EmergencyEvaExertion exertion = new EmergencyEvaExertion();

    public EmergencyEvaState() {}
    public EmergencyEvaState(UUID issue, int remainingTicks) {
        this.issue = issue;
        this.remainingTicks = Math.clamp(remainingTicks, 0, SERVICE_TICKS);
    }
    public EmergencyEvaState(UUID issue, int remainingTicks, int load) {
        this(issue, remainingTicks);
        exertion = new EmergencyEvaExertion(load, 0);
    }
    public EmergencyEvaState copy() {
        var copy = new EmergencyEvaState(issue, remainingTicks);
        copy.exertion = new EmergencyEvaExertion(exertion.load(), exertion.fractionalDebit());
        return copy;
    }
    public int exertionLoad() { return exertion.load(); }
    public float exertionIntensity() { return exertion.intensity(); }
    public void recoverUnworn() { exertion.advance(false); }
    public UUID issue() { return issue; }
    public int remainingTicks() { return remainingTicks; }
    public void tickWorn() { tickWorn(false); }
    public void tickWorn(boolean running) {
        if (remainingTicks <= 0) return;
        exertion.advance(running);
        remainingTicks = Math.max(0, remainingTicks - exertion.debit());
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
        tag.putInt("exertionLoad", exertion.load());
        tag.putInt("fractionalDebit", exertion.fractionalDebit());
        return tag;
    }
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        issue = tag.hasUUID("issue") ? tag.getUUID("issue") : NO_ISSUE;
        remainingTicks = Math.clamp(tag.getInt("remainingTicks"), 0, SERVICE_TICKS);
        exertion = new EmergencyEvaExertion(tag.getInt("exertionLoad"), tag.getInt("fractionalDebit"));
        lastSyncedMask = -1;
    }
}
