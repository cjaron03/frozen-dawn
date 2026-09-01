#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/ambient"
TMP="${TMPDIR:-/tmp}/frozendawn_post_maeve_wind.wav"
mkdir -p "$OUT"

encode() {
  oggenc -Q -q 4 -o "$2" "$1"
}

wind() {
  local output="$1" seed="$2" duration="$3" floor="$4" motion="$5" whistle="$6"
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "anoisesrc=color=pink:duration=$duration:sample_rate=44100:seed=$seed" \
    -f lavfi -i "anoisesrc=color=white:duration=$duration:sample_rate=44100:seed=$((seed + 17))" \
    -f lavfi -i "sine=frequency=$whistle:duration=$duration:sample_rate=44100" \
    -filter_complex \
    "[0:a]highpass=f=$floor,lowpass=f=5200,tremolo=f=$motion:d=0.23,\
flanger=delay=2.4:depth=1.6:regen=2:width=37:speed=0.10,volume=0.25[air];\
[1:a]highpass=f=620,lowpass=f=8600,tremolo=f=0.13:d=0.34,volume=0.055[grain];\
[2:a]vibrato=f=0.21:d=0.07,tremolo=f=0.11:d=0.91,highpass=f=300,lowpass=f=1800,volume=0.018[edge];\
[air][grain][edge]amix=inputs=3:normalize=0,acompressor=threshold=-29dB:ratio=2.2:attack=80:release=700,\
afade=t=in:st=0:d=1.8,afade=t=out:st=$(awk "BEGIN {print $duration-2.0}"):d=1.95,\
alimiter=limit=0.88,volume=0.52[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
  encode "$TMP" "$output"
}

# The post-erasure world retains air movement but loses the former low roar.
wind "$OUT/wind_post_maeve_light.ogg" 91081 57 260 0.11 731
wind "$OUT/wind_post_maeve_strong.ogg" 91097 63 190 0.14 619

rm -f "$TMP"
