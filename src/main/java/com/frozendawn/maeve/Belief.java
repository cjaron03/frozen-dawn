package com.frozendawn.maeve;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class Belief {
    final String pattern;
    private double confidence;
    private int evidence;
    private int contradictions;
    private long lastConfirmed = -1;
    private long updated;
    private long lastObserved;
    private UUID contributionEncounter;
    private boolean supported;
    private boolean contradicted;
    private final Deque<ObservedEvidence> provenance = new ArrayDeque<>();

    Belief(String pattern) {
        this.pattern = pattern;
    }

    boolean record(ObservedEvidence observation) {
        return record(observation, observation.supporting() ? BeliefPolicy.SUPPORT : -BeliefPolicy.CONTRADICTION);
    }

    boolean record(ObservedEvidence observation, double adjustment) {
        lastObserved = Math.max(lastObserved, observation.time());
        if (!observation.encounter().equals(contributionEncounter)) {
            contributionEncounter = observation.encounter();
            supported = false;
            contradicted = false;
        }
        if (observation.supporting() ? supported : contradicted) {
            if (observation.supporting()) {
                // A repeated witness can verify freshness without earning more confidence.
                confidence = currentConfidence(observation.time());
                updated = observation.time();
                lastConfirmed = observation.time();
                provenance.removeIf(e -> e.supporting() && e.encounter().equals(observation.encounter()));
                retain(observation);
            }
            return false;
        }
        confidence = BeliefPolicy.clamp(currentConfidence(observation.time()) + adjustment);
        updated = observation.time();
        if (observation.supporting()) {
            supported = true;
            evidence = BeliefPolicy.increment(evidence);
            lastConfirmed = observation.time();
        } else {
            contradicted = true;
            contradictions = BeliefPolicy.increment(contradictions);
        }
        retain(observation);
        return true;
    }

    private void retain(ObservedEvidence observation) {
        if (provenance.size() == BeliefPolicy.MAX_PROVENANCE) provenance.removeFirst();
        provenance.addLast(observation);
    }

    double currentConfidence(long now) {
        return BeliefPolicy.decay(confidence, updated, lastConfirmed, now);
    }

    long lastObserved() { return lastObserved; }

    MaeveDirector.BeliefSnapshot snapshot(long now) {
        long age = lastConfirmed < 0 ? -1 : Math.max(0, now - lastConfirmed);
        return new MaeveDirector.BeliefSnapshot(pattern, currentConfidence(now), evidence, contradictions,
                lastConfirmed, lastObserved, age, age >= BeliefPolicy.STALE_AFTER,
                confidence, updated, now,
                provenance.stream().map(ObservedEvidence::snapshot).toList());
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("pattern", pattern);
        tag.putDouble("confidence", confidence);
        tag.putInt("evidence", evidence);
        tag.putInt("contradictions", contradictions);
        tag.putLong("lastConfirmed", lastConfirmed);
        tag.putLong("updated", updated);
        tag.putLong("lastObserved", lastObserved);
        if (contributionEncounter != null) tag.putUUID("contributionEncounter", contributionEncounter);
        tag.putBoolean("supported", supported);
        tag.putBoolean("contradicted", contradicted);
        ListTag history = new ListTag();
        provenance.forEach(entry -> history.add(entry.save()));
        tag.put("provenance", history);
        return tag;
    }

    static Belief load(CompoundTag tag) {
        String pattern = tag.getString("pattern");
        if (!pattern.matches("[A-Z][A-Z0-9_]{0,63}")) return null;
        Belief belief = new Belief(pattern);
        belief.confidence = BeliefPolicy.clamp(tag.getDouble("confidence"));
        belief.evidence = Math.max(0, tag.getInt("evidence"));
        belief.contradictions = Math.max(0, tag.getInt("contradictions"));
        belief.lastConfirmed = tag.contains("lastConfirmed") ? Math.max(-1, tag.getLong("lastConfirmed")) : -1;
        belief.updated = Math.max(0, tag.getLong("updated"));
        belief.lastObserved = Math.max(0, tag.getLong("lastObserved"));
        belief.contributionEncounter = tag.hasUUID("contributionEncounter") ? tag.getUUID("contributionEncounter") : null;
        belief.supported = tag.getBoolean("supported");
        belief.contradicted = tag.getBoolean("contradicted");
        ListTag history = tag.getList("provenance", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, history.size() - BeliefPolicy.MAX_PROVENANCE); i < history.size(); i++) {
            ObservedEvidence entry = ObservedEvidence.load(history.getCompound(i));
            if (entry != null) belief.provenance.addLast(entry);
        }
        return belief.provenance.isEmpty() ? null : belief;
    }
}
