package com.frozendawn.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Persistent, server-authoritative EVA suit integrity state for one player. */
public final class SuitIntegrity implements INBTSerializable<CompoundTag> {

    private int punctures;
    private int o2Ticks;
    private int graceTicks;
    private int patchTicks = -1;
    private int temporarySeals;
    private int temporarySealTicks;
    private double ventAccumulator;
    private boolean warned25;
    private boolean warned10;

    public int punctures() {
        return punctures;
    }

    public void setPunctures(int punctures) {
        this.punctures = Math.max(0, punctures);
    }

    public int o2Ticks() {
        return o2Ticks;
    }

    public void setO2Ticks(int o2Ticks) {
        this.o2Ticks = Math.max(0, o2Ticks);
    }

    public int graceTicks() {
        return graceTicks;
    }

    public void setGraceTicks(int graceTicks) {
        this.graceTicks = Math.max(0, graceTicks);
    }

    public int patchTicks() {
        return patchTicks;
    }

    public void setPatchTicks(int patchTicks) {
        this.patchTicks = Math.max(-1, patchTicks);
    }

    public int temporarySeals() {
        return temporarySeals;
    }

    public void setTemporarySeals(int temporarySeals) {
        this.temporarySeals = Math.max(0, temporarySeals);
    }

    public int temporarySealTicks() {
        return temporarySealTicks;
    }

    public void setTemporarySealTicks(int temporarySealTicks) {
        this.temporarySealTicks = Math.max(0, temporarySealTicks);
    }

    public boolean warned25() {
        return warned25;
    }

    public double ventAccumulator() {
        return ventAccumulator;
    }

    public void setVentAccumulator(double ventAccumulator) {
        this.ventAccumulator = Math.max(0.0D, ventAccumulator);
    }

    public void setWarned25(boolean warned25) {
        this.warned25 = warned25;
    }

    public boolean warned10() {
        return warned10;
    }

    public void setWarned10(boolean warned10) {
        this.warned10 = warned10;
    }

    public void clearWarnings() {
        warned25 = false;
        warned10 = false;
    }

    public void resetAfterDeath() {
        punctures = 0;
        o2Ticks = 0;
        graceTicks = 0;
        patchTicks = -1;
        temporarySeals = 0;
        temporarySealTicks = 0;
        ventAccumulator = 0.0D;
        clearWarnings();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Punctures", punctures);
        tag.putInt("O2Ticks", o2Ticks);
        tag.putInt("GraceTicks", graceTicks);
        tag.putInt("PatchTicks", patchTicks);
        tag.putInt("TemporarySeals", temporarySeals);
        tag.putInt("TemporarySealTicks", temporarySealTicks);
        tag.putDouble("VentAccumulator", ventAccumulator);
        tag.putBoolean("Warned25", warned25);
        tag.putBoolean("Warned10", warned10);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        punctures = Math.max(0, tag.getInt("Punctures"));
        o2Ticks = Math.max(0, tag.getInt("O2Ticks"));
        graceTicks = Math.max(0, tag.getInt("GraceTicks"));
        patchTicks = tag.contains("PatchTicks") ? Math.max(-1, tag.getInt("PatchTicks")) : -1;
        temporarySeals = Math.max(0, tag.getInt("TemporarySeals"));
        temporarySealTicks = Math.max(0, tag.getInt("TemporarySealTicks"));
        ventAccumulator = Math.max(0.0D, tag.getDouble("VentAccumulator"));
        warned25 = tag.getBoolean("Warned25");
        warned10 = tag.getBoolean("Warned10");
    }
}
