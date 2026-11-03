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

# Wind-up, 2 s: a low tone rising an octave, 48 to 96 Hz on an exponential glide, with its octave a hair sharp so the
# two beat faster as it climbs, and a thin glassy hiss (pink noise at 3 kHz) swelling under it. It swells to the end
# and stops dead: the discharge itself makes no sound.
render windup \
  -f lavfi -i "aevalsrc='0.55*sin(2*PI*138.5*(pow(2\,t/2)-1))+0.22*sin(2*PI*1.004*277*(pow(2\,t/2)-1))':s=44100:d=2" \
  -f lavfi -i "anoisesrc=color=pink:duration=2:sample_rate=44100:seed=55002" \
  -f lavfi -i "aevalsrc='min(1\, t/0.15)*(0.45+0.55*t/2)':s=44100:d=2" \
  -f lavfi -i "aevalsrc='0.08*pow(t/2\,2)':s=44100:d=2" \
  -filter_complex "[1:a]bandpass=f=3000:width_type=q:w=1.2[hiss0];[hiss0][3:a]amultiply[hiss];[0:a][hiss]amix=inputs=2:normalize=0[tone];[tone][2:a]amultiply,afade=t=out:st=1.98:d=0.02,alimiter=limit=0.8:level=0[out]"
