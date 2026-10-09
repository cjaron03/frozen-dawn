package com.frozendawn.data;

import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** ORSA's local position archive survives the loss of an individual emergency issue. */
public final class ContinuityRecoveryState implements INBTSerializable<CompoundTag> {
    public static final int MAX_SHELTER_ERROR = 16;
    public static final int SEARCH_RADIUS = 32;
    private GlobalPos shelter;
    private GlobalPos shelterEstimate;
    private GlobalPos telemetry;
    private boolean shelterSelected;

    @Nullable public GlobalPos shelter() { return shelter; }
    @Nullable public GlobalPos shelterEstimate() { return shelterEstimate; }
    @Nullable public GlobalPos telemetry() { return telemetry; }
    public boolean shelterSelected() { return shelterSelected; }
    public void selectShelter(boolean selected) { shelterSelected = selected; }

    public boolean recordShelter(GlobalPos position, UUID owner) {
        if (position.equals(shelter)) return false;
        shelter = position;
        // Derive once from the owner and recorded shelter, never the replacement issue.
        var random = new Random(owner.getMostSignificantBits() ^ owner.getLeastSignificantBits()
                ^ position.pos().asLong() ^ position.dimension().location().hashCode());
        int dx, dz;
        do {
            dx = random.nextInt(MAX_SHELTER_ERROR * 2 + 1) - MAX_SHELTER_ERROR;
            dz = random.nextInt(MAX_SHELTER_ERROR * 2 + 1) - MAX_SHELTER_ERROR;
        } while (dx * dx + dz * dz > MAX_SHELTER_ERROR * MAX_SHELTER_ERROR || dx == 0 && dz == 0);
        shelterEstimate = GlobalPos.of(position.dimension(), position.pos().offset(dx, 0, dz));
        return true;
    }

    /** Empty-handed emergency deaths preserve the original equipment destination. */
    public void recordLoss(GlobalPos position, boolean hasOrdinaryDrops) {
        if (hasOrdinaryDrops) { telemetry = position; shelterSelected = false; }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        putPosition(tag, "shelter", shelter);
        putPosition(tag, "estimate", shelterEstimate);
        putPosition(tag, "telemetry", telemetry);
        tag.putBoolean("shelterSelected", shelterSelected);
        return tag;
    }

    /** Send only the approximate shelter fix; the exact bed position stays server-side. */
    public CompoundTag clientRecord() {
        var tag = new CompoundTag();
        putPosition(tag, "estimate", shelterEstimate);
        putPosition(tag, "telemetry", telemetry);
        tag.putBoolean("shelterSelected", shelterSelected);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        shelter = readPosition(tag, "shelter");
        shelterEstimate = readPosition(tag, "estimate");
        telemetry = readPosition(tag, "telemetry");
        shelterSelected = tag.getBoolean("shelterSelected");
    }

    private static void putPosition(CompoundTag tag, String key, @Nullable GlobalPos position) {
        if (position == null) return;
        var entry = new CompoundTag();
        entry.putString("dimension", position.dimension().location().toString());
        entry.putLong("position", position.pos().asLong());
        tag.put(key, entry);
    }

    @Nullable
    private static GlobalPos readPosition(CompoundTag tag, String key) {
        if (!tag.contains(key, CompoundTag.TAG_COMPOUND)) return null;
        var entry = tag.getCompound(key);
        var dimension = ResourceLocation.tryParse(entry.getString("dimension"));
        return dimension == null || !entry.contains("position", CompoundTag.TAG_LONG) ? null
                : GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                        BlockPos.of(entry.getLong("position")));
    }
}
