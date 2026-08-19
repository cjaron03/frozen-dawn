#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/block/stillpoint_core"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "anoisesrc=color=pink:amplitude=0.95:d=1.45:s=48000:seed=4107" \
  -f lavfi -i "sine=frequency=92:duration=1.45:sample_rate=48000" \
  -filter_complex "[0:a]highpass=f=140,lowpass=f=3400,afade=t=in:st=0:d=0.12,afade=t=out:st=0.88:d=0.57,volume=1.45[n];[1:a]lowpass=f=360,afade=t=in:st=0:d=0.08,afade=t=out:st=0.72:d=0.70,volume=0.75[b];[n][b]amix=inputs=2:normalize=0,acompressor=threshold=0.20:ratio=3:attack=5:release=130,volume=1.85,alimiter=limit=0.94" \
  -ar 48000 -ac 1 "$TMP/enter.wav"

ffmpeg -hide_banner -loglevel error -y -i "$TMP/enter.wav" \
  -af "areverse,afade=t=in:st=0:d=0.06,afade=t=out:st=1.05:d=0.40,volume=1.08,alimiter=limit=0.94" \
  -ar 48000 -ac 1 "$TMP/exit.wav"

oggenc -Q -q 5 -o "$OUT/enter.ogg" "$TMP/enter.wav"
oggenc -Q -q 5 -o "$OUT/exit.ogg" "$TMP/exit.wav"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "sine=frequency=310:duration=0.9:sample_rate=48000" \
  -f lavfi -i "sine=frequency=620:duration=0.9:sample_rate=48000" \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.32:d=0.9:s=48000:seed=811" \
  -filter_complex "[0:a]afade=t=out:st=0.18:d=0.72,volume=0.72[a];[1:a]afade=t=out:st=0.08:d=0.62,volume=0.38[b];[2:a]highpass=f=900,lowpass=f=4600,afade=t=out:st=0.05:d=0.45,volume=0.62[n];[a][b][n]amix=inputs=3:normalize=0,acompressor=threshold=0.18:ratio=3:attack=3:release=110,volume=3.0,alimiter=limit=0.92" \
  -ar 48000 -ac 1 "$TMP/use.wav"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "anoisesrc=color=brown:amplitude=0.98:d=2.5:s=48000:seed=303" \
  -f lavfi -i "sine=frequency=46:duration=2.5:sample_rate=48000" \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.75:d=2.5:s=48000:seed=904" \
  -filter_complex "[0:a]lowpass=f=780,afade=t=in:st=0:d=0.04,afade=t=out:st=1.15:d=1.35,volume=1.8[body];[1:a]lowpass=f=180,afade=t=out:st=0.85:d=1.6,volume=1.2[sub];[2:a]highpass=f=700,lowpass=f=5200,afade=t=in:st=0:d=0.35,afade=t=out:st=1.0:d=1.2,volume=0.72[air];[body][sub][air]amix=inputs=3:normalize=0,acompressor=threshold=0.16:ratio=4:attack=3:release=190,alimiter=limit=0.95" \
  -ar 48000 -ac 1 "$TMP/exhaust.wav"

oggenc -Q -q 5 -o "$OUT/use.ogg" "$TMP/use.wav"
oggenc -Q -q 5 -o "$OUT/exhaust.ogg" "$TMP/exhaust.wav"
