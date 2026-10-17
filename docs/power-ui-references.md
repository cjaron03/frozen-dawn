# Power UI reference study — October 12, 2026

Owner's first live account: “we need a WAY better Ui for this. how do some mods like thermal expansion or something handle this?” Functional stations are not accepted by that account. Owning checkout remains `/Users/jaroncabral/.codex/worktrees/power-network/minecraft-mod`, `feat/power-network`, base `d3e10cd`. The live Power Network Check save retains its changes. This turn studies interaction patterns and produces an original interface sketch.

Decision: native Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21 UI, using Frozen Dawn's existing ORSA panel style and server-authoritative topology. References inform interaction and information structure. No reference dependency, source code, art, textures, or sounds are imported.

| Candidate | Intended use | Verified rights / compatibility | Safe takeaway | Decision |
| --- | --- | --- | --- | --- |
| Classic Thermal Expansion / Dynamics | Machine configuration and physically readable connections | Official documentation explicitly targets 1.12. ThermalExpansion repository README contains its custom CoFH license and requests permission for GUI art reuse. No 1.21.1 dependency compatibility is asserted. | Local configuration tabs; clear machine controls; adjacent ducts connect automatically and a wrench toggles individual sides. | Study the interaction; author native ORSA UI. |
| Botania | Tool-driven targeting and inspection | Current official Lexicon warns it can precede releases. Repository `1.20.x/LICENSE.txt` is a custom license, including alternative-license, attribution and copyleft conditions. No compatibility claim for installation. | Holding the wand displays aiming beams; hovering reveals buffer and highlights a spreader's target. | Best reference for world targeting. Original implementation and assets. |
| Flux Networks | Network and connection inspection | Official GUI Tabs wiki edited October 17, 2019; historical documentation. `1.20/LICENSE` states MIT. `GuiFluxConfiguratorHome.java` in that branch is commented legacy source, so it is not evidence of a current working API. No installation needed. | A connection list with names and per-connection actions; separate network statistics. | Best reference for an inspection panel; no source adaptation. |

Primary sources:

- [Thermal furnace Configuration and Redstone tabs](https://teamcofh.com/docs/1.12/thermal-expansion/redstone-furnace/), [energy cell GUI and front readout](https://teamcofh.com/docs/1.12/thermal-expansion/energy-cell/), [automatic adjacent fluxduct connections and wrench toggling](https://teamcofh.com/docs/1.12/thermal-dynamics/fluxducts/).
- [ThermalExpansion README license and GUI art policy](https://github.com/CoFH/ThermalExpansion#readme).
- [Botania Lexicon: Wand and Mana Spreader](https://botaniamod.net/lexicon.html#mana_spreader), [exact license](https://github.com/VazkiiMods/Botania/blob/1.20.x/LICENSE.txt).
- [Flux official GUI Tabs wiki](https://github.com/SonarSonic/Flux-Networks/wiki/GUI-TABS), [exact license](https://github.com/SonarSonic/Flux-Networks/blob/1.20/LICENSE), [historical configurator source](https://github.com/SonarSonic/Flux-Networks/blob/1.20/src/main/java/sonar/fluxnetworks/client/gui/GuiFluxConfiguratorHome.java).

## Original implementation direction

Give world linking and network inspection complementary surfaces. A compact Binder HUD keeps the selected source visible, identifies the target, shows distance versus the shorter endpoint's reach, and gives a readable instruction or rejection before clicking. Outline the selected source and target; draw a temporary beam to the candidate. Use a green preview for a validated link, red for a rejection, and an explicit pending state while the server checks room geometry. Include text and line styles so color is not the only cue. Feedthrough failures should mark the actual relevant boundary block and say what to place there. Keep coordinates in secondary details.

An empty-hand relay interaction opens an ORSA panel. Show connected-node/link counts, supply/demand in kW, storage in kJ, and a list of this relay's direct links with distance, active/inactive reason and one Disconnect action per edge. This topology slice should explicitly say there is no electrical source; an active link is not evidence of delivered power. Names in the sketch are example labels. User renaming and permanent network names are possible later features, not a prerequisite for fixing basic linking.

Local integration points: replace `ConduitBinderItem.tell` and `showReading`'s ordinary chat/action-bar flow with a dedicated HUD and transient status payload; extend `PowerLinkRenderer` with source/target outlines and preview; extend `PowerLinkRules` to return rejection detail including a boundary position; add a server-bound inspection/action flow and native relay Screen using the existing GeothermalCoreScreen styling. Permit preview requests only while the player holds the Binder, checks their actual nearby target and saved source, and limits request frequency. Server validates every mutation and maintains existing range, feedthrough, dimension and chunk-loading rules. The inspection screen must validate proximity and an incident saved link before disconnecting it.

Initial UI sketch has locally interactive valid/range/wall feedback and a complementary relay panel. It is a design preview, not implementation inside the running game. Browser inspection confirmed link success, sealed-wall feedback, panel rendering and an individual disconnect. No shipped assets were added; the provenance ledger has no new third-party asset entries.

Confidence: high in the documented design patterns; reference versions vary. Current reference-mod loader compatibility and source reuse obligations beyond the inspected license are outside the chosen approach. Human UI acceptance remains pending. Implementation needs tests for preview requests, stale targets, accurate inactive reasons, single-edge disconnect authority and client cleanup, followed by the same saved-world visual replay.

## Owner-selected direction

The owner subsequently chose a really simple Botania-style targeting flow. The connection-screen recommendation above is superseded for this slice. Implement only source/target highlights, preview beam, short contextual feedback, and click/air-cancel/Shift-clear controls. The current implementation is recorded in the latest power-network checkpoint.
