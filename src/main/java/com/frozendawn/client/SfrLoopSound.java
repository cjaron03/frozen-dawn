package com.frozendawn.client;

import com.frozendawn.block.SfrControllerBlockEntity;
import com.frozendawn.init.ModSounds;
import com.frozendawn.power.FusionPhysics;
import com.frozendawn.power.FusionPhysics.State;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** SFR-1 running sounds, one instance per Controller and kind, read from the synced sound state. Loops follow the
 * CFU-7 hum: a heartbeat shows the engine still ticks them, and a muted start is retried every two seconds. The
 * ignition charge is one-shot: it plays once per ignition and is cut off if the ignition aborts. */
public final class SfrLoopSound extends AbstractTickableSoundInstance {
    public enum Kind { CRYO, IGNITION, BURN, PUMP, ALARM }
    private static final Map<SfrControllerBlockEntity, EnumMap<Kind, SfrLoopSound>> PLAYING = new WeakHashMap<>();
    private static final Map<SfrControllerBlockEntity, EnumMap<Kind, Long>> RETRY = new WeakHashMap<>();
    private static final float FADE = 0.05F;
    private final SfrControllerBlockEntity controller;
    private final Kind kind;
    private long heartbeat = Long.MIN_VALUE / 2;
    private double rampHeat = -1;

    private SfrLoopSound(SfrControllerBlockEntity controller, Kind kind, BlockPos at) {
        super(event(kind), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.controller = controller; this.kind = kind;
        x = at.getX() + .5; y = at.getY() + .5; z = at.getZ() + .5;
        looping = kind != Kind.IGNITION; delay = 0; attenuation = Attenuation.LINEAR;
        volume = kind == Kind.IGNITION || kind == Kind.ALARM ? target() : 0; pitch = pitch();
    }
    private static SoundEvent event(Kind kind) {
        return switch (kind) {
            case CRYO -> ModSounds.SFR_CRYO.get(); case IGNITION -> ModSounds.SFR_IGNITION.get(); case BURN -> ModSounds.SFR_BURN.get();
            case PUMP -> ModSounds.SFR_PUMP.get(); case ALARM -> ModSounds.SFR_ALARM.get();
        };
    }
    private static boolean wanted(Kind kind, SfrControllerBlockEntity.Sound s) {
        return switch (kind) {
            case CRYO -> s.state() == State.COOLDOWN || s.state() == State.READY;
            case IGNITION -> s.state() == State.IGNITION;
            case BURN -> plasma(s);
            case PUMP -> s.pumping() && s.pump() != null;
            case ALARM -> s.alarm();
        };
    }

    private static boolean plasma(SfrControllerBlockEntity.Sound s) { return s.state() == State.BURNING || s.state() == State.RAMP_DOWN; }

    /** Client block entity ticker. */
    public static void tick(SfrControllerBlockEntity controller) {
        var s = controller.sound();
        var manager = Minecraft.getInstance().getSoundManager();
        var playing = PLAYING.computeIfAbsent(controller, k -> new EnumMap<>(Kind.class));
        var retry = RETRY.computeIfAbsent(controller, k -> new EnumMap<>(Kind.class));
        long now = controller.getLevel() == null ? 0 : controller.getLevel().getGameTime();
        for (var kind : Kind.values()) {
            var sound = playing.get(kind);
            if (kind == Kind.IGNITION) {
                // Cut off on abort; let the charge finish its thump when ignition reaches BURNING.
                if (!wanted(kind, s)) { if (sound != null) { if (!plasma(s)) { sound.stop(); manager.stop(sound); } playing.remove(kind); } }
                else if (sound == null) { sound = new SfrLoopSound(controller, kind, controller.getBlockPos()); playing.put(kind, sound); manager.play(sound); }
                continue;
            }
            if (!wanted(kind, s)) continue; // a playing loop fades itself out
            if (sound != null && !sound.isStopped() && now - sound.heartbeat < 40) continue;
            if (sound != null && !sound.isStopped() && now - retry.getOrDefault(kind, Long.MIN_VALUE / 2) < 40) continue;
            if (sound != null) { sound.stop(); manager.stop(sound); }
            retry.put(kind, now);
            var at = kind == Kind.PUMP ? s.pump() : controller.getBlockPos();
            sound = new SfrLoopSound(controller, kind, at);
            playing.put(kind, sound);
            manager.play(sound);
        }
    }

    private float target() {
        var s = controller.sound();
        if (controller.isRemoved() || !wanted(kind, s)) return 0;
        double heat = Math.clamp(s.heatKw() / FusionPhysics.RATED_KW, 0, 1);
        return switch (kind) {
            case CRYO -> .6F; case IGNITION -> 1.2F; case PUMP -> .7F; case ALARM -> 1.6F;
            case BURN -> (float) (.3 * rampFloor(s, heat) + .7 * heat);
        };
    }
    /** 1 while burning. During a ramp-down the steady burn's floor shrinks with the heat it began at, so the roar starts
     * where it was and winds down to silence. */
    private double rampFloor(SfrControllerBlockEntity.Sound s, double heat) {
        if (s.state() != State.RAMP_DOWN) { rampHeat = -1; return 1; }
        if (rampHeat < 0) rampHeat = heat;
        return rampHeat > 0 ? Math.min(1, heat / rampHeat) : 0;
    }
    private float pitch() {
        var s = controller.sound();
        return switch (kind) {
            case BURN -> (float) (.85 + .3 * Math.clamp(s.heatKw() / FusionPhysics.RATED_KW, 0, 1));
            case PUMP -> (float) (.8 + .4 * (1 - Math.clamp(s.pressure(), 0, 1)));
            default -> 1F;
        };
    }

    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        if (controller.getLevel() != null) heartbeat = controller.getLevel().getGameTime();
        if (kind == Kind.IGNITION) { if (controller.isRemoved()) stop(); return; }
        float target = target();
        // The alarm switches; the running loops fade in and out.
        volume = kind == Kind.ALARM ? target : volume < target ? Math.min(target, volume + FADE) : Math.max(target, volume - FADE);
        pitch = pitch();
        if (volume <= 0.001F && target <= 0) { stop(); var map = PLAYING.get(controller); if (map != null && map.get(kind) == this) map.remove(kind); }
    }
}
