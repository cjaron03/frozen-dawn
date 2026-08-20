#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/block/stillpoint_core"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "anoisesrc=color=brown:amplitude=0.7:duration=4:sample_rate=48000" \
  -f lavfi -i "sine=frequency=34:duration=4:sample_rate=48000" \
  -f lavfi -i "sine=frequency=67:duration=4:sample_rate=48000" \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.24:duration=4:sample_rate=48000" \
  -filter_complex \
  "[0:a]highpass=f=18,lowpass=f=240,volume='0.04+0.62*(t/4)*(t/4)':eval=frame[a0]; \
   [1:a]tremolo=f=3.2:d=0.55,volume='0.03+0.44*(t/4)*(t/4)*(t/4)':eval=frame[a1]; \
   [2:a]tremolo=f=6.7:d=0.72,volume='0.02+0.20*(t/4)*(t/4)':eval=frame[a2]; \
   [3:a]highpass=f=1700,lowpass=f=6200,volume='0.01+0.23*(t/4)*(t/4)*(t/4)':eval=frame[a3]; \
   [a0][a1][a2][a3]amix=inputs=4:normalize=0,acompressor=threshold=0.24:ratio=5:attack=8:release=100,alimiter=limit=0.92,afade=t=in:st=0:d=0.18[a]" \
  -map "[a]" -ar 48000 -ac 1 "$TMP/charge.wav"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.9:duration=3.6:sample_rate=48000" \
  -f lavfi -i "anoisesrc=color=brown:amplitude=0.8:duration=3.6:sample_rate=48000" \
  -f lavfi -i "sine=frequency=29:duration=3.6:sample_rate=48000" \
  -f lavfi -i "sine=frequency=58:duration=3.6:sample_rate=48000" \
  -f lavfi -i "aevalsrc=0.32*sin(2*PI*(110+720*t*t)*t)*exp(-1.25*t):d=3.6:s=48000" \
  -filter_complex \
  "[0:a]highpass=f=420,lowpass=f=7600,afade=t=in:st=0:d=0.04,afade=t=out:st=0.18:d=1.25,volume=0.82[a0]; \
   [1:a]highpass=f=18,lowpass=f=185,afade=t=in:st=0:d=0.03,afade=t=out:st=1.45:d=2.05,volume=0.88[a1]; \
   [2:a]afade=t=out:st=0.9:d=2.5,volume=0.72[a2]; \
   [3:a]tremolo=f=7.5:d=0.38,afade=t=in:st=0:d=0.08,afade=t=out:st=1.1:d=2.25,volume=0.42[a3]; \
   [4:a]aecho=0.8:0.55:95|210:0.34|0.16,volume=0.58[a4]; \
   [a0][a1][a2][a3][a4]amix=inputs=5:normalize=0,acompressor=threshold=0.23:ratio=6:attack=3:release=180,alimiter=limit=0.96[a]" \
  -map "[a]" -ar 48000 -ac 1 "$TMP/form.wav"

oggenc -Q -q 5 -o "$OUT/charge.ogg" "$TMP/charge.wav"
oggenc -Q -q 6 -o "$OUT/form.ogg" "$TMP/form.wav"
