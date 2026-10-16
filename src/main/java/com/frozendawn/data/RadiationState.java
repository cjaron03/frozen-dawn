package com.frozendawn.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Per-player radiation dose. Not copied on death, so dose resets when the player dies. */
public final class RadiationState implements INBTSerializable<CompoundTag> {
    public double dose, felt, rate, contamination;
    public int treatmentSeconds;
    public boolean contaminationWarned;

    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putDouble("Dose", dose); tag.putDouble("Felt", felt); tag.putDouble("Contamination", contamination);
        tag.putInt("Treatment", treatmentSeconds); tag.putBoolean("Warned", contaminationWarned);
        return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        dose = finite(tag.getDouble("Dose")); felt = finite(tag.getDouble("Felt")); contamination = finite(tag.getDouble("Contamination"));
        treatmentSeconds = Math.max(0, tag.getInt("Treatment")); contaminationWarned = tag.getBoolean("Warned"); rate = 0;
    }
    private static double finite(double v) { return Double.isFinite(v) ? Math.max(0, v) : 0; }
}
