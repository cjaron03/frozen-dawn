#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$OUT/ui/suit" "$OUT/ambient" "$OUT/player/hearthrot"

# Same local Samantha voice used by the other generated ORSA suit lines. The
# AppKit exporter validates that speech frames exist before FFmpeg touches them.
CLANG_MODULE_CACHE_PATH="$TMP/module-cache" clang -fobjc-arc \
  -framework AppKit "$ROOT/tools/generate_samantha_tts.m" \
  -o "$TMP/generate_samantha_tts"
"$TMP/generate_samantha_tts" "$TMP/contamination.aiff" \
  "Internal contamination detected."
ffmpeg -hide_banner -loglevel error -y -i "$TMP/contamination.aiff" \
  -af "highpass=f=170,lowpass=f=5200,acompressor=threshold=-22dB:ratio=2.2:attack=8:release=100,volume=1.12" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 \
  "$OUT/ui/suit/hearthrot_contamination.ogg"

BREATH="$OUT/ambient/eva_breathing.ogg"
ICE="$ROOT/tools/audio_sources/undone_architect/ice_crack_2205.ogg"
COUGH="$ROOT/tools/audio_sources/hearthrot/strong_double_cough_cc0.wav"
WHEEZE="$ROOT/tools/audio_sources/hearthrot/wheezing_cc0.wav"
BREATH_CATCH="$ROOT/tools/audio_sources/hearthrot/zombie_choking_cc0.wav"

ffmpeg -hide_banner -loglevel error -y \
  -i "$BREATH" -stream_loop -1 -i "$ICE" \
  -filter_complex "[0:a]atrim=0:8,highpass=f=900,lowpass=f=3400,volume=0.34[b];[1:a]atrim=0:8,highpass=f=1200,lowpass=f=5600,volume=0.12[i];[b][i]amix=inputs=2:duration=shortest,afade=t=in:st=0:d=0.3,afade=t=out:st=7.5:d=0.5" \
  -t 8 -ac 2 -c:a vorbis -strict experimental -q:a 4 "$OUT/ambient/hearthrot_rasp.ogg"

ffmpeg -hide_banner -loglevel error -y -i "$COUGH" \
  -af "highpass=f=85,lowpass=f=7000,acompressor=threshold=-24dB:ratio=2.8:attack=5:release=100,volume=2.10,alimiter=limit=0.95" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/player/hearthrot/cough_one.ogg"
ffmpeg -hide_banner -loglevel error -y -i "$COUGH" \
  -af "highpass=f=95,lowpass=f=6200,atempo=0.94,equalizer=f=1500:t=q:w=1.1:g=1.8,acompressor=threshold=-24dB:ratio=2.8:attack=5:release=100,volume=2.00,alimiter=limit=0.95" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/player/hearthrot/cough_two.ogg"
ffmpeg -hide_banner -loglevel error -y -i "$COUGH" \
  -af "highpass=f=80,lowpass=f=6800,atempo=1.07,equalizer=f=900:t=q:w=1.0:g=1.5,acompressor=threshold=-24dB:ratio=2.8:attack=5:release=100,volume=2.08,alimiter=limit=0.95" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/player/hearthrot/cough_three.ogg"

ffmpeg -hide_banner -loglevel error -y -ss 0.45 -t 8.8 -i "$WHEEZE" \
  -af "highpass=f=70,lowpass=f=6500,acompressor=threshold=-27dB:ratio=2.6:attack=12:release=180,volume=1.85,alimiter=limit=0.95,afade=t=in:st=0:d=0.12,afade=t=out:st=8.35:d=0.45" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/player/hearthrot/wheeze.ogg"

ffmpeg -hide_banner -loglevel error -y -ss 0.08 -t 4.40 -i "$BREATH_CATCH" \
  -af "highpass=f=90,lowpass=f=6200,acompressor=threshold=-32dB:ratio=2.8:attack=8:release=160,loudnorm=I=-18:TP=-2:LRA=7,afade=t=in:st=0:d=0.08,afade=t=out:st=3.95:d=0.45" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 \
  "$OUT/player/hearthrot/breath_catch.ogg"

ffmpeg -hide_banner -loglevel error -y -i "$ICE" \
  -af "atrim=0:1.15,highpass=f=420,lowpass=f=6200,acompressor=threshold=-20dB:ratio=2.5,volume=1.25,afade=t=out:st=0.85:d=0.3" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/player/hearthrot/crystallize.ogg"

# Short, dry cuts preserve the hit cadence while removing the human hurt vocal.
ffmpeg -hide_banner -loglevel error -y -i "$ICE" \
  -af "atrim=0.02:0.58,asetpts=PTS-STARTPTS,highpass=f=320,lowpass=f=7600,atempo=1.16,acompressor=threshold=-23dB:ratio=2.6:attack=3:release=70,loudnorm=I=-15:TP=-1.5:LRA=5,afade=t=out:st=0.38:d=0.10" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/player/hearthrot/hurt_crack_one.ogg"
ffmpeg -hide_banner -loglevel error -y -i "$ICE" \
  -af "atrim=0.30:1.00,asetpts=PTS-STARTPTS,highpass=f=280,lowpass=f=6800,atempo=0.96,equalizer=f=1800:t=q:w=1.1:g=1.8,acompressor=threshold=-23dB:ratio=2.6:attack=3:release=75,loudnorm=I=-15:TP=-1.5:LRA=5,afade=t=out:st=0.58:d=0.14" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/player/hearthrot/hurt_crack_two.ogg"
ffmpeg -hide_banner -loglevel error -y -i "$ICE" \
  -af "atrim=0.82:1.56,asetpts=PTS-STARTPTS,highpass=f=360,lowpass=f=7200,atempo=1.08,equalizer=f=1100:t=q:w=1.0:g=1.4,acompressor=threshold=-23dB:ratio=2.6:attack=3:release=70,loudnorm=I=-15:TP=-1.5:LRA=5,afade=t=out:st=0.52:d=0.14" \
  -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT/player/hearthrot/hurt_crack_three.ogg"
