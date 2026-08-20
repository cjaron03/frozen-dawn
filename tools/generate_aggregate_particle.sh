#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT/src/main/resources/assets/frozendawn/textures/entity/aggregate_fragment.png"
OUTPUT="$ROOT/src/main/resources/assets/frozendawn/textures/particle/aggregate_convergence.png"
EXPULSION_OUTPUT="$ROOT/src/main/resources/assets/frozendawn/textures/particle/aggregate_expulsion.png"

mkdir -p "$(dirname "$OUTPUT")"
ffmpeg -hide_banner -loglevel error -y \
  -i "$SOURCE" \
  -vf "crop=16:16:0:0,format=rgba" \
  -frames:v 1 "$OUTPUT"

echo "Wrote ${OUTPUT#$ROOT/}"

ffmpeg -hide_banner -loglevel error -y \
  -i "$SOURCE" \
  -vf "crop=16:16:0:16,format=rgba" \
  -frames:v 1 "$EXPULSION_OUTPUT"

echo "Wrote ${EXPULSION_OUTPUT#$ROOT/}"
