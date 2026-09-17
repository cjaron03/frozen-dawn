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
            lines.add("DIRECTOR OBJECTIVE: no position directive available; ordinary local behavior applies.");
            lines.add("SELECTED STRATEGY: none; low confidence only biases available utility actions.");
        } else {
            lines.add("DIRECTOR OBJECTIVE: test a historical " + selected.pattern() + " prediction by holding a recoverable position.");
            lines.add("SELECTED STRATEGY: " + (selected.cover() == null ? "WATCH_LAST_RECOVERY_POINT" : "HOLD_RANGED_COVER")
                    + " | recoveryCost=" + selected.recoveryCost());
            lines.add("POSITION DIRECTIVE: observer=" + selected.observer() + " subject=" + selected.player()
                    + " position=" + selected.position().toShortString() + " dimension=" + selected.evidence().dimension()
                    + " cover=" + selected.cover());
            lines.add("INHERITED EVIDENCE: " + selected.evidence().action() + " tick=" + selected.evidence().time()
                    + " observer=" + selected.evidence().observer() + " encounter=" + selected.evidence().encounter()
                    + " anchor=" + selected.evidence().position().toShortString());
            lines.add("EXECUTION: started=" + selected.startedAt() + " arrived=" + selected.arrivedAt()
                    + " holdUntil=" + selected.holdUntil() + " contradicted=" + selected.contradictedAt()
                    + "; hold=" + CommitmentPolicy.HOLD_TICKS + " ticks; wrong-beat minimum=" + CommitmentPolicy.WRONG_BEAT_TICKS);
        }
        lines.add("ARCHITECT PACKET: full reconnaissance packets are not implemented; only the bounded position directive above is issued.");
        return List.copyOf(lines);
    }
}
