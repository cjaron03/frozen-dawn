# Maeve Slice 5 — finite attention

Branch: `feat/maeve-attention`, targeting `feat/maeve-director`, after Slice 4 merged as `e878c40` (PR #90). Contract: [Maeve Director — Source of Truth](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), §§9.12a, 9.15–9.19 and 12.

**Live gate: pending. Do not merge on automated results alone.** A tester must repeatedly shed a stalking Architect by creating pressure elsewhere and describe the result as a usable tactic.

## Implemented behavior

`AttentionManager` owns one server-wide set of focus slots: two on Cinematic, three on Normal/custom presets and five on Brutal. All dimensions and players compete for the same slots. There is no attention balance, spending, recharge or regeneration. The slot count is the only attention value that varies with difficulty.

The eviction order is reconnaissance, passive tracking, active commitment, siege, then Master encounters, which are never evicted. Each concern must remain admitted for at least 100 overworld ticks before eviction. Equal-priority ties use admission time and subject UUID for deterministic results. Refreshing a concern does not reset its admission time. If no mature, evictable slot exists, admission waits for another qualifying local observation; there is no unbounded pending-job queue. Rejected admission reasons appear in a bounded diagnostic history.

This slice connects existing passive Architect observation, positioning commitments, and real Master encounters. Tracking one player is one concern shared by up to eight observers. A sole tracker beginning a commitment promotes its existing concern without spending a second slot or restarting dwell. A shared tracking group retains its other observers when one begins a separate commitment. Reconnaissance and siege have their required priority positions in the policy; new mission and siege execution are not implemented here.

Admissions require an eligible Survival/Adventure subject, a real ordinary or Master Architect, the same dimension, at most 48 blocks, and the observer's own current line of sight through loaded chunks. Existing target selection supplies a candidate only. It is not evidence. Mind copies, Aggregate children, NoAI actors, dead/removed actors and passive Hearth residents/assessors cannot become ordinary tracking executors. Masters claim focus only after local combat is active and the subject is seen. Full slots never postpone or alter the Master's immediate local combat response; an additional boss can fight locally while awaiting Director focus.

On eviction, every loaded executor of the concern receives a visible disengagement order before its slot is reassigned. A positioning commitment ends as `ATTENTION_EVICTED`, clears the thinking hold, mining target and pending approach work, and retains its used encounter bet. Losing attention adds no belief evidence, contradiction or cooldown. The local Architect turns away from the last position it actually witnessed and tries to walk away for 200 ticks. It suppresses immediate reacquisition of that player for 600 ticks, then returns to ordinary targeting. These are local departure/reacquisition guards, not a regenerating global attention resource. New damage from a living attacker permits immediate local self-defense.

Departure uses loaded, supported, collision-free two-block walking segments, testing at most seven headings and five body samples per candidate. It neither places nor breaks blocks. If no safe segment exists, it turns away and drops pursuit without walking into a hazard. Confined terrain can limit the physical departure; the live test therefore needs to establish that the normal open-ground case reads clearly.

Completed observation/commitment work, dead or unloaded executors and ended boss encounters release their slots. Passive tracking without renewed qualifying sight expires after 100 ticks. Changing to a lower slot tier drains eligible concerns using the same visible eviction and dwell rules. Already-active protected Masters can temporarily exceed the new, lower capacity; no new concern is admitted until they complete. Absolute occupancy remains at most five, and a preset change never cancels a boss fight.

## Persistence, diagnostics and bounds

Attention is transient execution state. Existing version-three tactical saves and the violation ledger keep their format and contents. Reload releases focus and local departure state; loaded entities can subsequently reacquire focus through valid observation. Beliefs, encounter contribution limits and used commitment flags retain their established persistence behavior. ERASED, forced erasure and debug erasure clear focus, recent attention history and pending departure orders immediately. Debug reversal starts empty. Shutdown clears the server-instance cache.

`MaeveDirector` remains the sole external entry point, below its enforced 300-line limit. Package-private `AttentionManager` and `AttentionCoordinator` own policy and executor references; the entity's controller owns movement. The manager retains at most five concerns, eight executor UUIDs per concern, 16 recent events and 16 deduplicated deferral reasons. At most 256 departed executor references are retained for erasure cleanup and removed after departure suppression ends. No attention archive is written inside the world.

Local admissions and refreshes occur at one-second boundaries. Cleanup examines at most 40 active executor references plus the bounded departure list once per second, using entity UUID lookups in already-loaded levels. No chunk is loaded for attention, no world-wide entity search is introduced, and no inventories, hidden player health or omniscient target-position memory are used to acquire focus.

Operator-only `/fd maeve status` and `/fd maeve dump` show shared occupancy, type, subject, executor UUIDs, admitted time, remaining dwell, observed positions/dimensions/times, admissions, deferrals, completions and evictions. An erased dump contains no former concerns. Actor traces include `MAEVE_ATTENTION_EVICTED` and the concern that was dropped.

## Verification and live comparison

Required command: `./gradlew architectVerify architectMonkey --console=plain`, retaining existing fixture and stress seeds. Policy tests cover every eviction priority, minimum dwell, duplicate admission, deterministic ties, Master protection, tier changes, bounded history, promotion and mandatory executor notification. Required GameTests cover event-level visibility filtering, actual departure movement, shared observers, commitment interruption without changing beliefs, direct self-defense, budget sharing across players, full-budget local boss response, reload and immediate erasure cleanup.

`tools/prepare_maeve_attention_playtest.py` copies a closed QA world into **Maeve Attention Encounter**, refusing to overwrite an existing destination. It replaces only the copy's QA pack. Setup uses authoritative debug commands to activate Maeve, choose the real Cinematic tier and establish pre-existing hostile conduct. It clears tactical memory for this disposable fixture; it does not inject beliefs, focus slots or confidence. Two caged real Masters provide distinct existing concerns while the ordinary Architect observes. The small door initially prevents the nearby Master from seeing the player. Opening that real door exposes the additional encounter to local observation.

1. Open **Maeve Attention Encounter** and run `/function maeve_attention:setup`.
2. Run `/function maeve_attention:control`. Stay on blue, leave the wooden door closed and watch the ordinary Architect to the right. The round pauses after 12 seconds. Describe the behavior, then run `/fd maeve dump` directly.
3. Run `/function maeve_attention:tactic`. Stay on blue. At the green prompt and bell, right-click the nearby wooden door once, keep it open, and look right at the ordinary Architect. Describe the difference at the pause, then run `/fd maeve dump` directly.
4. Repeat `tactic` to test reliability. Preserve both direct dumps and the exported actor traces. No 31-second training waits are needed; production dwell, observation and departure timings are unchanged.

The expected causal chain for the reviewer is a mature passive-tracking concern plus one protected Master, then a second locally seen Master concern, eviction of tracking, and physical departure by its executor. The control must not show that eviction while the door stays closed. The round's scheduled final NoAI pause is test control, not Maeve behavior. Its later slot cleanup must not be mistaken for the pressure-triggered eviction.

This fixture makes the currently available concern types compete without adding reconnaissance early. It does not demonstrate arbitrary sounds attracting attention or a general-purpose noise-decoy system. Those are not implemented claims. Multiplayer budget tests use server-side test players; a new two-client soak has not yet been performed.
