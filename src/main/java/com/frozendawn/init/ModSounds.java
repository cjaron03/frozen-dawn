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
}
