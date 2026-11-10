# The gun payoff: branch roadmap

**Status:** drafted 2026-11-07 for the owner's approval. Nothing is built.

**Spec:** Notion, "Continuity Payoff (the gun) — Branch Spec" under Backlog. Canon: Canon Hierarchy CH-18 to CH-21 and CH-29 to CH-33, plus CH-34 to CH-39 once the owner signs them off. Section numbers below are the spec's.

**Why it is split:** the 2026-11-05 ruling put the whole reveal on one branch. Sections 6b to 6h and chamber zero were added after that. They roughly tripled the work and mostly touch separate systems, so on 2026-11-07 the owner chose one branch per piece, in the order below.

## Rules for every branch

- No branch name contains "continuity" (owner, 2026-11-07).
- Cite canon by ID. The spec's Notion-only notes stay out of the repo, including code comments, commit messages and PR descriptions.
- Never edit MACS. Never open the Notion page "SEALED: Sequel Seed".
- All player-facing text comes from lang keys or the existing book JSON, word for word as canon gives it. Capitalization follows CH-33.
- Each branch gets its own plan in this planning worktree. The owner approves the plan and it merges into `feat/maeve-director` before that branch's worktree is cut, at `/Volumes/SSD/Work/frozen-dawn-<feature>`.
- New items and blocks start creative-only (AGENTS.md). Anything judged by look or sound gets a mockup or prototype the owner approves first.

## Branches

| # | Branch | Spec sections | Needs first |
|---|---|---|---|
| 1 | `docs/payoff-plan` | Canon IDs CH-34 to CH-39 on Notion, section 0 in `docs/aurora-shepherd.md`, this roadmap | Owner sign-off |
| 2 | `feat/payoff-clues` | 6 (uplink moves into Phases 3-4), 6a (EVA wording, Kevin "registered enrollee" line), 6d step 1 (the forecast line), 6h (2071 page), Translator manual entry | 1 merged. Merged into `feat/maeve-director` as PR 169 |
| 3 | `feat/payoff-reveal` | 1 (shared flag, Terminus migration), 2 (CH-18), 3 (CH-19), 4 (CH-20), 5 (boot cue and its toggle), 6d steps 2 and 2b (Projection Review, the replay) | 2 merged. Built 2026-11-09: tests, both gates, the owner's visual pass and the branch review passed. The pass also approved a power-on boot cue, a typed death line, an overhauled first-join opening and a muted, glitching replay; branch 6 builds on that `OrsaAwakeningIntro`. Merged into `feat/maeve-director` as PR 173 (748c5ddf) |
| 4 | `feat/returned-respawn-echo` | 5a (detuned ring on replacement), 5b (Vel-thae transmissions), 5c (no ring once erased) | 1 merged. Plan: [returned-respawn-echo-plan.md](returned-respawn-echo-plan.md), approved 2026-11-09. It also re-levels the respawn ring and the Thaeven transmission sounds, which shipped near silent |
| 5 | `feat/heart-memory-node` | 6b (node 5 from the ledgers), 5d (Kevin heard) | 1 merged |
| 6 | `feat/thaeven-loop` | 6e beats 1 to 4, the Record I page | 3 merged, since both edit `OrsaAwakeningIntro`. Plan: [thaeven-loop-plan.md](thaeven-loop-plan.md), approved 2026-11-09. Built 2026-11-09: tests, the owner's visual pass and the branch review passed, and the review's three fixes are in. The pass also added a first-boot ORSA beat and sting, the sting on terminal and Aurora boots, and a worn, glitched replay beat and voice. Merged into `feat/maeve-director` as PR 175 (867fe242) |
| 7 | `feat/ending-b-welcome` | 6c | 1 merged |
| 8 | `feat/kevins-blanket` | 6g | 1 merged |
| 9 | `feat/the-descent` | 6f and chamber zero, in slices | Still a draft: its own canon IDs (with the CH-27 revision), mockups, sound sign-off, then a creative-only slice before world generation |

Order: 1, then 2 and 3 in turn. 4, 5, 7 and 8 can run beside them after 1. 6 follows 3. 9 goes last.

- Branch 2 lands before 3 so the clues are in worlds before the payoff.
- Branch 3 keeps the reveal in one piece: the flag, the death-screen line, the manual changes and the replay fire in one sequence. The boot cue and the replay share `OrsaAwakeningIntro`.
- Parked: 6d step 3 (the vision during Maeve's erasure) waits for the owner's canon ruling on Edge §13.13. The "From Now" advancement in branch 9 waits for E11.

## Checked against `feat/maeve-director` f27d3934

- `ContinuityPayoff` does not exist yet. `TerminusLedger` still keeps its own `turned` flag, which branch 3 migrates.
- Every class the spec names exists: `PatchouliLandingBranding`, `OrsaAwakeningIntro` (its `DIAGNOSTIC_LINES` still hardcoded), `HearthPopulationPolicy`, `HeartMemoryNodeClient`, `HeartMaeveErasureManager`, `HeartEchoManager`, `StarterBooks`, `ThaevenLoreWorldManager`, `FrozenDawnEndingScreen`.
- No player-facing text uses "enrollee" yet.
- The book anchors exist: the Projected Timeline sign-off, the bulletin's "Who is Kevin?" page, the satellite log's broadcast requirement, ORSA Uplink in The Way Out, and Record III's raw lines in `thaeven_records/pattern_residue.json`.
- The EVA HUD still reads `EMERGENCY EVA ACTIVE`, and the ending's `Welcome home.` key exists.

## Gates

Branch 1 changes only `docs/`, so it skips both gates. The statuses are posted as `Docs only, gate not run` after the owner approves the merge. Every other branch runs `architectVerify` and `architectMonkey`, and gets the owner's visual pass in its own playtest world.
