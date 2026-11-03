package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.mixin.SoundEngineAccessor;
import com.frozendawn.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Hyperion's deathwand is silent, so the Terminus's discharge is heard as an absence (owner's pick): for the operator
 * every sound drops to nothing, holds there, then fades back. The listener's gain is the master volume times this
 * envelope; vanilla's own master volume comes back at the end.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class TerminusHush {
    public static final int HOLD_TICKS = 26, FADE_TICKS = 24;
    private static int age = -1;

    /** The share of the master volume heard {@code age} ticks after the discharge. */
    public static float gain(int age) {
        return Mth.clamp((age - HOLD_TICKS) / (float) FADE_TICKS, 0, 1);
    }

    public static void start() {
        age = 0;
        apply(0);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (age < 0 || Minecraft.getInstance().isPaused()) return;
        if (++age >= HOLD_TICKS + FADE_TICKS) {
            age = -1;
            apply(1);
            return;
        }
        apply(gain(age));
    }

    @SubscribeEvent public static void forget(ClientPlayerNetworkEvent.LoggingOut event) {
        if (age >= 0) apply(1);
        age = -1;
    }

    private static void apply(float share) {
        var mc = Minecraft.getInstance();
        var engine = (SoundEngineAccessor) ((SoundManagerAccessor) mc.getSoundManager()).frozendawn$getSoundEngine();
        engine.frozendawn$getListener().setGain(mc.options.getSoundSourceVolume(SoundSource.MASTER) * share);
    }

    private TerminusHush() { }
}
