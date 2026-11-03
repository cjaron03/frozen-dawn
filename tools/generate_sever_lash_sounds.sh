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

# Owner pick, 2026-11-03: "more dangerous". Every sound is built on the plasma roar of the hum: brown and pink noise
# through a sweeping phaser, driven into tanh saturation, over a deep sub.

# Hum, 2 s loop: the roar (brown noise under 700 Hz, pink band-passed at 1.4 kHz) over a 40 Hz sub with its octave,
# throbbing at 7 Hz. The game raises its pitch with the emitter's heat.
render hum_loop \
  -f lavfi -i "anoisesrc=color=brown:duration=3.5:sample_rate=44100:seed=54003" \
  -f lavfi -i "anoisesrc=color=pink:duration=3.5:sample_rate=44100:seed=54004" \
  -f lavfi -i "aevalsrc=0.5*sin(2*PI*40*t)+0.25*sin(2*PI*80*t):s=44100:d=3.5" \
  -filter_complex "[0:a]lowpass=f=700,volume=1.4[low];[1:a]bandpass=f=1400:width_type=q:w=0.7,volume=0.6[mid];[low][mid]amix=inputs=2:normalize=0,aphaser=speed=0.5:decay=0.6,volume=2,asoftclip=type=tanh,volume=0.5[roar];[2:a]tremolo=f=7:d=0.5[sub];[roar][sub]amix=inputs=2:normalize=0,alimiter=limit=0.7:level=0[mix];$(seam 2 mix out)"

# Start, 0.8 s: ignition. A spark click and a 35 Hz thump, then the roar flares in over 60 ms with its low end opening
# from 200 Hz to 900 Hz, and settles to the hum's level.
render start \
  -f lavfi -i "anoisesrc=color=brown:duration=0.8:sample_rate=44100:seed=54011" \
  -f lavfi -i "anoisesrc=color=white:duration=0.8:sample_rate=44100:seed=54012" \
  -f lavfi -i "aevalsrc='0.8*min(1\, t/0.005)*exp(-9*t)*sin(2*PI*(35*t+20*(1-exp(-8*t))/8))':s=44100:d=0.8" \
  -f lavfi -i "aevalsrc='min(1\, t/0.06)*(0.6+0.8*exp(-6*t))':s=44100:d=0.8" \
  -f lavfi -i "aevalsrc='exp(-500*t)':s=44100:d=0.8" \
  -filter_complex "[0:a]lowpass=f=900,volume=2,aphaser=speed=2:decay=0.5,volume=2.4,asoftclip=type=tanh,volume=0.6[r];[r][3:a]amultiply[roar];[1:a]highpass=f=3000,volume=1.5[s];[s][4:a]amultiply[spark];[roar][2:a][spark]amix=inputs=3:normalize=0,afade=t=out:st=0.6:d=0.2,alimiter=limit=0.85:level=0[out]"

# Crack, 0.45 s: the whip snaps. A two-millisecond snap, a saturated roar burst that chokes off, a 45 Hz punch and a
# zap falling from 2 kHz.
render crack \
  -f lavfi -i "anoisesrc=color=white:duration=0.45:sample_rate=44100:seed=54013" \
  -f lavfi -i "aevalsrc='exp(-900*t)':s=44100:d=0.45" \
  -f lavfi -i "anoisesrc=color=brown:duration=0.45:sample_rate=44100:seed=54014" \
  -f lavfi -i "aevalsrc='exp(-14*t)':s=44100:d=0.45" \
  -f lavfi -i "aevalsrc='0.7*exp(-20*t)*sin(2*PI*45*t)+0.25*exp(-16*t)*sin(2*PI*(250*t+60*(1-exp(-30*t))))':s=44100:d=0.45" \
  -filter_complex "[0:a]highpass=f=1500,volume=1.8[s];[s][1:a]amultiply[snap];[2:a]lowpass=f=1500,volume=3,asoftclip=type=tanh[b];[b][3:a]amultiply,volume=0.8[burst];[snap][burst][4:a]amix=inputs=3:normalize=0,afade=t=out:st=0.38:d=0.07,alimiter=limit=0.9:level=0[out]"

# Sizzle, 0.45 s: a cut. Hard fry and fixed crackles over a short burst of the roar, saturated together.
POPS="0.02:0.9 0.05:0.6 0.07:0.8 0.11:0.5 0.13:0.9 0.16:0.6 0.19:0.7 0.21:0.4 0.24:0.8 0.27:0.5 0.29:0.6 0.32:0.4 0.35:0.5 0.38:0.3 0.41:0.3"
POP_ENV=$(for p in $POPS; do t="${p%%:*}"; a="${p##*:}"; printf '+%s*gte(t\\,%s)*exp(-300*(t-%s))' "$a" "$t" "$t"; done)
render sizzle \
  -f lavfi -i "anoisesrc=color=white:duration=0.45:sample_rate=44100:seed=54015" \
  -f lavfi -i "aevalsrc='0.35*min(1\, t/0.02)*(1-t/0.45)${POP_ENV}':s=44100:d=0.45" \
  -f lavfi -i "anoisesrc=color=brown:duration=0.45:sample_rate=44100:seed=54016" \
  -f lavfi -i "aevalsrc='0.6*min(1\, t/0.01)*(1-t/0.45)':s=44100:d=0.45" \
  -filter_complex "[0:a]highpass=f=2500,lowpass=f=9000,volume=1.4[n];[n][1:a]amultiply[fry];[2:a]lowpass=f=800,volume=2.5[r];[r][3:a]amultiply[body];[fry][body]amix=inputs=2:normalize=0,volume=1.8,asoftclip=type=tanh,volume=0.6,alimiter=limit=0.75:level=0[out]"

# Vent, 1.8 s: the overheat relief. A 50 Hz valve slam, then a violent steam blast (band-passed hiss and the roar,
# saturated) that holds for 0.3 s, dies away and leaves a low rumble.
render vent \
  -f lavfi -i "aevalsrc='0.9*min(1\, t/0.004)*exp(-16*t)*sin(2*PI*50*t)':s=44100:d=1.8" \
  -f lavfi -i "anoisesrc=color=white:duration=1.8:sample_rate=44100:seed=54017" \
  -f lavfi -i "anoisesrc=color=brown:duration=1.8:sample_rate=44100:seed=54018" \
  -f lavfi -i "aevalsrc='min(1\, t/0.03)*if(gt(t\,0.3)\, exp(-1.8*(t-0.3))\, 1)':s=44100:d=1.8" \
  -filter_complex "[1:a]bandpass=f=2800:width_type=q:w=0.6,volume=1.8[hiss];[2:a]lowpass=f=600,volume=2.2,aphaser=speed=1:decay=0.5[roar];[hiss][roar]amix=inputs=2:normalize=0,volume=1.6,asoftclip=type=tanh[steam];[steam][3:a]amultiply[s];[0:a][s]amix=inputs=2:normalize=0,afade=t=out:st=1.5:d=0.3,alimiter=limit=0.85:level=0,volume=0.7[out]"
