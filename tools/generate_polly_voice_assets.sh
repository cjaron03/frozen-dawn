#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="${POLLY_MANIFEST:-$ROOT/tools/audio_sources/generated_voice/polly_manifest.tsv}"
OUT_ROOT="$ROOT/src/main/resources/assets/frozendawn/sounds"
VOICE="${POLLY_VOICE:-Joanna}"
ENGINE="${POLLY_ENGINE:-neural}"
LANGUAGE="${POLLY_LANGUAGE_CODE:-en-US}"
DRY_RUN=0

usage() {
  printf '%s\n' "Usage: $0 [--dry-run] [--voice VOICE] [--engine ENGINE]"
  printf '%s\n' "Defaults: POLLY_VOICE=$VOICE POLLY_ENGINE=$ENGINE POLLY_LANGUAGE_CODE=$LANGUAGE"
}

while (($#)); do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --voice) VOICE="$2"; shift 2 ;;
    --engine) ENGINE="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -f "$MANIFEST" ]] || { printf 'Manifest not found: %s\n' "$MANIFEST" >&2; exit 1; }

if ((DRY_RUN == 0)); then
  command -v aws >/dev/null || { printf '%s\n' 'aws CLI is required for generation.' >&2; exit 1; }
  command -v ffmpeg >/dev/null || { printf '%s\n' 'ffmpeg is required for generation.' >&2; exit 1; }
  aws polly describe-voices \
    --language-code "$LANGUAGE" \
    --voice-id "$VOICE" \
    --output json >/dev/null
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

render() {
  local profile="$1"
  local input="$2"
  local output="$3"
  local filter

  case "$profile" in
    orsa)
      filter='highpass=f=170,lowpass=f=5200,acompressor=threshold=-22dB:ratio=2.2:attack=8:release=100,volume=1.12'
      ;;
    radio)
      filter='aresample=22050,highpass=f=180,lowpass=f=3600,acompressor=threshold=0.07:ratio=4:attack=4:release=80,acrusher=bits=10:mix=0.16,aecho=0.8:0.12:61|127:0.11|0.045,loudnorm=I=-15:TP=-2:LRA=7,alimiter=limit=0.96'
      ;;
    successor)
      filter='aresample=22050,asetrate=18400,aresample=22050,highpass=f=90,lowpass=f=2500,acompressor=threshold=-24dB:ratio=3.2:attack=5:release=120,aecho=0.7:0.10:54|111:0.12,loudnorm=I=-17:TP=-2:LRA=8,alimiter=limit=0.96'
      ;;
    *) printf 'Unknown processing profile: %s\n' "$profile" >&2; exit 1 ;;
  esac

  mkdir -p "$(dirname "$output")"
  ffmpeg -hide_banner -loglevel error -y -i "$input" \
    -af "$filter" -ac 2 -ar 44100 -c:a libvorbis -q:a 5 "$output"
}

count=0
while IFS=$'\t' read -r relative profile text || [[ -n "${relative:-}" ]]; do
  [[ -z "${relative:-}" || "${relative:0:1}" == "#" ]] && continue
  ((count += 1))
  output="$OUT_ROOT/$relative"
  printf '[%02d] %s (%s, %s)\n' "$count" "$relative" "$profile" "$VOICE"
  if ((DRY_RUN == 1)); then
    continue
  fi

  input="$TMP/${count}.mp3"
  aws polly synthesize-speech \
    --output-format mp3 \
    --voice-id "$VOICE" \
    --engine "$ENGINE" \
    --language-code "$LANGUAGE" \
    --text-type text \
    --text "$text" \
    "$input" >/dev/null
  render "$profile" "$input" "$output"
done < "$MANIFEST"

printf 'Prepared %d Polly voice assets.\n' "$count"
if ((DRY_RUN == 1)); then
  printf '%s\n' 'Dry run only; no AWS requests or files were written.'
else
  printf 'Voice=%s Engine=%s Language=%s\n' "$VOICE" "$ENGINE" "$LANGUAGE"
fi
