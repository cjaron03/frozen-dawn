#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEXTURE="$ROOT/src/main/resources/assets/frozendawn/sounds/ambient/bloom/drone.ogg"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/music/bloom"
TMP="${TMPDIR:-/tmp}/frozendawn_bloom_music.wav"

mkdir -p "$OUT"
trap 'rm -f "$TMP"' EXIT

render_track() {
  local name="$1"
  local duration="$2"
  local root="$3"
  local fifth="$4"
  local color="$5"
  local pulse="$6"

  ffmpeg -hide_banner -loglevel error -y \
    -stream_loop -1 -i "$TEXTURE" \
    -f lavfi -i "sine=frequency=$root:duration=$duration:sample_rate=44100" \
    -f lavfi -i "sine=frequency=$fifth:duration=$duration:sample_rate=44100" \
    -f lavfi -i "sine=frequency=$color:duration=$duration:sample_rate=44100" \
    -filter_complex \
    "[0:a]atrim=0:$duration,asetpts=PTS-STARTPTS,lowpass=f=2400,highpass=f=45,\
volume=0.24,afade=t=in:st=0:d=5,afade=t=out:st=$(($duration - 8)):d=8[texture];\
[1:a]volume='0.040*(0.72+0.28*sin(2*PI*t*$pulse))':eval=frame,\
vibrato=f=0.20:d=0.025,lowpass=f=420[root];\
[2:a]volume='0.027*(0.76+0.24*sin(2*PI*t*0.045))':eval=frame,\
vibrato=f=0.14:d=0.020,lowpass=f=560[fifth];\
[3:a]volume='0.016*(0.68+0.32*sin(2*PI*t*0.032))':eval=frame,\
vibrato=f=0.11:d=0.018,lowpass=f=720[color];\
[texture][root][fifth][color]amix=inputs=4:weights='1 1 1 1':normalize=0,\
aecho=0.78:0.42:900|1700:0.17|0.10,\
afade=t=in:st=0:d=5,afade=t=out:st=$(($duration - 8)):d=8,\
volume=28.0,alimiter=limit=0.88[out]" \
    -map "[out]" -ac 2 -ar 44100 -c:a pcm_s16le "$TMP"
  oggenc -Q -q 5 -o "$OUT/$name.ogg" "$TMP"
}

# Low, unresolved intervals keep these in the terrain's sound vocabulary.
render_track roots 56 73.42 110.00 164.81 0.055
render_track hollow 62 65.41 98.00 146.83 0.041
render_track pale 58 82.41 123.47 185.00 0.048
