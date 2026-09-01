# Audio Release Provenance Audit

**Audit date:** 2026-09-01

**Scope:** `feat/homo-reliquus` release candidate and its runtime audio assets
**Conclusion:** the focused release blockers found on 2026-08-27 have been
resolved. Undocumented Undone, post-Maeve wind, and interface recordings were
replaced with deterministic procedural audio; the Archivist sobbing source was
replaced with a verified CC0 recording; and all shipped generated speech was
re-rendered through the documented Piper pipeline. Every runtime OGG is now
listed in the generated shipped-audio inventory with a content hash, provenance
group, evidence path, and license class.

This is a provenance review, not a legal opinion. A deterministic edit, a local
file hash, or a filename is evidence of identity and processing, not evidence of
the right to redistribute the underlying recording.

## Cleared or conditionally cleared

These groups have checked-in ledgers and source records that identify a permitted
source or original/procedural generation. Keep the ledger with the source archive
and preserve the listed attribution where applicable. "Cleared" here means the
listed inputs were checked against their source record; it does not clear other
unlisted audio in the same feature family.

| Group | Status | Evidence |
| --- | --- | --- |
| Rimebound | Cleared as original procedural audio | `tools/audio_sources/rimebound/SOURCES.md` and the checked-in generators |
| Stillpoint Core structural sounds | Cleared as original procedural audio | `tools/audio_sources/stillpoint_core/SOURCES.md` and the checked-in generators |
| Master Architect and Thae Iven | Cleared by project-owner authorship declaration | `tools/audio_sources/master_architect/SOURCES.md`; no third-party or generated inputs are declared |
| Generated voices | Cleared for distribution under the voice asset license | `tools/audio_sources/generated_voice/NOTICE.md`; 52 Piper `en_US-amy-medium` derivatives are attributed and distributed under CC BY-SA 4.0 |
| Undone | Cleared as original procedural audio | `tools/audio_sources/undone/SOURCES.md` and `tools/generate_undone_sounds.sh` |
| Post-Maeve wind | Cleared as original procedural audio | `tools/audio_sources/post_maeve_wind/SOURCES.md` and `tools/generate_post_maeve_wind.sh` |
| Surveyor and suit interface cues | Cleared as original procedural audio | `tools/audio_sources/interface/SOURCES.md` and `tools/generate_interface_sounds.sh` |
| Aggregate | Cleared for the listed CC0 inputs | `tools/audio_sources/aggregate/SOURCES.md`; the linked Freesound pages were checked, including `animal_groan.mp3` by Lewis.B.M, sound 571386 |
| Frostwrithe | Conditionally cleared | `tools/audio_sources/frostwrithe/SOURCES.md`; the ledger records CC0 terms, but this audit did not independently re-open every listed source page |
| Hearthrot respiratory layers | Cleared for the listed CC0 inputs | `tools/audio_sources/hearthrot/SOURCES.md`; the cough, wheeze, and choking source pages identify CC0 terms |
| Remnant | Cleared for the listed CC0 inputs | `tools/audio_sources/remnant/SOURCES.md` |
| Resonant | Conditionally cleared | `tools/audio_sources/resonant/SOURCES.md`; the ledger records CC0 terms, but this audit did not independently re-open every listed source page |
| Undone Architect | Conditionally cleared | `tools/audio_sources/undone_architect/SOURCES.md`; the Freesound source is CC0, while the LaSonotheque source remains subject to its recorded royalty-free terms |
| Bloom, Bloombound, and Spore | Conditionally cleared | `tools/audio_sources/bloom/SOURCES.md`; derived from the Undone Architect ledger plus procedural layers |
| Archivist audio | Cleared with retained provenance | `tools/audio_sources/archivist/SOURCES.md`; sobbing uses SnowFightStudios' CC0 recording, the terminal scream is CC BY 4.0, and structural layers inherit their documented sources |
| Archivist terminal scream | Cleared with attribution | Valerie-Vivegnis, "Scream woman pain 4," CC BY 4.0: https://freesound.org/people/Valerie-Vivegnis/sounds/767890/ |

Freesound's own licensing guidance distinguishes CC0, CC BY, and CC BY-NC and
requires attribution for CC BY material: https://freesound.org/help/faq/. The
Archivist scream is therefore not covered by a blanket CC0 or original-assets
statement.

## Resolved in this pass

- Replaced all former macOS/system-voice exports with the local Piper output
  recorded in `tools/audio_sources/generated_voice/polly_manifest.tsv`.
- Added the voice-model attribution, modification notice, and CC BY-SA 4.0 asset
  terms in `tools/audio_sources/generated_voice/NOTICE.md`.
- Replaced undocumented Undone, post-Maeve wind, and Surveyor/UI recordings
  with deterministic procedural synthesis.
- Replaced the owner-supplied Archivist sobbing file with SnowFightStudios'
  verified CC0 source and retained its page, creator, hash, and transformations.
- Classified every shipped runtime OGG in
  `docs/audio/SHIPPED_AUDIO_INVENTORY.tsv`; unknown paths make the generator fail.

## Evidence Reviewed

On 2026-08-27 and 2026-09-01, the following source pages were re-opened and matched to the
checked-in ledgers: Aggregate's listed Freesound inputs, the Frostwrithe scurry,
spider, organic-ice, and icicle-collapse inputs, the Resonant wall-knock,
concrete-drag, and metal-hinge inputs, and the Archivist terminal scream. The
Freesound FAQ was also reviewed for the distinction between CC0, CC BY, and
CC BY-NC. A source page that could not be fetched during this pass is not treated
as independently verified merely because its ledger says CC0.

Local `ffprobe` metadata and matching hashes were used only to identify supplied
and shipped derivatives. They do not establish ownership or redistribution
permission. The former owner-supplied Archivist sobbing file was removed and
replaced with a separately archived CC0 source whose page and hash are recorded
in `tools/audio_sources/archivist/SOURCES.md`.

The generated-voice inventory was checked against the tracked TTS scripts and
runtime sound paths. All 52 manifest entries now resolve to Piper-generated
runtime assets. The Piper model card identifies the Mycroft Mimic 3 voices
dataset, whose repository license is CC BY-SA 4.0. Frozen Dawn records those
files as modified voice-model derivatives under the same asset license; the
Piper engine and model weights are not bundled in the jar.

## Required release checks

1. Run `python3 tools/build_audio_inventory.py` and require all shipped OGG paths
   to classify successfully.
2. Run the full Gradle build and inspect the release jar rather than relying on
   the source tree alone.
3. Keep the generated-voice CC BY-SA 4.0 notice and the Valerie-Vivegnis CC BY
   4.0 credit in release distributions and source archives.
4. Restart the client and audition representative regenerated Undone, Archivist,
   wind, interface, and spoken events before publishing.
