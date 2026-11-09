#!/usr/bin/env bash
# The ORSA boot sting: tools/GenerateOrsaSting.java sings it dry, FFmpeg adds the room and encodes it.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/ui/orsa_boot_sting.ogg"
DRY="$(mktemp -t orsa_sting).wav"
trap 'rm -f "$DRY"' EXIT
java "$ROOT/tools/GenerateOrsaSting.java" "$DRY"
ffmpeg -hide_banner -loglevel error -y -i "$DRY" \
  -af "aecho=0.8:0.55:47|89|151|233:0.28|0.2|0.14|0.09,highpass=f=60,loudnorm=I=-20:TP=-3:LRA=11" \
  -ar 44100 -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT"
echo "$OUT"
