#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT/tools/audio_sources/remnant"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/entity/remnant"
mkdir -p "$OUT"

encode() {
  local name="$1"
  shift
  ffmpeg -hide_banner -loglevel error -y "$@" -ac 2 \
    -c:a vorbis -strict experimental -q:a 6 \
    "$OUT/$name.ogg"
}

# Sparse, recognizable breath. Processing is intentionally light so it remains
# recorded air rather than becoming another synthetic Heart-family drone.
encode ambient \
  -ss 5.2 -t 6.4 -i "$SOURCE/spirit_breathing_cc0.mp3" \
  -af "highpass=f=70,lowpass=f=5200,volume=1.2,afade=t=in:d=0.35,afade=t=out:st=5.2:d=1.2"

# The refuge's latch is mostly physical, with a nearly subliminal exhale after it.
encode latch \
  -ss 0.1 -t 1.4 -i "$SOURCE/snow_collapse_cc0.mp3" \
  -ss 2.0 -t 1.4 -i "$SOURCE/spirit_breathing_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=180,lowpass=f=4200,volume=1.35[a];[1:a]highpass=f=120,lowpass=f=1800,volume=0.24,adelay=180|180[b];[a][b]amix=inputs=2:duration=longest:normalize=0,alimiter=limit=0.94"

# Low structural pressure plus a ghost-breath moving through the wall cavity.
encode wall_pressure \
  -ss 13.0 -t 3.4 -i "$SOURCE/spirit_breathing_cc0.mp3" \
  -ss 19.0 -t 3.4 -i "$SOURCE/snow_collapse_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=60,lowpass=f=3300,volume=1.05[a];[1:a]lowpass=f=1200,volume=0.65[b];[a][b]amix=inputs=2:duration=longest:normalize=0,aecho=0.8:0.22:90:0.16,alimiter=limit=0.94"

# A room-sized exhalation followed by real frozen structure settling.
encode wall_shift \
  -ss 31.0 -t 4.8 -i "$SOURCE/ghost_wails_cc0.mp3" \
  -ss 33.0 -t 4.8 -i "$SOURCE/snow_collapse_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=80,lowpass=f=4300,volume=0.82[a];[1:a]highpass=f=100,lowpass=f=5000,volume=0.9[b];[a][b]amix=inputs=2:duration=longest:normalize=0,aecho=0.8:0.25:120:0.18,alimiter=limit=0.94"

# The grab is the shortest, closest vocal event in the set.
encode grab \
  -t 2.6 -i "$SOURCE/ghost_screech_cc0.mp3" \
  -ss 20.0 -t 2.6 -i "$SOURCE/spirit_breathing_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=160,lowpass=f=6500,volume=1.05[a];[1:a]highpass=f=80,lowpass=f=2600,volume=0.62[b];[a][b]amix=inputs=2:duration=longest:normalize=0,alimiter=limit=0.96"

# A quick spectral fracture, used only after the Remnant is already revealed.
encode hurt \
  -ss 0.25 -t 1.55 -i "$SOURCE/ghost_screech_cc0.mp3" \
  -ss 7.0 -t 1.55 -i "$SOURCE/snow_collapse_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=220,lowpass=f=7000,volume=0.92[a];[1:a]highpass=f=300,lowpass=f=5200,volume=0.72[b];[a][b]amix=inputs=2:duration=longest:normalize=0,alimiter=limit=0.95"

# The body dies before the shelter does: one abrupt cry tears into a short,
# violent spectral rupture and ends with the model rather than trailing it.
encode death \
  -ss 10.8 -t 2.7 -i "$SOURCE/ghost_wails_cc0.mp3" \
  -ss 0.05 -t 2.25 -i "$SOURCE/ghost_screech_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=110,lowpass=f=6500,volume=1.22,atempo=1.18[a];[1:a]highpass=f=280,lowpass=f=7900,volume=0.96,adelay=110|110[b];[a][b]amix=inputs=2:duration=longest:normalize=0,aecho=0.8:0.2:70|145:0.2|0.08,alimiter=limit=0.98,atrim=duration=2.55,afade=t=out:st=2.28:d=0.27"

# The shelter follows after the creature's voice is gone: only timber, packed
# snow, and load-bearing structure remain in this cue.
encode collapse \
  -t 8.05 -i "$SOURCE/building_collapse_cc0.mp3" \
  -ss 40.0 -t 8.05 -i "$SOURCE/snow_collapse_cc0.mp3" \
  -filter_complex "[0:a]highpass=f=45,lowpass=f=7000,volume=1.18[a];[1:a]highpass=f=130,lowpass=f=6000,volume=0.8[b];[a][b]amix=inputs=2:duration=longest:normalize=0,aecho=0.8:0.2:150:0.10,alimiter=limit=0.97"

printf 'Generated Remnant sound set in %s\n' "$OUT"
