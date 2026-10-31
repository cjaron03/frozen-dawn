package com.frozendawn.client;

import com.frozendawn.block.ReactorControllerBlockEntity;
import com.frozendawn.init.ModSounds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Looping overheat klaxon (350C) or failure siren (700C) from a Controller. One instance per reactor, so blasts
 * cannot stack after a server hitch; restarted if it was muted on start (vacuum) once it can be heard again. */
public final class ReactorAlarmSound extends AbstractTickableSoundInstance {
    private static final Map<ReactorControllerBlockEntity, ReactorAlarmSound> PLAYING = new WeakHashMap<>();
    private static final Map<ReactorControllerBlockEntity, Long> RETRY = new WeakHashMap<>();
    private final ReactorControllerBlockEntity controller;
    private final int level;
    private long heartbeat = Long.MIN_VALUE / 2;

    private ReactorAlarmSound(ReactorControllerBlockEntity controller, int level) {
        super(level == ReactorControllerBlockEntity.ALARM_FAILURE ? ModSounds.REACTOR_MELTDOWN_ALERT.get() : ModSounds.REACTOR_KLAXON.get(),
                SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.controller = controller; this.level = level;
        var pos = controller.getBlockPos();
        x = pos.getX() + .5; y = pos.getY() + .5; z = pos.getZ() + .5;
        looping = true; delay = 0; volume = level == ReactorControllerBlockEntity.ALARM_FAILURE ? 2.5f : 2f; pitch = 1;
        attenuation = Attenuation.LINEAR;
    }

    public static void tick(ReactorControllerBlockEntity controller) {
        int level = controller.alarm();
        var manager = Minecraft.getInstance().getSoundManager(); var playing = PLAYING.get(controller);
        if (level == ReactorControllerBlockEntity.ALARM_NONE) {
            if (playing != null) { playing.stop(); manager.stop(playing); PLAYING.remove(controller); }
            return;
        }
        long now = controller.getLevel() == null ? 0 : controller.getLevel().getGameTime();
        if (playing != null && playing.level == level && !playing.isStopped() && now - playing.heartbeat < 40) return;
        if (playing != null && playing.level == level && !playing.isStopped() && now - RETRY.getOrDefault(controller, Long.MIN_VALUE / 2) < 40) return;
        if (playing != null) { playing.stop(); manager.stop(playing); }
        RETRY.put(controller, now);
        var sound = new ReactorAlarmSound(controller, level);
        PLAYING.put(controller, sound);
        manager.play(sound);
    }

    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        if (controller.getLevel() != null) heartbeat = controller.getLevel().getGameTime();
        if (controller.isRemoved() || controller.alarm() != level) stop();
    }
}
