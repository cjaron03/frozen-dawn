# ORSA Field Manual audit

Branch `fix/orsa-manual-layout`, cut from `origin/feat/maeve-director` at 2b1aebd.
Book: `src/main/resources/assets/frozendawn/patchouli_books/frozen_dawn_guide`.
Phase 1 (audit) is below as first reported, with corrections marked. Phase 2 results
are in the last section.

## Linter

```
java tools/ManualLint.java                 # whole book, per-entry report, exit 1 on errors
java tools/ManualLint.java --entry o2_     # one entry
java tools/ManualLint.java --dump          # print every predicted line with its y
java tools/ManualLint.java --stats         # words per page and natural line counts
java tools/ManualLint.java --mode OVERFLOW # predict another Patchouli overflow mode
```

Needs Java 21 and the vanilla client resources jar (found in `build/moddev/artifacts`
or the Gradle neoform cache). No game launch.

What it models, ported from Patchouli 1.21.1-92 (`BookTextParser`, `TextLayouter`):

- Vanilla glyph widths read from the font bitmaps (space provider, `nonlatin_european`,
  `accented`, `ascii`), bold +1 per glyph, `$(...)` macros and commands, the style stack.
- Text page width 116, line height 9, start y 22 (page 0), 12 (titled page), -4
  (untitled page), 59 (crafting page text), 120 (image page text).
- The default overflow mode RESIZE (also what `run-lab/config/patchouli-client.toml`
  uses): a page that does not fit is laid out again with a smaller scale until it does.
- Paper edge from `orsa_book.png`: clean paper ends at page-local row 149; rows 150-154
  are bevel. Corner marks: left page x 106-115 y 144-146, right page x 109-116 y 141-149.

Checks: overflow (needs RESIZE shrink), past paper, page-mark collision, title wider
than the page, mid-word split, leading space, two or more blank lines, leading or
trailing break, unknown commands, raw `\n`, emoji, glyphs only Unifont can draw.

Required cases, all flagged: O2 Canisters p1, p2, p3, p5, p7 overflow, the p2
"th|e" and "button-pre|ssing" splits and leading space; EVA p1-p5; Thermal Heater p1
(29 natural lines, room for 14, shrunk to 71%).

## Root cause of the unhyphenated mid-word splits

35 splits, two causes.

1. **Patchouli RESIZE re-layout bug (27 splits).** `TextLayouter` keeps `widthSoFar`
   between passes. When a page overflows and is laid out again at a smaller scale, the
   stale width sends `breakLine` down its no-overflow path (`flush(); y += lineHeight`),
   which never updates `lineStart`. Every later break in that paragraph uses offsets
   shifted by the flushed span length. In O2 p2 the flushed span is "Automatic:" (10
   characters), which moves the legal break before "button" to "button-pre|ssing".
   It needs both a page that overflows and a formatting span boundary at the start of a
   paragraph. Every one disappears when the page fits without resizing, so splitting
   overflowing pages fixes them; no text change is needed for the split itself.
2. **Java `BreakIterator` line rules (8 splits).** The legacy iterator allows a break
   after "°" and between a digit and "x": "-10°|C", "-70°|C", "0.6|x". These happen even
   at full scale. Fix by rewording so the value does not land at a line end, or by
   writing "0.6 times" instead of "0.6x".

## Results (phase 1)

Correction: Patchouli draws the entry name on page 1 of an entry and ignores that
page's title, so the first count included 14 page-1 titles that never render. The
linter now skips them: 472 errors, 37 overwide titles.

75 of 84 entries/categories have issues: 486 errors, 6 warnings.

| Finding | Count |
|---|---|
| Overflow (page shrunk by RESIZE) | 186 |
| Past paper | 141 |
| Title too wide | 51 |
| Page mark collision | 48 |
| Mid-word split | 35 |
| Leading space | 22 |
| Unknown command `$(br22)` (miteaway p3, x3) | 3 |
| Leading break (warning) | 6 |

No page had two or more consecutive blank lines.

Widest titles: "ORSA Incident Report TIR-2042-1171" 186 px, "Replacement Thermocouple
Request" 178, "Ingest Node Maintenance Bulletin" 164, "A Fun Guide to Food Spoilage!" 147,
"A Fun Guide to Hyperthermia!" 144, "A Fun Guide to Hypothermia!" 138, the Mk.II and
Mk.III canister page titles, and the "THREAT REPORT: ..." page titles. Limit is 116 px.

### Per entry

Counts per finding. Entries marked (T) are modified or replaced by `origin/thermal-2.0`.

| Entry | Overflow | Past paper | Page mark | Title | Split | Lead space | Markup | Lead break |
|---|---|---|---|---|---|---|---|---|
| book.json | 1 |  |  |  |  |  |  |  |
| un_notice/evacuation | 1 |  |  |  |  | 3 |  |  |
| orsa_intel/what_is_orsa | 8 | 8 | 3 |  |  |  |  |  |
| orsa_intel/projected_timeline (T) | 4 | 5 | 1 | 2 | 2 |  |  | 1 |
| orsa_intel/villager_journal | 2 | 1 | 1 |  |  |  |  |  |
| orsa_intel/orsa_bulletin | 4 | 4 |  | 4 |  |  |  |  |
| orsa_intel/vasik_log | 5 | 3 | 1 | 1 |  |  |  |  |
| orsa_intel/document_recall | 1 | 1 | 1 | 2 |  |  |  |  |
| orsa_intel/incident_report | 7 | 4 | 2 | 2 | 2 | 1 |  |  |
| orsa_intel/satellite_log | 4 | 4 | 1 | 1 |  |  |  |  |
| orsa_intel/launch_manifest | 1 | 1 | 1 |  |  |  |  |  |
| orsa_intel/board_packet | 5 | 4 | 1 | 2 |  |  |  |  |
| orsa_intel/camp_field_report | 3 | 3 |  |  |  |  |  |  |
| orsa_intel/martian_command_packet | 2 | 1 |  | 1 |  |  |  |  |
| orsa_intel/newspaper_cannibal | 2 | 1 |  | 1 |  |  |  |  |
| orsa_intel/camp_transfer_log | 1 | 1 | 1 |  |  |  |  |  |
| orsa_intel/newspaper_conspiracy | 2 | 2 |  | 1 |  |  |  |  |
| orsa_intel/newspaper_weather | 2 | 1 | 1 | 1 |  |  |  |  |
| orsa_intel/station_upload_log | 1 | 1 |  |  |  |  |  |  |
| orsa_intel/newspaper_missing | 2 | 1 | 1 | 1 |  |  |  |  |
| orsa_intel/station_requisition | 1 | 1 |  | 1 |  |  |  |  |
| orsa_intel/cargo_drop_manifest | 1 | 1 |  |  |  |  |  |  |
| orsa_intel/station_maintenance_bulletin | 1 |  |  | 1 |  |  |  |  |
| orsa_intel/vehicle_transcript_abandoned | 2 | 2 | 1 | 1 |  |  |  |  |
| orsa_intel/station_relay_diagnostics | 2 | 1 |  | 1 |  |  |  |  |
| orsa_intel/vehicle_transcript_breakdown | 2 | 2 | 1 | 1 |  |  |  |  |
| orsa_intel/vehicle_transcript_failed | 2 | 2 | 1 | 1 |  |  |  |  |
| getting_started/overview (T) | 2 | 1 | 1 |  |  |  |  | 1 |
| getting_started/phase_timeline (T) | 3 | 2 | 1 |  | 1 |  |  |  |
| getting_started/hypothermia (T) | 7 | 4 | 1 | 2 | 2 | 3 |  |  |
| getting_started/hyperthermia (T) | 6 | 5 |  | 2 | 4 | 1 |  |  |
| getting_started/food_spoilage (T) | 7 | 6 |  | 2 | 1 | 3 |  |  |
| getting_started/hot_wings | 1 | 2 |  |  |  |  |  |  |
| orsa_rd/frostbite_warning | 2 | 2 |  |  |  |  |  |  |
| heating/thermal_core | 1 | 1 | 1 |  |  |  |  |  |
| heating/thermal_heater (T) | 1 | 1 |  |  |  |  |  |  |
| heating/insulated_glass (T) | 1 | 1 | 1 |  |  |  |  |  |
| heating/heater_upgrades (T) | 1 | 1 | 1 | 2 | 1 |  |  |  |
| heating/thermal_capacitor (T) | 1 | 1 |  |  |  |  |  |  |
| heating/thermal_container | 2 | 1 | 1 |  |  |  |  |  |
| heating/orsa_multitool | 1 | 1 |  |  |  |  |  |  |
| heating/frost_ward_torch | 1 |  |  |  |  |  |  |  |
| heating/miteaway | 4 | 2 |  |  | 1 |  | 3 |  |
| heating/cryo_fuel | 1 |  |  |  |  |  |  |  |
| heating/phase_barometer | 1 | 2 |  |  |  |  |  |  |
| heating/geothermal_vents | 4 | 3 | 2 |  | 1 | 2 |  |  |
| orsa_equipment/insulated_clothing | 2 | 2 | 1 | 1 |  |  |  |  |
| orsa_equipment/heavy_insulation | 2 | 2 |  | 2 | 1 | 1 |  |  |
| orsa_equipment/eva_suit (T) | 4 | 4 | 3 |  |  |  |  |  |
| orsa_equipment/snowshoes | 1 | 1 |  |  |  |  |  |  |
| orsa_equipment/o2_canisters | 5 | 5 | 2 | 3 | 2 | 2 |  |  |
| orsa_equipment/continuity_protocol | 4 | 3 | 2 | 1 |  |  |  |  |
| orsa_equipment/blizzard_goggles | 1 | 1 | 1 |  | 1 |  |  |  |
| orsa_equipment/surveyor_lens | 6 | 3 |  | 1 | 8 | 3 |  |  |
| orsa_equipment/ice_claws | 1 | 1 |  |  |  |  |  |  |
| orsa_equipment/acheronite_compass | 2 | 1 |  |  |  |  |  |  |
| orsa_equipment/comfort_items | 1 | 1 |  | 1 |  |  |  |  |
| orsa_rd/acheronite_crystals | 3 |  |  |  |  |  |  |  |
| orsa_rd/acheron_forge | 2 | 1 |  |  |  |  |  |  |
| orsa_rd/acheronite_tools | 1 | 1 | 1 |  |  |  |  |  |
| orsa_rd/acheronite_armor | 2 | 1 | 1 |  |  |  |  |  |
| orsa_rd/lined_eva | 2 | 1 | 1 | 1 |  |  |  |  |
| orsa_rd/soul_harvesting | 2 | 2 |  |  | 2 | 1 |  |  |
| orsa_rd/acheronite_block | 1 |  |  |  |  |  |  |  |
| advanced/ice_shards | 1 | 1 | 1 |  |  |  |  |  |
| advanced/frozen_heart | 2 | 1 |  |  |  |  |  |  |
| advanced/frozen_atmosphere_shard | 4 | 2 |  | 2 | 3 | 2 |  |  |
| advanced/geothermal_core (T) | 3 | 1 | 1 |  | 1 |  |  |  |
| advanced/transponder | 3 |  | 2 | 1 |  |  |  | 1 |
| advanced/fuel_processing_silo | 4 | 3 |  |  | 2 |  |  | 1 |
| advanced/rocket_assembly | 1 | 2 | 1 |  |  |  |  | 2 |
| threats/frostbitten | 2 | 1 |  | 1 |  |  |  |  |
| threats/hollow | 2 | 1 | 1 | 1 |  |  |  |  |
| threats/returned | 4 | 5 | 2 | 2 |  |  |  |  |
| threats/frostmite | 2 | 3 | 1 | 1 |  |  |  |  |

Entries not listed are clean. Full detail: run the linter.

### Other text defects

- `heating/miteaway` p3: `$(br22)` three times, renders literally.
- `threats/hollow`: "(noPhysics)" is developer jargon in ORSA voice.
- `orsa_intel/document_recall` title uses a raw `§c` color code.
- `orsa_equipment/continuity_protocol` p3: `$(k:...)` keybind text, width depends on the
  player's binding; leave headroom on that page.
- Three entries share the name "Recorded Audio Transcript" and two pairs share sortnums
  with neighbours; Patchouli then orders them alphabetically.

### EVA blank placeholder page

Not reproduced. `eva_suit.json` has five text pages and six crafting pages on both this
base and `thermal-2.0`; no empty text page, and every crafting page names a recipe that
exists. If the owner still sees a blank page in game, a screenshot or the page number
would locate it.

## Missing content (report only)

Values from the code on this base:

- **Heater range and warmth.** `TemperatureManager.getHeaterHeat`: base radius 7 /
  +35 C, Iron 9 / +50, Gold 11 / +65, Diamond 14 / +80. A capacitor doubles radius and
  multiplies warmth by 1.5. Phase 5+ unsheltered heaters lose 40% radius. Not in the
  book.
- **Refueling.** Right-click with fuel to refuel; empty hand opens the menu. Not stated.
- **Fuel durations.** Coal/charcoal 24000 ticks (20 min), blaze powder 12000 (10 min),
  coal block 240000 (200 min), cryo fuel 96000 (80 min), divided by the tier's fuel
  multiplier. Consumption x1 phases 1-3, x2 phase 4, x4 phase 5, x8 phase 6
  (`ENABLE_FUEL_PHASE_SCALING`). Fuel does not burn in unloaded chunks. None of this is in
  the book.
- **Sheltered.** `isSheltered`: any solid block or Insulated Glass within 4 blocks above.
  The book never defines it, though heater and exposure behavior depend on it.
- **Sealed rooms and airlocks.** Not on this base; they arrive with
  `feat/atmospheric-breach` / `thermal-2.0`, which add the entries.
- **Thermal Core needs the Nether.** Correction: Thermal Core p2 already says to gather
  blaze rods from the Nether early. Covered.
- **Geothermal Core acquisition.** Recipe page exists (Frozen Heart + 2 Thermal Cores +
  diamond blocks + obsidian); no text on where the Frozen Heart comes from on that page.
- **Suit Patch Kit.** Only a crafting page in the EVA entry and one line on EVA p5; no
  explanation of punctures, patch time or failure.
- **HUD boxes.** Temperature, air status, bubble and suit integrity HUDs are drawn
  (`ClientEvents` layers); the book mentions the O2 number and a service timer only.
  No page explains reading the temperature box or the integrity readout.
- **Hollow / Frozen Breath.** Correction: covered. The Hollow entry lists the Frozen
  Breath drop and links Cryo Fuel; Cryo Fuel, Frost Ward Torch and Soul Harvesting all
  name the Hollow. Gap: the Resonant also drops Frozen Breath and has no entry.

## Behavior issues (report only)

- **Locked entries.** 27 entries are advancement-locked (24 of 26 in ORSA Intelligence,
  plus Acheronite Compass, Fuel Processing Silo, Rocket Assembly). Nothing in the book
  says documents unlock entries or where to find them.
- **Manual not re-issued.** `WorldTickHandler.onPlayerJoin` gives the manual once, gated
  by the `frozendawn:received_books` persistent flag. No respawn or clone handler gives
  it back, so it is lost with the inventory on death.
- **ORSA Document does not open.** `OrsaDocumentItem.use` grants the per-document
  advancement, consumes the item and prints "Document archived in your ORSA Field
  Survival Manual". The book is named "ORSA Field Manual", the message does not say which
  entry unlocked, and the item never opens the book. An empty `doc_type` does nothing.

## Rewrite after merge

`origin/thermal-2.0` (7d4648d, includes the atmospheric breach work) rewrites 12 entries
and adds 6. These will be rewritten when the thermal model, ground temperature and
pressurization land; layout fixes made to them now would conflict.

Modified: overview, phase_timeline, hypothermia, hyperthermia, food_spoilage,
thermal_heater, heater_upgrades, insulated_glass, thermal_capacitor, geothermal_core,
eva_suit, projected_timeline.

Added: ration_warmer, airlocks, atmospheric_breach, room_climate, thermostat,
vacuum_combustion.

The linter on `thermal-2.0` reports 594 errors; the six new entries already fail
(ration_warmer 16, airlocks 13, atmospheric_breach 11, room_climate 20, thermostat 10,
vacuum_combustion 4). Running the linter there before that branch merges would catch them.

## Organization assessment (proposals only)

Current order: UN Emergency Notice, ORSA Intelligence, Getting Started, Heating & Tools,
ORSA Equipment, Acheronite, Endgame, Threat Assessment (Endgame and Threats share
sortnum 4).

Problems:

- A new player opens to lore first. ORSA Intelligence (26 entries, 24 locked) sits ahead
  of Getting Started, and "What is ORSA?" (8 pages, up to 39 natural lines per page) is
  its first entry.
- Density. Many pages hold 25-68 natural lines on a 14-16 line page; RESIZE shrinks them
  to 45-75% text, which is the "wall of text". Densest: threats/returned (68),
  threats/frostmite (55), orsa_bulletin p4 (68), what_is_orsa (39), continuity_protocol
  (38); heating single-text entries run about 100 words per page.
- Misfiled entries: `orsa_rd/frostbite_warning` shows in Getting Started;
  `heating/geothermal_vents` is titled "Orbital Geological Note"; MiteAway, Frost Ward
  Torch and Cryo Fuel sit under Heating & Tools.
- Survival basics are split: temperature in Getting Started, heaters in Heating, clothing
  in Equipment, with no single "first night" path.

Proposals:

1. Order: Getting Started, Shelter & Heating, Equipment, Threats, Acheronite, Endgame,
   then ORSA Intelligence and the UN Notice last as an archive.
2. One idea per page: split by the existing bold headings so each page has one topic
   and fits at full scale. This is also the phase 2 overflow fix.
3. Short entry titles (under 116 px); put the long document headers in the first page's
   title or body.
4. Add a short "How documents work" entry at the top of ORSA Intelligence explaining
   locked entries.
5. Move threats, frostbite and gear entries into the categories they describe; give
   duplicate names distinct titles (for example "Audio Transcript: Abandoned").

## Phase 2 recommendation

- Fix layout now only in entries `thermal-2.0` does not touch (63 of the 75 flagged).
  Split overflowing pages, shorten titles, remove leading spaces, reword the eight
  break-iterator splits, fix `$(br22)`, "the The" and "(noPhysics)".
- Leave the 12 (T) entries for the post-merge rewrite, or fix them on top of
  `thermal-2.0` after it merges.
- Treat the organization pass as a separate change after the layout fixes, so the diff
  stays reviewable.
- Re-run the linter until clean; the owner does the visual pass in `runClient`.

## Phase 2 results

Scope: the 63 flagged entries `thermal-2.0` does not touch, plus the landing text.
The 12 (T) entries keep their text for the post-merge rewrite, except the three
"A Fun Guide to..." entry names, which `thermal-2.0` does not change (trial merge with
`git merge-tree` is clean for every book file).

- **Overflow.** Every overflowing page split at paragraph, line or sentence boundaries;
  no text removed. Continuation pages are untitled (16 lines instead of 14). Open
  formatting is reopened on the continuation page. All RESIZE mid-word splits are gone
  with the overflow.
- **Titles.** Entry names: Public Safety Bulletin, Dr. Vasik's Log, Recalled
  PSB-2042-07, Incident Report 1171, Satellite System Log, Mars Command Packet, The
  Northern Star, Missing Persons Board, Thermocouple Request, Ingest Node Bulletin,
  Relay Diagnostics, Insulated Gear (T1), Heavy Insulation (T2), Atmosphere Shard,
  Hypothermia, Hyperthermia, Food Spoilage. The three audio transcripts are now
  Transcript: Cabin Mic / Breakdown / Whiteout. Page titles: Winter Prep Tips, Shelter
  Guidelines, Common Questions, Appendix B: Lighthouse, Session Opening,
  Infrastructure, O2 Canister Mk.I / Mk.II / Mk.III, Lined EVA Suit, Activation,
  Variant C: Architect.
- **Text.** MiteAway `$(br22)` to `$(br2)`; Hollow "(noPhysics)" removed; Returned
  "1.5x damage" to "+50% damage"; Evacuation Notice indent spaces removed; landing
  text padding reduced from eight blank lines to six (the masthead is 47 px, six lines
  are 54 px).
- **Linter.** No errors in the 63 entries or the landing text. Remaining: 107 errors,
  all in the 12 (T) entries. About 20 warnings: continuation pages that begin inside a
  bullet list start one line down, because Patchouli always breaks before `$(li)`.

Not done here: the organization pass (category order, quick reference, item lookup
pages), the missing content, and the behavior issues above.

Owner visual pass: `./gradlew runClient --console=plain`, open the manual, page through
O2 Canisters, What is ORSA?, The Returned and MiteAway.
