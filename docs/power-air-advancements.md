# Power and Air advancements and emergency lighting

Owning checkout: `/Users/jaroncabral/.codex/worktrees/power-air-advancements/minecraft-mod`.
Branch: `feat/power-air-advancements`, based on merged Maeve Director `3b4e096f`.
Owner requested all four advancements and explicitly included the Emergency Light.

Sources: [approved hidden advancement additions](https://app.notion.com/p/3f67cfaa89018122ba5fe99275a16576)
and [Power System, including its electric-light additions](https://app.notion.com/p/3f57cfaa89018123a786fbb637b5ecb1).

## Implemented behavior

| Hidden advancement | Server trigger |
| --- | --- |
| In Space, No One Can Hear You Brown Out | A Panel Light is shed, a charged ORSA Emergency Light actually runs on that network, and the player occupies the lamp's verified sealed base room. |
| Unlimited Power! | The player sets 100% power through a valid Geothermal Core menu. Loading/direct setters, remote/stale menus and spectators cannot award it. |
| Canary in the Furnace | The existing O2 allocator pauses a fueled vanilla furnace, blast furnace or smoker after its shortage grace. Players in the affected O2 pool receive it. |
| Holy Cow | A complete, air-filled vacuum-phase pool with positive supply is overloaded, and subtracting its cows' actual demand removes that overload. |

All four keep the exact approved titles/descriptions, are hidden under Phase 6,
and persist through ordinary per-player advancement progress. Spectators and
outside players are excluded. The hooks do not discover geometry, change gas or
allocate extra power. Cow credit ignores unknown accounting, zero supply and
shortages that already exist without cows. Ordinary fuel exhaustion is not Canary.

The separate `frozendawn:orsa_emergency_light` block/item/entity is craftable and
accepts normal Binder links, including a Cell Dock circuit. Its compact metal base
is eight model pixels wide; the stepped crimson lens stands 5.5 pixels from its
mounting face. Click a floor, wall or ceiling to orient it. Small selection geometry
matches the fixture; it has no collision and cannot seal a room. Binder beams end
at the lens. The model uses native materials with client tinting for a deep red
lens and subdued red highlight. Minecraft's actual emitted light remains uncolored.

Healthy connected supply arms a 12,000-tick internal reserve with zero measured grid
draw. The lamp is normally off. Failure or shed normal lighting activates it at
light level 6 and consumes one reserve tick per loaded tick. It turns off when empty;
restored supply re-arms it. Remaining reserve persists through reload and pauses
while unloaded. Fresh or broken/replaced lights start empty. Right-click shows one
short status/time line; there is no screen. No heat, O2 demand, sound or power export.

Existing decorative ruin Emergency Light IDs, phase flicker, entities and assets
remain unchanged. Palettes lacking the new facing property default upright. No
existing saved-data format or network packet changed. Recipe, loot, creative entry,
mining tag, item model and handbook entry are included. No external assets or new
dependency were added.

## Playtest fixture fixes

`tools/prepare_power_air_advancement_playtest.py` creates a fresh metadata-only
Power & Air Check and refuses existing destinations. A local pack filters other
advancements, restores exact copies of the four tested definitions and supplies a
silent parent. It preselects Default difficulty. The owner performs allocation,
linking, burner loading and Add cow; no advancement grant/revoke commands are used.
The power Core sits in an opaque cabinet so its permanent glow cannot mask backup.

The original far cow-room shell was absent. Setup previously marked ready without
checking distant chunk readiness. Fresh setup now requests 12 bounded chunk tickets,
waits across ticks for every target chunk, verifies the far station and releases
those tickets. Local progression pause is set idempotently instead of toggled.
These exact updated functions parsed in the active client; built=1 prevented setup
from rerunning in the owner save. A one-use guarded live repair added only the
missing shell/devices, preserved existing interiors/inventories and passed its
actual block audit. It did not spawn the cow or grant its advancement. The owner
then tested the real shortage successfully. The original bridge policy was restored
byte-for-byte after scoped diagnostics; no unrelated policy change is included.

## Verification and owner acceptance

Java21 `architectVerify` passed 804 unit tests, 588 native tests and all 579 required cases, zero failures,
errors or skips, including these seven required native cases:

- `powerAirDefinitionsLoadAsFourHiddenJokes`
- `unlimitedPowerRequiresValidCoreMenuAction`
- `emergencyLightBacksUpForTenMinutesAndKeepsItsReserve`
- `emergencyBrownoutAwardsOnlyAffectedBasePlayers`
- `canaryAwardsOnlyForRealOxygenStarvation`
- `holyCowRequiresCowToCauseTheShortage`
- `compactEmergencyLightMountsWithoutSealingRooms`

The later client palette change passed a full Gradle build and all 804 unit tests.
Substituting only ClientEvents and its two model files reproduces the prior passing
native source fingerprint; gameplay/native inputs are unchanged. Native tests were
not rerun for that palette-only change. Existing Gradle/deprecated client event-bus
warnings remain. Runtime models loaded without errors. The jar matches final
classes/resources and excludes lab/test classes. Shared smoke profile is preserved.

Final executable-source SHA256:
`5c25da18f27adccbb3b32a0580464b3b6319500388b879c74711782c4c73d6d4`.
Jar `build/libs/frozendawn-2.0.1-alpha.jar` SHA256:
`921ece2184d17657aeb4dbe8cbef850571ab730071cb4d4a5f52ea5c6d834374`.

Owner accepted all four live triggers, backup switchover/countdown, recharge,
reserve persistence after reopen, continued countdown and a fresh unlinked lamp
remaining empty/off. Saved progress contains all four completed advancements.
Native tests additionally cover exact ten-minute depletion, saved reserve/clamps,
invalid menu actions, spectators/outsiders, partial brownout, unknown room refusal,
pending O2 chunks, counterfactual cow shortage, six mounts and legacy palettes.

Evidence under `build/power-air-advancement-evidence/` includes original failed
compile/partial-brownout fixture attempts, passing gates/reports, class/resource
proof, closed-save ZIP/hash backups, owner accounts and live repair receipts.
The partial-brownout test now explicitly refuses credit while geometry is unknown
and awards only after normal verification; its production hook remains read-only.
Final review/owner receipt is in `commit-review/`. No blocking review findings.

Original RH-5 and other worktrees remain untouched. Power & Air Check is still open
in the owning Gradle client; no restart/reset/save edit was needed for commit
review. GPR remains paused. Owner authorizes this commit; push is a separate decision.
