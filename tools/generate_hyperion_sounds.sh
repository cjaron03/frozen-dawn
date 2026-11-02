#!/usr/bin/env bash
# SMS-1 Hyperion sounds, from the approved ladder in docs/art/hyperion-storage/audio-ladder-sketch.html: ffmpeg lavfi
# synthesis only, fixed noise seeds, mono 44.1 kHz Vorbis (oggenc -q 5: ffmpeg's own Vorbis encoder only writes stereo,
# and Minecraft attenuates only mono sounds by distance). The conventions of tools/generate_sfr_sounds.sh: loops are
# seamless, every tone and modulation rate a whole multiple of 1/duration, and a noisy loop renders 1.5 s long so its
# first second warms the filters and its last 0.5 s crossfades over the head.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/blocks/hyperion"
TMP="${TMPDIR:-/tmp}/frozendawn_hyperion.wav"
mkdir -p "$OUT"
trap 'rm -f "$TMP"' EXIT

render() { # name, then ffmpeg inputs and filter args ending in [out]
  local name="$1"; shift
  ffmpeg -hide_banner -loglevel error -y "$@" -map "[out]" -ac 1 -ar 44100 -map_metadata -1 -fflags +bitexact -flags:a +bitexact -c:a pcm_s16le "$TMP"
  oggenc -Q -q 5 -s 52000 -o "$OUT/$name.ogg" "$TMP"
}
# Closes a loop of $1 seconds: [$2] is the mix, rendered $1 + 1.5 s long; [$3] is the seamless loop.
seam() { echo "[$2]atrim=1:$(echo "$1 + 1.5" | bc),asetpts=PTS-STARTPTS,asplit[$3h][$3b];[$3b]atrim=0.5:$(echo "$1 + 0.5" | bc),asetpts=PTS-STARTPTS[$3body];[$3h]atrim=0:0.5,asetpts=PTS-STARTPTS[$3head];[$3body][$3head]acrossfade=d=0.5:c1=tri:c2=tri[$3]"; }

# Hum, 5 s loop: 50 Hz and a soft 100 Hz triangle over low-passed brown noise, all pulsing at 0.8 Hz. Runtime volume
# falls as the surroundings warm, so a battery in deep cold sounds at ease.
render hum_loop \
  -f lavfi -i "aevalsrc=0.30*sin(2*PI*50*t)+0.075*(sin(2*PI*100*t)-sin(2*PI*300*t)/9+sin(2*PI*500*t)/25):s=44100:d=6.5" \
  -f lavfi -i "anoisesrc=color=brown:duration=6.5:sample_rate=44100:seed=52001" \
  -filter_complex "[1:a]lowpass=f=400,volume=0.25[n];[0:a][n]amix=inputs=2:normalize=0,tremolo=f=0.8:d=0.3,alimiter=limit=0.5:level=0[mix];$(seam 5 mix out)"

# Chirp, 0.2 s: one 80 ms rising blip, 2.2 to 3.0 kHz, every ten seconds while heat is winning.
render chirp \
  -f lavfi -i "aevalsrc='0.6*min(1\, t/0.005)*exp(-35*t)*sin(2*PI*(2200*t+5000*t*t))':s=44100:d=0.2" \
  -filter_complex "[0:a]afade=t=out:st=0.17:d=0.03,alimiter=limit=0.7:level=0[out]"

# Warning, 0.84 s loop: alternating 881/619 Hz soft squares (odd harmonics to the 7th), each 0.42 s tone a whole number
# of cycles and ramped at its edges. Runtime pitch rises 1.0 to 1.6 as the quench nears.
render warning_loop \
  -f lavfi -i "aevalsrc='if(lt(t\,0.42)\, sin(2*PI*880.952381*t)+sin(6*PI*880.952381*t)/3+sin(10*PI*880.952381*t)/5+sin(14*PI*880.952381*t)/7\, sin(2*PI*619.047619*t)+sin(6*PI*619.047619*t)/3+sin(10*PI*619.047619*t)/5+sin(14*PI*619.047619*t)/7) * min(1\, min(mod(t\,0.42)\, 0.42-mod(t\,0.42))/0.01) * 0.45':s=44100:d=0.84" \
  -filter_complex "[0:a]lowpass=f=2200,alimiter=limit=0.6:level=0[out]"

# Whine, 25 s: the final seconds. A band-limited saw sweeping 220 to 1800 Hz, shaped by a high- and low-pass band,
# swelling as it rises, with a tremolo that quickens from 4 to 14 Hz.
render whine \
  -f lavfi -i "aevalsrc='(0.25+0.75*min(1\, t/22.5))*if(gt(t\,22.5)\, (25-t)/2.5\, 1)*(1-0.33*(0.5+0.5*sin(2*PI*(4*t+0.2*t*t))))*0.22*(sin(2*PI*2616.7*(exp(0.084076*t)-1))+sin(4*PI*2616.7*(exp(0.084076*t)-1))/2+sin(6*PI*2616.7*(exp(0.084076*t)-1))/3+sin(8*PI*2616.7*(exp(0.084076*t)-1))/4+sin(10*PI*2616.7*(exp(0.084076*t)-1))/5)':s=44100:d=25" \
  -filter_complex "[0:a]highpass=f=300,lowpass=f=4500,afade=t=in:st=0:d=0.05,alimiter=limit=0.7:level=0[out]"

# Quench, 1.5 s: a heavy thump falling 90 to 28 Hz under a noise burst that darkens as it dies.
render quench \
  -f lavfi -i "aevalsrc='0.95*min(1\, t/0.003)*exp(-5*t)*sin(2*PI*90*(0.6/log(90/28))*(1-exp(-log(90/28)*t/0.6)))':s=44100:d=1.5" \
  -f lavfi -i "anoisesrc=color=white:duration=1.5:sample_rate=44100:seed=52002" \
  -f lavfi -i "aevalsrc='min(1\, t/0.002)*exp(-7*t)':s=44100:d=1.5" \
  -filter_complex "[1:a]lowpass=f=1600[b];[b][2:a]amultiply,volume=1.2[burst];[0:a][burst]amix=inputs=2:normalize=0,afade=t=out:st=1.2:d=0.3,alimiter=limit=0.9:level=0[out]"

# Vent, 8.5 s: the steam. A band-passed noise roar over a low rumble: in over 0.25 s, held to 2.5 s, then dying away.
render vent \
  -f lavfi -i "anoisesrc=color=white:duration=8.5:sample_rate=44100:seed=52003" \
  -f lavfi -i "anoisesrc=color=brown:duration=8.5:sample_rate=44100:seed=52004" \
  -f lavfi -i "aevalsrc='min(1\, t/0.25)*if(gt(t\,2.5)\, exp(-1.1*(t-2.5))\, 1)':s=44100:d=8.5" \
  -filter_complex "[0:a]bandpass=f=2600:width_type=q:w=0.7,volume=1.5[roar];[1:a]lowpass=f=250,volume=1.6[rumble];[roar][rumble]amix=inputs=2:normalize=0[steam];[steam][2:a]amultiply,afade=t=out:st=8.0:d=0.5,alimiter=limit=0.75:level=0[out]"

# Aftermath, 10 s: the shell settling. Sparse resonant ticks that thin out, four inharmonic metal pings (1 : 2.76 :
# 5.4, like a struck pipe) and a fading high boil-off hiss. No pitch sweeps, nothing voice-like.
TICKS="0.3:0.48 0.5:0.47 0.75:0.46 0.95:0.45 1.3:0.43 1.55:0.42 1.9:0.4 2.4:0.38 2.8:0.36 3.4:0.33 4.1:0.3 4.9:0.25 5.8:0.21 6.9:0.15 8.1:0.11 9.3:0.06"
TICK_ENV=$(for p in $TICKS; do t="${p%%:*}"; a="${p##*:}"; printf '+%s*gte(t\\,%s)*exp(-160*(t-%s))' "$a" "$t" "$t"; done)
ping() { # time, base Hz, level
  printf '+%s*gte(t\\,%s)*(exp(-2.5*(t-%s))*sin(2*PI*%s*(t-%s))+0.5*exp(-4.4*(t-%s))*sin(2*PI*%s*(t-%s))+0.25*exp(-8*(t-%s))*sin(2*PI*%s*(t-%s)))' \
    "$3" "$1" "$1" "$2" "$1" "$1" "$(echo "$2 * 2.76" | bc)" "$1" "$1" "$(echo "$2 * 5.4" | bc)" "$1"
}
PINGS="$(ping 0.9 512 0.16)$(ping 3.1 437 0.14)$(ping 5.3 690 0.11)$(ping 7.7 588 0.08)"
render aftermath \
  -f lavfi -i "anoisesrc=color=white:duration=10:sample_rate=44100:seed=52005" \
  -f lavfi -i "aevalsrc='0${TICK_ENV}':s=44100:d=10" \
  -f lavfi -i "aevalsrc='0${PINGS}':s=44100:d=10" \
  -f lavfi -i "anoisesrc=color=white:duration=10:sample_rate=44100:seed=52006" \
  -f lavfi -i "aevalsrc='0.08*exp(-0.44*t)':s=44100:d=10" \
  -filter_complex "[0:a]bandpass=f=3600:width_type=q:w=6,volume=3[r];[r][1:a]amultiply[ticks];[3:a]highpass=f=3000[h];[h][4:a]amultiply[hiss];[ticks][2:a][hiss]amix=inputs=3:normalize=0,afade=t=in:st=0:d=0.01,afade=t=out:st=9.5:d=0.5,alimiter=limit=0.8:level=0[out]"

# All clear, 1.3 s: three falling bell tones, 1320, 990 and 660 Hz, 0.28 s apart.
render all_clear \
  -f lavfi -i "aevalsrc='0.3*(min(1\, t/0.01)*exp(-9*t)*sin(2*PI*1320*t)+gte(t\,0.28)*min(1\, (t-0.28)/0.01)*exp(-9*(t-0.28))*sin(2*PI*990*(t-0.28))+gte(t\,0.56)*min(1\, (t-0.56)/0.01)*exp(-9*(t-0.56))*sin(2*PI*660*(t-0.56)))':s=44100:d=1.3" \
  -filter_complex "[0:a]afade=t=out:st=1.1:d=0.2,alimiter=limit=0.7:level=0[out]"

# Ring clang, 1.2 s: the floating rings landing on the base after a quench. Three heavy metal strikes 0.06 s apart,
# one per falling ring, each a thump gliding 70 to 40 Hz with inharmonic partials at 220 x (1 : 2.76 : 5.4) dying at
# 3, 5 and 9 per second, over a short band-passed knock. No sweep above 300 Hz, nothing voice-like.
strike() { # time, level
  printf '+%s*gte(t\\,%s)*(0.7*exp(-12*(t-%s))*sin(2*PI*(40*(t-%s)+4.5*(1-exp(-(t-%s)/0.15))))+0.25*exp(-3*(t-%s))*sin(2*PI*220*(t-%s))+0.15*exp(-5*(t-%s))*sin(2*PI*607.2*(t-%s))+0.08*exp(-9*(t-%s))*sin(2*PI*1188*(t-%s)))' \
    "$2" "$1" "$1" "$1" "$1" "$1" "$1" "$1" "$1" "$1" "$1"
}
knock() { printf '+%s*gte(t\\,%s)*exp(-60*(t-%s))' "$2" "$1" "$1"; }
render ring_clang \
  -f lavfi -i "aevalsrc='0$(strike 0 1)$(strike 0.06 0.85)$(strike 0.12 0.7)':s=44100:d=1.2" \
  -f lavfi -i "anoisesrc=color=pink:duration=1.2:sample_rate=44100:seed=52008" \
  -f lavfi -i "aevalsrc='0$(knock 0 1)$(knock 0.06 0.85)$(knock 0.12 0.7)':s=44100:d=1.2" \
  -filter_complex "[1:a]bandpass=f=1200:width_type=q:w=1.2[k];[k][2:a]amultiply,volume=0.6[knock];[0:a][knock]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.002,afade=t=out:st=1.0:d=0.2,alimiter=limit=0.85:level=0[out]"
