package com.frozendawn.client;

import com.frozendawn.block.HyperionControllerBlockEntity;
import com.frozendawn.block.HyperionMultiblock;
import com.frozendawn.init.ModSounds;
import com.frozendawn.power.HyperionPhysics;
import com.frozendawn.power.HyperionPhysics.Stage;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** SMS-1 Hyperion loops, one instance per Controller and kind, read from the synced snapshot, with the SFR-1 loops'
 * lifecycle: a heartbeat shows the engine still ticks them, and a muted start is retried every two seconds. The hum
 * plays while the battery holds charge or is being cooled, quieter the colder it sits; the warning loop rises in pitch
 * as the quench nears. The final whine plays once per final stage and fades out if the cells recover first. */
@EventBusSubscriber(modid = com.frozendawn.FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HyperionLoopSound extends AbstractTickableSoundInstance {
    public enum Kind { HUM, WARNING, WHINE }
    private static final Map<HyperionControllerBlockEntity, EnumMap<Kind, HyperionLoopSound>> PLAYING = new WeakHashMap<>();
    private static final Map<HyperionControllerBlockEntity, EnumMap<Kind, Long>> RETRY = new WeakHashMap<>();
    private static final float FADE = 0.05F;
    private final HyperionControllerBlockEntity controller;
    private final Kind kind;
    private long heartbeat = Long.MIN_VALUE / 2, seenAt;
    private double seenToQuench = Double.NaN;

    private HyperionLoopSound(HyperionControllerBlockEntity controller, Kind kind) {
        super(switch (kind) { case HUM -> ModSounds.HYPERION_HUM.get(); case WARNING -> ModSounds.HYPERION_WARNING.get(); case WHINE -> ModSounds.HYPERION_WHINE.get(); }, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.controller = controller; this.kind = kind;
        var at = controller.getBlockPos();
        x = at.getX() + .5; y = at.getY() + .5; z = at.getZ() + .5;
        looping = kind != Kind.WHINE; delay = 0; attenuation = Attenuation.LINEAR;
        volume = kind == Kind.HUM ? 0 : target(); pitch = pitch();
    }
    private static boolean wanted(Kind kind, HyperionControllerBlockEntity.Snapshot s) {
        if (s.problem() != HyperionMultiblock.Problem.NONE) return false;
        return switch (kind) {
            case HUM -> s.stage() == Stage.WARM ? s.flowIn() > 0 : s.stored() > 0;
            case WARNING -> s.stage() == Stage.WARNING;
            case WHINE -> s.stage() == Stage.FINAL;
        };
    }

    /** Leaving a world clears the sound manager without ticking these sounds, and each one holds its Controller, so the
     * weak keys would never go: drop them here. */
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { PLAYING.clear(); RETRY.clear(); }

    /** Client block entity ticker. */
    public static void tick(HyperionControllerBlockEntity controller) {
        var s = controller.snapshot();
        var manager = Minecraft.getInstance().getSoundManager();
        var playing = PLAYING.computeIfAbsent(controller, k -> new EnumMap<>(Kind.class));
        var retry = RETRY.computeIfAbsent(controller, k -> new EnumMap<>(Kind.class));
        long now = controller.getLevel() == null ? 0 : controller.getLevel().getGameTime();
        for (var kind : Kind.values()) {
            var sound = playing.get(kind);
            if (!wanted(kind, s)) {
                // A playing sound fades itself out; a whine that already ran to its end is forgotten, so the next final stage whines.
                if (kind == Kind.WHINE && sound != null && now - sound.heartbeat >= 40) playing.remove(kind);
                continue;
            }
            if (kind == Kind.WHINE && sound != null) continue; // once per final stage, never restarted
            if (sound != null && !sound.isStopped() && now - sound.heartbeat < 40) continue;
            if (sound != null && !sound.isStopped() && now - retry.getOrDefault(kind, Long.MIN_VALUE / 2) < 40) continue;
            if (sound != null) { sound.stop(); manager.stop(sound); }
            retry.put(kind, now);
            sound = new HyperionLoopSound(controller, kind);
            playing.put(kind, sound);
            manager.play(sound);
        }
    }

    private float target() {
        var s = controller.snapshot();
        if (controller.isRemoved() || !wanted(kind, s)) return 0;
        return switch (kind) { case HUM -> (float) (0.6 * Math.clamp((s.surroundingsC() + 270) / 290, 0.02, 1)); case WARNING -> 1.2F; case WHINE -> 1.5F; };
    }
    /** The warning rises from 1.0 when it begins to 1.6 as the final stage starts. */
    private float pitch() {
        if (kind != Kind.WARNING) return 1F;
        var s = controller.snapshot();
        double start = HyperionPhysics.warningStartS(s.warningSeconds()), span = start - HyperionPhysics.FINAL_S;
        if (span <= 0 || !Double.isFinite(s.secondsToQuench())) return 1F;
        return (float) (1.0 + 0.6 * Math.clamp((start - toQuench(s.secondsToQuench())) / span, 0, 1));
    }
    /** The time to the quench is synced once a second; counting it down between syncs (never past a second) makes the
     * pitch glide instead of stepping. */
    private double toQuench(double synced) {
        long now = controller.getLevel() == null ? 0 : controller.getLevel().getGameTime();
        if (synced != seenToQuench) { seenToQuench = synced; seenAt = now; }
        return synced - Math.min(1.0, (now - seenAt) / 20.0);
    }

    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        if (controller.getLevel() != null) heartbeat = controller.getLevel().getGameTime();
        float target = target();
        // The warning switches; the hum fades in and out, the whine out.
        volume = kind == Kind.WARNING ? target : volume < target ? Math.min(target, volume + FADE) : Math.max(target, volume - FADE);
        pitch = pitch();
        if (volume <= 0.001F && target <= 0) { stop(); var map = PLAYING.get(controller); if (map != null && map.get(kind) == this) map.remove(kind); }
    }
}
