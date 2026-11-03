package com.frozendawn.maeve;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** An immutable report of a locally witnessed action, never a live entity reference. */
record ObservedEvidence(UUID observer, UUID encounter, String dimension, BlockPos position,
                        long time, String action, boolean supporting, double confidenceWeight) {
    ObservedEvidence(UUID observer, UUID encounter, String dimension, BlockPos position,
                     long time, String action, boolean supporting) {
        this(observer, encounter, dimension, position, time, action, supporting, Double.NaN);
    }
    ObservedEvidence {
        position = position.immutable();
    }
    ObservedEvidence withWeight(double weight) {
        return new ObservedEvidence(observer, encounter, dimension, position, time, action, supporting, weight);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("observer", observer);
        tag.putUUID("encounter", encounter);
        tag.putString("dimension", dimension);
        tag.putLong("position", position.asLong());
        tag.putLong("time", time);
        tag.putString("action", action);
        tag.putBoolean("supporting", supporting);
        if (Double.isFinite(confidenceWeight)) tag.putDouble("confidenceWeight", confidenceWeight);
        return tag;
    }

    static ObservedEvidence load(CompoundTag tag) {
        String action = tag.getString("action");
        String dimension = tag.getString("dimension");
        if (!tag.hasUUID("observer") || !tag.hasUUID("encounter")
                || !tag.contains("position") || !tag.contains("time") || tag.getLong("time") < 0
                || action.isBlank() || action.length() > 256
                || ResourceLocation.tryParse(dimension) == null) return null;
        double weight = tag.contains("confidenceWeight") ? tag.getDouble("confidenceWeight") : Double.NaN;
        if (!Double.isFinite(weight) || Math.abs(weight) > 1) weight = Double.NaN;
        return new ObservedEvidence(tag.getUUID("observer"), tag.getUUID("encounter"), dimension,
                BlockPos.of(tag.getLong("position")), tag.getLong("time"), action, tag.getBoolean("supporting"), weight);
    }

    MaeveDirector.EvidenceSnapshot snapshot() {
        return new MaeveDirector.EvidenceSnapshot(observer, encounter, dimension, position, time, action, supporting, confidenceWeight);
    }
}
