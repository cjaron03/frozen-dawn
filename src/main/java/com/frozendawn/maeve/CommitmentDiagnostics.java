package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Explains the selected bet and rejected alternatives without advancing execution. */
final class CommitmentDiagnostics {
    private CommitmentDiagnostics() { }

    static List<String> format(MaeveDirector.CommitmentSnapshot view) {
        List<String> lines = new ArrayList<>();
        lines.add("COMMITMENT: " + view.outcome() + " | encounter=" + view.encounter() + " | betUsed=" + view.issued());
        lines.add("POLICY: threshold=" + CommitmentPolicy.THRESHOLD + "; one issued bet per encounter; cheapest recovery wins.");
        lines.add("COOLDOWN: this encounter=" + view.blocked() + "; next encounter=" + view.blockNext());
        for (var hint : view.hints()) {
            lines.add(String.format(Locale.ROOT, "HISTORICAL BASIS: %s confidence=%.4f (frozen before this encounter; still decays), support tick=%d",
                    hint.pattern(), hint.confidence(), hint.evidence().time()));
        }
        view.alternatives().forEach(alternative -> lines.add("CANDIDATE: " + alternative));
        var selected = view.selected();
        if (selected == null) {
            lines.add("POSITION OBJECTIVE: no position directive available; see MACS reconnaissance for survey missions.");
            lines.add("POSITION STRATEGY: none; low confidence may still bias local utility actions.");
        } else {
            lines.add("DIRECTOR OBJECTIVE: test a historical " + selected.pattern() + " prediction by holding a recoverable position.");
            lines.add("SELECTED STRATEGY: " + (selected.spatial() != null ? "WATCH_ACCESS_POINT" : selected.cover() == null ? "WATCH_LAST_RECOVERY_POINT" : "HOLD_RANGED_COVER")
                    + " | recoveryCost=" + selected.recoveryCost());
            lines.add("POSITION DIRECTIVE: observer=" + selected.observer() + " subject=" + selected.player()
                    + " position=" + selected.position().toShortString() + " dimension=" + selected.evidence().dimension()
                    + " cover=" + selected.cover());
            if (selected.spatial() != null) lines.add("OBSERVED CROSSING: " + selected.spatial().inside().toShortString()
                    + " -> " + selected.spatial().outside().toShortString() + "; discovered obstruction=" + selected.obstruction());
            lines.add("INHERITED EVIDENCE: " + selected.evidence().action() + " tick=" + selected.evidence().time()
                    + " observer=" + selected.evidence().observer() + " encounter=" + selected.evidence().encounter()
                    + " anchor=" + selected.evidence().position().toShortString());
            lines.add("EXECUTION: started=" + selected.startedAt() + " arrived=" + selected.arrivedAt()
                    + " holdUntil=" + selected.holdUntil() + " contradicted=" + selected.contradictedAt()
                    + "; hold=" + CommitmentPolicy.HOLD_TICKS + " ticks; wrong-beat minimum=" + CommitmentPolicy.WRONG_BEAT_TICKS);
        }
        lines.add("RECONNAISSANCE PACKETS: listed separately under MACS reconnaissance, including completed mission reports.");
        return List.copyOf(lines);
    }
}
