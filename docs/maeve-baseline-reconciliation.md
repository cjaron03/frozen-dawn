# Maeve baseline reconciliation — initial audit

Status: hidden pacifist advancement implemented and automated verification passed, 2026-09-30. The owner approved Returned descendants and implementation on this isolated branch while Pawn Convergence PR #98 continues separately in parallel. Owner confirmed both manual tests passed on 2026-09-30: the clean launch awarded the advancement, and the Undone-kill control withheld it.

Source: [MACS §§1–5 and §9.19](https://www.notion.so/39d7cfaa890181c1bad4f6babad80880), updated and verified after the owner decisions below. The owner removed disposition-to-Director pressure and the launch reprieve. The remaining implementation is the pacifist advancement. The permanent violation ledger and erasable tactical memory stay separate.

## Checkout

- Branch: `feat/maeve-baseline-reconciliation`.
- Starting integration commit: `c94ddd042783c50ee4d8fee5e524600c9032ba9e`.
- Worktree: `/Users/jaroncabral/.codex/worktrees/maeve-baseline-reconciliation/minecraft-mod`.
- PR #98 has a separate owner and checkout. Incorporate its integration result before final combined validation; this audit neither changes nor accepts that PR.

## Settled owner decisions

| Decision | Current behavior | Recommendation | Status |
| --- | --- | --- | --- |
| §2 activation | `MaeveDirector.current` activates from `PhaseManager.isVacuumActive`; no Major Hearth requirement | Keep Phase 6 late alone. The first Major Hearth encounter carries the narrative signal. | Confirmed; no code change |
| §5 components | Hearth loot supplies existing rocket engine/fin/hull/nose cone, fuel cells, cryo fuel and suit/salvage supplies | Align the specification with existing loot. | Confirmed; Notion updated |

The owner also removed disposition-based Director pressure and the good-standing launch reprieve. Existing local Hearth disposition and conduct behavior remains. The pacifist advancement is retained: zero Returned-faction kills, other combat allowed, with per-player eligibility through a successful launch. Owner confirmed player-attributed kills, including attributed projectiles, explosives and pets. Unattributed environmental or trap deaths do not invalidate the run. The owner confirmed post-Maeve Returned descendants too. The explicit lineage tag and hidden presentation are now implemented below.

## Current implementation map

| Requirement | Evidence | Remaining work |
| --- | --- | --- |
| Disposition affects pacing and leash | `ReturnedHearthSavedData.moodFor` maps relationship to DORMANT / WATCHFUL / AGITATED / HOSTILE; no disposition references were found in the Director package | Removed by owner. No Director integration is required. |
| Sustained good-standing launch reprieve | Current relationships are NEUTRAL / SUSPICIOUS / ORSATHAE. Contact/visit history exists. Launch code has no conduct hook. | Removed by owner. No launch-pressure exemption is required. |
| Pacifist launch advancement | `RocketLaunchManager.finishLaunch` is the completion hook. Launch statistics already read `Stats.ENTITY_KILLED` for Returned, Mimics, Hollows and Architects. The faction-wide advancement is now implemented by `PacifistAdvancement` and `PacifistKillHandler`. | Automated eligibility, persistence, old-statistics and successful-launch award checks passed. Both manual clean-launch and Undone-kill control tests passed, confirmed by the owner on 2026-09-30. |
| Hearth loot wording | `HearthReconciliationManager` builds the existing salvage chest; the three named specialist components are not present in that path | Settled: Notion now describes existing salvage. No new items required. |
| Plunder wording | Plunder remains a permanent Hearth violation. | Notion removes the obsolete reprieve-forfeiture wording. |

The former reprieve qualification and multiplayer-standing questions are retired with that feature. Pacifist eligibility is individual: another player's kills do not automatically disqualify an eligible passenger.

## Advancement presentation

Title: **A Pacifist in This World**. Description: **“Do not answer! Do not answer!! Do not answer!!!”**, followed on a new line by **“Leave Earth without killing any of the Returned or their descendants.”**. The first description line directly quotes the pacifist listener in Liu Cixin's *The Three-Body Problem*, English translation by Ken Liu; the title shortens the listener's self-identification. Owner requested this homage on 2026-09-30. Quote source: https://www.mdpi.com/2073-4336/17/3/22 (section 5).

## Implementation and verification

`returned_lineage` includes Returned, Mimic, every Architect role (including Masters and copies), Undone, Bloombound Undone, Undone Architect, Remnant, Aggregate, Aggregate Fragment and Heart Successor. It excludes unrelated threats such as Hollow/Resonant, Frostbitten/Rimebound, Frostmites and Frostwrithe. Future descendants can extend the entity-type tag.

`PacifistKillHandler` records non-cancelled player-attributed lineage deaths at the final event priority. Attribution resolves vanilla kill credit and player-owned projectiles, explosives and pets. A permanent UUID ledger in `frozendawn_pacifist.dat` survives player replacement, reconnect, death, world reload and Maeve erasure. Existing vanilla entity kill statistics disqualify historical killers when launch eligibility is checked. Previously unrecorded attribution cannot be reconstructed for old saves.

`RocketLaunchManager.finishLaunch` awards only an eligible actual passenger at successful launch. Other players' kills do not disqualify that passenger. The advancement is hidden, challenge-framed, uses a rocket-nose-cone icon, shows a private toast and has no chat announcement or material reward. Title and description are the exact strings above.

`./gradlew architectVerify architectMonkey --console=plain` passed 628 unit tests, eight gate-harness tests, 195 native GameTests (all 190 required) and the unchanged 500 seeded stress cases plus 195 repeated native tests. Five required native cases cover real direct/projectile/explosion/pet attribution; unrelated, environmental and cancelled deaths; UUID replacement and NBT persistence; historical statistics; actual successful launch, hidden display and passenger isolation. Published-jar inspection confirms the advancement/tag are included and GameTests excluded.

Evidence: `build/maeve-pacifist-evidence/verification.json`, `native.xml`, `stress.xml`, `unit/` and `gradle.log`.

Initial gate artifact SHA-256: `c0b0a9535d8e2a941e14dff948ae450b11bc3e64de5cdae92cc1d6ee7d389168`.
Source SHA-256: `7c61a7fe4b5834d6bb1691d70ce157299d1fc28d7ca214c753f3299056c5d0e9`.

Client handoff: launch this checkout with Java 21 using `./gradlew runClientLab --console=plain`. Its isolated game directory is `run-lab`; startup log is `/private/tmp/maeve-pacifist-client.log`. Use a fresh disposable test world for native replay. `/gametest run frozendawn:pacifistsuccessfullaunchawardsonlyeligiblepassenger` exercises the actual server-side launch award with fixture players; it does not establish the owner's visual toast acceptance. Owner confirmed the manual clean launch and Undone-kill control both passed on 2026-09-30; visual acceptance is complete.

The description clarification subsequently passed `./gradlew build`; the rebuilt jar and installed smoke jar share SHA-256 `df2f84103757053a5e0b209ec39514617d524f8fd980b066f7c2a9420592c96b`. The Gradle client was restarted with the updated wording. This slice is ready for review against `feat/maeve-director`; final integration remains pending. PR #98 remains separately owned, and its eventual integration result must be incorporated before final combined acceptance.
