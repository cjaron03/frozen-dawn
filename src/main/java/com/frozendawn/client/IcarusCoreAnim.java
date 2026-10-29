package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.HalseyCoreBlockEntity;
import com.frozendawn.block.IcarusStabilizerBlockEntity;
import com.frozendawn.init.ModSounds;
import com.frozendawn.power.IcarusLook;
import com.frozendawn.power.IcarusPhysics;
import com.frozendawn.power.IcarusPhysics.State;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** The Icarus's moving parts on one client, eased tick by tick toward what the synced core Visual says, so the renderers
 * draw smooth motion from a state that only changes once a second. Kept per core and dropped with it. Also nudges the
 * camera of a player within 24 blocks of a critical core. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class IcarusCoreAnim {
    private static final Map<HalseyCoreBlockEntity, IcarusCoreAnim> ANIMS = new WeakHashMap<>();
    public static final float CORE_RADIUS = 2.5f, SPECK_RADIUS = 0.18f;
    /** Where beat wraps; a multiple of every period the renderer animates by. */
    public static final float BEAT_WRAP = 7200;
    private static final double SHAKE_RANGE = 48;
    /** Beats per heartbeat; it divides BEAT_WRAP, as must every other period of beat. The thump lands just before the
     * visual swell. */
    public static final float BEAT = 36, THUMP_AT = 2;
    private static float kick;
    private static long kickDecayed;
    private static float shake;
    private static long shakeUntil;

    private State state = State.COLD;
    /** Ticks since the core entered its current state. */
    public int stateTicks;
    private float glow, glowO, bloom, bloomO, flash, flashO, front, frontO, tail, tailO, churn, churnO, ring, ringO, ringSpeed, beat, beatO;
    /** The plasma's radius in blocks, the antimatter speck's size (0 to 1), how solid the dissolving core block is (0 to
     * 1) and the shield's eased opacity. */
    private float radius, radiusO, speck, speckO, solid = -1, solidO, shield, shieldO;
    private final float[] beam = new float[IcarusLook.STABILIZERS], beamO = new float[IcarusLook.STABILIZERS];
    /** How far each stabilizer's patch of shield has spread from where its beam strikes, 0 to 1. */
    private final float[] spread = new float[IcarusLook.STABILIZERS], spreadO = new float[IcarusLook.STABILIZERS];

    public static IcarusCoreAnim of(HalseyCoreBlockEntity core) { return ANIMS.computeIfAbsent(core, k -> new IcarusCoreAnim()); }

    /** Client block entity ticker. */
    public static void tick(HalseyCoreBlockEntity core) {
        var a = of(core); var v = core.visual();
        if (v.state() != a.state) { a.state = v.state(); a.stateTicks = 0; } else a.stateTicks++;
        a.glowO = a.glow; a.bloomO = a.bloom; a.flashO = a.flash; a.frontO = a.front; a.tailO = a.tail; a.churnO = a.churn; a.ringO = a.ring; a.beatO = a.beat;
        a.radiusO = a.radius; a.speckO = a.speck; a.solidO = a.solid; a.shieldO = a.shield;
        System.arraycopy(a.beam, 0, a.beamO, 0, a.beam.length);
        System.arraycopy(a.spread, 0, a.spreadO, 0, a.spread.length);
        double heat = heat(v.coreC());

        // Glow eases toward its target, quickly at first and gently at the end. A CLIP dims it within a second; the Cole
        // Protocol follows the falling output, then the cooling core, so the plasma fades and shrinks step by step.
        float out = (float) Math.clamp(v.grossMw() / IcarusPhysics.MAX_OUTPUT_MW, 0, 1);
        float glowTarget = switch (v.state()) {
            case IGNITION -> 1;
            case RAMP, COLE_RAMP_DOWN -> (float) (0.3 + 0.5 * out + 0.2 * heat);
            // Running, output only falls to nothing when the matter runs out: the cascade starves, so the plasma sinks to
            // a flickering ember over the last quarter megawatt and swells back as matter returns.
            case RUNNING -> {
                float live = (float) Math.clamp(v.grossMw() / 0.25, 0, 1);
                float ember = 0.06f + 0.04f * Mth.sin(a.stateTicks * 0.9f) * Mth.sin(a.stateTicks * 0.37f);
                yield (float) (ember + live * (0.24 + 0.5 * out) + 0.2 * heat);
            }
            case COLE_COOL, COLE_RECOVER -> (float) (0.1 + 0.5 * heat);
            default -> 0;
        };
        float glowRate = v.state() == State.CLIPPED ? 0.15f : v.state().name().startsWith("COLE") ? 0.025f : 0.06f;
        a.glow = ease(a.glow, glowTarget, glowRate, 0.002f);

        // Ignition: a flash, then the plasma blooms from the speck past full size and settles back.
        boolean was = a.radius > 0;
        if (v.state() == State.IGNITION) {
            if (a.stateTicks == 0) a.flash = 1;
            a.bloom = Math.min(1, a.stateTicks / (IcarusPhysics.IGNITION_S * 20f));
            a.radius = SPECK_RADIUS + (CORE_RADIUS - SPECK_RADIUS) * overshoot(a.bloom);
        } else if (a.glow > 0.02f) {
            a.bloom = 1;
            // A dimming plasma draws in toward the speck it grew from.
            float size = Mth.clamp(a.glow / 0.35f, 0, 1);
            a.radius = ease(a.radius, CORE_RADIUS * (0.3f + 0.7f * size * size * (3 - 2 * size)), 0.08f, 0.002f);
        } else {
            a.bloom = 0;
            a.radius = was ? ease(a.radius, 0, 0.25f, 0.02f) : 0;
            // The last of the plasma winks out in a soft pop.
            if (was && a.radius == 0) a.flash = Math.max(a.flash, 0.35f);
        }
        a.flash = Math.max(0, a.flash - 0.07f);

        // The antimatter speck: it rides the injection line in, waits through priming, is swallowed by the bloom, shows
        // again whenever the plasma is gone with antimatter still held, and shrinks as the discharge returns it.
        float speckTarget = switch (v.state()) {
            case INJECTION -> Math.min(1, a.stateTicks / 40f);
            case PRIMING -> 1;
            case IGNITION -> Math.clamp(1 - a.bloom * 2.5f, 0, 1);
            case CLIPPED, COLE_RAMP_DOWN, COLE_COOL, COLE_RECOVER -> a.radius < 0.6f ? 1 : 0;
            case COLE_DISCHARGE -> a.radius < 0.6f ? Math.clamp(v.stepLeft() / (float) IcarusPhysics.DISCHARGE_S, 0, 1) : 0;
            default -> 0;
        };
        a.speck = ease(a.speck, speckTarget, 0.1f, 0.004f);

        // The core block dissolves into the injected antimatter and condenses again as the discharge empties the core.
        // HalseyCoreBlock is HIDDEN through both; this only animates the stand-in the renderer draws meanwhile.
        boolean hidden = IcarusLook.coreHidden(v.state());
        float solidTarget = !hidden || v.state() == State.COLE_DISCHARGE && a.radius == 0 ? 1 : 0;
        if (a.solid < 0) a.solid = a.solidO = hidden ? 0 : 1;
        else a.solid = approach(a.solid, solidTarget, solidTarget > a.solid ? 0.012f : 0.025f);

        // Matter stream: it climbs from the nozzle while priming, and on a cut its tail lifts away into the core.
        boolean feeding = switch (v.state()) { case PRIMING, IGNITION, RAMP, RUNNING -> true; case COLE_RAMP_DOWN -> v.grossMw() > 0.05; default -> false; };
        if (feeding) { a.tail = 0; a.front = approach(a.front, 1, 0.025f); }
        else if (a.front > 0) { a.tail = approach(a.tail, 1, 0.15f); if (a.tail >= 1) { a.front = 0; a.tail = 0; a.frontO = 0; a.tailO = 0; } }

        int lit = IcarusLook.beamsLit(v.state(), v.stepLeft());
        for (int i = 0; i < a.beam.length; i++) a.beam[i] = approach(a.beam[i], i < lit ? 1 : 0, 0.06f);
        // Each patch of shield spreads from its beam's strike once the beam arrives, and draws back into it as it goes.
        for (int i = 0; i < a.spread.length; i++) a.spread[i] = ease(a.spread[i], i < lit && a.beam[i] > 0.95f ? 1 : 0, 0.03f, 0.002f);
        a.shield = ease(a.shield, (float) IcarusLook.shieldAlpha(v.state(), v.shieldPct()), 0.05f, 0.001f);

        // Churn in degrees per tick; the rings spin with the beams and park square when they stop.
        a.churn += (float) (IcarusLook.churn(v.grossMw(), v.coreC()) * 360 / 20);
        float ringTarget = lit > 0 ? (float) (4 + 8 * heat) : 0;
        a.ringSpeed = approach(a.ringSpeed, ringTarget, 0.08f);
        a.ring += a.ringSpeed;
        if (ringTarget == 0 && a.ringSpeed < 0.3f) a.ring += (Math.round(a.ring / 90) * 90 - a.ring) * 0.1f;
        // The running core's own clock: it quickens with glow and heat, and wraps after BEAT_WRAP, which every period the
        // renderer uses divides, so the wrap never shows.
        double danger = IcarusLook.danger(v.coreC(), v.shieldPct(), v.alarm());
        a.beat += 0.6f + 0.9f * a.glow + 0.8f * (float) heat + 1.5f * (float) danger;
        if (a.beat >= BEAT_WRAP) { a.beat -= BEAT_WRAP; a.beatO -= BEAT_WRAP; }

        effects(core, a);
        // Near the end the ground shakes harder the worse it gets, the core thumps on every heartbeat and jolts the view,
        // and the screen's edges pulse red (IcarusDangerHud).
        var player = Minecraft.getInstance().player;
        if (player != null && core.getLevel() != null && danger > 0) {
            long now = core.getLevel().getGameTime();
            double d = Math.sqrt(player.distanceToSqr(core.getBlockPos().getCenter()));
            float near = (float) Math.max(0, 1 - d / SHAKE_RANGE);
            if (danger > 0.2 && near > 0) { shake = (float) ((0.1 + 1.4 * danger * danger) * near); shakeUntil = now + 2; }
            boolean beat = Mth.floor((a.beat - THUMP_AT) / BEAT) != Mth.floor((a.beatO - THUMP_AT) / BEAT);
            if (beat && danger > 0.3) {
                var c = core.getBlockPos().getCenter();
                core.getLevel().playLocalSound(c.x, c.y, c.z, ModSounds.ICARUS_CORE_THUMP.get(), SoundSource.BLOCKS, (float) (0.6 + 1.2 * danger), (float) (0.9 + 0.2 * danger), false);
                kick = Math.max(kick, (float) (1.5 * danger * near));
            }
            if (d < IcarusDangerHud.RANGE) IcarusDangerHud.report(now, (float) (danger * Math.sqrt(1 - d / IcarusDangerHud.RANGE)), v.shieldPct(), a.beat);
        }
    }

    /** Sounds and particles for the chance events IcarusFx rolls: a crackle where each stray annihilation lands, and the
     * failing stabilizer spitting sparks, smoking while its beam is out and sputtering each time it cuts. */
    private static void effects(HalseyCoreBlockEntity core, IcarusCoreAnim a) {
        var level = core.getLevel();
        if (level == null) return;
        long now = level.getGameTime();
        var centre = core.getBlockPos().getCenter();
        var random = level.getRandom();
        if (now % IcarusFx.HIT_SLOT == 0) {
            int sounded = 0;
            for (var hit : IcarusFx.hits(core, now, a.radius > 0.3f && a.glow > 0.05f)) {
                if (hit.slot() * IcarusFx.HIT_SLOT != now || sounded++ >= 2) continue;
                var at = centre.add(hit.dir().scale(IcarusFx.SHIELD_RADIUS));
                level.playLocalSound(at.x, at.y, at.z, ModSounds.ICARUS_SHIELD_HIT.get(), SoundSource.BLOCKS, 0.6f, 0.8f + 0.5f * random.nextFloat(), false);
            }
        }
        double failing = IcarusFx.failing(core);
        if (failing <= 0) return;
        var dir = net.minecraft.core.Direction.from2DDataValue(IcarusFx.sputterIndex(core));
        var emitter = centre.add(dir.getStepX() * (IcarusStabilizerBlockEntity.CORE_DISTANCE - 0.5), 0, dir.getStepZ() * (IcarusStabilizerBlockEntity.CORE_DISTANCE - 0.5));
        long slot = Math.floorDiv(now, IcarusFx.SPUTTER_SLOT);
        boolean out = IcarusFx.cutOut(core, slot, failing);
        if (out && now % IcarusFx.SPUTTER_SLOT == 0 && !IcarusFx.cutOut(core, slot - 1, failing))
            level.playLocalSound(emitter.x, emitter.y, emitter.z, ModSounds.ICARUS_STABILIZER_SPUTTER.get(), SoundSource.BLOCKS, 0.8f, 0.85f + 0.3f * random.nextFloat(), false);
        for (int i = 0; i < (out ? 3 : 1); i++)
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, emitter.x + random.nextGaussian() * 0.3, emitter.y + random.nextGaussian() * 0.3, emitter.z + random.nextGaussian() * 0.3,
                    random.nextGaussian() * 0.15, random.nextFloat() * 0.15, random.nextGaussian() * 0.15);
        if (out && random.nextFloat() < 0.35f + 0.4f * failing)
            level.addParticle(ParticleTypes.SMOKE, emitter.x, emitter.y + 0.4, emitter.z, 0, 0.04, 0);
    }

    /** A double pulse, lub-dub, over a phase of 0 to 1. */
    public static float heartbeat(float phase) {
        float x = phase - Mth.floor(phase);
        return (float) (Math.exp(-Math.pow((x - 0.08) / 0.04, 2)) + 0.6 * Math.exp(-Math.pow((x - 0.22) / 0.05, 2)));
    }

    private static float approach(float from, float to, float step) { return from < to ? Math.min(to, from + step) : Math.max(to, from - step); }
    /** Moves rate of the way to the target each tick, and at least min, so it settles without a hard stop or a long tail. */
    private static float ease(float from, float to, float rate, float min) { return approach(from, to, Math.max(min, Math.abs(to - from) * rate)); }
    /** 0 to 1 with a 12% overshoot before it settles: the plasma's bloom. */
    private static float overshoot(float t) { float c = 1.7f, u = t - 1; return 1 + (c + 1) * u * u * u + c * u * u; }
    public static double heat(double coreC) { return Math.clamp((coreC - IcarusPhysics.AMBIENT_C) / (IcarusPhysics.CRITICAL_C - IcarusPhysics.AMBIENT_C), 0, 1); }

    public float glow(float pt) { return Mth.lerp(pt, glowO, glow); }
    public float bloom(float pt) { return Mth.lerp(pt, bloomO, bloom); }
    public float flash(float pt) { return Mth.lerp(pt, flashO, flash); }
    public float streamFront(float pt) { return Mth.lerp(pt, frontO, front); }
    public float streamTail(float pt) { return Mth.lerp(pt, tailO, tail); }
    public float churn(float pt) { return Mth.lerp(pt, churnO, churn); }
    public float ring(float pt) { return Mth.lerp(pt, ringO, ring); }
    public float ringSpeed() { return ringSpeed; }
    public float beat(float pt) { return Mth.lerp(pt, beatO, beat); }
    public float beam(int index, float pt) { return Mth.lerp(pt, beamO[index], beam[index]); }
    public float spread(int index, float pt) { return Mth.lerp(pt, spreadO[index], spread[index]); }
    public float radius(float pt) { return Mth.lerp(pt, radiusO, radius); }
    public float speck(float pt) { return Mth.lerp(pt, speckO, speck); }
    public float solid(float pt) { return Mth.lerp(pt, solidO, solid); }
    public float shield(float pt) { return Mth.lerp(pt, shieldO, shield); }

    @SubscribeEvent public static void shakeCamera(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        if (now != kickDecayed) { kick *= 0.75f; kickDecayed = now; }
        if (now > shakeUntil) shake = 0;
        if (shake <= 0 && kick < 0.01f) return;
        float scale = mc.options.screenEffectScale().get().floatValue(), time = (float) (now + event.getPartialTick());
        float amount = shake * scale, jolt = kick * scale;
        event.setRoll(event.getRoll() + amount * Mth.sin(time * 2.3f) + jolt * Mth.sin(time * 9.1f));
        event.setPitch(event.getPitch() + amount * 0.5f * Mth.sin(time * 3.7f + 1.1f) + jolt * 0.6f * Mth.sin(time * 11.3f + 0.4f));
        event.setYaw(event.getYaw() + jolt * 0.4f * Mth.sin(time * 7.7f + 2.0f));
    }
}
