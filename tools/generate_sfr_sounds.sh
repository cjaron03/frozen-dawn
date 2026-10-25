#!/usr/bin/env bash
# SFR-1 Solstice Reactor sounds: ffmpeg lavfi synthesis only, fixed noise seeds, mono 44.1 kHz Vorbis (oggenc -q 5:
# ffmpeg's own Vorbis encoder only writes stereo, and Minecraft attenuates only mono sounds by distance).
# Loops are seamless: every tone and modulation rate is a whole multiple of 1/duration, and each loop renders 1.5 s long:
# the first second warms every filter, chorus and limiter (they start from silence, which clicked at the loop point),
# and the last 0.5 s crossfades over the head so the noise joins.
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
# Closes a loop of $1 seconds: [$2] is the mix, rendered $1 + 1.5 s long; [$3] is the seamless loop.
seam() { echo "[$2]atrim=1:$(echo "$1 + 1.5" | bc),asetpts=PTS-STARTPTS,asplit[$3h][$3b];[$3b]atrim=0.5:$(echo "$1 + 0.5" | bc),asetpts=PTS-STARTPTS[$3body];[$3h]atrim=0:0.5,asetpts=PTS-STARTPTS[$3head];[$3body][$3head]acrossfade=d=0.5:c1=tri:c2=tri[$3]"; }

# Cryocooler, 4 s: a 1 Hz soft chuff on brown noise, with a faint high helium tick between chuffs.
render cryo_loop \
  -f lavfi -i "anoisesrc=color=brown:duration=5.5:sample_rate=44100:seed=51001" \
  -f lavfi -i "aevalsrc=0.05*sin(2*PI*3500*t)*exp(-70*mod(t+0.5\,1)):s=44100:d=5.5" \
  -filter_complex "[0:a]lowpass=f=700,tremolo=f=1:d=0.85,volume=0.9[chuff];[chuff][1:a]amix=inputs=2:normalize=0,alimiter=limit=0.5:level=0[mix];$(seam 4 mix out)"

# Ignition, 11.5 s: a 10 s capacitor charge sweeping 80 to 900 Hz with a rising whine, then a heavy thump.
render ignition \
  -f lavfi -i "aevalsrc='if(lt(t\,10)\, (0.18+0.25*t/10)*sin(2*PI*(80*t+820*t*t*t/300)) + 0.08*(t/10)*(t/10)*sin(6*PI*(80*t+820*t*t*t/300))\, 0) + if(gte(t\,10)\, 0.9*exp(-3.5*(t-10))*sin(2*PI*(44*(t-10)-6*(t-10)*(t-10))) + 0.35*exp(-9*(t-10))*sin(2*PI*90*(t-10))\, 0)':s=44100:d=11.5" \
  -f lavfi -i "anoisesrc=color=pink:duration=11.5:sample_rate=44100:seed=51002" \
  -filter_complex "[1:a]lowpass=f=300,volume=0.6,afade=t=in:st=9.98:d=0.02,afade=t=out:st=10.05:d=0.6[boom];[0:a]afade=t=in:st=0:d=0.4[charge];[charge][boom]amix=inputs=2:normalize=0,afade=t=out:st=11.0:d=0.5,alimiter=limit=0.8:level=0[out]"

# Burn, 8 s: a 40 Hz sub beating against 40.25 Hz for a slow throb, an 80/120/160 Hz magnet hum, a detuned A3/E4/A4
# field choir, a band-passed plasma roar breathing at 0.5 Hz, a high ion shimmer and sparse electric crackles, all
# pulsing at 1.5 Hz.
render burn_loop \
  -f lavfi -i "aevalsrc=0.20*sin(2*PI*40*t)+0.20*sin(2*PI*40.25*t)+0.11*sin(2*PI*80*t)+0.06*sin(2*PI*120*t)+0.03*sin(2*PI*160*t):s=44100:d=9.5" \
  -f lavfi -i "aevalsrc=0.035*(sin(2*PI*220*t)+sin(2*PI*220.5*t))+0.025*(sin(2*PI*329.625*t)+sin(2*PI*330.125*t))+0.018*(sin(2*PI*440*t)+sin(2*PI*440.375*t))+0.008*sin(2*PI*880.25*t):s=44100:d=9.5" \
  -f lavfi -i "anoisesrc=color=pink:duration=9.5:sample_rate=44100:seed=51003" \
  -f lavfi -i "anoisesrc=color=white:duration=9.5:sample_rate=44100:seed=51009" \
  -f lavfi -i "anoisesrc=color=white:duration=9.5:sample_rate=44100:seed=51010" \
  -f lavfi -i "aevalsrc='exp(-90*abs(mod(t\,8)-0.70))+0.7*exp(-120*abs(mod(t\,8)-2.35))+exp(-80*abs(mod(t\,8)-3.15))+0.6*exp(-110*abs(mod(t\,8)-5.55))+0.9*exp(-90*abs(mod(t\,8)-6.85))':s=44100:d=9.5" \
  -filter_complex "[2:a]bandpass=f=520:width_type=q:w=0.8,tremolo=f=0.5:d=0.45,volume=0.42[roar];[3:a]highpass=f=6000,tremolo=f=3:d=0.6,volume=0.10[ion];[4:a]bandpass=f=3200:width_type=q:w=0.9[c];[c][5:a]amultiply,volume=0.9[crackle];[1:a]chorus=0.6:0.9:40|55:0.4|0.3:0.25|0.375:2|1.5,volume=1.3[choir];[0:a][choir][roar][ion][crackle]amix=inputs=5:normalize=0:duration=shortest,tremolo=f=1.5:d=0.18,alimiter=limit=0.55:level=0[mix];$(seam 8 mix out)"

# Rotary vacuum pump, 4 s: a 25 Hz pulsed grind over a thin mechanical rasp. Runtime pitch thins it as the channel empties.
render pump_loop \
  -f lavfi -i "aevalsrc=(0.28*sin(2*PI*25*t)+0.14*sin(2*PI*50*t)+0.08*sin(2*PI*75*t)+0.04*sin(2*PI*150*t))*(0.55+0.45*sin(2*PI*5*t)):s=44100:d=5.5" \
  -f lavfi -i "anoisesrc=color=white:duration=5.5:sample_rate=44100:seed=51004" \
  -filter_complex "[1:a]bandpass=f=900:width_type=q:w=1.2,tremolo=f=25:d=0.7,volume=0.35[rasp];[0:a][rasp]amix=inputs=2:normalize=0,alimiter=limit=0.45:level=0[mix];$(seam 4 mix out)"

# Warning, 1.2 s: descending two-tone, about 1046 then 784 Hz, each tone ramped so the cycle joins cleanly.
render alarm_loop \
  -f lavfi -i "aevalsrc='if(lt(t\,0.6)\, sin(2*PI*1045.8333*t)+0.25*sin(6*PI*1045.8333*t)\, sin(2*PI*783.3333*t)+0.25*sin(6*PI*783.3333*t)) * min(1\, min(mod(t\,0.6)\, 0.6-mod(t\,0.6))/0.015) * 0.19':s=44100:d=1.2" \
  -filter_complex "[0:a]alimiter=limit=0.7:level=0[out]"

# Ready, 0.9 s: a short rising three-note chime.
render ready \
  -f lavfi -i "aevalsrc='0.5*(if(lt(t\,0.9)\, sin(2*PI*659*t)*exp(-5*t))+if(gte(t\,0.15)\, sin(2*PI*880*(t-0.15))*exp(-5*(t-0.15)))+if(gte(t\,0.3)\, sin(2*PI*1175*(t-0.3))*exp(-4*(t-0.3))))':s=44100:d=0.9" \
  -filter_complex "[0:a]afade=t=in:st=0:d=0.005,afade=t=out:st=0.75:d=0.15,alimiter=limit=0.75:level=0[out]"

# Ignited, 3.2 s, after the ignition thump: a plasma bloom of swelling band-passed noise, a rising A major arpeggio
# (A4, C#5, E5, A5) of bell tones, then the chord held with a soft shimmer as it fades.
render ignited \
  -f lavfi -i "aevalsrc='0.22*(sin(2*PI*440*t)*exp(-1.1*t) + if(gte(t\,0.12)\, sin(2*PI*554.37*(t-0.12))*exp(-1.1*(t-0.12))) + if(gte(t\,0.24)\, sin(2*PI*659.26*(t-0.24))*exp(-1.0*(t-0.24))) + if(gte(t\,0.36)\, sin(2*PI*880*(t-0.36))*exp(-0.9*(t-0.36)) + 0.3*sin(2*PI*1760*(t-0.36))*exp(-2.5*(t-0.36))))':s=44100:d=3.2" \
  -f lavfi -i "aevalsrc='0.10*(sin(2*PI*220*t)+0.7*sin(2*PI*277.18*t)+0.6*sin(2*PI*329.63*t))*min(1\, t/0.5)*exp(-0.9*t)':s=44100:d=3.2" \
  -f lavfi -i "anoisesrc=color=pink:duration=3.2:sample_rate=44100:seed=51011" \
  -f lavfi -i "aevalsrc='min(1\, t/0.25)*exp(-2.2*t)':s=44100:d=3.2" \
  -filter_complex "[2:a]bandpass=f=700:width_type=q:w=0.6,volume=1.4[n];[n][3:a]amultiply[bloom];[1:a]chorus=0.6:0.9:35|50:0.4|0.3:0.25|0.4:2|1.5[pad];[0:a][pad][bloom]amix=inputs=3:normalize=0,afade=t=in:st=0:d=0.01,afade=t=out:st=2.6:d=0.6,alimiter=limit=0.8:level=0[out]"

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

# DMS off, 1.9 s: three rising whoops, 500 to 1100 Hz with a square-ish edge, each 0.45 s with a short gap, so
# disabling mitigation sounds like an interlock dropping out rather than the descending warning tone.
render dms_off \
  -f lavfi -i "aevalsrc='if(lt(mod(t\,0.6)\,0.45)\, (sin(2*PI*(500*mod(t\,0.6)+667*mod(t\,0.6)*mod(t\,0.6))) + 0.3*sin(6*PI*(500*mod(t\,0.6)+667*mod(t\,0.6)*mod(t\,0.6)))) * min(1\, min(mod(t\,0.6)\, 0.45-mod(t\,0.6))/0.02)\, 0) * 0.22':s=44100:d=1.9" \
  -filter_complex "[0:a]afade=t=out:st=1.75:d=0.15,alimiter=limit=0.75:level=0[out]"

# Valve, 0.8 s: Stop shutting the fuel valve. A heavy 62 Hz thud that sags in pitch, a short metallic knock (a
# band-passed click with an inharmonic 417/1093 Hz ring) and a little low body noise.
render valve \
  -f lavfi -i "aevalsrc='0.9*exp(-9*t)*sin(2*PI*(62*t-14*t*t)) + 0.22*exp(-22*t)*(sin(2*PI*417*t)+0.6*sin(2*PI*1093*t))':s=44100:d=0.8" \
  -f lavfi -i "anoisesrc=color=white:duration=0.8:sample_rate=44100:seed=51013" \
  -f lavfi -i "anoisesrc=color=pink:duration=0.8:sample_rate=44100:seed=51014" \
  -f lavfi -i "aevalsrc='exp(-60*t)':s=44100:d=0.8" \
  -f lavfi -i "aevalsrc='exp(-6*t)':s=44100:d=0.8" \
  -filter_complex "[1:a]bandpass=f=1800:width_type=q:w=1.5[k];[k][3:a]amultiply,volume=1.2[knock];[2:a]lowpass=f=400[b];[b][4:a]amultiply,volume=0.4[body];[0:a][knock][body]amix=inputs=3:normalize=0,afade=t=in:st=0:d=0.003,afade=t=out:st=0.65:d=0.15,alimiter=limit=0.85:level=0[out]"

# Shutdown, 1.6 s: the ramp's clean end. A soft descending two-tone bell, E5 then A4, each with a quiet octave.
render shutdown \
  -f lavfi -i "aevalsrc='0.45*(sin(2*PI*659.26*t)*exp(-3.2*t) + 0.2*sin(2*PI*1318.5*t)*exp(-6*t) + if(gte(t\,0.3)\, sin(2*PI*440*(t-0.3))*exp(-2.4*(t-0.3)) + 0.2*sin(2*PI*880*(t-0.3))*exp(-5*(t-0.3))))':s=44100:d=1.6" \
  -filter_complex "[0:a]afade=t=in:st=0:d=0.005,afade=t=out:st=1.35:d=0.25,alimiter=limit=0.7:level=0[out]"

# Isotope Column, 4 s: a soft 50/100 Hz pump hum on filtered brown noise, with small rising bubble blips at uneven
# times inside the loop.
render column_loop \
  -f lavfi -i "aevalsrc=0.10*sin(2*PI*50*t)+0.05*sin(2*PI*100*t)+0.02*sin(2*PI*150*t):s=44100:d=5.5" \
  -f lavfi -i "anoisesrc=color=brown:duration=5.5:sample_rate=44100:seed=51012" \
  -f lavfi -i "aevalsrc='0.10*(if(gte(mod(t\,4)\,0.30)\, exp(-28*(mod(t\,4)-0.30))*sin(2*PI*(520*(mod(t\,4)-0.30)+1800*(mod(t\,4)-0.30)*(mod(t\,4)-0.30))))+0.8*if(gte(mod(t\,4)\,1.15)\, exp(-32*(mod(t\,4)-1.15))*sin(2*PI*(640*(mod(t\,4)-1.15)+2200*(mod(t\,4)-1.15)*(mod(t\,4)-1.15))))+if(gte(mod(t\,4)\,1.55)\, exp(-26*(mod(t\,4)-1.55))*sin(2*PI*(460*(mod(t\,4)-1.55)+1600*(mod(t\,4)-1.55)*(mod(t\,4)-1.55))))+0.7*if(gte(mod(t\,4)\,2.70)\, exp(-30*(mod(t\,4)-2.70))*sin(2*PI*(580*(mod(t\,4)-2.70)+2000*(mod(t\,4)-2.70)*(mod(t\,4)-2.70))))+0.9*if(gte(mod(t\,4)\,3.35)\, exp(-28*(mod(t\,4)-3.35))*sin(2*PI*(500*(mod(t\,4)-3.35)+1900*(mod(t\,4)-3.35)*(mod(t\,4)-3.35)))))':s=44100:d=5.5" \
  -filter_complex "[1:a]lowpass=f=400,volume=0.5[bed];[0:a][bed][2:a]amix=inputs=3:normalize=0,alimiter=limit=0.4:level=0[mix];$(seam 4 mix out)"
