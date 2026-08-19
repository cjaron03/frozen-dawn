#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/ui/suit"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

say -v Samantha -o "$TMP/stillpoint_field.aiff" \
  "Localized exclusion field detected. Acoustic transmission will resume within established boundary."
ffmpeg -hide_banner -loglevel error -y -i "$TMP/stillpoint_field.aiff" \
  -af "highpass=f=170,lowpass=f=5200,acompressor=threshold=-22dB:ratio=2.2:attack=8:release=100,volume=1.12" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/stillpoint_field.ogg"
