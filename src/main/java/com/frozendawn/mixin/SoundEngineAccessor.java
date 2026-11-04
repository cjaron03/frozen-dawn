package com.frozendawn.mixin;

import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> frozendawn$getInstanceToChannel();
    @Accessor("listener")
    Listener frozendawn$getListener();

    @Accessor("loaded")
    boolean frozendawn$isLoaded();

    @Invoker("calculateVolume")
    float frozendawn$calculateVolume(SoundInstance sound);
}
