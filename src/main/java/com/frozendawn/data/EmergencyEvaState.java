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

    public EmergencyEvaState() {}
    public EmergencyEvaState(UUID issue, int remainingTicks) {
        this.issue = issue;
        this.remainingTicks = Math.clamp(remainingTicks, 0, SERVICE_TICKS);
    }
    public UUID issue() { return issue; }
    public int remainingTicks() { return remainingTicks; }
    public void tickWorn() { remainingTicks = Math.max(0, remainingTicks - 1); }
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
        return tag;
    }
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        issue = tag.hasUUID("issue") ? tag.getUUID("issue") : NO_ISSUE;
        remainingTicks = Math.clamp(tag.getInt("remainingTicks"), 0, SERVICE_TICKS);
        lastSyncedMask = -1;
    }
}
