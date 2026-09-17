package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Bounded operator text only: no on-disk belief archive or gameplay consumers. */
final class DirectorDiagnostics {
    private DirectorDiagnostics() { }

    static List<String> format(MaeveDirector.Snapshot snapshot, UUID player) {
        List<String> lines = new ArrayList<>();
        lines.add("Maeve " + snapshot.lifecycle() + " | profiles=" + snapshot.profiles()
                + " beliefs=" + snapshot.beliefCount());
        if (player == null || !snapshot.lifecycle().equals("ACTIVE")) return List.copyOf(lines);
        lines.add("Player " + player + " | retained beliefs=" + snapshot.beliefs().size());
        for (var belief : snapshot.beliefs()) {
            lines.add(String.format(Locale.ROOT,
                    "%s confidence=%.4f evidence=%d contradictions=%d confirmed=%d observed=%d ageTicks=%d stale=%s",
                    belief.pattern(), belief.confidence(), belief.evidence(), belief.contradictions(),
                    belief.lastConfirmed(), belief.lastObserved(), belief.ageTicks(), belief.stale()));
            for (var event : belief.provenance()) {
                lines.add((event.supporting() ? "  SUPPORT " : "  CONTRADICTION ") + event.action()
                        + " tick=" + event.time() + " observer=" + event.observer()
                        + " encounter=" + event.encounter() + " at=" + event.dimension()
                        + " " + event.position().toShortString());
            }
        }
        return List.copyOf(lines);
    }
}
