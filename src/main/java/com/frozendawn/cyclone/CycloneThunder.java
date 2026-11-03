package com.frozendawn.cyclone;

import java.util.Random;
import java.util.random.RandomGenerator;

/** Listener-local cosmetic thunder. No entities, damage, blocks or server random state. */
public final class CycloneThunder {
    public enum Band { NONE, DISTANT, MID, CLOSE }
    public record Cue(Band band, boolean frontFlash) {
        public static final Cue NONE = new Cue(Band.NONE, false);
    }
    private CycloneThunder() {}

    public static int nextDelay(CycloneStage stage, CycloneSeverity severity, RandomGenerator random) {
        return switch (stage) {
            case BOMBOGENESIS -> random.nextInt(1000, 1401);
            case ARRIVAL -> random.nextInt(200, 401);
            case PEAK -> switch (severity) {
                case MODERATE -> random.nextInt(160, 241);
                case SEVERE -> random.nextInt(80, 161);
                case CATASTROPHIC -> random.nextInt(40, 101);
            };
            case DISSIPATION -> random.nextInt(600, 1001);
            default -> Integer.MAX_VALUE;
        };
    }

    public static Cue choose(CycloneStage stage, CycloneSeverity severity, float intensity, RandomGenerator random) {
        return switch (stage) {
            case BOMBOGENESIS -> new Cue(Band.DISTANT, random.nextFloat() < .45f);
            case ARRIVAL -> new Cue(intensity > .5f && random.nextFloat() < .45f ? Band.MID : Band.DISTANT, false);
            case PEAK -> {
                float roll = random.nextFloat();
                float close = switch (severity) { case MODERATE -> .30f; case SEVERE -> .45f; case CATASTROPHIC -> .60f; };
                yield new Cue(intensity > .5f && roll < close ? Band.CLOSE : roll < .85f ? Band.MID : Band.DISTANT, false);
            }
            case DISSIPATION -> new Cue(Band.DISTANT, false);
            default -> Cue.NONE;
        };
    }

    /** Spatial edge and collapse fading also attenuate sound, rather than stopping at a hard boundary. */
    public static float gain(CycloneStage stage, float intensity, float trackWeight, float surface, float exposure) {
        float strength = stage == CycloneStage.BOMBOGENESIS ? trackWeight : intensity;
        return Math.clamp(strength, 0, 1) * Math.clamp(surface, 0, 1)
                * (.25f + .75f * Math.clamp(exposure, 0, 1));
    }

    /** Simulation-time deadlines prevent duplicate ticks and never replay a missed backlog. */
    public static final class Schedule {
        private long seed, start, next, lastTime;
        private CycloneStage stage = CycloneStage.NONE;
        private Random random;
        public void clear() { stage = CycloneStage.NONE; random = null; }
        public Cue tick(CycloneStorm storm, CycloneSample sample, long time, boolean audible) {
            if (storm == null || !audible || switch (sample.stage()) {
                case BOMBOGENESIS, ARRIVAL, PEAK, DISSIPATION -> false;
                default -> true;
            }) { clear(); return Cue.NONE; }
            if (random == null || seed != storm.seed() || start != storm.startGameTime()
                    || stage != sample.stage() || time < lastTime - 200 || time - lastTime > 200) {
                seed = storm.seed(); start = storm.startGameTime(); stage = sample.stage();
                random = new Random(seed ^ start ^ (stage.ordinal() * 0x9e3779b97f4a7c15L));
                next = time + nextDelay(stage, storm.severity(), random);
                lastTime = time;
                return Cue.NONE;
            }
            // Network clock corrections of a tick or two must not continually re-arm the timer.
            if (time <= lastTime) return Cue.NONE;
            lastTime = time;
            if (time < next) return Cue.NONE;
            var cue = choose(stage, storm.severity(), sample.intensity(), random);
            next = time + nextDelay(stage, storm.severity(), random);
            return cue;
        }
    }
}
