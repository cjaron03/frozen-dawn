#!/usr/bin/env bash
# ORSA LC-4 Sever Lash sounds: ffmpeg lavfi synthesis only, fixed noise seeds, mono 44.1 kHz Vorbis (oggenc -q 5:
# ffmpeg's own Vorbis encoder only writes stereo, and Minecraft attenuates only mono sounds by distance). The
# conventions of tools/generate_hyperion_sounds.sh: the hum loop is seamless, every tone and modulation rate a whole
# multiple of 1/duration, rendered 1.5 s long so its first second warms the filters and its last 0.5 s crossfades over
# the head. Sources are recorded in tools/audio_sources/sever_lash/SOURCES.md.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/item/sever_lash"
TMP="${TMPDIR:-/tmp}/frozendawn_sever_lash.wav"
mkdir -p "$OUT"
trap 'rm -f "$TMP"' EXIT

render() { # name, then ffmpeg inputs and filter args ending in [out]
  local name="$1"; shift
  ffmpeg -hide_banner -loglevel error -y "$@" -map "[out]" -ac 1 -ar 44100 -map_metadata -1 -fflags +bitexact -flags:a +bitexact -c:a pcm_s16le "$TMP"
  oggenc -Q -q 5 -s 53000 -o "$OUT/$name.ogg" "$TMP"
}
# Closes a loop of $1 seconds: [$2] is the mix, rendered $1 + 1.5 s long; [$3] is the seamless loop.
seam() { echo "[$2]atrim=1:$(echo "$1 + 1.5" | bc),asetpts=PTS-STARTPTS,asplit[$3h][$3b];[$3b]atrim=0.5:$(echo "$1 + 0.5" | bc),asetpts=PTS-STARTPTS[$3body];[$3h]atrim=0:0.5,asetpts=PTS-STARTPTS[$3head];[$3body][$3head]acrossfade=d=0.5:c1=tri:c2=tri[$3]"; }

# Hum, 2 s loop: a 110 Hz buzz (five saw harmonics) over a 55 Hz floor, beating against 113 Hz three times a second,
# with a band-passed crackle chopped at 12 Hz. The game raises its pitch with the emitter's heat.
render hum_loop \
  -f lavfi -i "aevalsrc=0.2*(sin(2*PI*110*t)+sin(4*PI*110*t)/2+sin(6*PI*110*t)/3+sin(8*PI*110*t)/4+sin(10*PI*110*t)/5)+0.12*sin(2*PI*113*t)+0.1*sin(2*PI*55*t):s=44100:d=3.5" \
  -f lavfi -i "anoisesrc=color=white:duration=3.5:sample_rate=44100:seed=53001" \
  -filter_complex "[1:a]bandpass=f=2500:width_type=q:w=1,volume=0.5,tremolo=f=12:d=0.8[c];[0:a][c]amix=inputs=2:normalize=0,lowpass=f=6000,tremolo=f=6:d=0.15,alimiter=limit=0.6:level=0[mix];$(seam 2 mix out)"

# Start, 0.7 s: the emitter strikes. A click, a buzz sweeping 40 to 110 Hz in 0.35 s and settling on the hum's note,
# and a short band-passed whoomp.
PHASE='if(lt(t\,0.35)\, 40*t+100*t*t\, 26.25+110*(t-0.35))'
render start \
  -f lavfi -i "aevalsrc='min(1\, t/0.02)*0.3*(sin(2*PI*${PHASE})+sin(4*PI*${PHASE})/2+sin(6*PI*${PHASE})/3)':s=44100:d=0.7" \
  -f lavfi -i "anoisesrc=color=white:duration=0.7:sample_rate=44100:seed=53002" \
  -f lavfi -i "aevalsrc='0.9*exp(-400*t)+0.6*min(1\, t/0.03)*exp(-6*t)':s=44100:d=0.7" \
  -filter_complex "[1:a]bandpass=f=1200:width_type=q:w=0.8,volume=1.4[n];[n][2:a]amultiply[whoomp];[0:a][whoomp]amix=inputs=2:normalize=0,afade=t=out:st=0.5:d=0.2,alimiter=limit=0.8:level=0[out]"

# Crack, 0.4 s: the whip snaps. A two-millisecond broadband snap, a short noisy body, and an electric zap falling from
# 3 kHz to 300 Hz.
render crack \
  -f lavfi -i "anoisesrc=color=white:duration=0.4:sample_rate=44100:seed=53003" \
  -f lavfi -i "aevalsrc='exp(-900*t)':s=44100:d=0.4" \
  -f lavfi -i "anoisesrc=color=pink:duration=0.4:sample_rate=44100:seed=53004" \
  -f lavfi -i "aevalsrc='0.6*exp(-25*t)':s=44100:d=0.4" \
  -f lavfi -i "aevalsrc='0.3*exp(-18*t)*sin(2*PI*(300*t+90*(1-exp(-30*t))))':s=44100:d=0.4" \
  -filter_complex "[0:a]highpass=f=1500,volume=1.6[s];[s][1:a]amultiply[snap];[2:a]bandpass=f=900:width_type=q:w=0.8[b];[b][3:a]amultiply[body];[snap][body][4:a]amix=inputs=3:normalize=0,afade=t=out:st=0.35:d=0.05,alimiter=limit=0.9:level=0[out]"

# Sizzle, 0.45 s: a cut. High fry over a scatter of crackles at fixed times, in fast and out slow.
POPS="0.02:0.9 0.05:0.6 0.07:0.8 0.11:0.5 0.13:0.9 0.16:0.6 0.19:0.7 0.21:0.4 0.24:0.8 0.27:0.5 0.29:0.6 0.32:0.4 0.35:0.5 0.38:0.3 0.41:0.3"
POP_ENV=$(for p in $POPS; do t="${p%%:*}"; a="${p##*:}"; printf '+%s*gte(t\\,%s)*exp(-300*(t-%s))' "$a" "$t" "$t"; done)
render sizzle \
  -f lavfi -i "anoisesrc=color=white:duration=0.45:sample_rate=44100:seed=53005" \
  -f lavfi -i "aevalsrc='0.35*min(1\, t/0.02)*(1-t/0.45)${POP_ENV}':s=44100:d=0.45" \
  -filter_complex "[0:a]highpass=f=3000,lowpass=f=9000,volume=1.3[n];[n][1:a]amultiply,alimiter=limit=0.7:level=0[out]"

# Vent, 1.8 s: the overheat relief. A valve thump at 60 Hz, then a steam hiss that holds for 0.3 s and dies away over a
# low rumble.
render vent \
  -f lavfi -i "aevalsrc='0.7*min(1\, t/0.004)*exp(-20*t)*sin(2*PI*60*t)':s=44100:d=1.8" \
  -f lavfi -i "anoisesrc=color=white:duration=1.8:sample_rate=44100:seed=53006" \
  -f lavfi -i "anoisesrc=color=brown:duration=1.8:sample_rate=44100:seed=53007" \
  -f lavfi -i "aevalsrc='min(1\, t/0.04)*if(gt(t\,0.3)\, exp(-1.6*(t-0.3))\, 1)':s=44100:d=1.8" \
  -filter_complex "[1:a]bandpass=f=3200:width_type=q:w=0.7,volume=1.6[hiss];[2:a]lowpass=f=300,volume=1.2[rumble];[hiss][rumble]amix=inputs=2:normalize=0[steam];[steam][3:a]amultiply[s];[0:a][s]amix=inputs=2:normalize=0,afade=t=out:st=1.5:d=0.3,alimiter=limit=0.8:level=0[out]"
