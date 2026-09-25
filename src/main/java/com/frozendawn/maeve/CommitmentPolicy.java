package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** §9.13a: lagged confidence, cheapest recovery, one issued bet, next-encounter cooldown. */
final class CommitmentPolicy {
    static final double THRESHOLD = 0.75D;
    static final int HOLD_TICKS = 400;
    static final int APPROACH_TICKS = 100;
    static final int SPATIAL_APPROACH_TICKS = 240;
    static final int WRONG_BEAT_TICKS = 60;
    private StrategyPerformance performance = new StrategyPerformance();
    StrategyPerformance performance() { return performance; }
    private final Map<String, Belief> baseline = new LinkedHashMap<>();
    private final Set<String> blocked = new LinkedHashSet<>();
    private final Set<String> blockNext = new LinkedHashSet<>();
    private final Set<String> confirmedHere = new LinkedHashSet<>();
    private UUID encounter;
    private boolean issued;
    private boolean reconIssued;
    private boolean combatStarted;
    private boolean active;
    private long lastContact;
    private String outcome = "NO_PRIOR_ENCOUNTER";
    private MaeveDirector.PositionDirective selected;
    private List<String> alternatives = List.of();

    void begin(UUID id, List<Belief> previous, long now) {
        performance.begin(now);
        encounter = id;
        lastContact = now;
        issued = false;
        reconIssued = false;
        combatStarted = false;
        active = false;
        selected = null;
        outcome = "NOT_EVALUATED";
        alternatives = List.of();
        blocked.clear();
        blocked.addAll(blockNext);
        blockNext.clear();
        confirmedHere.clear();
        baseline.clear();
        // Freeze history before this encounter's first observation; new evidence cannot
        // unlock an answer during the encounter that supplied it. Reads still decay.
        for (Belief belief : previous) {
            if (BeliefDescriptions.patterns().contains(belief.pattern)) {
                baseline.put(belief.pattern, Belief.load(belief.save()));
            }
        }
    }

    List<MaeveDirector.CommitmentHint> hints(long now) {
        List<MaeveDirector.CommitmentHint> hints = new ArrayList<>();
        baseline.forEach((pattern, belief) -> {
            var snapshot = belief.snapshot(now);
            snapshot.provenance().stream().filter(MaeveDirector.EvidenceSnapshot::supporting)
                    .max(Comparator.comparingLong(MaeveDirector.EvidenceSnapshot::time))
                    .ifPresent(event -> hints.add(new MaeveDirector.CommitmentHint(pattern, snapshot.confidence(), event)));
        });
        return List.copyOf(hints);
    }

    MaeveDirector.UtilityBias utilityBias(long now) {
        Belief ranged = baseline.get(BeliefStore.RANGED);
        Belief recovery = baseline.get(BeliefStore.RECOVERY);
        return new MaeveDirector.UtilityBias(ranged == null ? 0 : (float) (0.4D * ranged.currentConfidence(now)),
                recovery == null ? 0 : (float) (0.3D * recovery.currentConfidence(now)));
    }

    String ineligible(String pattern, long now) {
        if (encounter == null) return "NO_PRIOR_ENCOUNTER";
        if (issued) return "ENCOUNTER_BET_ALREADY_USED";
        if (blocked.contains(pattern)) return "CONTRADICTION_COOLDOWN";
        if (blockNext.contains(pattern)) return "CONTRADICTED_THIS_ENCOUNTER";
        if (confirmedHere.contains(pattern)) return "PREDICTION_ALREADY_CONFIRMED";
        Belief belief = baseline.get(pattern);
        return belief == null || belief.currentConfidence(now) < THRESHOLD ? "BELOW_THRESHOLD" : "ELIGIBLE";
    }

    boolean choose(UUID player, UUID observer, List<MaeveDirector.PositionCandidate> candidates, long now) {
        if (issued) return false;
        var reasons = new ArrayList<String>();
        var options = CounterVariantPolicy.options(this, candidates, now, reasons).stream()
                .filter(c -> Double.isFinite(c.recoveryCost()) && c.recoveryCost() >= 0)
                .sorted(Comparator.comparingDouble(MaeveDirector.PositionCandidate::recoveryCost)
                        .thenComparing(MaeveDirector.PositionCandidate::pattern)).toList();
        MaeveDirector.PositionCandidate winner = null;
        for (var option : options) {
            String reason = ineligible(option.pattern(), now);
            var hint = hints(now).stream().filter(h -> h.pattern().equals(option.pattern())).findFirst();
            if (reason.equals("ELIGIBLE") && hint.isPresent() && performance.deferred(CounterVariantPolicy.key(option), hint.get().evidence().dimension()))
                reason = "RECENT_COUNTER_FAILURES";
            if (reason.equals("ELIGIBLE") && winner == null) winner = option;
            else if (reason.equals("ELIGIBLE")) reason = "MORE_COSTLY_TO_ABANDON";
            reasons.add(CounterVariantPolicy.key(option) + " recoveryCost=" + option.recoveryCost() + " " + reason);
        }
        alternatives = List.copyOf(reasons);
        if (winner == null) { outcome = "NO_ELIGIBLE_SAFE_POSITION"; return false; }
        String chosenPattern = winner.pattern();
        var hint = hints(now).stream().filter(h -> h.pattern().equals(chosenPattern)).findFirst();
        if (hint.isEmpty()) return false;
        selected = new MaeveDirector.PositionDirective(player, observer, encounter, winner.pattern(),
                hint.get().confidence(), hint.get().evidence(), winner.position(), winner.cover(),
                winner.recoveryCost(), now, -1, -1, -1, winner.spatial(), null, winner.advancingCover(), winner.keepAwayArcher());
        performance.start(selected);
        issued = true;
        active = true;
        outcome = "APPROACHING";
        return true;
    }

    void confirm(String pattern) {
        if (BeliefDescriptions.patterns().contains(pattern)) confirmedHere.add(pattern);
    }

    void contradict(String pattern, long now) {
        if (!BeliefDescriptions.patterns().contains(pattern)) return;
        blockNext.add(pattern);
        if (active && selected.pattern().equals(pattern) && selected.contradictedAt() < 0) {
            selected = copy(selected.arrivedAt(), selected.holdUntil(), now);
            outcome = rangedPillar() ? "CONTRADICTED_COVER_COMBAT" : "CONTRADICTED_HOLD";
        }
    }

    void arrived(long now) {
        if (active && selected.arrivedAt() < 0) {
            selected = copy(now, swordGuard() ? -1 : now + HOLD_TICKS, selected.contradictedAt());
            performance.arrived(now);
            outcome = rangedPillar() ? (selected.contradictedAt() < 0 ? "COVER_COMBAT" : "CONTRADICTED_COVER_COMBAT")
                    : selected.contradictedAt() < 0 ? "HOLDING" : "CONTRADICTED_HOLD";
        }
    }

    private MaeveDirector.PositionDirective copy(long arrived, long until, long contradiction) {
        return new MaeveDirector.PositionDirective(selected.player(), selected.observer(), selected.encounter(),
                selected.pattern(), selected.confidence(), selected.evidence(), selected.position(), selected.cover(),
                selected.recoveryCost(), selected.startedAt(), arrived, until, contradiction, selected.spatial(), selected.obstruction(), selected.advancingCover(), selected.keepAwayArcher());
    }

    void discover(BlockPos obstruction, long now) {
        if (!active || selected.spatial() == null || selected.obstruction() != null) return;
        blockNext.add(selected.pattern());
        selected = new MaeveDirector.PositionDirective(selected.player(), selected.observer(), selected.encounter(), selected.pattern(),
                selected.confidence(), selected.evidence(), selected.position(), selected.cover(), selected.recoveryCost(),
                selected.startedAt(), selected.arrivedAt(), selected.holdUntil(), now, selected.spatial(), obstruction.immutable(), selected.advancingCover(), selected.keepAwayArcher());
        outcome = "ACCESS_BLOCKED_REPLAN";
    }

    private long deadline() {
        if (selected.obstruction() != null) return selected.contradictedAt() + WRONG_BEAT_TICKS;
        if (swordGuard() && selected.arrivedAt() >= 0) return lastContact + BeliefPolicy.ENCOUNTER_GAP;
        return selected.arrivedAt() < 0 ? selected.startedAt() + (selected.spatial() == null ? APPROACH_TICKS : SPATIAL_APPROACH_TICKS)
                : Math.max(selected.holdUntil(), selected.contradictedAt() + WRONG_BEAT_TICKS);
    }

    void contact(long now) { lastContact = now; }
    private boolean swordGuard() { return selected.pattern().equals(BeliefStore.SWORD); }
    private boolean rangedPillar() { return selected.pattern().equals(BeliefStore.RANGED) && !selected.advancingCover(); }
    private String expiryReason() {
        return selected.obstruction() != null ? "DISCOVERY_REPLAN"
                : swordGuard() && selected.arrivedAt() >= 0 ? "ENCOUNTER_ENDED" : "TIME_COMPLETE";
    }

    MaeveDirector.PositionDirective active(long now) {
        performance.clock(now);
        if (active) {
            if (now < selected.startedAt() || now >= deadline()) finish(expiryReason());
        }
        return active ? selected : null;
    }

    void finish(String reason) { if (active) { active = false; outcome = reason; performance.finish(reason); } }
    MaeveDirector.PositionDirective selected() { return selected; }
    String outcome(long now) {
        if (!active) return outcome;
        return now < selected.startedAt() || now >= deadline() ? expiryReason() : outcome;
    }
    List<String> alternatives() { return alternatives; }
    Set<String> blocked() { return Set.copyOf(blocked); }
    Set<String> blockNext() { return Set.copyOf(blockNext); }
    UUID encounter() { return encounter; }
    boolean issued() { return issued; }
    boolean canSurvey() { return encounter != null && !reconIssued && !combatStarted && !issued; }
    String surveyAdmission() {
        return encounter == null ? "NO_ENCOUNTER" : reconIssued ? "SURVEY_ALREADY_USED"
                : combatStarted || issued ? "COMBAT_ALREADY_STARTED" : "AWAITING_HISTORICAL_UNCERTAINTY";
    }
    void surveyIssued() { reconIssued = true; }
    boolean engage() { boolean changed = !combatStarted; combatStarted = true; return changed; }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (encounter != null) tag.putUUID("encounter", encounter);
        tag.putBoolean("issued", issued);
        tag.putBoolean("reconIssued", reconIssued);
        tag.putBoolean("combatStarted", combatStarted);
        tag.putString("outcome", outcome);
        tag.put("performance", performance.save());
        ListTag beliefs = new ListTag();
        baseline.values().forEach(b -> beliefs.add(b.save()));
        tag.put("baseline", beliefs);
        tag.put("blocked", strings(blocked));
        tag.put("blockNext", strings(blockNext));
        tag.put("confirmedHere", strings(confirmedHere));
        // Execution never resumes across a server/entity reload. The persisted used flag
        // and cooldowns prevent a reload from buying a second bet or skipping caution.
        return tag;
    }

    private static ListTag strings(Set<String> values) {
        ListTag list = new ListTag();
        values.forEach(s -> list.add(net.minecraft.nbt.StringTag.valueOf(s)));
        return list;
    }

    static CommitmentPolicy load(CompoundTag tag) {
        CommitmentPolicy state = new CommitmentPolicy();
        state.performance = StrategyPerformance.load(tag.getCompound("performance"));
        state.encounter = tag.hasUUID("encounter") ? tag.getUUID("encounter") : null;
        state.issued = tag.getBoolean("issued");
        state.reconIssued = tag.getBoolean("reconIssued");
        state.combatStarted = tag.getBoolean("combatStarted");
        state.outcome = state.issued ? "RELOAD_RELEASED" : "NOT_EVALUATED";
        for (Tag entry : tag.getList("baseline", Tag.TAG_COMPOUND)) {
            Belief belief = Belief.load((CompoundTag) entry);
            if (belief != null && BeliefDescriptions.patterns().contains(belief.pattern)) state.baseline.put(belief.pattern, belief);
        }
        for (String pattern : BeliefDescriptions.patterns()) {
            if (tag.getList("confirmedHere", Tag.TAG_STRING).contains(net.minecraft.nbt.StringTag.valueOf(pattern))) state.confirmedHere.add(pattern);
            if (tag.getList("blocked", Tag.TAG_STRING).contains(net.minecraft.nbt.StringTag.valueOf(pattern))) state.blocked.add(pattern);
            if (tag.getList("blockNext", Tag.TAG_STRING).contains(net.minecraft.nbt.StringTag.valueOf(pattern))) state.blockNext.add(pattern);
        }
        return state;
    }
}
