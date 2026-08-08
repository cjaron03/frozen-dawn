#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CREATURE="$ROOT/tools/audio_sources/undone_architect/dragon_growls_cc0.ogg"
ICE="$ROOT/tools/audio_sources/undone_architect/ice_crack_2205.ogg"
CORE_OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/block/bloom_core"
MOB_OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/entity/bloombound_undone"
TMP="${TMPDIR:-/tmp}/frozendawn_bloom_enrichment.wav"

mkdir -p "$CORE_OUT" "$MOB_OUT"

encode() {
  oggenc -Q -q 5 -o "$2" "$1"
}

ffmpeg -hide_banner -loglevel error -y -i "$ICE" \
  -filter_complex \
  "[0:a]asplit=3[a][b][c];\
[a]atrim=0.12:1.35,asetpts=PTS-STARTPTS,areverse,asetrate=53200,aresample=44100,highpass=f=520,lowpass=f=5200,volume=0.75[a1];\
[b]atrim=0.35:1.75,asetpts=PTS-STARTPTS,asetrate=31800,aresample=44100,lowpass=f=1450,vibrato=f=5.2:d=0.13,volume=0.38[b1];\
[c]atrim=0.0:0.24,asetpts=PTS-STARTPTS,highpass=f=1400,adelay=760|760,volume=0.55[c1];\
[a1][b1][c1]amix=inputs=3:normalize=0,afade=t=in:st=0:d=0.08,afade=t=out:st=1.35:d=0.32,alimiter=limit=0.91,volume=0.55[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$CORE_OUT/pulse.ogg"

ffmpeg -hide_banner -loglevel error -y -i "$ICE" \
  -filter_complex \
  "[0:a]asplit=3[a][b][c];\
[a]highpass=f=180,volume=1.18[a1];\
[b]areverse,asetrate=34000,aresample=44100,lowpass=f=2400,volume=0.78[b1];\
[c]asetrate=57500,aresample=44100,highpass=f=1850,adelay=170|170,volume=0.65[c1];\
[a1][b1][c1]amix=inputs=3:normalize=0,acrusher=bits=11:mix=0.08,alimiter=limit=0.94,volume=0.55[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$CORE_OUT/break.ogg"

render_creature() {
  local start="$1"
  local duration="$2"
  local output="$3"
  local base_rate="$4"
  local nerve_rate="$5"
  local finish="$6"
  ffmpeg -hide_banner -loglevel error -y \
    -ss "$start" -t "$duration" -i "$CREATURE" -i "$ICE" \
    -filter_complex \
    "[0:a]asplit=2[body][nerve];\
[body]asetrate=$base_rate,aresample=44100,highpass=f=75,lowpass=f=1900,vibrato=f=4.6:d=0.18,volume=0.72[body1];\
[nerve]asetrate=$nerve_rate,aresample=44100,highpass=f=720,lowpass=f=6100,vibrato=f=13:d=0.31,tremolo=f=9.5:d=0.14,volume=0.48[nerve1];\
[1:a]atrim=0.05:0.82,asetpts=PTS-STARTPTS,areverse,asetrate=50500,aresample=44100,highpass=f=900,adelay=260|260,volume=0.55[crystal];\
[body1][nerve1][crystal]amix=inputs=3:weights='0.85 0.62 0.52':normalize=0,$finish,alimiter=limit=0.93,volume=0.55[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
  encode "$TMP" "$output"
}

render_creature 16.0 2.7 "$MOB_OUT/ambient_1.ogg" 38500 67600 \
  "flanger=delay=1.2:depth=1.8:regen=8:width=26:speed=0.42,afade=t=out:st=2.25:d=0.45"
render_creature 44.8 2.9 "$MOB_OUT/ambient_2.ogg" 36200 70400 \
  "acrusher=bits=12:mix=0.08,afade=t=out:st=2.42:d=0.48"
render_creature 0.2 0.9 "$MOB_OUT/attack.ogg" 35000 72800 \
  "highpass=f=95,volume=1.42"
render_creature 11.0 0.75 "$MOB_OUT/hurt.ogg" 40200 75600 \
  "highpass=f=120,volume=1.35"
render_creature 54.4 4.0 "$MOB_OUT/death.ogg" 29200 59800 \
  "tremolo=f=6.2:d=0.21,acrusher=bits=10:mix=0.12,afade=t=out:st=3.25:d=0.7,volume=1.34"
