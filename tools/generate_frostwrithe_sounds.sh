#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/tools/audio_sources/frostwrithe"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/entity/frostwrithe"
mkdir -p "$OUT"

encode() {
  oggenc -Q -q 5 -o "$OUT/$1.ogg" "$OUT/$1.wav"
  rm -f "$OUT/$1.wav"
}

ffmpeg -hide_banner -loglevel error -y -ss 1.2 -t 4.2 -i "$SRC/cockroach_scurry_cc0.mp3" \
  -af "highpass=f=280,lowpass=f=7200,volume=2.0,alimiter=limit=0.86" -ac 1 -c:a pcm_s16le "$OUT/movement.wav"
encode movement

ffmpeg -hide_banner -loglevel error -y \
  -ss 0.2 -t 2.8 -i "$SRC/cockroach_scurry_cc0.mp3" \
  -ss 3.0 -t 2.8 -i "$SRC/organic_ice_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=350,volume=0.8,afade=t=in:st=0:d=1.8[bugs];[1:a]lowpass=f=5200,volume=0.7,afade=t=in:st=0:d=1.4[ice];[bugs][ice]amix=inputs=2:normalize=0,alimiter=limit=0.9" \
  -ac 1 -c:a pcm_s16le "$OUT/assemble.wav"
encode assemble

ffmpeg -hide_banner -loglevel error -y -ss 10.0 -t 2.2 -i "$SRC/organic_ice_cc0.mp3" \
  -af "atempo=0.86,lowpass=f=4200,volume=1.45,alimiter=limit=0.88" -ac 1 -c:a pcm_s16le "$OUT/shell.wav"
encode shell

ffmpeg -hide_banner -loglevel error -y \
  -ss 0.1 -t 0.75 -i "$SRC/spider_scuttle_cc0.mp3" \
  -ss 1.0 -t 0.75 -i "$SRC/icicle_collapse_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=400,volume=1.2[a];[1:a]highpass=f=220,volume=1.1[b];[a][b]amix=inputs=2:normalize=0,alimiter=limit=0.9" \
  -ac 1 -c:a pcm_s16le "$OUT/shed.wav"
encode shed

ffmpeg -hide_banner -loglevel error -y \
  -ss 0.0 -t 2.6 -i "$SRC/icicle_collapse_cc0.mp3" \
  -ss 3.5 -t 2.6 -i "$SRC/cockroach_scurry_cc0.mp3" \
  -filter_complex "[0:a]volume=1.0,afade=t=out:st=1.2:d=1.4[collapse];[1:a]highpass=f=320,volume=1.0,afade=t=in:st=0:d=1.5[bugs];[collapse][bugs]amix=inputs=2:normalize=0,alimiter=limit=0.92" \
  -ac 1 -c:a pcm_s16le "$OUT/disassemble.wav"
encode disassemble

ffmpeg -hide_banner -loglevel error -y \
  -ss 5.5 -t 2.4 -i "$SRC/cockroach_scurry_cc0.mp3" \
  -ss 1.2 -t 2.4 -i "$SRC/ice_grind_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=350,volume=0.8,afade=t=out:st=1.2:d=1.2[bugs];[1:a]lowpass=f=5000,volume=0.75,afade=t=in:st=0:d=1.2[ice];[bugs][ice]amix=inputs=2:normalize=0,alimiter=limit=0.9" \
  -ac 1 -c:a pcm_s16le "$OUT/regroup.wav"
encode regroup

ffmpeg -hide_banner -loglevel error -y -ss 0.15 -t 0.9 -i "$SRC/icicle_collapse_cc0.mp3" \
  -af "highpass=f=120,volume=1.35,alimiter=limit=0.9" -ac 1 -c:a pcm_s16le "$OUT/body_check.wav"
encode body_check

ffmpeg -hide_banner -loglevel error -y -ss 0.0 -t 1.3 -i "$SRC/spider_scuttle_cc0.mp3" \
  -af "highpass=f=360,atempo=0.92,volume=1.25,alimiter=limit=0.88" -ac 1 -c:a pcm_s16le "$OUT/climb.wav"
encode climb

ffmpeg -hide_banner -loglevel error -y -ss 1.0 -t 1.5 -i "$SRC/ice_grind_cc0.mp3" \
  -af "highpass=f=180,atempo=1.12,volume=1.35,alimiter=limit=0.9" -ac 1 -c:a pcm_s16le "$OUT/bridge.wav"
encode bridge

ffmpeg -hide_banner -loglevel error -y \
  -ss 7.0 -t 1.8 -i "$SRC/cockroach_scurry_cc0.mp3" \
  -ss 0.0 -t 1.8 -i "$SRC/spider_scuttle_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=300,volume=1.3[a];[1:a]highpass=f=420,atempo=1.25,volume=1.1[b];[a][b]amix=inputs=2:normalize=0,alimiter=limit=0.9" \
  -ac 1 -c:a pcm_s16le "$OUT/overrun.wav"
encode overrun

ffmpeg -hide_banner -loglevel error -y \
  -ss 0.0 -t 2.2 -i "$SRC/icicle_collapse_cc0.mp3" \
  -ss 1.0 -t 2.2 -i "$SRC/organic_ice_cc0.mp3" \
  -filter_complex "[0:a]lowpass=f=5200,volume=1.05[a];[1:a]highpass=f=220,volume=0.65,afade=t=out:st=0.8:d=1.4[b];[a][b]amix=inputs=2:normalize=0,alimiter=limit=0.9" \
  -ac 1 -c:a pcm_s16le "$OUT/terminal.wav"
encode terminal
