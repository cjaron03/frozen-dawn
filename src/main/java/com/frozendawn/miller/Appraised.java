package com.frozendawn.miller;

import com.frozendawn.miller.Feelings.Feeling;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** One thing she saw, who did it, and what it raised. This is how she can say why. */
public record Appraised(long time, UUID who, String deed, Map<Feeling, Double> raised) {
    public Appraised {
        raised = raised.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(raised));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("time", time);
        tag.putUUID("who", who);
        tag.putString("deed", deed);
        var feelings = new CompoundTag();
        raised.forEach((feeling, intensity) -> feelings.putDouble(feeling.name(), intensity));
        tag.put("raised", feelings);
        return tag;
    }

    /** Null if the entry is too damaged to name anyone. */
    static Appraised load(CompoundTag tag) {
        if (!tag.hasUUID("who")) return null;
        var raised = new EnumMap<Feeling, Double>(Feeling.class);
        var feelings = tag.getCompound("raised");
        for (Feeling feeling : Feeling.values()) if (feelings.contains(feeling.name())) raised.put(feeling, feelings.getDouble(feeling.name()));
        return new Appraised(tag.getLong("time"), tag.getUUID("who"), tag.getString("deed"), raised);
    }
}
