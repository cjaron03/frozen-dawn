package com.frozendawn.maeve;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** One frozen, bounded roster. Casualties cannot create more hotspot evidence. */
final class ConvergenceGroup {
    final UUID id, hotspot;
    final String dimension;
    final BlockPos destination;
    final int cycle;
    final long cloudAt;
    final Map<UUID, BlockPos> donors = new LinkedHashMap<>();
    final Set<UUID> dead = new LinkedHashSet<>();
    long firstArrival = -1, contactAt = -1, evaluatedAt;
    CompoundTag history = new CompoundTag();
    UUID contactPawn, contactPlayer;
    BlockPos contactPosition;

    ConvergenceGroup(UUID id, Hotspot h, Map<UUID, BlockPos> donors, long now) {
        this(id, h.id, h.dimension, h.anchor, h.cycle, donors, now);
        history.putInt("lifetimeDeaths", h.deaths); history.putInt("completedEncounters", h.encounters);
        history.putDouble("weight", h.weight(now)); history.putInt("priorWipes", h.wipes);
        history.putLong("lastEvidence", h.lastObserved);
        for (var score : ConvergencePolicy.scores(h.avoid, h.wipes, h.weight(now))) history.putDouble(score.action(), score.total());
        var deaths = new ListTag();
        for (var d : h.evidence) {
            var row = new CompoundTag(); row.putUUID("pawn", d.pawn()); row.putUUID("encounter", d.encounter());
            row.putLong("position", d.position().asLong()); row.putLong("time", d.time()); deaths.add(row);
        }
        history.put("evidence", deaths);
    }
    private ConvergenceGroup(UUID id, UUID hotspot, String dimension, BlockPos destination, int cycle, Map<UUID, BlockPos> donors, long now) {
        if (donors.size() < 2 || donors.size() > 3) throw new IllegalArgumentException("A convergence has two or three pawns");
        this.id = id; this.hotspot = hotspot; this.dimension = dimension; this.destination = destination.immutable(); this.cycle = cycle;
        donors.forEach((key, pos) -> this.donors.put(key, pos.immutable())); cloudAt = evaluatedAt = now;
    }
    boolean owns(UUID pawn) { return donors.containsKey(pawn); }
    boolean warningComplete(long now) { return now - cloudAt >= ConvergencePolicy.WARNING; }
    boolean death(UUID pawn) { return owns(pawn) && dead.add(pawn); }
    boolean allDead() { return dead.size() == donors.size(); }
    void contact(UUID pawn, UUID player, BlockPos position, long now) {
        if (contactAt >= 0 || !owns(pawn) || !warningComplete(now)) return;
        if (dead.contains(pawn)) return;
        contactPawn = pawn; contactPlayer = player; contactPosition = position.immutable(); contactAt = now;
    }
    String completedOutcome() { return contactAt >= 0 ? "SUCCESS" : allDead() ? "WIPE" : "UNKNOWN"; }
    CompoundTag save() {
        var t = new CompoundTag(); t.putUUID("id", id); t.putUUID("hotspot", hotspot); t.putString("dimension", dimension);
        t.putLong("destination", destination.asLong()); t.putInt("cycle", cycle); t.putLong("cloudAt", cloudAt);
        t.putLong("firstArrival", firstArrival); t.putLong("evaluatedAt", evaluatedAt); t.putLong("contactAt", contactAt);
        if (contactPawn != null) { t.putUUID("contactPawn", contactPawn); t.putUUID("contactPlayer", contactPlayer); t.putLong("contactPosition", contactPosition.asLong()); }
        var list = new ListTag(); donors.forEach((id, pos) -> { var row = new CompoundTag(); row.putUUID("pawn", id); row.putLong("donor", pos.asLong()); row.putBoolean("dead", dead.contains(id)); list.add(row); });
        t.put("roster", list); t.put("decisionHistory", history.copy()); return t;
    }
    static ConvergenceGroup load(CompoundTag t) {
        if (!t.hasUUID("id") || !t.hasUUID("hotspot") || ResourceLocation.tryParse(t.getString("dimension")) == null) return null;
        Map<UUID, BlockPos> donors = new LinkedHashMap<>(); Set<UUID> dead = new LinkedHashSet<>();
        for (Tag raw : t.getList("roster", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw; if (!row.hasUUID("pawn")) continue;
            donors.put(row.getUUID("pawn"), BlockPos.of(row.getLong("donor"))); if (row.getBoolean("dead")) dead.add(row.getUUID("pawn"));
            if (donors.size() == 3) break;
        }
        if (donors.size() < 2) return null;
        var g = new ConvergenceGroup(t.getUUID("id"), t.getUUID("hotspot"), t.getString("dimension"), BlockPos.of(t.getLong("destination")), Math.max(0, t.getInt("cycle")), donors, Math.max(0, t.getLong("cloudAt")));
        var history = t.getCompound("decisionHistory");
        g.history.putInt("lifetimeDeaths", Math.max(0, history.getInt("lifetimeDeaths")));
        g.history.putInt("completedEncounters", Math.max(0, history.getInt("completedEncounters")));
        double weight = history.getDouble("weight"); g.history.putDouble("weight", Double.isFinite(weight) ? Math.clamp(weight, 0, 1_000_000) : 0);
        g.history.putInt("priorWipes", Math.clamp(history.getInt("priorWipes"), 0, 2));
        g.history.putLong("lastEvidence", Math.max(0, history.getLong("lastEvidence")));
        for (String action : java.util.List.of("CONVERGE", "AVOID")) {
            double score = history.getDouble(action); g.history.putDouble(action, Double.isFinite(score) ? score : 0);
        }
        var evidence = new ListTag();
        for (Tag raw : history.getList("evidence", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw; if (!row.hasUUID("pawn") || !row.hasUUID("encounter")) continue;
            var kept = new CompoundTag(); kept.putUUID("pawn", row.getUUID("pawn")); kept.putUUID("encounter", row.getUUID("encounter"));
            kept.putLong("position", row.getLong("position")); kept.putLong("time", Math.max(0, row.getLong("time"))); evidence.add(kept);
            if (evidence.size() == ConvergencePolicy.MAX_EVIDENCE) break;
        }
        g.history.put("evidence", evidence);
        g.dead.addAll(dead); g.firstArrival = t.getLong("firstArrival"); g.evaluatedAt = Math.max(g.cloudAt, t.getLong("evaluatedAt"));
        if (t.hasUUID("contactPawn") && t.hasUUID("contactPlayer") && g.owns(t.getUUID("contactPawn")) && t.getLong("contactAt") >= g.cloudAt + ConvergencePolicy.WARNING) {
            g.contactPawn = t.getUUID("contactPawn"); g.contactPlayer = t.getUUID("contactPlayer"); g.contactPosition = BlockPos.of(t.getLong("contactPosition")); g.contactAt = t.getLong("contactAt");
        }
        return g;
    }
}
