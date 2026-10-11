package com.frozendawn.entity.blind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Bounded, immutable sensed regions. Subject IDs never refresh a position. */
public final class BlindSignalMemory {
    public enum Sense { ECHO, HEARING, VIBRATION }
    public record Signal(Sense sense, BlockPos region, int uncertainty, double confidence,
                         double travel, long observed, UUID subject, double qualityCeiling) {
        public Signal(Sense sense, BlockPos region, int uncertainty, double confidence,
                      double travel, long observed, UUID subject) {
            this(sense,region,uncertainty,confidence,travel,observed,subject,1);
        }
        public Signal { region = region.immutable(); }
        public boolean fresh(long now) { return now >= observed && now - observed <= BlindArchitectPolicy.FRESH_TICKS; }
        public double confidenceAt(long now) {
            return Math.max(0, confidence - Math.max(0, now - observed - 40) * .003);
        }
        public boolean lock(long now) { return sense == Sense.ECHO && subject != null && fresh(now) && confidenceAt(now) >= BlindArchitectPolicy.LOCK; }
    }
    private final List<Signal> signals = new ArrayList<>();
    public void clear() { signals.clear(); }
    public List<Signal> snapshot() { return List.copyOf(signals); }
    public void add(Signal next) {
        for (int i = 0; i < signals.size(); i++) {
            Signal old = signals.get(i);
            if (old.sense != next.sense || old.region.distSqr(next.region) > Math.pow((old.uncertainty + next.uncertainty) / 2.0, 2)) continue;
            double confidence = next.confidence;
            if (next.sense == Sense.ECHO && next.observed > old.observed && next.observed - old.observed <= 80)
                confidence = Math.min(next.qualityCeiling, Math.max(confidence, Math.min(.80, old.confidence + .15)));
            signals.set(i, new Signal(next.sense, next.region, next.uncertainty, confidence, next.travel, next.observed, next.subject,next.qualityCeiling));
            return;
        }
        if (signals.size() == BlindArchitectPolicy.MAX_TRACKS)
            signals.remove(signals.stream().min(Comparator.comparingLong(Signal::observed)).orElseThrow());
        signals.add(next);
    }
    public Signal select(BlockPos actor, long now) {
        signals.removeIf(s -> now < s.observed || now - s.observed > BlindArchitectPolicy.SEARCH_TICKS);
        // A fresh confirmed return takes precedence over anonymous decoys. Among peers, use nearest region.
        return signals.stream().filter(s -> s.confidenceAt(now) > .05)
                .min(Comparator.comparingInt((Signal s) -> s.lock(now) ? 0 : s.sense == Sense.VIBRATION ? 2 : 1)
                        .thenComparingDouble(s -> actor.distSqr(s.region))
                        .thenComparing(Comparator.comparingDouble(Signal::confidence).reversed())
                        .thenComparing(Comparator.comparingLong(Signal::observed).reversed())
                        .thenComparingLong(s -> s.region.asLong())).orElse(null);
    }
    public void miss(Signal signal) { signals.remove(signal); }
}
