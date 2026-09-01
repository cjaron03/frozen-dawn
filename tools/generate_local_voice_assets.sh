#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="${LOCAL_TTS_MANIFEST:-$ROOT/tools/audio_sources/generated_voice/polly_manifest.tsv}"
OUT_ROOT="${LOCAL_TTS_OUTPUT_ROOT:-$ROOT/src/main/resources/assets/frozendawn/sounds}"
PYTHON="${PIPER_PYTHON:-python3}"
VOICE="${PIPER_VOICE:-en_US-amy-medium}"
DATA_DIR="${PIPER_DATA_DIR:-$ROOT/.local/piper-voices}"
DRY_RUN=0

usage() {
  printf '%s\n' "Usage: $0 [--dry-run] [--voice VOICE] [--data-dir DIR]"
  printf '%s\n' "Defaults: PIPER_VOICE=$VOICE PIPER_DATA_DIR=$DATA_DIR"
  printf '%s\n' "The Piper model is downloaded outside Git and is never packed into the jar."
}

while (($#)); do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --voice) VOICE="$2"; shift 2 ;;
    --data-dir) DATA_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -f "$MANIFEST" ]] || { printf 'Manifest not found: %s\n' "$MANIFEST" >&2; exit 1; }

if ((DRY_RUN == 0)); then
  command -v "$PYTHON" >/dev/null || { printf 'Python executable not found: %s\n' "$PYTHON" >&2; exit 1; }
  command -v ffmpeg >/dev/null || { printf '%s\n' 'ffmpeg is required for generation.' >&2; exit 1; }
  "$PYTHON" -c 'import piper' >/dev/null 2>&1 || {
    printf '%s\n' 'Piper is not installed. Run: python3 -m pip install piper-tts' >&2
    exit 1
  }
  [[ -f "$DATA_DIR/$VOICE.onnx" && -f "$DATA_DIR/$VOICE.onnx.json" ]] || {
    printf 'Piper voice is missing from %s: %s (+ .onnx.json)\n' "$DATA_DIR" "$VOICE" >&2
    printf 'Download it with: %s -m piper.download_voices %s --data-dir %s\n' "$PYTHON" "$VOICE" "$DATA_DIR" >&2
    exit 1
  }
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

render() {
  local profile="$1"
  local input="$2"
  local output="$3"
  local filter

  case "$profile" in
    orsa) filter='highpass=f=170,lowpass=f=5200,acompressor=threshold=-22dB:ratio=2.2:attack=8:release=100,volume=1.12' ;;
    radio) filter='aresample=22050,highpass=f=180,lowpass=f=3600,acompressor=threshold=0.07:ratio=4:attack=4:release=80,acrusher=bits=10:mix=0.16,aecho=0.8:0.12:61|127:0.11|0.045,loudnorm=I=-15:TP=-2:LRA=7,alimiter=limit=0.96' ;;
    successor) filter='aresample=22050,asetrate=18400,aresample=22050,highpass=f=90,lowpass=f=2500,acompressor=threshold=-24dB:ratio=3.2:attack=5:release=120,aecho=0.7:0.10:54|111:0.12|0.08,loudnorm=I=-17:TP=-2:LRA=8,alimiter=limit=0.96' ;;
    *) printf 'Unknown processing profile: %s\n' "$profile" >&2; exit 1 ;;
  esac

  mkdir -p "$(dirname "$output")"
  ffmpeg -hide_banner -loglevel error -y -i "$input" \
    -af "$filter" -ac 2 -ar 44100 -c:a vorbis -strict -2 -q:a 5 "$output"
}

count=0
while IFS=$'\t' read -r relative profile text || [[ -n "${relative:-}" ]]; do
  [[ -z "${relative:-}" || "${relative:0:1}" == "#" ]] && continue
  ((count += 1))
  output="$OUT_ROOT/$relative"
  printf '[%02d] %s (%s, %s)\n' "$count" "$relative" "$profile" "$VOICE"
  if ((DRY_RUN == 1)); then continue; fi

  input="$TMP/${count}.wav"
  printf '%s\n' "$text" | "$PYTHON" -m piper -m "$VOICE" --data-dir "$DATA_DIR" -f "$input"
  render "$profile" "$input" "$output"
done < "$MANIFEST"

printf 'Prepared %d local Piper voice assets.\n' "$count"
if ((DRY_RUN == 1)); then
  printf '%s\n' 'Dry run only; no model execution or files were written.'
else
  printf 'Voice=%s DataDir=%s\n' "$VOICE" "$DATA_DIR"
fi
