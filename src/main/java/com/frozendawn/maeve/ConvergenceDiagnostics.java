package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ConvergenceDiagnostics {
    static List<String> format(ConvergenceMemory memory, long now, String decision) {
        if (memory == null) return List.of("PAWN CONVERGENCE | inactive/erased; no tactical history");
        var lines = new ArrayList<String>();
        lines.add("PAWN CONVERGENCE | hotspots=" + memory.hotspots.size() + " trackedPawns=" + memory.population.pawns.size() + " decision=" + decision);
        lines.add("history=lifetime >=6 deaths / >=3 completed encounters; recentWeight>=3.0; radius=24 halfLife=24000 cooldown=12000; dispatch deaths=outcomes only");
        for (var h : memory.hotspots.values()) {
            lines.add(String.format(Locale.ROOT, "HOTSPOT %s %s %s deaths=%d encounters=%d weight=%.4f eligibility=%s wipes=%d avoid=%s cycle=%d cooldownRemaining=%d reason=%s",
                    h.id, h.dimension, h.anchor.toShortString(), h.deaths, h.encounters, h.weight(now), h.eligibility(now), h.wipes, h.avoid, h.cycle, Math.max(0, h.cooldownUntil - now), h.reason));
            lines.add("  episode=" + h.encounter + " lastContact=" + h.lastContact + " lastEvidence=" + h.lastObserved);
            ConvergencePolicy.scores(h.avoid, h.wipes, h.weight(now)).forEach(score -> lines.add("  " + score.describe()));
            h.evidence.forEach(e -> lines.add("  death pawn=" + e.pawn() + " encounter=" + e.encounter() + " position=" + e.position().toShortString() + " tick=" + e.time() + " cause=UNKNOWN"));
            h.results.forEach(r -> lines.add("  result=" + r.getString("outcome") + " reason=" + r.getString("reason") + " dispatch=" + r.getUUID("id") + " cloudAt=" + r.getLong("cloudAt") + " firstArrival=" + r.getLong("firstArrival") + " slot=SIEGE contactPawn=" + (r.hasUUID("contactPawn") ? r.getUUID("contactPawn") : "none") + " contactPlayer=" + (r.hasUUID("contactPlayer") ? r.getUUID("contactPlayer") : "none") + " contactTick=" + r.getLong("contactAt") + " decisionHistory=" + r.getCompound("decisionHistory") + " roster=" + r.getList("roster", net.minecraft.nbt.Tag.TAG_COMPOUND)));
        }
        if (memory.active != null) {
            var g = memory.active;
            lines.add("  ACTIVE dispatch=" + g.id + " hotspot=" + g.hotspot + " slot=SIEGE cloudAt=" + g.cloudAt + " earliestArrival=" + (g.cloudAt + ConvergencePolicy.WARNING) + " firstArrival=" + g.firstArrival + " decisionHistory=" + g.history + " roster=" + g.donors + " dead=" + g.dead + " contactPawn=" + g.contactPawn + " contactPlayer=" + g.contactPlayer + " contactAt=" + g.contactAt);
        }
        return List.copyOf(lines);
    }
    private ConvergenceDiagnostics() { }
}
