#!/usr/bin/env bash
# ORSA TR-0 Terminus sounds: ffmpeg lavfi synthesis only, fixed noise seed, mono 44.1 kHz Vorbis (oggenc -q 5: ffmpeg's
# own Vorbis encoder only writes stereo, and Minecraft attenuates only mono sounds by distance). The conventions of
# tools/generate_sever_lash_sounds.sh. Sources are recorded in tools/audio_sources/terminus/SOURCES.md.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/item/terminus"
TMP="${TMPDIR:-/tmp}/frozendawn_terminus.wav"
mkdir -p "$OUT"
trap 'rm -f "$TMP"' EXIT

render() { # name, then ffmpeg inputs and filter args ending in [out]
  local name="$1"; shift
  ffmpeg -hide_banner -loglevel error -y "$@" -map "[out]" -ac 1 -ar 44100 -map_metadata -1 -fflags +bitexact -flags:a +bitexact -c:a pcm_s16le "$TMP"
  oggenc -Q -q 5 -s 55001 -o "$OUT/$name.ogg" "$TMP"
}

# Wind-up, 2 s (owner's pick, after Hyperion's silent deathwand): a slow heartbeat, a 48 Hz thump decaying fast, its
# rate quickening from 1.2 to 6 beats a second, under a thin 5.2 kHz whine barely there and rising a little. Heard only
# within 8 blocks. It stops dead: the discharge makes no sound; the world goes quiet instead (TerminusHush).
render windup \
  -f lavfi -i "aevalsrc='0.035*sin(2*PI*(5200*t+150*t*t))*pow(t/2\,1.5)+0.7*sin(2*PI*48*mod(1.2*t+1.2*t*t\,1)/(1.2+2.4*t))*exp(-22*mod(1.2*t+1.2*t*t\,1)/(1.2+2.4*t))*(0.4+0.6*t/2)':s=44100:d=2" \
  -filter_complex "[0:a]afade=t=out:st=1.99:d=0.01,alimiter=limit=0.8:level=0[out]"
