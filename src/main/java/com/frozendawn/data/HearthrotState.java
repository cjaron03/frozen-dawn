package com.frozendawn.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Persistent per-player authority for Hearthrot and exterior rig colonization. */
public final class HearthrotState implements INBTSerializable<CompoundTag> {
    private int stage;
    private double progressTicks;
    private int transitionMask;
    private int stationaryTicks;
    private boolean stillnessEpisodeRolled;
    private int coughTicks;
    private int wheezeTicks;
    private int contaminationWarningTicks = -1;
    private boolean contaminationWarned;
    private double colonizationRemainder;
    private double baselineO2Remainder;
    private long lastPosition;
    private boolean hasLastPosition;

    public int stage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = Math.max(0, Math.min(6, stage));
    }

    public double progressTicks() {
        return progressTicks;
    }

    public void setProgressTicks(double progressTicks) {
        this.progressTicks = Math.max(0.0D, progressTicks);
    }

    public int transitionMask() {
        return transitionMask;
    }

    public void setTransitionMask(int transitionMask) {
        this.transitionMask = Math.max(0, transitionMask);
    }

    public int stationaryTicks() {
        return stationaryTicks;
    }

    public void setStationaryTicks(int stationaryTicks) {
        this.stationaryTicks = Math.max(0, stationaryTicks);
    }

    public boolean stillnessEpisodeRolled() {
        return stillnessEpisodeRolled;
    }

    public void setStillnessEpisodeRolled(boolean stillnessEpisodeRolled) {
        this.stillnessEpisodeRolled = stillnessEpisodeRolled;
    }

    public int coughTicks() {
        return coughTicks;
    }

    public void setCoughTicks(int coughTicks) {
        this.coughTicks = Math.max(0, coughTicks);
    }

    public int wheezeTicks() {
        return wheezeTicks;
    }

    public void setWheezeTicks(int wheezeTicks) {
        this.wheezeTicks = Math.max(0, wheezeTicks);
    }

    public int contaminationWarningTicks() {
        return contaminationWarningTicks;
    }

    public void setContaminationWarningTicks(int contaminationWarningTicks) {
        this.contaminationWarningTicks = Math.max(-1, contaminationWarningTicks);
    }

    public boolean contaminationWarned() {
        return contaminationWarned;
    }

    public void setContaminationWarned(boolean contaminationWarned) {
        this.contaminationWarned = contaminationWarned;
    }

    public double colonizationRemainder() {
        return colonizationRemainder;
    }

    public void setColonizationRemainder(double colonizationRemainder) {
        this.colonizationRemainder = colonizationRemainder;
    }

    public double baselineO2Remainder() {
        return baselineO2Remainder;
    }

    public void setBaselineO2Remainder(double baselineO2Remainder) {
        this.baselineO2Remainder = Math.max(0.0D, baselineO2Remainder);
    }

    public long lastPosition() {
        return lastPosition;
    }

    public boolean hasLastPosition() {
        return hasLastPosition;
    }

    public void rememberPosition(long position) {
        lastPosition = position;
        hasLastPosition = true;
    }

    public void clearPosition() {
        hasLastPosition = false;
    }

    public void copyAfterDeath(HearthrotState original) {
        setStage(original.stage > 0 ? Math.max(1, original.stage - 1) : 0);
        progressTicks = 0.0D;
        transitionMask = original.transitionMask;
        stationaryTicks = 0;
        stillnessEpisodeRolled = false;
        coughTicks = 0;
        wheezeTicks = 0;
        contaminationWarningTicks = -1;
        contaminationWarned = original.contaminationWarned;
        colonizationRemainder = original.colonizationRemainder;
        baselineO2Remainder = 0.0D;
        hasLastPosition = false;
    }

    public void clearForDebug() {
        stage = 0;
        progressTicks = 0.0D;
        transitionMask = 0;
        stationaryTicks = 0;
        stillnessEpisodeRolled = false;
        coughTicks = 0;
        wheezeTicks = 0;
        contaminationWarningTicks = -1;
        contaminationWarned = false;
        colonizationRemainder = 0.0D;
        baselineO2Remainder = 0.0D;
        hasLastPosition = false;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Stage", stage);
        tag.putDouble("ProgressTicks", progressTicks);
        tag.putInt("TransitionMask", transitionMask);
        tag.putInt("StationaryTicks", stationaryTicks);
        tag.putBoolean("StillnessEpisodeRolled", stillnessEpisodeRolled);
        tag.putInt("CoughTicks", coughTicks);
        tag.putInt("WheezeTicks", wheezeTicks);
        tag.putInt("ContaminationWarningTicks", contaminationWarningTicks);
        tag.putBoolean("ContaminationWarned", contaminationWarned);
        tag.putDouble("ColonizationRemainder", colonizationRemainder);
        tag.putDouble("BaselineO2Remainder", baselineO2Remainder);
        tag.putLong("LastPosition", lastPosition);
        tag.putBoolean("HasLastPosition", hasLastPosition);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        setStage(tag.getInt("Stage"));
        setProgressTicks(tag.getDouble("ProgressTicks"));
        setTransitionMask(tag.getInt("TransitionMask"));
        setStationaryTicks(tag.getInt("StationaryTicks"));
        stillnessEpisodeRolled = tag.getBoolean("StillnessEpisodeRolled");
        setCoughTicks(tag.getInt("CoughTicks"));
        setWheezeTicks(tag.getInt("WheezeTicks"));
        contaminationWarningTicks = tag.contains("ContaminationWarningTicks")
                ? Math.max(-1, tag.getInt("ContaminationWarningTicks")) : -1;
        contaminationWarned = tag.getBoolean("ContaminationWarned");
        colonizationRemainder = tag.getDouble("ColonizationRemainder");
        baselineO2Remainder = Math.max(0.0D, tag.getDouble("BaselineO2Remainder"));
        lastPosition = tag.getLong("LastPosition");
        hasLastPosition = tag.getBoolean("HasLastPosition");
    }
}
