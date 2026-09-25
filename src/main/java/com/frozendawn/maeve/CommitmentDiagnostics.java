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
            lines.add("DIRECTOR OBJECTIVE: test a historical " + selected.pattern() + " prediction through a bounded, recoverable commitment.");
            lines.add("SELECTED STRATEGY: " + (selected.advancingCover() ? "ADVANCE_RANGED_MANTLET" : selected.pattern().equals(BeliefStore.SWORD) ? "GUARD_SWORD" : selected.pattern().equals(BeliefStore.PURSUIT) ? "WITHDRAW_AND_HOLD" : selected.spatial() != null ? "WATCH_ACCESS_POINT" : selected.cover() == null ? "WATCH_LAST_RECOVERY_POINT" : "FIGHT_WITH_RANGED_COVER")
                    + " | recoveryCost=" + selected.recoveryCost());
            lines.add("POSITION DIRECTIVE: observer=" + selected.observer() + " subject=" + selected.player()
                    + " position=" + selected.position().toShortString() + " dimension=" + selected.evidence().dimension()
                    + " cover=" + selected.cover());
            if (selected.pattern().equals(BeliefStore.SWORD)) lines.add("SWORD GUARD: same shield for this encounter; recovery suspends guarding; 35 guarding / 25 exposed ticks; frontal only; guarding stops attacks and movement; axe disable=100 ticks.");
            if (selected.advancingCover()) lines.add("MANTLET: threshold=0.90; fixed local front; 2x2 packed ice; at most 5 screens / 20 placements in a separate pool; 10 ticks per block, 80 ticks between screens; slow advance; open flanks; broken cover is not repaired.");
            else if (selected.pattern().equals(BeliefStore.RANGED)) lines.add("PILLAR: build once, then resume local pursuit and melee; no stationary hold; spent ice and exposed flanks are the cost; no replacement commitment.");
            if (selected.spatial() != null) lines.add("OBSERVED CROSSING: " + selected.spatial().inside().toShortString()
                    + " -> " + selected.spatial().outside().toShortString() + "; discovered obstruction=" + selected.obstruction());
            lines.add("INHERITED EVIDENCE: " + selected.evidence().action() + " tick=" + selected.evidence().time()
                    + " observer=" + selected.evidence().observer() + " encounter=" + selected.evidence().encounter()
                    + " anchor=" + selected.evidence().position().toShortString());
            lines.add("EXECUTION: started=" + selected.startedAt() + " arrived=" + selected.arrivedAt()
                    + " holdUntil=" + selected.holdUntil() + " contradicted=" + selected.contradictedAt()
                    + (selected.pattern().equals(BeliefStore.SWORD) ? "; lifetime=encounter; contact timeout=" + BeliefPolicy.ENCOUNTER_GAP
                    : selected.pattern().equals(BeliefStore.RANGED) && !selected.advancingCover()
                    ? "; combat outcome window=" + CommitmentPolicy.HOLD_TICKS + " ticks; movement is not held"
                    : "; hold=" + CommitmentPolicy.HOLD_TICKS + " ticks; wrong-beat minimum=" + CommitmentPolicy.WRONG_BEAT_TICKS));
        }
        lines.add("RECONNAISSANCE PACKETS: listed separately under MACS reconnaissance, including completed mission reports.");
        return List.copyOf(lines);
    }
}
