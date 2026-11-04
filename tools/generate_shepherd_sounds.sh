#!/usr/bin/env bash
# Aurora's sounds: ffmpeg lavfi synthesis only, mono 44.1 kHz Vorbis (oggenc -q 5), as tools/generate_sever_lash_sounds.sh.
# Each sound has auditions a, b and c. The one the game plays is set by PICK below; every audition is also written to
# build/shepherd-sound-candidates for the owner to hear. Sources are recorded in tools/audio_sources/shepherd/SOURCES.md.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOUNDS="$ROOT/src/main/resources/assets/frozendawn/sounds"
CANDIDATES="$ROOT/build/shepherd-sound-candidates"
TMP="${TMPDIR:-/tmp}/frozendawn_shepherd.wav"
mkdir -p "$SOUNDS/entity/shepherd" "$SOUNDS/block/cs1_dock" "$CANDIDATES"
trap 'rm -f "$TMP"' EXIT

PICK_BLIP=a
PICK_BOOT=a
PICK_REFUSE=a

render() { # audition file, then an aevalsrc expression and its length in seconds
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i "aevalsrc='$2':s=44100:d=$3" -ac 1 -ar 44100 -map_metadata -1 \
    -fflags +bitexact -flags:a +bitexact -c:a pcm_s16le "$TMP"
  oggenc -Q -q 5 -s 53000 -o "$CANDIDATES/$1.ogg" "$TMP"
}

# Blip: her voice, one every three typed characters, so it has to sit under the game and never tire.
render blip_a '0.45*min(1\, t/0.002)*exp(-70*t)*(sin(2*PI*1180*t)+0.25*sin(2*PI*2360*t))' 0.06
render blip_b '0.3*min(1\, t/0.002)*exp(-60*t)*(sin(2*PI*620*t)+0.33*sin(2*PI*1860*t)+0.2*sin(2*PI*3100*t))' 0.06
render blip_c '0.45*min(1\, t/0.002)*exp(-85*t)*sin(2*PI*(1500*t-2500*t*t))' 0.05

# Boot: the cell seats and the light goes green.
render boot_a '0.4*min(1\, t/0.01)*(exp(-5*t)*sin(2*PI*523*t)+if(gte(t\,0.22)\, exp(-4*(t-0.22))*sin(2*PI*784*t)\, 0))' 1.1
render boot_b '0.08*exp(-200*t)*sin(2*PI*90*t)+0.35*(if(gte(t\,0.08)\, exp(-7*(t-0.08))*sin(2*PI*392*t)\, 0)+if(gte(t\,0.24)\, exp(-7*(t-0.24))*sin(2*PI*587*t)\, 0)+if(gte(t\,0.4)\, exp(-4*(t-0.4))*sin(2*PI*880*t)\, 0))' 1.2
render boot_c '0.35*min(1\, t/0.3)*exp(-2.4*t)*(sin(2*PI*(110*t+150*t*t))+0.5*sin(2*PI*(220*t+300*t*t)))' 1.3

# Refuse: a dry click, the slot not taking an empty cell.
render refuse_a '0.7*exp(-400*t)*sin(2*PI*2400*t)+0.5*exp(-55*t)*sin(2*PI*130*t)' 0.14
render refuse_b '0.6*(exp(-400*t)*sin(2*PI*1800*t)+if(gte(t\,0.09)\, exp(-400*(t-0.09))*sin(2*PI*1500*t)\, 0))+0.4*exp(-40*t)*sin(2*PI*100*t)' 0.22
render refuse_c '0.5*exp(-30*t)*sin(2*PI*160*t)+0.2*exp(-300*t)*sin(2*PI*900*t)' 0.18

cp "$CANDIDATES/blip_$PICK_BLIP.ogg" "$SOUNDS/entity/shepherd/blip.ogg"
cp "$CANDIDATES/boot_$PICK_BOOT.ogg" "$SOUNDS/block/cs1_dock/boot.ogg"
cp "$CANDIDATES/refuse_$PICK_REFUSE.ogg" "$SOUNDS/block/cs1_dock/refuse.ogg"
