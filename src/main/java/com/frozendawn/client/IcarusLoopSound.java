package com.frozendawn.client;

import com.frozendawn.block.HalseyCoreBlockEntity;
import com.frozendawn.init.ModSounds;
import com.frozendawn.power.IcarusLook;
import com.frozendawn.power.IcarusPhysics;
import com.frozendawn.power.IcarusPhysics.State;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** AMR-1 Icarus running sounds, one instance per Halsey Core and kind, read from the synced core Visual. They follow
 * SfrLoopSound: a heartbeat shows the engine still ticks a loop, a muted start is retried every two seconds, and loops fade
 * in and out. All of them sit at the core; the block-sound namespace keeps them audible in vacuum. The core's roar and
 * song follow the plasma's eased glow, so they swell through ignition and spool down with the Cole ramp-down. */
public final class IcarusLoopSound extends AbstractTickableSoundInstance {
    public enum Kind { FRAME, STABILIZER, SHIELD, CORE, PLASMA, KLAXON, STRAIN }
    private static final Map<HalseyCoreBlockEntity, EnumMap<Kind, IcarusLoopSound>> PLAYING = new WeakHashMap<>();
    private static final Map<HalseyCoreBlockEntity, EnumMap<Kind, Long>> RETRY = new WeakHashMap<>();
    private static final float FADE = 0.05F;
    private final HalseyCoreBlockEntity core;
    private final Kind kind;
    private long heartbeat = Long.MIN_VALUE / 2;

    private IcarusLoopSound(HalseyCoreBlockEntity core, Kind kind) {
        super(event(kind), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.core = core; this.kind = kind;
        var at = core.getBlockPos();
        x = at.getX() + .5; y = at.getY() + .5; z = at.getZ() + .5;
        looping = true; delay = 0; attenuation = Attenuation.LINEAR; volume = 0; pitch = pitch();
    }
    private static SoundEvent event(Kind kind) {
        return switch (kind) {
            case FRAME -> ModSounds.ICARUS_FRAME_HUM.get(); case STABILIZER -> ModSounds.ICARUS_STABILIZER_WHINE.get();
            case SHIELD -> ModSounds.ICARUS_SHIELD_HUM.get(); case CORE -> ModSounds.ICARUS_CORE_ROAR.get();
            case PLASMA -> ModSounds.ICARUS_CORE_SONG.get();
            case KLAXON -> ModSounds.ICARUS_KLAXON.get(); case STRAIN -> ModSounds.ICARUS_STRAIN.get();
        };
    }
    private static boolean wanted(Kind kind, HalseyCoreBlockEntity core) {
        var v = core.visual();
        return switch (kind) {
            case FRAME -> v.state() != State.COLD && v.state() != State.DETONATED;
            case STABILIZER -> IcarusLook.beamsLit(v.state(), v.stepLeft()) > 0;
            case SHIELD -> IcarusLook.beamsLit(v.state(), v.stepLeft()) > 0 && v.shieldPct() > 0;
            case CORE -> IcarusCoreAnim.of(core).glow(1) > .01F || v.coreC() > 500;
            case PLASMA -> IcarusCoreAnim.of(core).glow(1) > .01F;
            case KLAXON -> v.alarm() == IcarusPhysics.Alarm.CRITICAL;
            case STRAIN -> IcarusLook.danger(v.coreC(), v.shieldPct(), v.alarm()) > .15;
        };
    }

    /** Client block entity ticker. */
    public static void tick(HalseyCoreBlockEntity core) {
        var manager = Minecraft.getInstance().getSoundManager();
        var playing = PLAYING.computeIfAbsent(core, k -> new EnumMap<>(Kind.class));
        var retry = RETRY.computeIfAbsent(core, k -> new EnumMap<>(Kind.class));
        long now = core.getLevel() == null ? 0 : core.getLevel().getGameTime();
        for (var kind : Kind.values()) {
            if (!wanted(kind, core)) continue; // a playing loop fades itself out
            var sound = playing.get(kind);
            if (sound != null && !sound.isStopped() && now - sound.heartbeat < 40) continue;
            if (sound != null && !sound.isStopped() && now - retry.getOrDefault(kind, Long.MIN_VALUE / 2) < 40) continue;
            if (sound != null) { sound.stop(); manager.stop(sound); }
            retry.put(kind, now);
            sound = new IcarusLoopSound(core, kind);
            playing.put(kind, sound);
            manager.play(sound);
        }
    }

    private float target() {
        var v = core.visual();
        if (core.isRemoved() || !wanted(kind, core)) return 0;
        double heat = IcarusCoreAnim.heat(v.coreC()), glow = IcarusCoreAnim.of(core).glow(1);
        return switch (kind) {
            case FRAME -> .5F;
            case STABILIZER -> .2F + .15F * IcarusLook.beamsLit(v.state(), v.stepLeft());
            case SHIELD -> (float) (.4 + .5 * Math.clamp(v.shieldPct() / 100, 0, 1));
            case CORE -> (float) (glow * (.5 + .4 * Math.clamp(v.grossMw() / IcarusPhysics.MAX_OUTPUT_MW, 0, 1)) + .9 * heat);
            case PLASMA -> (float) (glow * (.55 + .3 * heat));
            // Volume above 1 carries further: the klaxon reaches about 35 blocks at its worst.
            case KLAXON -> (float) (.8 + 1.4 * IcarusLook.danger(v.coreC(), v.shieldPct(), v.alarm()));
            case STRAIN -> (float) (.2 + IcarusLook.danger(v.coreC(), v.shieldPct(), v.alarm()));
        };
    }
    /** The roar and song climb with heat and sink as the plasma dims; the shield hum rises as the shield weakens. */
    private float pitch() {
        var v = core.visual();
        double heat = IcarusCoreAnim.heat(v.coreC()), glow = IcarusCoreAnim.of(core).glow(1);
        return switch (kind) {
            case CORE -> (float) (.5 + .25 * glow + heat);
            case PLASMA -> (float) (.75 + .25 * glow + .35 * heat);
            case KLAXON -> (float) (.95 + .3 * Math.max(0, IcarusLook.danger(v.coreC(), v.shieldPct(), v.alarm()) - .55) / .45);
            case STRAIN -> (float) (.75 + .35 * IcarusLook.danger(v.coreC(), v.shieldPct(), v.alarm()));
            case SHIELD -> (float) (.8 + .5 * (1 - Math.clamp(v.shieldPct() / 100, 0, 1)));
            case STABILIZER -> .85F + .05F * IcarusLook.beamsLit(v.state(), v.stepLeft());
            default -> 1F;
        };
    }

    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        if (core.getLevel() != null) heartbeat = core.getLevel().getGameTime();
        float target = target();
        volume = volume < target ? Math.min(target, volume + FADE) : Math.max(target, volume - FADE);
        pitch = pitch();
        if (volume <= 0.001F && target <= 0) { stop(); var map = PLAYING.get(core); if (map != null && map.get(kind) == this) map.remove(kind); }
    }
}
