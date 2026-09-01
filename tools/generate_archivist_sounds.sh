#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ICE="$ROOT/tools/audio_sources/undone_architect/ice_crack_2205.ogg"
BREATH="$ROOT/tools/audio_sources/hearthrot/zombie_choking_cc0.wav"
SOB="$ROOT/tools/audio_sources/archivist/man_sobbing_cc0.mp3"
SCREAM="$ROOT/tools/audio_sources/archivist/scream_woman_pain_4.wav"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/entity/archivist"
TMP="${TMPDIR:-/tmp}/frozendawn_archivist.wav"
mkdir -p "$OUT"

encode() {
  oggenc -Q -q 5 -o "$2" "$1"
}

material() {
  local output="$1"
  local rate="$2"
  local delay="$3"
  local post="$4"
  ffmpeg -hide_banner -loglevel error -y -i "$ICE" \
    -f lavfi -i "anoisesrc=color=brown:duration=3:sample_rate=44100" \
    -filter_complex \
    "[0:a]asplit=3[a][b][c];\
[a]atrim=0.02:1.58,asetpts=PTS-STARTPTS,asetrate=$rate,aresample=44100,highpass=f=180,lowpass=f=3400,volume=0.72[a1];\
[b]atrim=0.20:0.72,asetpts=PTS-STARTPTS,areverse,asetrate=51500,aresample=44100,highpass=f=960,adelay=$delay|$delay,volume=0.48[b1];\
[c]atrim=1.10:1.78,asetpts=PTS-STARTPTS,highpass=f=1300,adelay=$((delay + 210))|$((delay + 210)),volume=0.42[c1];\
[1:a]lowpass=f=420,highpass=f=55,tremolo=f=3.2:d=0.16,volume=0.08[n];\
[a1][b1][c1][n]amix=inputs=4:normalize=0,$post,alimiter=limit=0.92,volume=0.55[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
  encode "$TMP" "$output"
}

material "$OUT/ambient_1.ogg" 30200 520 \
  "afade=t=in:st=0:d=0.12,afade=t=out:st=2.35:d=0.55"
material "$OUT/ambient_2.ogg" 33400 680 \
  "vibrato=f=2.8:d=0.08,afade=t=in:st=0:d=0.08,afade=t=out:st=2.22:d=0.62"
material "$OUT/ambient_3.ogg" 27800 830 \
  "flanger=delay=1.1:depth=1.2:regen=3:width=18:speed=0.23,afade=t=out:st=2.4:d=0.5"

material "$OUT/pack_1.ogg" 46800 90 \
  "atrim=0:0.72,highpass=f=320,volume=1.15"
material "$OUT/pack_2.ogg" 42100 130 \
  "atrim=0:0.82,highpass=f=240,volume=1.12"
material "$OUT/sort_1.ogg" 54800 170 \
  "atrim=0:0.88,highpass=f=500,tremolo=f=12:d=0.10,volume=1.06"
material "$OUT/sort_2.ogg" 58200 240 \
  "atrim=0:0.96,highpass=f=620,volume=1.08"
material "$OUT/step_1.ogg" 39400 40 \
  "atrim=0:0.42,lowpass=f=1800,volume=0.88"
material "$OUT/step_2.ogg" 43800 65 \
  "atrim=0:0.46,lowpass=f=2100,volume=0.86"

hurt() {
  local start="$1"
  local output="$2"
  ffmpeg -hide_banner -loglevel error -y -ss "$start" -t 0.72 -i "$BREATH" -i "$ICE" \
    -filter_complex \
    "[0:a]highpass=f=120,lowpass=f=2500,asetrate=38200,aresample=44100,\
compand=attacks=0.01:decays=0.12:points=-70/-70|-24/-12|-8/-3,volume=0.82[air];\
[1:a]atrim=0.12:0.55,asetpts=PTS-STARTPTS,highpass=f=900,volume=0.60[glass];\
[air][glass]amix=inputs=2:normalize=0,atrim=0:0.78,afade=t=out:st=0.55:d=0.20,\
alimiter=limit=0.92,volume=0.55[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
  encode "$TMP" "$output"
}

hurt 0.45 "$OUT/hurt_1.ogg"
hurt 2.62 "$OUT/hurt_2.ogg"

ffmpeg -hide_banner -loglevel error -y -i "$ICE" \
  -f lavfi -i "anoisesrc=color=brown:duration=4:sample_rate=44100" \
  -f lavfi -i "sine=frequency=46:duration=4:sample_rate=44100" \
  -filter_complex \
  "[0:a]asplit=3[a][b][c];\
[a]asetrate=24600,aresample=44100,lowpass=f=1450,volume=0.95[a1];\
[b]areverse,asetrate=36000,aresample=44100,highpass=f=500,adelay=620|620,volume=0.72[b1];\
[c]highpass=f=1250,adelay=1850|1850,volume=1.05[c1];\
[1:a]highpass=f=55,lowpass=f=680,volume=0.16[n];\
[2:a]tremolo=f=5.5:d=0.30,afade=t=out:st=2.8:d=0.8,volume=0.24[sub];\
[a1][b1][c1][n][sub]amix=inputs=5:normalize=0,atrim=0:3.65,\
afade=t=out:st=2.85:d=0.75,alimiter=limit=0.94,volume=0.55[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$OUT/death.ogg"

sob() {
  local start="$1"
  local duration="$2"
  local output="$3"
  local fade_out="$4"
  local rate="$5"
  ffmpeg -hide_banner -loglevel error -y -ss "$start" -t "$duration" -i "$SOB" \
    -filter_complex \
    "[0:a]asetrate=$rate,aresample=44100,highpass=f=75,lowpass=f=7200,\
loudnorm=I=-18:LRA=7:TP=-2.0,\
afade=t=in:st=0:d=0.035,afade=t=out:st=$fade_out:d=0.12,volume=0.78[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
  encode "$TMP" "$output"
}

sob 0.05 0.62 "$OUT/sob_1.ogg" 0.50 42800
sob 0.55 0.88 "$OUT/sob_2.ogg" 0.76 45200
sob 1.25 1.10 "$OUT/sob_3.ogg" 0.98 41400
sob 2.05 1.30 "$OUT/sob_4.ogg" 1.18 44400
sob 3.15 0.72 "$OUT/sob_5.ogg" 0.60 42100
sob 3.75 1.25 "$OUT/sob_6.ogg" 1.13 46300
sob 4.75 1.05 "$OUT/sob_7.ogg" 0.93 40800
sob 5.35 0.65 "$OUT/sob_8.ogg" 0.53 43700

# Preserve the human panic in the supplied scream, but seat it in the Archivist's
# cold material register so it carries through Phase 6 vacuum filtering.
ffmpeg -hide_banner -loglevel error -y -ss 0.42 -t 3.65 -i "$SCREAM" -i "$ICE" \
  -filter_complex \
  "[0:a]highpass=f=105,lowpass=f=7600,acompressor=threshold=-18dB:ratio=2.5:attack=8:release=120,loudnorm=I=-12:LRA=8:TP=-1.0,volume=1.08[voice];\
[1:a]atrim=0.04:0.95,asetpts=PTS-STARTPTS,asetrate=32000,aresample=44100,lowpass=f=1700,adelay=1450|1450,volume=0.32[ice];\
[voice][ice]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.025,afade=t=out:st=3.46:d=0.18,alimiter=limit=0.96[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$OUT/scream.ogg"

rm -f "$TMP"
