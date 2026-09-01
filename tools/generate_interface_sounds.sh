#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOUNDS="$ROOT/src/main/resources/assets/frozendawn/sounds"
TMP="${TMPDIR:-/tmp}/frozendawn_interface.wav"
mkdir -p "$SOUNDS/ui/suit" "$SOUNDS/ui"

encode() {
  oggenc -Q -q 5 -o "$2" "$1"
}

# Short O2 telemetry pulse with a restrained upper harmonic.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "aevalsrc=(0.42*sin(2*PI*880*t)+0.12*sin(2*PI*1760*t))*exp(-11*t):s=44100:d=0.14" \
  -filter_complex "[0:a]afade=t=in:st=0:d=0.006,afade=t=out:st=0.105:d=0.032,alimiter=limit=0.90,volume=0.58[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$SOUNDS/ui/suit/oxygen_beep.ogg"

# Continuous suit leak bed. TickableSuitLeakSound owns runtime looping and gain.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "anoisesrc=color=white:duration=4:sample_rate=44100:seed=48131" \
  -f lavfi -i "anoisesrc=color=pink:duration=4:sample_rate=44100:seed=48133" \
  -filter_complex \
  "[0:a]highpass=f=1450,lowpass=f=9300,tremolo=f=0.73:d=0.11,volume=0.21[jet];\
[1:a]highpass=f=310,lowpass=f=2800,tremolo=f=0.41:d=0.18,volume=0.10[seal];\
[jet][seal]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.08,afade=t=out:st=3.88:d=0.10,\
alimiter=limit=0.86,volume=0.50[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$SOUNDS/ui/suit/leak_hiss.ogg"

# First-boot ring: layered synthesized partials, no sampled bell or voice.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "sine=frequency=220:duration=7.4:sample_rate=44100" \
  -f lavfi -i "sine=frequency=331:duration=7.4:sample_rate=44100" \
  -f lavfi -i "sine=frequency=557:duration=7.4:sample_rate=44100" \
  -f lavfi -i "sine=frequency=887:duration=7.4:sample_rate=44100" \
  -filter_complex \
  "[0:a]tremolo=f=0.32:d=0.18,afade=t=in:st=0:d=0.55,afade=t=out:st=5.4:d=1.9,volume=0.25[a];\
[1:a]vibrato=f=0.43:d=0.05,adelay=180|180,afade=t=in:st=0:d=0.45,afade=t=out:st=5.0:d=2.2,volume=0.19[b];\
[2:a]vibrato=f=0.71:d=0.04,adelay=520|520,afade=t=in:st=0:d=0.38,afade=t=out:st=4.8:d=2.35,volume=0.12[c];\
[3:a]adelay=920|920,afade=t=in:st=0:d=0.28,afade=t=out:st=4.2:d=2.8,volume=0.07[d];\
[a][b][c][d]amix=inputs=4:normalize=0,aecho=0.7:0.45:93|211:0.18|0.10,\
highpass=f=95,lowpass=f=5200,alimiter=limit=0.90,volume=0.54[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$SOUNDS/ui/orsa_awakening_ring.ogg"

rm -f "$TMP"
