package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WorldDiagnostics {
    private WorldDiagnostics() { }
    static List<String> format(WorldModel world, long now) {
        if (world == null) return List.of("WORLD: no observed locations; unobserved regions are UNKNOWN.");
        var lines = new ArrayList<String>();
        var points = world.snapshot(now);
        lines.add("WORLD: " + points.size() + " retained event-labelled points; no structure classification.");
        points.stream().map(MaeveDirector.WorldPointSnapshot::dimension).distinct().sorted().forEach(dimension ->
                lines.add("SHELTER CENTROID: " + dimension + " " + world.center(dimension) + " (observed covered samples only)"));
        for (var p : points) {
            lines.add(String.format(Locale.ROOT, "%s %s %s state=%s confidence=%.4f evidence=%d contradictions=%d ageTicks=%d",
                    p.label(), p.dimension(), p.position().toShortString(), p.state(), p.confidence(), p.evidence(), p.contradictions(), Math.max(0, now - p.observedAt())));
            if (p.state().equals("BLOCKED")) lines.add("  REPLACEMENT: observed obstruction; former open confidence=" + p.previousConfidence());
            for (var event : p.provenance()) lines.add("  " + event.action() + " tick=" + event.time() + " observer=" + event.observer()
                    + " encounter=" + event.encounter() + " at=" + event.position().toShortString());
        }
        lines.add("UNKNOWN (derived from unobserved 16-block cells): " + world.unknown());
        return List.copyOf(lines);
    }
}
