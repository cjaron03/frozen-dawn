package com.frozendawn.maeve;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Regional tactical state lives in the same erasable save, never in a player/violation ledger. */
final class ConvergenceMemory {
    final Map<UUID, Hotspot> hotspots = new LinkedHashMap<>();
    final Map<UUID, Long> recentDeaths = new LinkedHashMap<>();
    final Map<UUID, Notice> notices = new LinkedHashMap<>();
    final PawnPopulation population = new PawnPopulation();
    ConvergenceGroup active;
    record Notice(int seen, long deliveredAt) { }

    Hotspot at(String dimension, BlockPos position) {
        return hotspots.values().stream().filter(h -> h.contains(dimension, position))
                .min(Comparator.comparingDouble((Hotspot h) -> h.anchor.distSqr(position)).thenComparing(h -> h.id)).orElse(null);
    }
    boolean death(UUID pawn, String dimension, BlockPos position, long now, boolean dispatched) {
        if (recentDeaths.containsKey(pawn)) return false;
        recentDeaths.put(pawn, now); if (recentDeaths.size() > 256) recentDeaths.remove(recentDeaths.keySet().iterator().next());
        if (dispatched) { if (active != null) active.death(pawn); return true; }
        Hotspot h = at(dimension, position);
        if (h == null) {
            if (hotspots.size() >= ConvergencePolicy.MAX_HOTSPOTS) {
                var victim = hotspots.values().stream().filter(p -> active == null || !active.hotspot.equals(p.id))
                        .min(Comparator.comparingLong((Hotspot p) -> p.lastObserved).thenComparing(p -> p.id)).orElseThrow();
                hotspots.remove(victim.id);
            }
            h = new Hotspot(UUID.randomUUID(), dimension, position); hotspots.put(h.id, h);
        }
        h.death(pawn, position, now); return true;
    }
    void finish(String outcome, String reason, long now) {
        if (active == null) return;
        Hotspot h = hotspots.get(active.hotspot); if (h != null) h.outcome(active, outcome, reason, now);
        active = null;
    }
    boolean notice(UUID player, long now) {
        Notice previous = notices.get(player);
        int seen = previous == null ? 0 : previous.seen();
        boolean deliver = seen < ConvergencePolicy.MESSAGE_LIMIT && (previous == null || now - previous.deliveredAt() >= ConvergencePolicy.COOLDOWN);
        if (notices.size() >= 128 && previous == null) notices.remove(notices.keySet().iterator().next());
        notices.put(player, new Notice(Math.min(ConvergencePolicy.MESSAGE_LIMIT, seen + 1), deliver ? now : previous.deliveredAt()));
        return deliver;
    }
    void clear() { hotspots.clear(); recentDeaths.clear(); notices.clear(); population.pawns.clear(); population.overflow = false; active = null; }
    CompoundTag save() {
        var t = new CompoundTag(); var list = new ListTag(); hotspots.values().forEach(h -> list.add(h.save())); t.put("hotspots", list);
        var deaths = new ListTag(); recentDeaths.forEach((id, time) -> { var r = new CompoundTag(); r.putUUID("pawn", id); r.putLong("time", time); deaths.add(r); }); t.put("recentDeaths", deaths);
        var notices = new ListTag(); this.notices.forEach((id, n) -> { var r = new CompoundTag(); r.putUUID("player", id); r.putInt("seen", n.seen()); r.putLong("deliveredAt", n.deliveredAt()); notices.add(r); }); t.put("notices", notices);
        t.put("population", population.save()); if (active != null) t.put("unfinished", active.save()); return t;
    }
    static ConvergenceMemory load(CompoundTag t) {
        var m = new ConvergenceMemory();
        for (Tag raw : t.getList("hotspots", Tag.TAG_COMPOUND)) { var h = Hotspot.load((CompoundTag) raw); if (h != null) m.hotspots.put(h.id, h); if (m.hotspots.size() == ConvergencePolicy.MAX_HOTSPOTS) break; }
        for (Tag raw : t.getList("recentDeaths", Tag.TAG_COMPOUND)) { var r = (CompoundTag) raw; if (r.hasUUID("pawn")) m.recentDeaths.put(r.getUUID("pawn"), Math.max(0, r.getLong("time"))); if (m.recentDeaths.size() == 256) break; }
        for (Tag raw : t.getList("notices", Tag.TAG_COMPOUND)) { var r = (CompoundTag) raw; if (r.hasUUID("player")) m.notices.put(r.getUUID("player"), new Notice(Math.clamp(r.getInt("seen"), 0, ConvergencePolicy.MESSAGE_LIMIT), Math.max(0, r.getLong("deliveredAt")))); if (m.notices.size() == 128) break; }
        m.population.load(t.getCompound("population"));
        m.active = ConvergenceGroup.load(t.getCompound("unfinished"));
        if (m.active != null) m.finish("UNKNOWN", "RELOAD_RELEASED", m.active.evaluatedAt);
        return m;
    }
}
