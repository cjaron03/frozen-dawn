#!/usr/bin/env bash
# SFR-1 Solstice Reactor sounds: ffmpeg lavfi synthesis only, fixed noise seeds, mono 44.1 kHz Vorbis (oggenc -q 5:
# ffmpeg's own Vorbis encoder only writes stereo, and Minecraft attenuates only mono sounds by distance).
# Loops are seamless: tones are whole multiples of 1/duration, and noise beds crossfade their last 0.5 s over their first.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/blocks/sfr"
TMP="${TMPDIR:-/tmp}/frozendawn_sfr.wav"
mkdir -p "$OUT"
trap 'rm -f "$TMP"' EXIT

render() { # name, then ffmpeg inputs and filter args ending in [out]
  local name="$1"; shift
  ffmpeg -hide_banner -loglevel error -y "$@" -map "[out]" -ac 1 -ar 44100 -map_metadata -1 -fflags +bitexact -flags:a +bitexact -c:a pcm_s16le "$TMP"
  oggenc -Q -q 5 -s 51000 -o "$OUT/$name.ogg" "$TMP"
}
# A seamless noise bed of $1 seconds: label [$2] from input index $3, generated 0.5 s longer than the loop.
loop_noise() { echo "[$3:a]asplit[$2h][$2b];[$2b]atrim=0.5:$(echo "$1 + 0.5" | bc),asetpts=PTS-STARTPTS[$2body];[$2h]atrim=0:0.5,asetpts=PTS-STARTPTS[$2head];[$2body][$2head]acrossfade=d=0.5:c1=tri:c2=tri[$2]"; }

# Cryocooler, 4 s: a 1 Hz soft chuff on brown noise, with a faint high helium tick between chuffs.
render cryo_loop \
  -f lavfi -i "anoisesrc=color=brown:duration=4.5:sample_rate=44100:seed=51001" \
  -f lavfi -i "aevalsrc=0.05*sin(2*PI*3500*t)*exp(-70*mod(t+0.5\,1)):s=44100:d=4" \
  -filter_complex "$(loop_noise 4 n 0);[n]lowpass=f=700,tremolo=f=1:d=0.85,volume=0.9[chuff];[chuff][1:a]amix=inputs=2:normalize=0,alimiter=limit=0.5:level=0[out]"

# Ignition, 11.5 s: a 10 s capacitor charge sweeping 80 to 900 Hz with a rising whine, then a heavy thump.
render ignition \
  -f lavfi -i "aevalsrc='if(lt(t\,10)\, (0.18+0.25*t/10)*sin(2*PI*(80*t+820*t*t*t/300)) + 0.08*(t/10)*(t/10)*sin(6*PI*(80*t+820*t*t*t/300))\, 0) + if(gte(t\,10)\, 0.9*exp(-3.5*(t-10))*sin(2*PI*(44*(t-10)-6*(t-10)*(t-10))) + 0.35*exp(-9*(t-10))*sin(2*PI*90*(t-10))\, 0)':s=44100:d=11.5" \
  -f lavfi -i "anoisesrc=color=pink:duration=11.5:sample_rate=44100:seed=51002" \
  -filter_complex "[1:a]lowpass=f=300,volume=0.6,afade=t=in:st=9.98:d=0.02,afade=t=out:st=10.05:d=0.6[boom];[0:a]afade=t=in:st=0:d=0.4[charge];[charge][boom]amix=inputs=2:normalize=0,afade=t=out:st=11.0:d=0.5,alimiter=limit=0.8:level=0[out]"

# Burn, 8 s: 30 Hz sub, 60 and 120 Hz magnet hum, band-passed pink plasma roar, a slow 0.25 Hz swell.
render burn_loop \
  -f lavfi -i "aevalsrc=0.30*sin(2*PI*30*t)+0.16*sin(2*PI*60*t)+0.07*sin(2*PI*120*t)+0.03*sin(2*PI*180*t):s=44100:d=8" \
  -f lavfi -i "anoisesrc=color=pink:duration=8.5:sample_rate=44100:seed=51003" \
  -filter_complex "$(loop_noise 8 p 1);[p]bandpass=f=420:width_type=q:w=0.7,volume=0.55[roar];[0:a][roar]amix=inputs=2:normalize=0,tremolo=f=0.25:d=0.3,alimiter=limit=0.5:level=0[out]"

# Rotary vacuum pump, 4 s: a 25 Hz pulsed grind over a thin mechanical rasp. Runtime pitch thins it as the channel empties.
render pump_loop \
  -f lavfi -i "aevalsrc=(0.28*sin(2*PI*25*t)+0.14*sin(2*PI*50*t)+0.08*sin(2*PI*75*t)+0.04*sin(2*PI*150*t))*(0.55+0.45*sin(2*PI*5*t)):s=44100:d=4" \
  -f lavfi -i "anoisesrc=color=white:duration=4.5:sample_rate=44100:seed=51004" \
  -filter_complex "$(loop_noise 4 w 1);[w]bandpass=f=900:width_type=q:w=1.2,tremolo=f=25:d=0.7,volume=0.35[rasp];[0:a][rasp]amix=inputs=2:normalize=0,alimiter=limit=0.45:level=0[out]"

# Warning, 1.2 s: descending two-tone, about 1046 then 784 Hz, each tone ramped so the cycle joins cleanly.
render alarm_loop \
  -f lavfi -i "aevalsrc='if(lt(t\,0.6)\, sin(2*PI*1045.8333*t)+0.25*sin(6*PI*1045.8333*t)\, sin(2*PI*783.3333*t)+0.25*sin(6*PI*783.3333*t)) * min(1\, min(mod(t\,0.6)\, 0.6-mod(t\,0.6))/0.015) * 0.19':s=44100:d=1.2" \
  -filter_complex "[0:a]alimiter=limit=0.7:level=0[out]"

# Ready, 0.9 s: a short rising three-note chime.
render ready \
  -f lavfi -i "aevalsrc='0.5*(if(lt(t\,0.9)\, sin(2*PI*659*t)*exp(-5*t))+if(gte(t\,0.15)\, sin(2*PI*880*(t-0.15))*exp(-5*(t-0.15)))+if(gte(t\,0.3)\, sin(2*PI*1175*(t-0.3))*exp(-4*(t-0.3))))':s=44100:d=0.9" \
  -filter_complex "[0:a]afade=t=in:st=0:d=0.005,afade=t=out:st=0.75:d=0.15,alimiter=limit=0.75:level=0[out]"

# Refuse, 0.6 s: a low double buzz.
render refuse \
  -f lavfi -i "aevalsrc='(sin(2*PI*110*t)+0.5*sin(4*PI*110*t)+0.33*sin(6*PI*110*t)+0.25*sin(8*PI*110*t)) * (between(t\,0.01\,0.25)+between(t\,0.33\,0.58)) * 0.38':s=44100:d=0.6" \
  -filter_complex "[0:a]lowpass=f=1800,alimiter=limit=0.75:level=0[out]"

# Disruption, 2.5 s: a sharp electric crack into a falling 600 to 40 Hz plasma collapse.
render disruption \
  -f lavfi -i "aevalsrc='0.7*exp(-1.4*t)*sin(2*PI*600*(2.5/log(15))*(1-exp(-log(15)*t/2.5)))':s=44100:d=2.5" \
  -f lavfi -i "anoisesrc=color=white:duration=2.5:sample_rate=44100:seed=51005" \
  -f lavfi -i "anoisesrc=color=pink:duration=2.5:sample_rate=44100:seed=51006" \
  -f lavfi -i "aevalsrc='exp(-45*t)':s=44100:d=2.5" \
  -f lavfi -i "aevalsrc='exp(-1.8*t)':s=44100:d=2.5" \
  -filter_complex "[1:a]highpass=f=2500[w];[w][3:a]amultiply,volume=1.8[crack];[2:a]lowpass=f=500[p];[p][4:a]amultiply,volume=0.9[collapse];[0:a][crack][collapse]amix=inputs=3:normalize=0,afade=t=out:st=2.2:d=0.3,alimiter=limit=0.85:level=0[out]"

# Quench, 5 s: a roaring cryogen vent, loud high-passed noise with a fast attack and slow decay, over a low rumble.
render quench \
  -f lavfi -i "anoisesrc=color=white:duration=5:sample_rate=44100:seed=51007" \
  -f lavfi -i "anoisesrc=color=brown:duration=5:sample_rate=44100:seed=51008" \
  -f lavfi -i "aevalsrc='min(1\, t/0.04)*exp(-0.55*t)':s=44100:d=5" \
  -f lavfi -i "aevalsrc='min(1\, t/0.2)*exp(-0.35*t)':s=44100:d=5" \
  -filter_complex "[0:a]highpass=f=1400,lowpass=f=11000,volume=1.6[hiss];[hiss][2:a]amultiply[vent];[1:a]lowpass=f=120,volume=2.2[r];[r][3:a]amultiply[rumble];[vent][rumble]amix=inputs=2:normalize=0,afade=t=out:st=4.5:d=0.5,alimiter=limit=0.74:level=0[out]"
