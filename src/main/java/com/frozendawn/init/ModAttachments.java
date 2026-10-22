package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.CognitiveLoadState;
import com.frozendawn.data.HearthrotState;
import com.frozendawn.data.SuitIntegrity;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(
                    NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FrozenDawn.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SuitIntegrity>>
            SUIT_INTEGRITY = ATTACHMENTS.register(
                    "suit_integrity",
                    () -> AttachmentType.serializable(SuitIntegrity::new).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CognitiveLoadState>>
            COGNITIVE_LOAD = ATTACHMENTS.register(
                    "cognitive_load",
                    () -> AttachmentType.serializable(CognitiveLoadState::new).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<HearthrotState>>
            HEARTHROT = ATTACHMENTS.register(
                    "hearthrot",
                    () -> AttachmentType.serializable(HearthrotState::new).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.frozendawn.data.RadiationState>>
            RADIATION = ATTACHMENTS.register("radiation",
                    () -> AttachmentType.serializable(com.frozendawn.data.RadiationState::new).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.frozendawn.data.EmergencyEvaState>>
            EMERGENCY_EVA = ATTACHMENTS.register("emergency_eva",
                    () -> AttachmentType.serializable(com.frozendawn.data.EmergencyEvaState::new).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.frozendawn.data.ContinuityRecoveryState>>
            CONTINUITY_RECOVERY = ATTACHMENTS.register("continuity_recovery",
                    () -> AttachmentType.serializable(com.frozendawn.data.ContinuityRecoveryState::new)
                            .copyOnDeath().build());

    /** Game time until which a spoonful of Miel keeps the player 5C warmer. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>>
            MIEL_WARM_UNTIL = ATTACHMENTS.register("miel_warm_until",
                    () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    private ModAttachments() {
    }
}
