package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.CognitiveLoadState;
import com.frozendawn.data.HearthrotState;
import com.frozendawn.data.SuitIntegrity;
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

    private ModAttachments() {
    }
}
