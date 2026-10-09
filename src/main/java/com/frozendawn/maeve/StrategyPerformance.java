package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Bounded, lagged results of authored position holds, separated from belief confidence. */
final class StrategyPerformance {
    static final int MAX_CONTEXTS = 8, MAX_RESULTS = 8, FAILURE_COOLDOWN = 2;
    record Key(String pattern, String dimension) { }
    record Baseline(double effectiveness, boolean deferred) { }
    record Result(ObservedEvidence evidence, String outcome, float dealt, float received, float blocked) { }
    private static final class Context {
        int uses, successes, failures, unknown, consecutiveFailures, cooldown;
        long lastUsed;
        final List<Result> results = new ArrayList<>();
    }
    private final Map<Key, Context> contexts = new LinkedHashMap<>();
    private final Map<Key, Baseline> baseline = new LinkedHashMap<>();
    private MaeveDirector.PositionDirective pending;
    private long now;
    private boolean arrived;
    private float dealt, received, blocked;
    private ObservedEvidence latest;

    void begin(long time) {
        clock(time); finish("ENCOUNTER_ENDED"); baseline.clear();
        contexts.forEach((key, value) -> {
            baseline.put(key, new Baseline(effectiveness(value, time), value.cooldown > 0));
            value.cooldown = Math.max(0, value.cooldown - 1);
        });
    }

    private static double effectiveness(Context value, long time) {
        double wins = 0, losses = 0;
        for (var result : value.results) {
            double weight = Math.pow(.5, Math.max(0, time - result.evidence().time() - BeliefPolicy.STALE_AFTER)
                    / (double) BeliefPolicy.HALF_LIFE);
            if (result.outcome().equals("SUCCESS")) wins += weight;
            if (result.outcome().equals("FAILURE")) losses += weight;
        }
        return (1 + wins) / (2 + wins + losses);
    }

    double multiplier(String pattern, String dimension) {
        return .5 + baseline.getOrDefault(new Key(pattern, dimension), new Baseline(.5, false)).effectiveness();
    }
    boolean deferred(String pattern, String dimension) {
        return baseline.getOrDefault(new Key(pattern, dimension), new Baseline(.5, false)).deferred();
    }
    void clock(long time) { now = time; }
    void start(MaeveDirector.PositionDirective directive) {
        pending = directive; now = directive.startedAt(); arrived = false; dealt = received = blocked = 0; latest = null;
        var key = new Key(CounterVariantPolicy.key(directive), directive.evidence().dimension());
        if (!contexts.containsKey(key) && contexts.size() >= MAX_CONTEXTS) {
            var oldest = contexts.entrySet().stream().min(Comparator.comparingLong((Map.Entry<Key, Context> e) -> e.getValue().lastUsed)
                    .thenComparing(e -> e.getKey().pattern()).thenComparing(e -> e.getKey().dimension())).orElseThrow().getKey();
            contexts.remove(oldest);
        }
        var value = contexts.computeIfAbsent(key, unused -> new Context());
        value.uses = increment(value.uses); value.lastUsed = now;
    }
    void arrived(long time) { clock(time); arrived = pending != null; }
    void damage(UUID actor, UUID player, String dimension, BlockPos position, long time, float amount, boolean outgoing) {
        if (pending == null || !arrived || !pending.observer().equals(actor) || !pending.player().equals(player)
                || !pending.evidence().dimension().equals(dimension) || !(amount > 0) || !Float.isFinite(amount)) return;
        clock(time);
        if (outgoing) dealt = Math.min(10000, dealt + amount); else received = Math.min(10000, received + amount);
        latest = new ObservedEvidence(actor, pending.encounter(), dimension, position, time,
                outgoing ? "HOLD_DEALT_FINAL_DAMAGE" : "HOLD_RECEIVED_FINAL_DAMAGE", outgoing);
    }
    void block(UUID actor, UUID player, String dimension, BlockPos position, long time, float amount) {
        if (pending == null || !arrived || !pending.pattern().equals(BeliefStore.SWORD)
                || !pending.observer().equals(actor) || !pending.player().equals(player)
                || !pending.evidence().dimension().equals(dimension) || !(amount > 0) || !Float.isFinite(amount)) return;
        clock(time); blocked = Math.min(10000, blocked + amount);
        latest = new ObservedEvidence(actor, pending.encounter(), dimension, position, time, "GUARD_BLOCKED_DAMAGE", true);
    }

    void finish(String reason) {
        if (pending == null) return;
        boolean interrupted = reason.equals("RELOAD_RELEASED") || reason.equals("ENCOUNTER_ENDED")
                || reason.contains("EVICT") || reason.contains("UNLOAD") || reason.contains("UNAVAILABLE") || reason.equals("UNOBSERVED_DAMAGE") || reason.equals("ERASED");
        String outcome = interrupted || latest == null ? "UNKNOWN" : (reason.equals("OWNER_KILLED") || reason.equals("SHIELD_DISABLED") || reason.equals("SHIELD_BROKEN")) ? "FAILURE" : dealt + blocked > received ? "SUCCESS" : "FAILURE";
        var value = contexts.get(new Key(CounterVariantPolicy.key(pending), pending.evidence().dimension()));
        if (outcome.equals("SUCCESS")) { value.successes = increment(value.successes); value.consecutiveFailures = 0; }
        else if (outcome.equals("FAILURE")) {
            value.failures = increment(value.failures); value.consecutiveFailures = Math.min(2, value.consecutiveFailures + 1);
            if (value.consecutiveFailures >= 2) value.cooldown = FAILURE_COOLDOWN;
        } else value.unknown = increment(value.unknown);
        var event = latest == null ? new ObservedEvidence(pending.observer(), pending.encounter(), pending.evidence().dimension(),
                pending.position(), Math.max(0, now), "HOLD_ENDED_" + reason, false) : latest;
        value.results.add(new Result(event, outcome, dealt, received, blocked));
        if (value.results.size() > MAX_RESULTS) value.results.removeFirst();
        pending = null; latest = null;
    }

    List<String> diagnostics(long time) {
        var lines = new ArrayList<String>();
        lines.add("STRATEGY PERFORMANCE | contexts=" + contexts.size() + " pending=" + (pending == null ? "none" : CounterVariantPolicy.key(pending))
                + " | success=surviving positive witnessed damage trade (plus actual prevented shield damage); silence/interruption=unknown");
        contexts.forEach((key, c) -> {
            lines.add(String.format(Locale.ROOT, "%s context=%s uses=%d success=%d failure=%d unknown=%d effectiveness=%.4f frozenMultiplier=%.4f deferred=%s lastUsed=%d",
                    key.pattern(), key.dimension(), c.uses, c.successes, c.failures, c.unknown, effectiveness(c, time),
                    multiplier(key.pattern(), key.dimension()), deferred(key.pattern(), key.dimension()), c.lastUsed));
            c.results.forEach(r -> lines.add("  result=" + r.outcome() + " dealt=" + r.dealt() + " received=" + r.received()
                    + " blocked=" + r.blocked() + " observer=" + r.evidence().observer() + " encounter=" + r.evidence().encounter()
                    + " position=" + r.evidence().position().toShortString() + " time=" + r.evidence().time() + " action=" + r.evidence().action()));
        });
        return List.copyOf(lines);
    }

    CompoundTag save() {
        var tag = new CompoundTag(); var list = new ListTag();
        contexts.forEach((key, c) -> {
            var row = new CompoundTag(); row.putString("pattern", key.pattern()); row.putString("dimension", key.dimension());
            row.putInt("uses", c.uses); row.putInt("successes", c.successes); row.putInt("failures", c.failures); row.putInt("unknown", c.unknown);
            row.putInt("consecutiveFailures", c.consecutiveFailures); row.putInt("cooldown", c.cooldown); row.putLong("lastUsed", c.lastUsed);
            var frozen = baseline.getOrDefault(key, new Baseline(.5, false));
            row.putDouble("frozen", frozen.effectiveness()); row.putBoolean("deferred", frozen.deferred());
            var results = new ListTag();
            c.results.forEach(r -> { var entry = r.evidence().save(); entry.putString("outcome", r.outcome());
                entry.putFloat("dealt", r.dealt()); entry.putFloat("received", r.received()); entry.putFloat("blocked", r.blocked()); results.add(entry); });
            row.put("results", results); list.add(row);
        });
        tag.put("contexts", list);
        // A saved unfinished use becomes UNKNOWN; local execution never resumes.
        if (pending != null) {
            var unfinished = new ObservedEvidence(pending.observer(), pending.encounter(), pending.evidence().dimension(),
                    pending.position(), Math.max(0, now), "HOLD_ENDED_RELOAD_RELEASED", false).save();
            unfinished.putString("pattern", CounterVariantPolicy.key(pending)); tag.put("unfinished", unfinished);
        }
        return tag;
    }

    static StrategyPerformance load(CompoundTag tag) {
        var memory = new StrategyPerformance();
        for (Tag raw : tag.getList("contexts", Tag.TAG_COMPOUND)) {
            if (memory.contexts.size() == MAX_CONTEXTS) break;
            var row = (CompoundTag) raw; var key = new Key(row.getString("pattern"), row.getString("dimension"));
            if (!CounterVariantPolicy.validKey(key.pattern())
                    || net.minecraft.resources.ResourceLocation.tryParse(key.dimension()) == null) continue;
            var c = new Context(); c.uses = Math.max(0, row.getInt("uses")); c.successes = Math.max(0, row.getInt("successes"));
            c.failures = Math.max(0, row.getInt("failures")); c.unknown = Math.max(0, row.getInt("unknown"));
            c.consecutiveFailures = Math.clamp(row.getInt("consecutiveFailures"), 0, 2);
            c.cooldown = Math.clamp(row.getInt("cooldown"), 0, FAILURE_COOLDOWN); c.lastUsed = Math.max(0, row.getLong("lastUsed"));
            for (Tag result : row.getList("results", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) result; var event = ObservedEvidence.load(entry); String outcome = entry.getString("outcome");
                if (event == null || !List.of("SUCCESS", "FAILURE", "UNKNOWN").contains(outcome)) continue;
                c.results.add(new Result(event, outcome, safeDamage(entry.getFloat("dealt")), safeDamage(entry.getFloat("received")), safeDamage(entry.getFloat("blocked"))));
                if (c.results.size() > MAX_RESULTS) c.results.removeFirst();
            }
            memory.contexts.put(key, c); memory.baseline.put(key, new Baseline(BeliefPolicy.clamp(row.getDouble("frozen")), row.getBoolean("deferred")));
        }
        var unfinished = tag.getCompound("unfinished"); var event = ObservedEvidence.load(unfinished);
        var c = memory.contexts.get(new Key(unfinished.getString("pattern"), unfinished.getString("dimension")));
        if (event != null && c != null) {
            c.unknown = increment(c.unknown); c.results.add(new Result(event, "UNKNOWN", 0, 0, 0));
            if (c.results.size() > MAX_RESULTS) c.results.removeFirst();
        }
        return memory;
    }
    private static float safeDamage(float value) { return Float.isFinite(value) ? Math.clamp(value, 0, 10000) : 0; }
    private static int increment(int value) { return value == Integer.MAX_VALUE ? value : value + 1; }
}
