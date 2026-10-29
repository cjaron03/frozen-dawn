#!/usr/bin/env bash
# AMR-1 Icarus sounds: ffmpeg lavfi synthesis only, fixed noise seeds, mono 44.1 kHz Vorbis through oggenc -q 5 (mono so
# Minecraft attenuates them by distance). Loops follow tools/generate_sfr_sounds.sh: every tone and modulation rate is a
# whole multiple of 1/duration, and each loop renders 1.5 s long so the seam helper can warm the filters and crossfade the
# tail over the head.
# The suit lines are not made here: they are Piper lines in tools/audio_sources/generated_voice/polly_manifest.tsv,
# rendered by tools/generate_local_voice_assets.sh like every other shipped voice.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/blocks/icarus"
WORK="$(mktemp -d)"
TMP="$WORK/render.wav"
mkdir -p "$OUT"
trap 'rm -rf "$WORK"' EXIT

render() { # name, then ffmpeg inputs and filter args ending in [out]
  local name="$1"; shift
  ffmpeg -hide_banner -loglevel error -y "$@" -map "[out]" -ac 1 -ar 44100 -map_metadata -1 -fflags +bitexact -flags:a +bitexact -c:a pcm_s16le "$TMP"
  oggenc -Q -q 5 -s 61000 -o "$OUT/$name.ogg" "$TMP"
}
seam() { echo "[$2]atrim=1:$(echo "$1 + 1.5" | bc),asetpts=PTS-STARTPTS,asplit[$3h][$3b];[$3b]atrim=0.5:$(echo "$1 + 0.5" | bc),asetpts=PTS-STARTPTS[$3body];[$3h]atrim=0:0.5,asetpts=PTS-STARTPTS[$3head];[$3body][$3head]acrossfade=d=0.5:c1=tri:c2=tri[$3]"; }

# Frame hum, 4 s loop: the energized frame. A 60 Hz transformer buzz with odd harmonics over faint brown noise.
render frame_hum \
  -f lavfi -i "aevalsrc=0.16*sin(2*PI*60*t)+0.08*sin(2*PI*180*t)+0.04*sin(2*PI*300*t)+0.02*sin(2*PI*420*t)+0.015*sin(2*PI*120.25*t):s=44100:d=5.5" \
  -f lavfi -i "anoisesrc=color=brown:duration=5.5:sample_rate=44100:seed=61001" \
  -filter_complex "[1:a]lowpass=f=250,volume=0.25[bed];[0:a][bed]amix=inputs=2:normalize=0,tremolo=f=0.5:d=0.1,alimiter=limit=0.4:level=0[mix];$(seam 4 mix out)"

# Power on, 2 s: a contactor clack, then the frame hum rising from nothing.
render power_on \
  -f lavfi -i "aevalsrc='0.8*exp(-40*t)*sin(2*PI*(90*t-20*t*t)) + min(1\, t/1.5)*(0.16*sin(2*PI*60*t)+0.08*sin(2*PI*180*t)+0.04*sin(2*PI*300*t))':s=44100:d=2" \
  -f lavfi -i "anoisesrc=color=white:duration=2:sample_rate=44100:seed=61002" \
  -f lavfi -i "aevalsrc='exp(-90*t)':s=44100:d=2" \
  -filter_complex "[1:a]bandpass=f=2400:width_type=q:w=1.2[c];[c][2:a]amultiply,volume=1.4[clack];[0:a][clack]amix=inputs=2:normalize=0,afade=t=out:st=1.7:d=0.3,alimiter=limit=0.8:level=0[out]"

# Stabilizer spin-up, 8 s: the four rings accelerating, a motor whine sweeping 120 to 1800 Hz with a rotor chop.
render stabilizer_spinup \
  -f lavfi -i "aevalsrc='(0.16+0.10*t/8)*sin(2*PI*(120*t+1680*t*t*t/192)) + 0.06*(t/8)*sin(4*PI*(120*t+1680*t*t*t/192))':s=44100:d=8" \
  -f lavfi -i "anoisesrc=color=pink:duration=8:sample_rate=44100:seed=61003" \
  -filter_complex "[1:a]bandpass=f=700:width_type=q:w=0.9,volume=0.35,afade=t=in:st=0:d=3[air];[0:a][air]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.5,afade=t=out:st=7.4:d=0.6,alimiter=limit=0.7:level=0[out]"

# Stabilizer whine, 4 s loop: rings at speed. 1800 Hz against 1800.5 Hz for a slow beat, a 450 Hz motor body and
# rushing air chopped at 12 Hz.
render stabilizer_whine \
  -f lavfi -i "aevalsrc=0.05*sin(2*PI*1800*t)+0.05*sin(2*PI*1800.5*t)+0.09*sin(2*PI*450*t)+0.04*sin(2*PI*900*t):s=44100:d=5.5" \
  -f lavfi -i "anoisesrc=color=pink:duration=5.5:sample_rate=44100:seed=61004" \
  -filter_complex "[1:a]bandpass=f=900:width_type=q:w=1.0,tremolo=f=12:d=0.5,volume=0.3[air];[0:a][air]amix=inputs=2:normalize=0,alimiter=limit=0.4:level=0[mix];$(seam 4 mix out)"

# Shield form, 3 s: the confinement closing. A rising shimmer sweep into a held D minor-ninth drone.
render shield_form \
  -f lavfi -i "aevalsrc='0.20*sin(2*PI*(200*t+300*t*t/3))*exp(-0.6*t) + min(1\, t/1.2)*0.08*(sin(2*PI*73.42*t)+sin(2*PI*146.83*t)+0.7*sin(2*PI*174.61*t)+0.5*sin(2*PI*329.63*t))':s=44100:d=3" \
  -f lavfi -i "anoisesrc=color=white:duration=3:sample_rate=44100:seed=61005" \
  -f lavfi -i "aevalsrc='min(1\, t/1.0)*exp(-1.4*t)':s=44100:d=3" \
  -filter_complex "[1:a]highpass=f=5000,volume=0.6[h];[h][2:a]amultiply[shimmer];[0:a]chorus=0.6:0.9:40|60:0.4|0.3:0.25|0.375:2|1.3[tone];[tone][shimmer]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.05,afade=t=out:st=2.4:d=0.6,alimiter=limit=0.75:level=0[out]"

# Shield hum, 6 s loop: the magnetic shell. A 36.67 Hz sub, a detuned D2/D3 drone and a slow 1/3 Hz shimmer of
# band-passed noise. Runtime pitch rises with strain.
render shield_hum \
  -f lavfi -i "aevalsrc=0.15*sin(2*PI*36.6667*t)+0.07*(sin(2*PI*73.3333*t)+sin(2*PI*73.5*t))+0.04*(sin(2*PI*146.6667*t)+sin(2*PI*147*t)):s=44100:d=7.5" \
  -f lavfi -i "anoisesrc=color=pink:duration=7.5:sample_rate=44100:seed=61006" \
  -filter_complex "[1:a]bandpass=f=1400:width_type=q:w=1.4,tremolo=f=0.3333:d=0.7,volume=0.18[ripple];[0:a][ripple]amix=inputs=2:normalize=0,alimiter=limit=0.45:level=0[mix];$(seam 6 mix out)"

# Core roar, 8 s loop: annihilation. A 27.5 Hz sub beating against 27.625 Hz, a band-passed roar breathing at 0.25 Hz,
# a thin gamma sizzle and dense crackle, all throbbing at 2 Hz. Runtime pitch 0.6 to 1.6 follows core temperature.
render core_roar \
  -f lavfi -i "aevalsrc=0.22*sin(2*PI*27.5*t)+0.22*sin(2*PI*27.625*t)+0.10*sin(2*PI*55*t)+0.05*sin(2*PI*82.5*t):s=44100:d=9.5" \
  -f lavfi -i "anoisesrc=color=pink:duration=9.5:sample_rate=44100:seed=61007" \
  -f lavfi -i "anoisesrc=color=white:duration=9.5:sample_rate=44100:seed=61008" \
  -f lavfi -i "anoisesrc=color=white:duration=9.5:sample_rate=44100:seed=61009" \
  -f lavfi -i "aevalsrc='pow(abs(sin(2*PI*3.375*t)*sin(2*PI*5.125*t))\,6)':s=44100:d=9.5" \
  -filter_complex "[1:a]bandpass=f=220:width_type=q:w=0.7,tremolo=f=0.25:d=0.5,volume=0.9[roar];[2:a]highpass=f=7000,volume=0.07[sizzle];[3:a]bandpass=f=2600:width_type=q:w=1.1[c];[c][4:a]amultiply,volume=1.1[crackle];[0:a][roar][sizzle][crackle]amix=inputs=4:normalize=0,tremolo=f=2:d=0.15,alimiter=limit=0.6:level=0[mix];$(seam 8 mix out)"

# Injector feed, 3 s: magnetic gates clocking the antimatter down the line, a 14 Hz train of falling blips over a
# cold vapor hiss.
render injector_feed \
  -f lavfi -i "aevalsrc='0.22*exp(-30*mod(t\,0.0714))*sin(2*PI*(1600*mod(t\,0.0714)-6000*mod(t\,0.0714)*mod(t\,0.0714)))*min(1\, t/0.3)':s=44100:d=3" \
  -f lavfi -i "anoisesrc=color=white:duration=3:sample_rate=44100:seed=61010" \
  -filter_complex "[1:a]highpass=f=3000,lowpass=f=9000,volume=0.25,afade=t=in:st=0:d=0.4[hiss];[0:a][hiss]amix=inputs=2:normalize=0,afade=t=out:st=2.4:d=0.6,alimiter=limit=0.7:level=0[out]"

# Capsule dock, 1.2 s: a capsule seated in its cradle. A heavy clunk, a latch click a beat later and a short seal hiss.
render capsule_dock \
  -f lavfi -i "aevalsrc='0.9*exp(-14*t)*sin(2*PI*(75*t-18*t*t)) + if(gte(t\,0.18)\, 0.35*exp(-60*(t-0.18))*(sin(2*PI*2200*(t-0.18))+0.5*sin(2*PI*3310*(t-0.18)))\, 0)':s=44100:d=1.2" \
  -f lavfi -i "anoisesrc=color=white:duration=1.2:sample_rate=44100:seed=61011" \
  -f lavfi -i "aevalsrc='if(gte(t\,0.3)\, min(1\, (t-0.3)/0.03)*exp(-7*(t-0.3))\, 0)':s=44100:d=1.2" \
  -filter_complex "[1:a]highpass=f=4000,volume=0.5[n];[n][2:a]amultiply[seal];[0:a][seal]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.003,afade=t=out:st=1.0:d=0.2,alimiter=limit=0.85:level=0[out]"

# Ignition, 6 s: the first annihilation. A 1.5 s rising charge, a deep concussive bloom and a long high ring as the
# roar takes over.
render ignition \
  -f lavfi -i "aevalsrc='if(lt(t\,1.5)\, (0.1+0.2*t/1.5)*sin(2*PI*(300*t+500*t*t))\, 0) + if(gte(t\,1.5)\, 1.0*exp(-1.8*(t-1.5))*sin(2*PI*(38*(t-1.5)-3*(t-1.5)*(t-1.5))) + 0.12*exp(-0.7*(t-1.5))*sin(2*PI*1975.5*(t-1.5))\, 0)':s=44100:d=6" \
  -f lavfi -i "anoisesrc=color=pink:duration=6:sample_rate=44100:seed=61012" \
  -f lavfi -i "aevalsrc='if(gte(t\,1.5)\, exp(-1.2*(t-1.5))\, 0)':s=44100:d=6" \
  -filter_complex "[1:a]lowpass=f=600,volume=1.3[n];[n][2:a]amultiply[bloom];[0:a][bloom]amix=inputs=2:normalize=0,afade=t=out:st=5.2:d=0.8,alimiter=limit=0.85:level=0[out]"

# Clip slam, 1.5 s: the safety clip dumping power. A hard magnetic slam, a falling zap and a ringing frame.
render clip_slam \
  -f lavfi -i "aevalsrc='1.0*exp(-11*t)*sin(2*PI*(55*t-10*t*t)) + 0.3*exp(-6*t)*sin(2*PI*1200*(1.5/log(12))*(1-exp(-log(12)*t/1.5))) + 0.12*exp(-3*t)*(sin(2*PI*523*t)+0.7*sin(2*PI*1367*t))':s=44100:d=1.5" \
  -f lavfi -i "anoisesrc=color=white:duration=1.5:sample_rate=44100:seed=61013" \
  -f lavfi -i "aevalsrc='exp(-50*t)':s=44100:d=1.5" \
  -filter_complex "[1:a]bandpass=f=1500:width_type=q:w=0.8[c];[c][2:a]amultiply,volume=1.5[crack];[0:a][crack]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.002,afade=t=out:st=1.2:d=0.3,alimiter=limit=0.9:level=0[out]"

# COLE complete, 2.2 s: the controlled shutdown's clean end. A slow three-note descent, D5, A4, D4.
render cole_complete \
  -f lavfi -i "aevalsrc='0.4*(sin(2*PI*587.33*t)*exp(-2.6*t) + if(gte(t\,0.35)\, sin(2*PI*440*(t-0.35))*exp(-2.4*(t-0.35))) + if(gte(t\,0.7)\, sin(2*PI*293.66*(t-0.7))*exp(-1.8*(t-0.7)) + 0.25*sin(2*PI*587.33*(t-0.7))*exp(-4*(t-0.7))))':s=44100:d=2.2" \
  -filter_complex "[0:a]afade=t=in:st=0:d=0.005,afade=t=out:st=1.9:d=0.3,alimiter=limit=0.7:level=0[out]"

# Caution, 1 s: two soft equal beeps at 880 Hz.
render alarm_caution \
  -f lavfi -i "aevalsrc='0.3*sin(2*PI*880*t)*(between(t\,0.02\,0.22)+between(t\,0.4\,0.6))':s=44100:d=1" \
  -filter_complex "[0:a]lowpass=f=4000,afade=t=out:st=0.8:d=0.2,alimiter=limit=0.7:level=0[out]"

# Warning, 1.5 s: three rising square-edged tones, 660, 880 and 1175 Hz.
render alarm_warning \
  -f lavfi -i "aevalsrc='0.24*(sin(2*PI*660*t)+0.3*sin(6*PI*660*t))*between(t\,0.02\,0.32) + 0.24*(sin(2*PI*880*t)+0.3*sin(6*PI*880*t))*between(t\,0.45\,0.75) + 0.24*(sin(2*PI*1175*t)+0.3*sin(6*PI*1175*t))*between(t\,0.88\,1.25)':s=44100:d=1.5" \
  -filter_complex "[0:a]lowpass=f=6000,afade=t=out:st=1.3:d=0.2,alimiter=limit=0.75:level=0[out]"

# Critical, 2 s: a fast pulsed klaxon, 8 Hz on a 1500 Hz to 900 Hz warble.
render alarm_critical \
  -f lavfi -i "aevalsrc='0.3*(sin(2*PI*(1200*t+150*sin(2*PI*4*t)/(2*PI*4)*2*PI))+0.35*sin(6*PI*1200*t))*gt(sin(2*PI*8*t)\,0)':s=44100:d=2" \
  -filter_complex "[0:a]lowpass=f=7000,afade=t=in:st=0:d=0.01,afade=t=out:st=1.8:d=0.2,alimiter=limit=0.8:level=0[out]"

# Boom, 7 s: the detonation in air. A white-hot crack, a 28 Hz pressure wave and a long rolling body.
render boom \
  -f lavfi -i "aevalsrc='1.0*exp(-1.6*t)*sin(2*PI*(28*t-1.5*t*t))':s=44100:d=7" \
  -f lavfi -i "anoisesrc=color=white:duration=7:sample_rate=44100:seed=61014" \
  -f lavfi -i "anoisesrc=color=brown:duration=7:sample_rate=44100:seed=61015" \
  -f lavfi -i "aevalsrc='exp(-30*t)':s=44100:d=7" \
  -f lavfi -i "aevalsrc='min(1\, t/0.05)*exp(-0.55*t)':s=44100:d=7" \
  -filter_complex "[1:a]lowpass=f=6000[w];[w][3:a]amultiply,volume=1.6[crack];[2:a]lowpass=f=300,volume=2.6[b];[b][4:a]amultiply[body];[0:a][crack][body]amix=inputs=3:normalize=0,afade=t=out:st=6.0:d=1.0,alimiter=limit=0.95:level=0[out]"

# Rumble, 8 s: the long low after-roll, brown noise below 90 Hz swelling and fading.
render rumble \
  -f lavfi -i "anoisesrc=color=brown:duration=8:sample_rate=44100:seed=61016" \
  -f lavfi -i "aevalsrc='min(1\, t/1.2)*exp(-0.35*t)*(0.8+0.2*sin(2*PI*0.7*t))':s=44100:d=8" \
  -filter_complex "[0:a]lowpass=f=90,lowpass=f=90,volume=4.5[r];[r][1:a]amultiply,afade=t=out:st=7:d=1,alimiter=limit=0.85:level=0[out]"

# Ringing, 8 s: the ears after the boom in air. A 4186 Hz tone beating against 4189 Hz, a faint 6.3 kHz overtone and a
# thin high hiss, at full strength at once and fading slowly. The client starts it as the boom lands.
render ringing \
  -f lavfi -i "aevalsrc='(0.16*sin(2*PI*4186*t)+0.12*sin(2*PI*4189*t)+0.03*sin(2*PI*6279*t))*min(1\, t/0.08)*exp(-0.42*t)':s=44100:d=8" \
  -f lavfi -i "anoisesrc=color=white:duration=8:sample_rate=44100:seed=61030" \
  -f lavfi -i "aevalsrc='min(1\, t/0.08)*exp(-0.5*t)':s=44100:d=8" \
  -filter_complex "[1:a]highpass=f=7000,volume=0.05[h];[h][2:a]amultiply[hiss];[0:a][hiss]amix=inputs=2:normalize=0,afade=t=out:st=7:d=1,alimiter=limit=0.5:level=0[out]"

# Vacuum thud, 1.5 s: the ground shock through the boots. A dull 34 Hz thump with no air above 200 Hz.
render vacuum_thud \
  -f lavfi -i "aevalsrc='1.0*exp(-4.5*t)*sin(2*PI*(34*t-4*t*t)) + 0.3*exp(-8*t)*sin(2*PI*68*t)':s=44100:d=1.5" \
  -f lavfi -i "anoisesrc=color=brown:duration=1.5:sample_rate=44100:seed=61017" \
  -f lavfi -i "aevalsrc='exp(-5*t)':s=44100:d=1.5" \
  -filter_complex "[1:a]lowpass=f=150,volume=1.5[n];[n][2:a]amultiply[body];[0:a][body]amix=inputs=2:normalize=0,lowpass=f=200,afade=t=in:st=0:d=0.01,afade=t=out:st=1.2:d=0.3,alimiter=limit=0.9:level=0[out]"

