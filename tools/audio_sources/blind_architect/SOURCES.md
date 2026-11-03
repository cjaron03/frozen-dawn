# Blind Architect click sources

Source rights checked September 30, 2026 Pacific against the individual Freesound
asset pages. All three recordings are Creative Commons Zero 1.0 Universal:
https://creativecommons.org/publicdomain/zero/1.0/.

Original WAV downloads require a Freesound login. For this sound-design pass use
the publicly shared high-quality MP3 files identified by `data-static-file-url`
on each official source page. These are source-site previews of the recordings,
not the full-resolution WAV originals. Preserve them unmodified in `raw/` and
identify this encoding accurately in the processing manifest. No login bypass or
game-extracted audio is used.

## Xemptful — Mouth Clicks

- Original source URL: https://freesound.org/people/Xemptful/sounds/370869/
- Creator/uploader: Xemptful.
- Original recording filename: `370869__xemptful__mouth-clicks.wav`.
- Original recording format: 96 kHz, stereo, 16-bit WAV; page duration 1.369 s.
- Approved downloadable source: https://cdn.freesound.org/previews/370/370869_3531538-hq.mp3
- Local unchanged source: `raw/370869_3531538-hq.mp3`.
- License: CC0 1.0 Universal; page explicitly identifies Creative Commons 0.
- License evidence: `evidence/xemptful-370869.html` and canonical CC0 license.
- Asset role: natural tongue-click transients for processed click variations.
- Required attribution: none under CC0. Retain creator/source credit voluntarily
  here and in the packaged `AUDIO_NOTICE.md` if incorporated into runtime assets.
- Review status: license checked; download/processing/audition status is recorded
  in the generated manifest. Owner acceptance of the revised bank is recorded
  in `selection.json`.

## fleurescence — tongue click

- Original source URL: https://freesound.org/people/fleurescence/sounds/573148/
- Creator/uploader: fleurescence.
- Original recording filename: `573148__fleurescence__tongue-click.wav`.
- Original recording format: 48 kHz, mono, 16-bit WAV; page duration 0.337 s.
- Approved downloadable source: https://cdn.freesound.org/previews/573/573148_12947664-hq.mp3
- Local unchanged source: `raw/573148_12947664-hq.mp3`.
- License: CC0 1.0 Universal; page explicitly identifies Creative Commons 0.
- License evidence: `evidence/fleurescence-573148.html` and canonical CC0 license.
- Asset role: alternative tongue-click transient.
- Required attribution: none under CC0. Retain creator/source credit voluntarily.
- Review status: license checked; processing/selection status in manifest.

## Processing and outputs

`generate_clicks.py` preserves source hashes in `manifest.json`, decodes to mono
48 kHz PCM, isolates complete transients, applies subtle pitch changes, removes
low rumble, softens extreme high frequencies, adds quiet 19/33 ms reflections
and a restrained decaying resonance, then encodes Ogg Vorbis quality 4 with fixed
serial numbers. Six prepared effects are in `processed/click_1.ogg` through
`processed/click_6.ogg`. They are not registered or packed in runtime resources.

Variants 1–3 and 5 use Xemptful's recording; variants 4 and 6 use fleurescence's.
The manifest records crops, speed ratios, resonance gain, download timestamps
(UTC from retained source file modification time), source/output SHA-256 hashes,
tool versions and decoded audio measurements. All six have a final decoded peak
below 0.99; each is approximately 0.29–0.33 seconds, mono, 48 kHz.

Regenerate from the owning checkout:

```sh
python3 tools/audio_sources/blind_architect/generate_clicks.py
```

WAV previews remain outside runtime resources in `build/blind-architect-audio/`:
`click-audition.wav` plays variants 1–6 twice each, while `cadence-example.wav`
illustrates wandering, investigation, pursuit and close-contact timing. Both
reels decode the actual prepared Ogg files. The timing reel is not evidence of
AI behavior. No pre-recorded cadence loop is shipped; the eventual AI controls
the intervals between individual clicks. This earlier bank is retained for
comparison; the selected throat bank is described below.

When the selected effects are integrated into runtime resources, add their
event/subtitle, retain the credits in `AUDIO_NOTICE.md`, classify them as
CC0-derived in `tools/build_audio_inventory.py`, regenerate the inventory and
verify the mod build. Until then the shipped audio inventory stays unchanged.

The source-recording layers remain CC0-derived. Frozen Dawn's original processing
and procedural additions do not revoke the source recordings' CC0 status. Source
and generation files are development materials; the mod jar packs runtime audio
and its notices only. Processing is reproducible subject to ffmpeg and
vorbis-tools versions, recorded in the manifest.

## Throat-performance revision

The owner requested a direction close to The Last of Us Clickers. Naughty Dog's
[account of the original sound design](https://www.naughtydog.com/blog/the_last_of_us_part_i_hbo_tv_show_clickers)
describes human back-of-the-throat performances and increasingly vocal alert
states. This is a design reference only; no Naughty Dog game/show recordings,
actor recordings or samples were acquired or incorporated.

### Fupicat — Weird Throat Clicking

- Original source URL: https://freesound.org/people/Fupicat/sounds/688417/
- Creator/uploader: Fupicat; the uploader describes performing a deep bass voice
  that produces rapid throat clicks.
- Original filename: `688417__fupicat__weird-throat-clicking.ogg`.
- Original format reported by the page: mono 48 kHz Ogg Vorbis, 23.420 seconds,
  approximately 14 kbps. Its original low bitrate limits the available detail.
- Acquired source: https://cdn.freesound.org/previews/688/688417_7724198-hq.mp3
- Local unchanged source: `raw/688417_7724198-hq.mp3`.
- SHA-256: `d271d12a210eff760555aee008ac78a5b42fb8ccfb3c2951ac41d2eef77635ef`.
- License: CC0 1.0 Universal, verified on the individual official asset page.
- Retained evidence: `evidence/fupicat-688417.html`; canonical license evidence
  remains `evidence/cc0-1.0.html`.
- Required attribution: none under CC0; retain voluntary creator/source credit.
- Retrieval timestamp and acquired encoding metadata: `throat-manifest.json`.

`generate_throat_clicks.py` preserves the earlier six dry clicks and adds six
`throat_click_*.ogg` pulses plus `throat_idle_1.ogg`, `throat_search_1.ogg` and
`throat_lock_1.ogg`. Each mixes an actual throat-performance crop with irregular
human tongue-click accents, a quiet lower throat layer, filtering, mild saturation
and short reflections. No oscillator or generated voice supplies the vocal layer.
The short pulses are approximately 0.39–0.41 seconds and fit inside the proposed
fastest 0.5-second pulse interval. Multiple audible transients inside one burst
decorate a single sensing emission; they must not grant extra returns.
The longer 0.84–1.00-second vocal accents are presentation accents. The runtime controller schedules idle/search accents
at long intervals and a lock accent on acquisition; in-game mixing is pending.

Regenerate after the first bank exists:

```sh
python3 tools/audio_sources/blind_architect/generate_throat_clicks.py
```

`throat-manifest.json` records source and final-file hashes, exact crops and
processing settings, tool versions and decoded measurements. The comparison
reel `build/blind-architect-audio/throat-comparison.wav` plays the earlier dry
click twice, then all six throat pulses, then idle/search/lock vocal accents.
It decodes the final mono 48 kHz Ogg files. Encoded peaks are below 0.99 and all
short pulse durations are checked against 0.5 seconds. These checks do not prove
perceptual similarity or in-game listening acceptance. Runtime events are
registered for click/idle/search/lock, with these nine approved output files
copied byte-for-byte to `assets/frozendawn/sounds/entity/blind_architect/`.
`AUDIO_NOTICE.md` and the shipped audio inventory retain the source credits.

Owner listening feedback: "i like it" after the throat-comparison preview.
This selects the six throat pulses and three vocal accents for runtime
integration. `selection.json` preserves the approval timestamp, exact feedback,
reviewed audition hash and selected output hashes. Approval covers the audition
sound direction; in-game mixing, spatial playback and AI timing still need a
client pass. Regenerating assets does not automatically approve changed hashes.

## Archived page sanitization

The tracked source-page HTML retains the creator, recording, download and CC0 license evidence. An unrelated site-embedded Mapbox access token was replaced with `[REDACTED_SITE_EMBEDDED_MAPBOX_TOKEN]` before GitHub delivery. The original pages and before/after hashes are preserved locally under `build/blind-integration-evidence/delivery-20261011/`; source audio and shipped assets are unchanged.
