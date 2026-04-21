# Third-Party Audio

This document tracks audio in Frozen Dawn that is not original project audio or vanilla Minecraft runtime audio.

## Release Policy

- Any bundled non-original audio must have a source URL, creator credit, captured usage terms, and a release status before public distribution.
- If redistribution terms cannot be verified, remove or replace the bundled file before release.
- References to `minecraft:*` sound events or music paths are runtime references to the player's installed Minecraft assets. Frozen Dawn does not bundle those files.

## Bundled Third-Party Audio

### `phase4_guest_1.ogg`

- Local file: `src/main/resources/assets/frozendawn/sounds/music/phase4_guest_1.ogg`
- In-game event: `frozendawn:music.phase4_guest_1`
- Track title: `Title Music`
- Creator: Ross Bugden
- Source URL: `https://www.youtube.com/watch?v=v29KVjUXiS8`
- Creator profile: `https://www.instagram.com/rossbugden`
- Release status: Allowed with attribution based on captured source terms; re-verify before major public releases.

Captured source terms indicate the track is free to use and monetize with creator credit. The source also notes Content ID may create a claim on YouTube uploads and that credited uses can dispute it.

Attribution text:

```text
Music: "Title Music" by Ross Bugden (https://www.youtube.com/watch?v=v29KVjUXiS8)
```

## Removed / Blocked Audio

### `forest_night.ogg`

- Previous local file: `src/main/resources/assets/frozendawn/sounds/music/forest_night.ogg`
- Previous in-game event: `frozendawn:music.biome.forest_night`
- Release status: Removed before release.
- Reason: redistribution terms were not verified strongly enough for bundling inside the mod jar.

The early-forest special music path was removed rather than shipped with unclear provenance. Early gameplay now falls back to the existing sparse vanilla-runtime melancholy music pool.

## Evidence Retention Checklist

- Keep screenshots or PDFs of source pages and usage terms in the private release archive.
- Re-verify source terms before major public releases.
- Re-run an audio asset audit before publishing:

```bash
find src/main/resources/assets/frozendawn/sounds -type f \( -iname '*.ogg' -o -iname '*.wav' -o -iname '*.mp3' \) -print | sort
```
