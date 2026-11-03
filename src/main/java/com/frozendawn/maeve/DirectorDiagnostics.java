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
        lines.add(header(snapshot));
        if (player == null || !snapshot.lifecycle().equals("ACTIVE")) return List.copyOf(lines);
        lines.add("Player " + player + " | retained beliefs=" + snapshot.beliefs().size());
        for (var belief : snapshot.beliefs()) {
            if (ExitPrediction.parse(belief.pattern()) != null) lines.add("CONDITIONAL EXIT: " + BeliefDescriptions.meaning(belief.pattern()));
            lines.add(String.format(Locale.ROOT,
                    "%s confidence=%.4f evidence=%d contradictions=%d confirmed=%d observed=%d ageTicks=%d stale=%s",
                    belief.pattern(), belief.confidence(), belief.evidence(), belief.contradictions(),
                    belief.lastConfirmed(), belief.lastObserved(), belief.ageTicks(), belief.stale()));
            for (var event : belief.provenance()) {
                lines.add(event(event));
            }
        }
        return List.copyOf(lines);
    }

    static List<String> explain(MaeveDirector.Snapshot snapshot, UUID player, String pattern) {
        List<String> lines = new ArrayList<>();
        lines.add(header(snapshot));
        // An erased/dormant view has no tactical contents, even if a caller supplies a stale snapshot list.
        if (!snapshot.lifecycle().equals("ACTIVE")) return List.copyOf(lines);
        lines.add("Player " + player);
        var belief = snapshot.beliefs().stream().filter(b -> b.pattern().equals(pattern)).findFirst();
        if (belief.isEmpty()) {
            lines.add("No retained belief for " + pattern + ". No conclusion can be drawn from an empty record.");
            return List.copyOf(lines);
        }
        var value = belief.get();
        lines.add("BELIEF " + pattern + ": " + BeliefDescriptions.meaning(pattern));
        lines.add(String.format(Locale.ROOT, "CONFIDENCE %.4f / 1.0000 | heuristic score, not a calibrated probability",
                value.confidence()));
        lines.add("SUPPORT RULE: " + BeliefDescriptions.support(pattern));
        lines.add("CONTRADICTION RULE: " + BeliefDescriptions.contradiction(pattern));
        appendEvidence(lines, value, true);
        appendEvidence(lines, value, false);
        long retainedContributions = value.provenance().stream().filter(e -> e.confidenceWeight() != 0).count();
        long omitted = Math.max(0L, (long) value.evidence() + value.contradictions() - retainedContributions);
        lines.add("HISTORY: " + value.provenance().size() + "/" + BeliefPolicy.MAX_PROVENANCE
                + " retained entries; " + omitted + " counted contributions no longer retained.");
        lines.add("COUNTS: at most one contribution per polarity/pattern/player encounter, shared across witnesses.");
        lines.add("Repeated support refreshes confirmation; its latest VERIFY entry adds no count or confidence. The credited source is retained within the history cap.");
        lines.add("Encounter ends after " + BeliefPolicy.ENCOUNTER_GAP + " ticks without qualifying local contact.");
        appendConfidence(lines, value);
        lines.add("CURRENT UNCERTAINTY: " + (value.lastConfirmed() < 0 ? "Never confirmed by supporting evidence."
                : value.stale() ? "Stale: confirmation age has reached the decay grace period."
                : "Within the confirmation grace period; the hypothesis remains unproven."));
        lines.add(BeliefDescriptions.limitation(pattern));
        lines.add("Retained events explain observed support and contradictions; bounded history cannot reconstruct every past score change.");
        return List.copyOf(lines);
    }

    private static void appendEvidence(List<String> lines, MaeveDirector.BeliefSnapshot belief, boolean supporting) {
        var retained = belief.provenance().stream().filter(e -> e.supporting() == supporting).toList();
        int count = supporting ? belief.evidence() : belief.contradictions();
        lines.add((supporting ? "EVIDENCE" : "CONTRADICTIONS") + ": " + count
                + " contributing encounters; " + retained.size() + " retained events");
        if (retained.isEmpty()) lines.add(count == 0 ? "  None observed." : "  Details no longer retained.");
        retained.forEach(e -> lines.add(event(e)));
    }

    private static void appendConfidence(List<String> lines, MaeveDirector.BeliefSnapshot belief) {
        long onset = Math.max(belief.updatedAt(), belief.lastConfirmed() + BeliefPolicy.STALE_AFTER);
        long elapsed = Math.max(0L, belief.evaluatedAt() - onset);
        lines.add(String.format(Locale.ROOT, "SCORING: support +%.2f, contradiction -%.2f; clamp to [0,1] after each contribution.",
                BeliefPolicy.SUPPORT, BeliefPolicy.CONTRADICTION));
        lines.add(String.format(Locale.ROOT, "ACTIVE SCOUT: subject-matched behavioral support +%.2f; no bonus during departure or later combat.", BeliefPolicy.RECON_SUPPORT));
        lines.add("CLOCK: overworld game tick=" + belief.evaluatedAt() + " | last observed=" + belief.lastObserved());
        lines.add(belief.lastConfirmed() < 0 ? "CONFIRMATION: never; saved confirmation sentinel=-1"
                : "CONFIRMATION: tick=" + belief.lastConfirmed() + " | ageTicks=" + belief.ageTicks());
        lines.add(String.format(Locale.ROOT, "DECAY BASIS: stored score=%.4f at tick=%d; grace=%d ticks; half-life=%d ticks.",
                belief.storedConfidence(), belief.updatedAt(), BeliefPolicy.STALE_AFTER, BeliefPolicy.HALF_LIFE));
        lines.add("DECAY FROM: max(last score update, last confirmation + grace)=" + onset + "; elapsed=" + elapsed + " ticks.");
        lines.add(String.format(Locale.ROOT, "CURRENT SCORE: %.4f * 0.5^(%d/%d) = %.4f; reads do not mutate the stored score.",
                belief.storedConfidence(), elapsed, BeliefPolicy.HALF_LIFE, belief.confidence()));
    }

    private static String header(MaeveDirector.Snapshot snapshot) {
        return "Maeve " + snapshot.lifecycle() + " | profiles=" + snapshot.profiles() + " beliefs=" + snapshot.beliefCount();
    }

    private static String event(MaeveDirector.EvidenceSnapshot event) {
        return (event.confidenceWeight() == 0 ? "  VERIFY " : event.supporting() ? "  SUPPORT " : "  CONTRADICTION ") + event.action()
                + " tick=" + event.time() + " observer=" + event.observer()
                + " encounter=" + event.encounter() + " at=" + event.dimension()
                + " " + event.position().toShortString()
                + (Double.isFinite(event.confidenceWeight()) ? String.format(Locale.ROOT, " weight=%+.2f", event.confidenceWeight()) : "");
    }
}
