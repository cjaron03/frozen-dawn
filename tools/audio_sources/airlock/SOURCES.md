# ORSA airlock machinery — worn mechanism

Owner selected recording-based concept 4 on October 16, 2026. The shipped nine
cues now use real CC0 recordings: squeaking latches, rattling hardware, a small
machine, compressed air and a physical bell. This replaces the earlier project
procedural cues. No synthesized oscillators, generated noise, speech or music
are mixed into this set.

| Source / creator | Adopted recordings | Rights and evidence |
| --- | --- | --- |
| [Mechanical Sounds / Brian MacIntosh (BMacZero)](https://opengameart.org/content/mechanical-sounds) | squeakyclick1/2, mechanical2, rattle1, clank1 WAV | CC0; [author-page snapshot](licenses/mechanical.txt) |
| [100 CC0 SFX / rubberduck](https://opengameart.org/content/100-cc0-sfx) | machine_03, bell_01/02 OGG archive members | CC0; [pack-page snapshot](licenses/foley100.txt) |
| [Compressed Air-Short-13 / wavecal22](https://freesound.org/people/wavecal22/sounds/753130/) | official public HQ MP3 preview | CC0; [individual-page snapshot](licenses/air_long.txt) |
| [Compressed Air-Short-9 / wavecal22](https://freesound.org/people/wavecal22/sounds/753138/) | official public HQ MP3 preview | CC0; [individual-page snapshot](licenses/air_short.txt) |

All four sources explicitly permit reuse and adaptation under
[CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/),
with a retained [license text snapshot](licenses/license.txt). Attribution is
voluntary; creator credits are retained here and in AUDIO_NOTICE.md. These
recordings and their derivatives are CC0-derived assets; the repository code
license does not relicense them. No other Minecraft mod's assets are included.
No added runtime dependency or unresolved source-rights question.

## Reproduction and exact source ownership

Run `python3 tools/audio_sources/airlock/generate_machinery.py` with Python 3,
ffmpeg and oggenc. `--only` can update specified cue names. The old procedural
renderer is superseded; its exact source remains in Git history before this
change. The current entry point always renders the selected recorded set.

[Source ledger](source-ledger.json) records each retained original's creator,
source and download URL, original filename/format, retrieval UTC, archive member
and archive hash when applicable, local original/hash, license evidence/hash,
processing, review and attribution. Originals under `recordings/` are preserved
byte-for-byte; only the three adopted archive members are retained. Freesound
inputs are MP3 previews, not the account-gated full-resolution WAV downloads.
License snapshots preserve visible public text and links; scripts, forms and
anonymous session tokens are omitted. Raw development sources are not in the jar.

The generator verifies original hashes, trims/filter-recordings at 44.1 kHz,
normalizes layer levels, crossfades repeated machine segments and fades cuts.
It resamples the resulting mono mixes to 24 kHz and encodes Vorbis quality 5.
No pitch or speed shifts are used. [Manifest](manifest.json) records exact
per-cue source trims, filters, onsets, gains, loops, shipped encoding hash,
pre-encode peak, duration, channels and rate. Encoder versions/serial numbers
can change container hashes; preserve the shipped encodings.

- `door_open` / `door_close`: selected latch, hardware travel, rattle and stop.
- `pump_fill`: selected machine with a quiet rattle, 0.94 seconds.
- `pump_recover`: selected compressed-air release and latch, 0.94 seconds.
  Existing elapsed 0/20/40/60-tick events stay unchanged; segments finish before
  the next event and stop naturally if power is lost. No infinite client loop.
- `ready`: selected physical bell 01, trimmed to the familiar short ring.
- `evacuated`: a different physical bell 02 with a quiet residual air release.
- `refuse`: short dry hardware clunk.
- `interrupted`: rattle and mechanical stop.
- `valve`: squeaky hardware and brief air release; AtmosphericBreach still owns
  the separate bulk escaping-air whoosh.

Game-length door, refill and recovery MP3 previews are written to ignored
`build/power-network-evidence/airlock-recorded-sounds/rendered/`. These preview
asset timing only; in-game positional volume and vacuum filtering need an owner
listening pass. Nearby hardware retains the existing quieter vacuum feedback.
