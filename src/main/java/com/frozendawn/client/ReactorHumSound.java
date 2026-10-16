package com.frozendawn.client;

import com.frozendawn.block.ReactorControllerBlockEntity;
import com.frozendawn.init.ModSounds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Low running hum from a CFU-7 Controller. Louder and slightly higher with fission heat; fades after a SCRAM. */
public final class ReactorHumSound extends AbstractTickableSoundInstance {
    private static final Map<ReactorControllerBlockEntity, ReactorHumSound> PLAYING = new WeakHashMap<>();
    private static final Map<ReactorControllerBlockEntity, Long> RETRY = new WeakHashMap<>();
    private static final float FADE = 0.02F;
    private final ReactorControllerBlockEntity controller;
    private long heartbeat = Long.MIN_VALUE / 2;

    private ReactorHumSound(ReactorControllerBlockEntity controller) {
        super(ModSounds.REACTOR_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.controller = controller;
        var pos = controller.getBlockPos();
        x = pos.getX() + .5; y = pos.getY() + .5; z = pos.getZ() + .5;
        looping = true; delay = 0; volume = 0; pitch = .9F;
        attenuation = Attenuation.LINEAR;
    }

    /** Client block entity ticker. Starts one hum per running Controller. */
    public static void tick(ReactorControllerBlockEntity controller) {
        if (controller.hum() <= 0.01F) return;
        // A sound muted on start (vacuum, Stillpoint, ducking) never becomes active and never reports stopped:
        // ask the sound manager, and retry every two seconds until it is really audible again.
        var manager = Minecraft.getInstance().getSoundManager(); var playing = PLAYING.get(controller);
        long now = controller.getLevel() == null ? 0 : controller.getLevel().getGameTime();
        // Alive means the engine is ticking it (directly or through a muffling wrapper) - never ask isActive,
        // which cannot see a wrapped instance and would start duplicates.
        if (playing != null && !playing.isStopped() && now - playing.heartbeat < 40) return;
        if (playing != null && !playing.isStopped() && now - RETRY.getOrDefault(controller, Long.MIN_VALUE / 2) < 40) return;
        if (playing != null) { playing.stop(); manager.stop(playing); }
        RETRY.put(controller, now);
        var sound = new ReactorHumSound(controller);
        PLAYING.put(controller, sound);
        manager.play(sound);
    }

    @Override public boolean canStartSilent() { return true; }

    @Override public void tick() {
        if (controller.getLevel() != null) heartbeat = controller.getLevel().getGameTime();
        float hum = controller.isRemoved() ? 0 : controller.hum();
        float target = hum <= 0.01F ? 0 : .35F + 1.15F * hum;
        volume = volume < target ? Math.min(target, volume + FADE) : Math.max(target, volume - FADE);
        pitch = .9F + .15F * hum;
        if (volume <= 0.001F && target <= 0) { stop(); PLAYING.remove(controller); }
    }
}
