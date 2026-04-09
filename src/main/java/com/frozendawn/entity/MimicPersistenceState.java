package com.frozendawn.entity;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

final class MimicPersistenceState {

    private MimicPersistenceState() {
    }

    static void addAdditionalSaveData(MimicEntity mimic, CompoundTag tag) {
        tag.putInt("MimicPhase", mimic.getMimicPhase());
        tag.putInt("DespawnTimer", mimic.getDespawnTimerInternal());
        tag.putBoolean("Engaged", mimic.isEngagedInternal());
        mimic.getMimicTargetUUID().ifPresent(uuid -> tag.putUUID("MimicTarget", uuid));
    }

    static void readAdditionalSaveData(MimicEntity mimic, CompoundTag tag) {
        mimic.setMimicPhase(tag.getInt("MimicPhase"));
        mimic.setDespawnTimerInternal(tag.getInt("DespawnTimer"));
        mimic.setEngagedInternal(tag.getBoolean("Engaged"));
        if (tag.hasUUID("MimicTarget")) {
            mimic.setMimicTargetUUIDInternal(Optional.of(tag.getUUID("MimicTarget")));
        }
    }
}
