# Inventory and spawn egg playtest

Owning checkout: `/Users/jaroncabral/.codex/worktrees/inventory-models/minecraft-mod`, branch `fix/inventory-models`, based on `12bc79a9e12ad0de263f98d23d7e7e89d5f51d95`. Changes remain uncommitted. Preserve the original checkout's unrelated WIP.

## Accepted inventory views

The owner accepted the centred/angled Thermostat, O2 Gauge, Icicle and both relay pylons, then accepted the Monitoring Station Terminal after F3+T. Only their item-model `display.gui` transforms changed.

## Spawn eggs

Nine new creative eggs: Rimebound, Resonant, Remnant, Frostwrithe, Undone, Bloombound, Undone Architect, Spore and Archivist. They use the existing vanilla egg model, individual colour pairs, English names and the Frozen Dawn creative tab. Total eggs: fifteen.

Egg Remnants start active without a lure. Their save/load now preserves the absent anchor; bound Remnants retain their authored anchor. Egg Spores use their hatch location as the source, avoiding immediate rooting far from world origin. These initialization branches apply only to SPAWN_EGG. The Archivist's collection/sorting still requires its normal relic-site binding. Boss/helper eggs and dedicated Architect-variant eggs remain outside this pass.

## Verification and active save

Java 21 full build passed with 930 unit tests. Eleven focused native checks passed: all nine eggs used against blocks, correct mobs and survival item consumption, live ticking, standalone NBT reload, and two event-spawn controls including bound Remnant reload. The first native run caught the standalone Remnant reload bug (10/11); its failed report and log remain under `build/inventory-review/native-attempt-1`. The final focused runner, reports, logs and validation are in `build/inventory-review`; this is not a claim that the full Architect native/stress gates ran.

World: `run-lab/saves/New World`, created by the owner during icon review. The owner closed the client; all 39 save files were zipped and hash-verified before installing Java changes, then rechecked unchanged before reopening. Backup and manifest: `build/inventory-review/pre-spawn-eggs-world.zip` and `pre-spawn-eggs-world-manifest.json`. No player data, settings or inventory were edited to prepare this check. Shared smoke installation remains owned by the other checkout.

Launch/reopen with Java 21:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew runClientLab -PfdLabWorld='New World' --console=plain
```

The Gradle lab client reopened this exact save successfully in Creative (session `361237d5-1a45-48f6-8c56-94c1cc191504`). Startup logged no missing egg models or textures. The owner subsequently reported “looks good” for the egg view; individual creature combat behaviour was not claimed as reviewed. Search the creative inventory for each egg and place one at a time; the native checks establish spawning, not visual approval or multiplayer acceptance.

## Broader block audit and core priority

The owner prioritised Inert Convergence Core. Its existing item is now listed in the Frozen Dawn creative tab, with the existing model and three-use defaults. The subsequent full Java 21 build and 930 unit tests passed; no new native run was needed for this single list entry. The running egg client predates this Java change; its creative entry requires a restart. The earlier eleven native passes describe the egg revision. Build details and SHA-256: `build/inventory-review/core-creative-validation.json`.

The earlier six-entry list covered registered block items omitted from the tab. After adding the core, five remain: Street Light, Town PA Speaker, Bloom Mass, Bloom Crust and Living Bloom Tip.

A wider audit of all 135 registered blocks found 24 without a dedicated item after following custom BlockItem subclasses and shared wall/sign items:

| Group | Blocks | Assessment |
| --- | --- | --- |
| Strong creative-access candidates | Bloom Core; Acheronite Crystal; Frozen Atmosphere; Ossuary Mass; Ossuary Rib; Wall Emergency Alarm Beacon; Spent Torch; Extinguished Lantern | Need new item registrations/models. Acheronite Crystal is explicitly natural-only in the current source. Ordinary shards do not place crystals or atmosphere deposits. |
| Environmental/hazard review | Thermal Vent Pool; Fallout; Molten Corium | Placement and preview need to respect the existing vent registry, radiation and meltdown behaviour. |
| Encounter geometry | Convergence Residue; Unstable Ossuary Mass; Folded Membrane; Weak Shelter Seam; Silent Shelter Prop | Residue expires outside authored Ossuaries. Remnant lure blocks intentionally have no items and depend on encounter context. |
| Shared story structures | Hearth Relic; Vel-an Memory Wall; Residue Vessel | Explicitly documented as world-shared lore carriers with no block items. |
| Internal helpers | Held Lantern Light; Hearth Boundary Marker | Runtime/structure helpers; keep out of ordinary creative browsing. |
| Alternate forms | Spent Wall Torch; Sulfur Lava; Potted Rimewood Sapling | Sulfur Lava already has a bucket. Potted saplings are container forms; a future spent-torch item could handle both mounting forms. |

This is a registry/resource audit, not a claim that every omitted block should become obtainable in Survival. Only the selected core creative entry was added in this follow-up.

## Bloom/Ossuary additions and four-item replay

The owner requested Bloom Core, Ossuary Mass and Ossuary Rib as creative items, then a same-world review with Inert Convergence Core. Registered ordinary BlockItems for `bloom_core`, `aggregate_mass` and `aggregate_rib`, added them beside the core in the creative tab, and supplied item models inheriting their existing block models. Names reuse the existing block translations. The rib retains vanilla pillar-axis placement. Existing block code, textures, loot and recipes were not edited; Ossuary blocks retain their existing no-loot behaviour. Three of the 24 previously unrepresented block types now have items.

The full Java 21 build and 930 unit tests passed. All four creative references, translations, model parents, textures and packaged models were checked. No new native replay was run for these standard BlockItem registrations. Earlier egg checks remain separate. Exact build details and SHA-256: `build/inventory-review/bloom-ossuary/validation.json`.

The previous Gradle client exited cleanly after saving all dimensions. The latest 44-file `New World` save was locked, backed up, hash-verified and rechecked unchanged before reopening. Backup: `build/inventory-review/pre-bloom-ossuary/world.zip`; manifest beside it. No world state or player inventory was edited to prepare the review.

Reopen the same `New World` using the existing Gradle command above. Owner review should first compare the four inventory icons. Then place Bloom Core, Ossuary Mass, an upright and a sideways Ossuary Rib, and Inert Convergence Core. Record the owner's observations before diagnostic explanations. The new items are creative-access additions; owner appearance/placement acceptance and full Convergence Core gameplay acceptance remain distinct.

Confirmed reopened in owning Gradle lab session `0fc3cfc4-ecc5-4ee8-9de4-3bf30e2dcec6`. Owner inventory/placement observations are pending.

## Latest closed-save inspection

After the owner asked what is next, fresh runtime/log checks found the client closed. The latest log records a disk-full player-save warning and AggregateSavedData write failure at 02:30:56 on 2026-10-21. Player NBT was written at logout (02:32:24); level.dat and chunk shutdown saves completed later. Aggregate data remains timestamped 02:07:01, so late-session Aggregate state cannot be claimed preserved. Current free disk space was 29 GiB when inspected; no claim is made about who reclaimed it.

The current 66-file world was locked, archived and hash-verified without editing/restoring any save file: `build/inventory-review/post-block-review-save/world.zip`, manifest and retained client log beside it. Read-only inspection parsed all 35 compressed NBT save files and 4,356 occupied region records with zero read errors. This proves readability, not recovery of failed writes. Saved Aggregate data contains an active Overworld Stillpoint, but its latest state must be confirmed in-game before a reload acceptance claim. Detailed inspection: `build/inventory-review/post-block-review-save/inspection.json`.

Next proposed acceptance: core recovery/use counter and a controlled save/reload after confirming the existing Stillpoint state. Then final source review and PR preparation. No client restart, state reset or backup restoration was performed during this inspection. Owner “got it” was acknowledged without upgrading it to a detailed placement or persistence acceptance.

## Bloom terrain and cold-block creative review

The owner reported the preceding core checks were good, then requested Bloom Mass, Bloom Crust, Living Bloom Tip, Acheronite Crystal and Frozen Atmosphere in Creative and a review in the same world. Added creative entries for the existing Bloom trio and registered normal BlockItems for the crystal and atmosphere deposit. Existing block names are reused. Crystals place at the existing default AGE=0 and retain natural growth conditions. Crystal code changed only its now-outdated placement comment; the gameplay body was compared unchanged.

Bloom Crust now inherits its first thin-layer model, with a centred inventory transform. Frozen Atmosphere inherits its original translucent placed model, gets the standard vanilla block item transforms, and centres the thin layer in GUI. Bloom Tip and the young crystal use their existing textures as generated plant-style item icons. Bloom Mass retains its existing cube model. Growth, sublimation, collision, loot, recipes and world formation were not edited.

Final Java 21 build passed with 930 unit tests; five creative references, labels, parent/texture chains, packaged models and projected layer bounds were checked. Standard registrations/model additions did not get a new native run. Details and SHA-256: `build/inventory-review/bloom-cold-blocks/validation.json`; model audit beside it.

The closed 66-file `New World` save still exactly matched the previously verified `post-block-review-save` backup; both current file hashes and ZIP contents were rechecked under its session lock. No restoration or save edit was performed. The prior disk-full save caveat is retained above. Disk space is now available. The saved player is in Survival; the owner was told to use `/gamemode creative` for this block review. Phase, position, health and inventory remain as saved.

Use the same Gradle quick-play command above to reopen. Pending owner review: compare the five icons, place the Bloom trio together, try stacking crust layers, then place the crystal bud and atmosphere deposit on solid ground. Confirm crystal growth and atmosphere sublimation only against their actual existing phase/temperature conditions; these are distinct from model/placement acceptance.

Confirmed same-world startup in Gradle lab session `422a254e-4c85-4cee-8c6f-f4a03d459e5d`. Player remained in saved Survival mode. The initial snapshot was inside a sealed, warm room; cold-block placement should be reviewed outdoors against actual environmental conditions. Startup reported no missing models/textures for the five additions. Owner observations remain pending.

## Owner acceptance of five-block review

The owner answered “look good” when asked about Bloom Mass, Bloom Crust, Living Bloom Tip, Acheronite Crystal and Frozen Atmosphere inventory icons and outdoor placement, including trying stacked crust. Record this as appearance/placement acceptance; no detailed layer-count, natural-growth, sublimation, persistence or multiplayer proof is inferred. Fresh current-session log review found no ERROR, missing-model/texture or save-failure messages. The pre-existing Patchouli smoking-recipe warning remains. Accepted-view log and snapshot: `build/inventory-review/bloom-cold-blocks/accepted-view-client.log` and `accepted-view-status.json`. The client remains open in the owner-controlled world; no additional mutations or replay changes were performed. These additions are ready for final source review.

## Street Light and Town PA Speaker review

The owner selected the remaining two registered prop items: Street Light and Town PA Speaker. Added their existing items to the Creative tab beside the legacy lights. Both item models retain their original parents and gain only centred GUI transforms; all other contexts inherit their existing transforms. Placed geometry, block entities, recipes and phase behaviour are unchanged.

The full Java 21 build passed with 930 unit tests. Resource checks resolved parent models/textures, verified packaged item models, checked one Creative entry per item and confirmed projected GUI geometry fits the slot and is centred. Existing Street Light and PA block/entity sources match HEAD. Build/hash and model audit: `build/inventory-review/street-pa/validation.json` and `model-validation.json`. No new native run was needed for Creative entries and GUI transforms.

The previous client had already exited cleanly. The latest 66-file `New World` save was locked, archived and hash-verified without editing/restoring world files. Fresh backup: `build/inventory-review/street-pa/world.zip`; manifest and previous client log beside it. Earlier disk-full caveat remains documented above; this archive preserves the latest available save.

Reopen the exact same world with the owning Gradle lab command. First review both inventory icons, place the light on solid ground or iron bars, and try the PA facing several directions and mounted atop the light. This save is in phase 6: both legacy props are intentionally inactive after phase 4. A separate agreed earlier-phase view is required to hear the PA or observe powered lamp flicker. The PA broadcasts approximately every 55 seconds with a player within 64 blocks; phases 0-2 use clear audio and phases 3-4 degraded audio. Owner appearance/placement acceptance is pending. No phase, time, position or inventory edits were performed to prepare this review.

Confirmed same-world startup in Gradle lab session `a7827096-0816-4560-a1cc-23dd6c0a670b`, with the saved player in Creative. Fresh startup log has no ERROR, missing-model/texture or save-failure messages; the existing Patchouli recipe warning remains. Runtime snapshot and retained startup log are beside the validation record. Owner visual observations remain pending.

Owner answered “look good” to the Street Light / Town PA Speaker icon and placement review. Record appearance/placement acceptance only; active lighting and broadcasts were not reviewed in this phase 6 view. A fresh read of the same client session found no ERROR, missing-model/texture or save-failure messages. Retained evidence: `build/inventory-review/street-pa/accepted-view-client.log` and `accepted-view-status.json`. The owner-controlled client remains open; no phase, time or world mutations were performed after acceptance.

## Final review and PR preparation

Final source review found no blocking issue. Updated the owning branch onto `feat/maeve-director` at `b9315f12a31592f0227e684164a5e6a486513657`; the current manual and vanilla barometer recipe were retained without a PR diff. All 31 source paths and 52 earlier inventory evidence files retained their exact hashes through integration. Source recovery archive and a retained stash are recorded under `build/inventory-review/final-pr-review`. The unrelated Scribe checkout remains separately owned.

The integrated Java 21 `architectVerify architectMonkey` run passed: 930 unit tests, 701 native GameTests (all 681 named requirements), and 1,201 stress-server GameTests (all 500 named requirements), with zero failures/errors/skips. The native report includes all eleven new egg checks. Twenty-five packaged resources and 46 model dependencies were audited, along with the nine egg names and eleven selected Creative entries. Existing compiler deprecations remain. The shared smoke installation was not replaced. Final XML reports, log, source fingerprint, JAR SHA-256 and preservation checks are under `build/inventory-review/final-pr-review/validation.json` and adjacent files.

The owner declined another archive of this normal world. Leave its save in place; earlier archives and the documented historical disk-full caveat remain. Owner icon/placement acceptance is complete. Active Street Light/PA lighting and broadcasts were not replayed in phase 6, and multiplayer was not checked. No phase/time/player edits or gameplay resets were performed for the final review. Commit and open a PR for the accepted inventory batch; merge requires separate owner authorization.
