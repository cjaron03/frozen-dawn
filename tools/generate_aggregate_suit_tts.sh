#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/ui/suit"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

CLANG_MODULE_CACHE_PATH="$TMP/module-cache" clang -fobjc-arc \
  -framework AppKit "$ROOT/tools/generate_samantha_tts.m" \
  -o "$TMP/generate_samantha_tts"

render_line() {
  name=$1
  text=$2
  "$TMP/generate_samantha_tts" "$TMP/$name.aiff" "$text"
  ffmpeg -hide_banner -loglevel error -y -i "$TMP/$name.aiff" \
    -af "highpass=f=170,lowpass=f=5200,acompressor=threshold=-22dB:ratio=2.2:attack=8:release=100,volume=1.12" \
    -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/$name.ogg"
}

render_line aggregate_deposit \
  "Biological material: multiple. Metabolic activity: none."
render_line aggregate_ossuary \
  "Mass: eighteen thousand four hundred two kilograms. Metabolic activity: trace."
render_line aggregate_gestation \
  "Cohesion: increasing."
render_line aggregate_resolved \
  "Cohesion: zero point zero zero. Origin: multiple. Genetic consensus: none. Primary driver: repeated mortality."
