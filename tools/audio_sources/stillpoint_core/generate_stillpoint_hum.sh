#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/block/stillpoint_core"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi \
  -i "aevalsrc=(0.25*sin(2*PI*55*t)*(0.78+0.22*sin(2*PI*0.25*t)))+(0.38*sin(2*PI*110*t))+(0.21*sin(2*PI*220*t+0.55*sin(2*PI*0.5*t)))+(0.10*sin(2*PI*440*t)*(0.5+0.5*sin(2*PI*0.25*t)))+(0.045*sin(2*PI*660*t)):d=8:s=48000" \
  -af "highpass=f=32,lowpass=f=1800,acompressor=threshold=0.22:ratio=3:attack=18:release=220,volume=1.35,alimiter=limit=0.92" \
  -ar 48000 -ac 1 "$TMP/hum.wav"

oggenc -Q -q 5 -o "$OUT/hum.ogg" "$TMP/hum.wav"
