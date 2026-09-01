# Audio Release Provenance Audit

**Audit date:** 2026-08-27  
**Scope:** `feat/homo-reliquus` release candidate and its runtime audio assets  
**Conclusion:** the branch is not yet ready for a public release artifact. Several
late-game groups have usable source ledgers, but the older Undone, Successor,
wind, Surveyor/UI, and generated-voice groups do not currently have enough
source or platform evidence to clear redistribution. The Master Architect and
Thae Iven core files are recorded as project-owner-authored in their ledger;
adjacent spoken UI files remain separate and unresolved.

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
| Aggregate | Cleared for the listed CC0 inputs | `tools/audio_sources/aggregate/SOURCES.md`; the linked Freesound pages were checked, including `animal_groan.mp3` by Lewis.B.M, sound 571386 |
| Frostwrithe | Conditionally cleared | `tools/audio_sources/frostwrithe/SOURCES.md`; the ledger records CC0 terms, but this audit did not independently re-open every listed source page |
| Hearthrot respiratory layers | Cleared for the listed CC0 inputs | `tools/audio_sources/hearthrot/SOURCES.md`; the cough, wheeze, and choking source pages identify CC0 terms |
| Remnant | Cleared for the listed CC0 inputs | `tools/audio_sources/remnant/SOURCES.md` |
| Resonant | Conditionally cleared | `tools/audio_sources/resonant/SOURCES.md`; the ledger records CC0 terms, but this audit did not independently re-open every listed source page |
| Undone Architect | Conditionally cleared | `tools/audio_sources/undone_architect/SOURCES.md`; the Freesound source is CC0, while the LaSonotheque source remains subject to its recorded royalty-free terms |
| Bloom, Bloombound, and Spore | Conditionally cleared | `tools/audio_sources/bloom/SOURCES.md`; derived from the Undone Architect ledger plus procedural layers |
| Archivist structural layers | Conditionally cleared | `tools/audio_sources/archivist/SOURCES.md`; ice and hurt layers inherit their source ledgers, while the supplied sobbing source remains unresolved |
| Archivist terminal scream | Cleared with attribution | Valerie-Vivegnis, "Scream woman pain 4," CC BY 4.0: https://freesound.org/people/Valerie-Vivegnis/sounds/767890/ |

Freesound's own licensing guidance distinguishes CC0, CC BY, and CC BY-NC and
requires attribution for CC BY material: https://freesound.org/help/faq/. The
Archivist scream is therefore not covered by a blanket CC0 or original-assets
statement.

## Not cleared yet

The following files or groups remain release blockers until the repository records
the source URL and terms, or the assets are replaced with original/procedural
audio:

- Undone entity audio and the post-Maeve wind variants.
- Heart Successor voice files, ORSA awakening speech, and adjacent Thaeven UI audio.
- Surveyor Lens and UI/interface sounds whose source is not documented.
- User-supplied files whose exact source license was not recorded, including the
  Archivist sobbing source. The local file metadata is not sufficient to establish
  redistribution rights.
- macOS Samantha or other system-voice exports. The inventory and generation
  paths are recorded in `tools/audio_sources/generated_voice/SOURCES.md`; the
  applicable license version and redistribution permission for each generated
  performance must be confirmed before shipping. See Apple's license hub:
  https://www.apple.com/legal/sla/.

The runtime jar may contain processed derivatives, but that does not resolve the
underlying rights. The raw-source copies in GitHub also need the same provenance
review; source-archive publication is not automatically covered by a mod-code
license.

## Evidence Reviewed

On 2026-08-27, the following source pages were re-opened and matched to the
checked-in ledgers: Aggregate's listed Freesound inputs, the Frostwrithe scurry,
spider, organic-ice, and icicle-collapse inputs, the Resonant wall-knock,
concrete-drag, and metal-hinge inputs, and the Archivist terminal scream. The
Freesound FAQ was also reviewed for the distinction between CC0, CC BY, and
CC BY-NC. A source page that could not be fetched during this pass is not treated
as independently verified merely because its ledger says CC0.

Local `ffprobe` metadata and matching hashes were used only to identify supplied
and shipped derivatives. They do not establish ownership or redistribution
permission. In particular, the owner-supplied Archivist sobbing file has no
recoverable source-page/license evidence in this repository.

The generated-voice inventory was checked against the tracked TTS scripts and
runtime sound paths. The scripts establish that several outputs were rendered
with macOS Samantha, but do not establish a redistribution grant for the
exported performances. Legacy voice files without a retained generator remain
unresolved rather than being inferred from filenames or timbre.

## Required release actions

1. Add a source URL, creator, license/version, retrieval date, and processing note
   for each unresolved recording.
2. Obtain written redistribution permission or replace any source that cannot be
   cleared for a public mod jar and source archive.
3. Replace or separately license generated voice performances; do not call them
   Frozen Dawn-original audio merely because the text and processing are original.
4. Keep the Valerie-Vivegnis CC BY 4.0 credit in the release notices and source
   archive.
5. Re-run this audit after the remaining ledgers are complete, then publish the
   GitHub release artifact.
