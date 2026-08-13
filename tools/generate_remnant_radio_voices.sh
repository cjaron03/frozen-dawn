#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/entity/remnant"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

render_line() {
  local name="$1"
  local text="$2"
  # The ordinary ORSA field radio uses Samantha. Preserve that exact speaker,
  # then make the transmission source wrong through damaged radio processing.
  say -v Samantha -r 175 -o "$TMP/${name}_orsa.aiff" "$text"
  ffmpeg -hide_banner -loglevel error -y \
    -i "$TMP/${name}_orsa.aiff" \
    -filter_complex \
    "[0:a]aresample=22050,highpass=f=180,lowpass=f=3600,acompressor=threshold=0.07:ratio=4:attack=4:release=80,\
     acrusher=bits=10:mix=0.16,asplit=3[clean][ghost][drop];\
     [clean]volume=1.0[a];\
     [ghost]asetrate=21400,aresample=22050,adelay=38,volume=0.18[b];\
     [drop]tremolo=f=13:d=0.55,adelay=73,volume=0.11[c];\
     [a][b][c]amix=inputs=3:duration=longest:normalize=0,aecho=0.8:0.12:61|127:0.11|0.045,\
     loudnorm=I=-15:TP=-2:LRA=7,alimiter=limit=0.96,\
     afade=t=in:d=0.025,afade=t=out:st=1.9:d=0.35[out]" \
    -map "[out]" -ac 2 -ar 22050 -c:a vorbis -strict experimental -q:a 6 \
    "$OUT/radio_${name}.ogg"
}

render_line room "We made room for you."
render_line warm "We kept you warm."
render_line alone "You made us alone."
render_line forgive "Come inside. We forgive you."

printf 'Generated Remnant radio voices in %s\n' "$OUT"
