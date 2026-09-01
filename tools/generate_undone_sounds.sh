#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/entity/undone"
TMP="${TMPDIR:-/tmp}/frozendawn_undone.wav"
mkdir -p "$OUT"

encode() {
  oggenc -Q -q 5 -o "$2" "$1"
}

ambient() {
  local output="$1" seed="$2" fundamental="$3" duration="$4" pulse="$5"
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "anoisesrc=color=brown:duration=$duration:sample_rate=44100:seed=$seed" \
    -f lavfi -i "sine=frequency=$fundamental:duration=$duration:sample_rate=44100" \
    -f lavfi -i "sine=frequency=$((fundamental * 3 + 17)):duration=$duration:sample_rate=44100" \
    -filter_complex \
    "[0:a]highpass=f=45,lowpass=f=1200,flanger=delay=2.1:depth=2.8:regen=7:width=34:speed=0.14,volume=0.28[n];\
[1:a]vibrato=f=1.7:d=0.28,tremolo=f=$pulse:d=0.55,lowpass=f=410,volume=0.31[body];\
[2:a]vibrato=f=3.1:d=0.17,tremolo=f=0.37:d=0.72,highpass=f=170,lowpass=f=1100,volume=0.12[edge];\
[n][body][edge]amix=inputs=3:normalize=0,acompressor=threshold=-24dB:ratio=3:attack=18:release=260,\
afade=t=in:st=0:d=0.35,afade=t=out:st=$(awk "BEGIN {print $duration-0.7}"):d=0.68,alimiter=limit=0.92,volume=0.62[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
  encode "$TMP" "$output"
}

ambient "$OUT/ambient_1.ogg" 31091 61 6.4 0.83
ambient "$OUT/ambient_2.ogg" 74219 73 4.9 1.12
ambient "$OUT/ambient_3.ogg" 98299 47 5.8 0.69

# A dry intake that almost organizes itself into speech, but never uses a voice.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "anoisesrc=color=pink:duration=4.6:sample_rate=44100:seed=55021" \
  -f lavfi -i "sine=frequency=118:duration=4.6:sample_rate=44100" \
  -filter_complex \
  "[0:a]highpass=f=90,lowpass=f=5300,afftfilt=real='re*if(between(b,7,14)+between(b,26,38)+between(b,61,79),1.8,0.12)':imag='im',\
tremolo=f=2.35:d=0.86,volume=0.34[formants];\
[1:a]vibrato=f=4.2:d=0.26,tremolo=f=1.17:d=0.74,lowpass=f=620,volume=0.17[throat];\
[formants][throat]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.18,afade=t=out:st=3.75:d=0.78,\
alimiter=limit=0.90,volume=0.62[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$OUT/failed_word.ogg"

# Sparse mechanical breathing: pressure escaping a body with no lungs.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "anoisesrc=color=pink:duration=9:sample_rate=44100:seed=19381" \
  -f lavfi -i "sine=frequency=52:duration=9:sample_rate=44100" \
  -filter_complex \
  "[0:a]highpass=f=140,lowpass=f=3600,tremolo=f=0.42:d=0.94,acompressor=threshold=-31dB:ratio=5:attack=60:release=520,volume=0.36[air];\
[1:a]tremolo=f=0.42:d=0.88,vibrato=f=1.8:d=0.13,lowpass=f=170,volume=0.20[chest];\
[air][chest]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.45,afade=t=out:st=8.2:d=0.75,\
alimiter=limit=0.90,volume=0.58[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$OUT/breath.ogg"

impact() {
  local output="$1" seed="$2" duration="$3" base="$4" rate="$5"
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "anoisesrc=color=white:duration=$duration:sample_rate=44100:seed=$seed" \
    -f lavfi -i "sine=frequency=$base:duration=$duration:sample_rate=44100" \
    -filter_complex \
    "[0:a]highpass=f=180,lowpass=f=4300,agate=threshold=0.18:ratio=8:attack=2:release=$rate,volume=0.30[crack];\
[1:a]vibrato=f=7.4:d=0.22,tremolo=f=11:d=0.31,lowpass=f=760,volume=0.30[mass];\
[crack][mass]amix=inputs=2:normalize=0,afade=t=out:st=$(awk "BEGIN {print $duration-0.25}"):d=0.23,\
alimiter=limit=0.93,volume=0.66[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
  encode "$TMP" "$output"
}

impact "$OUT/attack.ogg" 41117 1.55 96 95
impact "$OUT/hurt.ogg" 61211 1.25 132 72
impact "$OUT/grab.ogg" 77191 1.50 74 130
impact "$OUT/grasp_cast.ogg" 99181 1.82 58 155
impact "$OUT/grasp_break.ogg" 33091 0.88 181 48

# The held victim hears a dense cyclic pressure rather than a vocal loop.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "anoisesrc=color=brown:duration=3.85:sample_rate=44100:seed=88117" \
  -f lavfi -i "sine=frequency=43:duration=3.85:sample_rate=44100" \
  -filter_complex \
  "[0:a]highpass=f=55,lowpass=f=920,tremolo=f=2.8:d=0.76,volume=0.31[drag];\
[1:a]tremolo=f=5.6:d=0.62,vibrato=f=1.2:d=0.12,volume=0.28[pulse];\
[drag][pulse]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.08,afade=t=out:st=3.48:d=0.34,\
alimiter=limit=0.92,volume=0.61[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$OUT/grasp_hold.ogg"

# Teeth-like crystalline chatter with an uneven sub pulse.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "anoisesrc=color=white:duration=4:sample_rate=44100:seed=20483" \
  -f lavfi -i "sine=frequency=67:duration=4:sample_rate=44100" \
  -filter_complex \
  "[0:a]highpass=f=1100,lowpass=f=7200,tremolo=f=8.7:d=0.96,agate=threshold=0.13:ratio=12:attack=1:release=34,volume=0.29[teeth];\
[1:a]tremolo=f=2.9:d=0.72,vibrato=f=3.8:d=0.18,lowpass=f=300,volume=0.24[root];\
[teeth][root]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.07,afade=t=out:st=3.48:d=0.48,\
alimiter=limit=0.91,volume=0.62[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$OUT/jaw.ogg"

# A descending synthetic wail collapses into structural noise. No voice source.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "aevalsrc=0.38*sin(2*PI*(214*t-12.2*t*t))+0.14*sin(2*PI*(437*t-24.7*t*t)):s=44100:d=6.35" \
  -f lavfi -i "anoisesrc=color=brown:duration=6.35:sample_rate=44100:seed=65027" \
  -f lavfi -i "anoisesrc=color=white:duration=6.35:sample_rate=44100:seed=65029" \
  -filter_complex \
  "[0:a]vibrato=f=5.4:d=0.31,tremolo=f=1.3:d=0.22,highpass=f=48,lowpass=f=1500,volume=0.58[wail];\
[1:a]highpass=f=38,lowpass=f=540,tremolo=f=4.7:d=0.48,volume=0.30[body];\
[2:a]highpass=f=780,lowpass=f=6200,agate=threshold=0.16:ratio=9:attack=1:release=48,adelay=3100|3100,volume=0.21[collapse];\
[wail][body][collapse]amix=inputs=3:normalize=0,acompressor=threshold=-18dB:ratio=3:attack=7:release=170,\
afade=t=in:st=0:d=0.09,afade=t=out:st=5.65:d=0.66,alimiter=limit=0.95,volume=0.67[out]" \
  -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$TMP"
encode "$TMP" "$OUT/death.ogg"

rm -f "$TMP"
