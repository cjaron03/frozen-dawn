#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ICE="$ROOT/tools/audio_sources/undone_architect/ice_crack_2205.ogg"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/entity/bloom_spore"
TMP="${TMPDIR:-/tmp}/frozendawn_bloom_spore.wav"

mkdir -p "$OUT"

encode() {
  oggenc -Q -q 5 -o "$2" "$1"
}

render() {
  local filter="$1"
  local output="$2"
  ffmpeg -hide_banner -loglevel error -y -i "$ICE" \
    -filter_complex "$filter" -map "[out]" -ac 1 -ar 44100 \
    -c:a pcm_s16le "$TMP"
  encode "$TMP" "$OUT/$output.ogg"
}

render "[0:a]asplit=3[a][b][c];[a]atrim=0.0:1.7,asetpts=PTS-STARTPTS,areverse,asetrate=27800,aresample=44100,lowpass=f=950,volume=0.45[a1];[b]atrim=0.2:1.9,asetpts=PTS-STARTPTS,asetrate=51800,aresample=44100,highpass=f=750,lowpass=f=3600,tremolo=f=3.1:d=0.12,volume=0.22[b1];[c]atrim=0.0:0.5,asetpts=PTS-STARTPTS,areverse,adelay=1450|1450,volume=0.18[c1];[a1][b1][c1]amix=inputs=3:normalize=0,afade=t=in:st=0:d=0.35,afade=t=out:st=2.45:d=0.65,alimiter=limit=0.88,volume=0.52[out]" ambient
render "[0:a]atrim=0.12:0.34,asetpts=PTS-STARTPTS,highpass=f=520,lowpass=f=4400,volume=0.72,afade=t=out:st=0.15:d=0.07[out]" step
render "[0:a]atrim=0.18:0.75,asetpts=PTS-STARTPTS,asetrate=50600,aresample=44100,highpass=f=360,lowpass=f=5200,acrusher=bits=13:mix=0.06,volume=0.72,afade=t=out:st=0.40:d=0.18[out]" contact
render "[0:a]asplit=3[a][b][c];[a]atrim=0.0:1.2,asetpts=PTS-STARTPTS,asetrate=35400,aresample=44100,lowpass=f=2100,volume=0.92[a1];[b]atrim=0.04:0.62,asetpts=PTS-STARTPTS,areverse,asetrate=58800,aresample=44100,highpass=f=1100,adelay=90|90,volume=0.58[b1];[c]atrim=0.28:1.55,asetpts=PTS-STARTPTS,asetrate=27600,aresample=44100,lowpass=f=820,adelay=260|260,volume=0.62[c1];[a1][b1][c1]amix=inputs=3:normalize=0,atrim=0:1.55,afade=t=out:st=1.15:d=0.36,alimiter=limit=0.94,volume=1.35[out]" death
render "[0:a]asplit=2[a][b];[a]atrim=0.0:1.85,asetpts=PTS-STARTPTS,asetrate=32600,aresample=44100,lowpass=f=1800,volume=0.76[a1];[b]atrim=0.1:1.0,asetpts=PTS-STARTPTS,areverse,asetrate=51000,aresample=44100,highpass=f=900,adelay=480|480,volume=0.46[b1];[a1][b1]amix=inputs=2:normalize=0,afade=t=out:st=1.65:d=0.5,alimiter=limit=0.92,volume=0.62[out]" collapse
render "[0:a]asplit=3[a][b][c];[a]atrim=0.0:1.45,asetpts=PTS-STARTPTS,areverse,asetrate=48800,aresample=44100,highpass=f=720,volume=0.58[a1];[b]atrim=0.2:1.8,asetpts=PTS-STARTPTS,asetrate=29400,aresample=44100,lowpass=f=1200,volume=0.54[b1];[c]atrim=0.0:0.42,asetpts=PTS-STARTPTS,adelay=920|920,highpass=f=1800,volume=0.42[c1];[a1][b1][c1]amix=inputs=3:normalize=0,afade=t=in:st=0:d=0.18,afade=t=out:st=1.75:d=0.5,alimiter=limit=0.91,volume=0.58[out]" growth_start
render "[0:a]atrim=0.05:0.42,asetpts=PTS-STARTPTS,asetrate=57000,aresample=44100,highpass=f=840,volume=0.76,afade=t=out:st=0.23:d=0.11[out]" corpse_strike
render "[0:a]asplit=2[a][b];[a]atrim=0.0:1.35,asetpts=PTS-STARTPTS,highpass=f=240,volume=0.92[a1];[b]atrim=0.0:0.8,asetpts=PTS-STARTPTS,areverse,asetrate=35400,aresample=44100,lowpass=f=1700,adelay=240|240,volume=0.65[b1];[a1][b1]amix=inputs=2:normalize=0,alimiter=limit=0.93,volume=0.62[out]" corpse_break
