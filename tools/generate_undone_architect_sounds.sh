#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/entity/undone_architect"
CREATURE="$ROOT/tools/audio_sources/undone_architect/dragon_growls_cc0.ogg"
ICE="$ROOT/tools/audio_sources/undone_architect/ice_crack_2205.ogg"
mkdir -p "$OUT"

encode_wav() {
  local wav="$1"
  local output="$2"
  oggenc -Q -q 5 -o "$output" "$wav"
  rm -f "$wav"
}

render_creature_segment() {
  local start="$1"
  local duration="$2"
  local output="$3"
  local low_rate="$4"
  local high_rate="$5"
  local impact_delay="$6"
  local post="$7"
  local wav="$OUT/.undone_architect_render.wav"
  ffmpeg -hide_banner -loglevel error -y \
    -ss "$start" -t "$duration" -i "$CREATURE" -i "$ICE" \
    -filter_complex \
    "[0:a]asplit=2[low][high];\
[low]asetrate=$low_rate,aresample=44100,highpass=f=60,lowpass=f=1750,vibrato=f=3.8:d=0.18[throat];\
[high]asetrate=$high_rate,aresample=44100,highpass=f=380,lowpass=f=3900,vibrato=f=11:d=0.34[nerve];\
[1:a]atrim=0:0.72,asetpts=PTS-STARTPTS,highpass=f=150,volume=0.78,adelay=$impact_delay|$impact_delay[crack];\
[throat][nerve][crack]amix=inputs=3:weights='1 0.48 0.46':normalize=0,$post,volume=0.55[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$wav"
  encode_wav "$wav" "$output"
}

render_accretion() {
  local wav="$OUT/.undone_architect_render.wav"
  ffmpeg -hide_banner -loglevel error -y \
    -ss 40.7 -t 2.4 -i "$CREATURE" -i "$ICE" \
    -filter_complex \
    "[0:a]areverse,asetrate=35200,aresample=44100,highpass=f=75,lowpass=f=1800,vibrato=f=7:d=0.22[suction];\
[1:a]areverse,asetrate=38000,aresample=44100,highpass=f=130,volume=1.15[ice];\
[suction][ice]amix=inputs=2:weights='1 0.72':normalize=0,tremolo=f=8.4:d=0.26,\
acrusher=bits=10:mix=0.18,volume=1.45,alimiter=limit=0.91,volume=0.55[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$wav"
  encode_wav "$wav" "$OUT/accrete.ogg"
}

render_death_wail() {
  local wav="$OUT/.undone_architect_render.wav"
  ffmpeg -hide_banner -loglevel error -y \
    -ss 54.6 -t 4.2 -i "$CREATURE" -i "$ICE" \
    -filter_complex \
    "[0:a]asplit=3[body][cry][edge];\
[body]asetrate=25800,aresample=44100,atrim=0:5.35,highpass=f=55,lowpass=f=1250,vibrato=f=3.1:d=0.14,volume=0.55[bodyvoice];\
[cry]asetrate=39800,aresample=44100,atempo=0.88,atrim=0:5.15,highpass=f=105,lowpass=f=3600,vibrato=f=5.7:d=0.27,compand=attacks=0.015:decays=0.20:points=-70/-70|-28/-17|-10/-5|0/-2,volume=1.18[maincry];\
[edge]asetrate=63500,aresample=44100,atempo=0.56,atrim=0:4.85,highpass=f=620,lowpass=f=5900,vibrato=f=9.2:d=0.41,tremolo=f=13.0:d=0.15,adelay=140|140,volume=0.62[shriek];\
[1:a]atrim=0:0.92,asetpts=PTS-STARTPTS,asetrate=36000,aresample=44100,highpass=f=120,lowpass=f=5200,adelay=4430|4430,volume=1.35[rupture];\
[bodyvoice][maincry][shriek][rupture]amix=inputs=4:weights='0.7 1 0.72 0.82':normalize=0,\
highpass=f=52,acrusher=bits=11:mix=0.08,afade=t=in:st=0:d=0.06,afade=t=out:st=4.72:d=0.55,\
volume=1.48,alimiter=limit=0.94,volume=0.55[out]" \
    -map "[out]" -ac 1 -ar 44100 -c:a pcm_s16le "$wav"
  encode_wav "$wav" "$OUT/death.ogg"
}

render_creature_segment 15.8 2.6 "$OUT/ambient_1.ogg" \
  33800 52200 780 \
  "tremolo=f=5.2:d=0.18,acrusher=bits=11:mix=0.10,afade=t=out:st=2.25:d=0.45,volume=1.35,alimiter=limit=0.90"
render_creature_segment 34.3 2.8 "$OUT/ambient_2.ogg" \
  31600 54800 1220 \
  "flanger=delay=2:depth=3:regen=12:width=32:speed=0.55,afade=t=out:st=2.45:d=0.5,volume=1.3,alimiter=limit=0.90"
render_creature_segment 52.0 2.9 "$OUT/ambient_3.ogg" \
  35200 50600 960 \
  "tremolo=f=7.6:d=0.16,afade=t=out:st=2.5:d=0.5,volume=1.3,alimiter=limit=0.90"

render_creature_segment 3.95 0.62 "$OUT/hurt_1.ogg" \
  32600 56600 80 \
  "acrusher=bits=10:mix=0.12,volume=1.6,alimiter=limit=0.94"
render_creature_segment 10.95 0.68 "$OUT/hurt_2.ogg" \
  30200 59200 110 \
  "acrusher=bits=10:mix=0.15,volume=1.55,alimiter=limit=0.94"

render_creature_segment 0.15 0.82 "$OUT/attack.ogg" \
  32200 57400 115 \
  "highpass=f=85,acrusher=bits=11:mix=0.10,volume=1.65,alimiter=limit=0.95"
render_creature_segment 8.2 0.95 "$OUT/attack_2.ogg" \
  29800 61200 145 \
  "highpass=f=90,tremolo=f=10:d=0.12,volume=1.6,alimiter=limit=0.95"
render_creature_segment 33.65 0.88 "$OUT/attack_3.ogg" \
  34400 64200 90 \
  "highpass=f=95,acrusher=bits=10:mix=0.12,volume=1.55,alimiter=limit=0.95"

render_accretion
render_death_wail
