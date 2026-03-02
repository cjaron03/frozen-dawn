package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, FrozenDawn.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_LIGHT = SOUNDS.register("ambient.wind_light",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.wind_light")));

    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_STRONG = SOUNDS.register("ambient.wind_strong",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.wind_strong")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SHELTER_CREAK = SOUNDS.register("ambient.shelter_creak",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.shelter_creak")));

    public static final DeferredHolder<SoundEvent, SoundEvent> EVA_BREATHING = SOUNDS.register("ambient.eva_breathing",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.eva_breathing")));

    public static final DeferredHolder<SoundEvent, SoundEvent> EVA_SUFFOCATE = SOUNDS.register("ambient.eva_suffocate",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.eva_suffocate")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_WHISPER = SOUNDS.register("ambient.sanity_whisper",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.sanity_whisper")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_FOOTSTEP = SOUNDS.register("ambient.sanity_footstep",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.sanity_footstep")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SANITY_THUD = SOUNDS.register("ambient.sanity_thud",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ambient.sanity_thud")));

    // --- Frostbitten Mob ---
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_AMBIENT = SOUNDS.register("entity.frostbitten.ambient",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_HURT = SOUNDS.register("entity.frostbitten.hurt",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.hurt")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_DEATH = SOUNDS.register("entity.frostbitten.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_THROW = SOUNDS.register("entity.frostbitten.throw",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.throw")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTBITTEN_EMERGE = SOUNDS.register("entity.frostbitten.emerge",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.frostbitten.emerge")));

    // --- Hollow Mob ---
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_AMBIENT = SOUNDS.register("entity.hollow.ambient",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_HURT = SOUNDS.register("entity.hollow.hurt",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.hurt")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_DEATH = SOUNDS.register("entity.hollow.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_GRAB = SOUNDS.register("entity.hollow.grab",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.grab")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_ENTOMB = SOUNDS.register("entity.hollow.entomb",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.hollow.entomb")));

    // --- Returned Mob ---
    public static final DeferredHolder<SoundEvent, SoundEvent> RETURNED_AMBIENT = SOUNDS.register("entity.returned.ambient",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.returned.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RETURNED_HURT = SOUNDS.register("entity.returned.hurt",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.returned.hurt")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RETURNED_DEATH = SOUNDS.register("entity.returned.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.returned.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RETURNED_STEP = SOUNDS.register("entity.returned.step",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "entity.returned.step")));
}
