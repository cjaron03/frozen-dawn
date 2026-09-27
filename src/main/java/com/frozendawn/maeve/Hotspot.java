package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Fixed observed region, lifetime totals, decaying pressure and an independently latched response. */
final class Hotspot {
    record Death(UUID pawn, UUID encounter, BlockPos position, long time) { }
    final UUID id;
    final String dimension;
    final BlockPos anchor;
    final List<Death> evidence = new ArrayList<>();
    final List<CompoundTag> results = new ArrayList<>();
    int deaths, encounters, wipes, cycle;
    double weight;
    long weightedAt, lastObserved, lastContact, cooldownUntil;
    UUID encounter;
    boolean episodeHasDeath, avoid;
    String reason = "INSUFFICIENT_HISTORY";

    Hotspot(UUID id, String dimension, BlockPos anchor) {
        this.id = id; this.dimension = dimension; this.anchor = anchor.immutable();
    }
    boolean contains(String dim, BlockPos position) {
        return dimension.equals(dim) && anchor.distSqr(position) <= ConvergencePolicy.RADIUS * ConvergencePolicy.RADIUS;
    }
    double weight(long now) { return ConvergencePolicy.decay(weight, weightedAt, now); }
    void advance(long now) {
        if (avoid && weight(now) < ConvergencePolicy.ACTIVATION_WEIGHT) {
            avoid = false; wipes = 0; cycle = ConvergencePolicy.increment(cycle); reason = "DECAY_REARMED";
        }
        if (encounter != null && now >= lastContact && now - lastContact >= ConvergencePolicy.QUIET) {
            if (episodeHasDeath) encounters = ConvergencePolicy.increment(encounters);
            encounter = null; episodeHasDeath = false;
        }
    }
    void contact(long now) {
        advance(now);
        if (encounter == null) encounter = UUID.randomUUID();
        lastContact = now;
    }
    void death(UUID pawn, BlockPos position, long now) {
        // Rearm BEFORE fresh evidence, including when the area was unloaded while it cooled.
        contact(now); episodeHasDeath = true;
        deaths = ConvergencePolicy.increment(deaths);
        weight = Math.min(1_000_000, weight(now) + 1); weightedAt = now; lastObserved = now;
        evidence.add(new Death(pawn, encounter, position.immutable(), now));
        if (evidence.size() > ConvergencePolicy.MAX_EVIDENCE) evidence.removeFirst();
    }
    String eligibility(long now) {
        if (deaths < ConvergencePolicy.MIN_DEATHS || encounters < ConvergencePolicy.MIN_ENCOUNTERS) return "INSUFFICIENT_LIFETIME_HISTORY";
        if (weight(now) < ConvergencePolicy.ACTIVATION_WEIGHT) return "STALE_PRESSURE";
        if (avoid) return "AVOID_TWO_WIPES";
        if (encounter != null) return "LOCAL_ENCOUNTER_ACTIVE";
        if (now < cooldownUntil) return "HOTSPOT_COOLDOWN";
        return "ELIGIBLE";
    }
    void outcome(ConvergenceGroup group, String outcome, String cause, long now) {
        advance(now);
        // A group whose history cooled during travel cannot re-latch an obsolete policy cycle.
        if (cycle == group.cycle) {
            if (outcome.equals("SUCCESS")) wipes = 0;
            else if (outcome.equals("WIPE")) { wipes = Math.min(2, wipes + 1); avoid = wipes >= 2; }
        }
        cooldownUntil = Math.max(cooldownUntil, now + ConvergencePolicy.COOLDOWN);
        reason = outcome + ":" + cause;
        CompoundTag result = group.save(); result.putString("outcome", outcome); result.putString("reason", cause); result.putLong("ended", now);
        results.add(result); if (results.size() > ConvergencePolicy.MAX_RESULTS) results.removeFirst();
    }
    CompoundTag save() {
        var t = new CompoundTag(); t.putString("label", "DANGER_ZONE"); t.putUUID("id", id); t.putString("dimension", dimension); t.putLong("anchor", anchor.asLong());
        t.putInt("lifetimeDeaths", deaths); t.putInt("completedEncounters", encounters); t.putDouble("weight", weight);
        t.putLong("weightedAt", weightedAt); t.putLong("lastObserved", lastObserved); t.putLong("lastContact", lastContact);
        t.putLong("cooldownUntil", cooldownUntil); t.putInt("wipes", wipes); t.putInt("cycle", cycle); t.putBoolean("avoid", avoid);
        t.putString("reason", reason); t.putBoolean("episodeHasDeath", episodeHasDeath);
        if (encounter != null) t.putUUID("encounter", encounter);
        var ev = new ListTag(); for (var d : evidence) {
            var row = new CompoundTag(); row.putUUID("pawn", d.pawn()); row.putUUID("encounter", d.encounter());
            row.putLong("position", d.position().asLong()); row.putLong("time", d.time()); ev.add(row);
        }
        t.put("evidence", ev); var history = new ListTag(); results.forEach(r -> history.add(r.copy())); t.put("results", history); return t;
    }
    static Hotspot load(CompoundTag t) {
        if (!t.hasUUID("id") || ResourceLocation.tryParse(t.getString("dimension")) == null) return null;
        var h = new Hotspot(t.getUUID("id"), t.getString("dimension"), BlockPos.of(t.getLong("anchor")));
        h.deaths = Math.max(0, t.getInt("lifetimeDeaths")); h.encounters = Math.max(0, t.getInt("completedEncounters"));
        double w = t.getDouble("weight"); h.weight = Double.isFinite(w) ? Math.clamp(w, 0, 1_000_000) : 0;
        h.weightedAt = Math.max(0, t.getLong("weightedAt")); h.lastObserved = Math.max(0, t.getLong("lastObserved"));
        h.lastContact = Math.max(0, t.getLong("lastContact")); h.cooldownUntil = Math.max(0, t.getLong("cooldownUntil"));
        h.wipes = Math.clamp(t.getInt("wipes"), 0, 2); h.cycle = Math.max(0, t.getInt("cycle"));
        h.avoid = t.getBoolean("avoid") && h.wipes >= 2; h.reason = t.getString("reason");
        h.encounter = t.hasUUID("encounter") ? t.getUUID("encounter") : null;
        h.episodeHasDeath = h.encounter != null && t.getBoolean("episodeHasDeath");
        for (Tag raw : t.getList("evidence", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw;
            if (row.hasUUID("pawn") && row.hasUUID("encounter")) h.evidence.add(new Death(row.getUUID("pawn"), row.getUUID("encounter"), BlockPos.of(row.getLong("position")), Math.max(0, row.getLong("time"))));
            if (h.evidence.size() == ConvergencePolicy.MAX_EVIDENCE) break;
        }
        for (Tag raw : t.getList("results", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw; var g = ConvergenceGroup.load(row);
            if (g == null || !List.of("WIPE", "SUCCESS", "UNKNOWN").contains(row.getString("outcome"))) continue;
            var safe = g.save(); safe.putString("outcome", row.getString("outcome")); safe.putString("reason", row.getString("reason")); safe.putLong("ended", Math.max(0, row.getLong("ended"))); h.results.add(safe);
            if (h.results.size() == ConvergencePolicy.MAX_RESULTS) break;
        }
        return h;
    }
}
