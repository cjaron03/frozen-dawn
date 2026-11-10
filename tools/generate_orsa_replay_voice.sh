#!/usr/bin/env bash
# The replay's ORSA voice (owner previews, 2026-11-09): a speaker left unmaintained for a long time. It reads the
# replay's own readout slow and low, warbling, crushed, narrow, under hum and crackle, catching on words and cutting
# out; after "Trauma: none recorded." it says one line off its script in the same worn voice, only slower, then
# carries on as if nothing happened.
#
# Each part sits in a fixed slot so the intro can type each readout line as the voice reaches it: the slots start at
# 0, 5.0, 7.8, 11.2 and 15.2 seconds, and the whole runs 20.2 seconds (OrsaAwakeningIntro's replay timing).
#
# tools/generate_local_voice_assets.sh calls this for the manifest row with the "orsa_replay" profile, with the same
# Piper settings in PIPER_PYTHON, PIPER_VOICE and PIPER_DATA_DIR. The parts below must read, joined with spaces,
# exactly as that row's text.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PYTHON="${PIPER_PYTHON:-python3}"
VOICE="${PIPER_VOICE:-en_US-amy-medium}"
DATA_DIR="${PIPER_DATA_DIR:-$ROOT/.local/piper-voices}"
OUT="${ORSA_REPLAY_OUT:-$ROOT/src/main/resources/assets/frozendawn/sounds/ui/orsa_awakening_voice_replay.ogg}"
PARTS=("Vitals: pulse elevated. Respiration irregular."
       "Trauma: none recorded."
       "Enrollee has done this before."
       "Enrollees remaining: one. As projected."
       "Current time: 88:14 local. Clock compromised.")
if [[ -n "${ORSA_REPLAY_TEXT:-}" && "$ORSA_REPLAY_TEXT" != "${PARTS[*]}" ]]; then
  printf '%s\n' 'The manifest row and the parts in tools/generate_orsa_replay_voice.sh differ.' >&2
  exit 1
fi
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
for i in "${!PARTS[@]}"; do
  printf '%s\n' "${PARTS[$i]}" | "$PYTHON" -m piper -m "$VOICE" --data-dir "$DATA_DIR" -f "$TMP/$i.wav"
done
# The ORSA voice profile, then the wear: slow and low, wow and flutter, a narrow old speaker, crushed, level drifting.
ORSA='highpass=f=170,lowpass=f=5200,acompressor=threshold=-22dB:ratio=2.2:attack=8:release=100,volume=1.12'
WORN="asetrate=41454,aresample=44100,${ORSA},vibrato=f=0.7:d=0.08,vibrato=f=6.5:d=0.05,highpass=f=340,lowpass=f=3300,\
acrusher=bits=9:samples=3:mix=0.5:mode=lin,tremolo=f=0.35:d=0.35"
slot() { printf 'apad=whole_dur=%s,atrim=0:%s,asetpts=PTS-STARTPTS' "$1" "$1"; }
cat > "$TMP/graph" <<GRAPH
[0:a]aresample=44100,asplit=4[v0a][v0b][v0c][v0d];
[v0a]atrim=0.2:3.55,asetpts=PTS-STARTPTS[v0p];
[v0b]atrim=3.55:3.73,asetpts=PTS-STARTPTS[v0r];
[v0c]atrim=3.55:3.73,asetpts=PTS-STARTPTS,volume=0.7[v0s];
[v0d]atrim=3.55,asetpts=PTS-STARTPTS[v0t];
[v0p][v0r][v0s][v0t]concat=n=4:v=0:a=1,${WORN},$(slot 5.0)[a0];
[1:a]aresample=44100,atrim=0.18,asetpts=PTS-STARTPTS,${WORN},asplit=2[v1a][v1b];
[v1b]atrim=1.62:2.2,asetpts=PTS-STARTPTS,volume=0.45,afade=t=out:st=0.25:d=0.33[v1e];
[v1a][v1e]concat=n=2:v=0:a=1,$(slot 2.8)[a1];
[2:a]aresample=44100,atrim=0.2,asetpts=PTS-STARTPTS,${WORN},atempo=0.8,$(slot 3.4)[a2];
[3:a]aresample=44100,asplit=3[v3a][v3b][v3c];
[v3a]atrim=0.22:1.95,asetpts=PTS-STARTPTS[v3p];
[v3b]atrim=1.80:1.95,asetpts=PTS-STARTPTS,volume=0.75[v3r];
[v3c]atrim=1.95,asetpts=PTS-STARTPTS[v3t];
[v3p][v3r][v3t]concat=n=3:v=0:a=1,${WORN},$(slot 4.0)[a3];
[4:a]aresample=44100,atrim=0.2,asetpts=PTS-STARTPTS,${WORN},$(slot 5.0)[a4];
[a0][a1][a2][a3][a4]concat=n=5:v=0:a=1,volume='if(between(t,1.85,2.0)+between(t,6.2,6.32)+between(t,13.4,13.62)+between(t,18.3,18.42),0.04,1)':eval=frame[voice];
anoisesrc=d=20.2:c=pink:r=44100:a=0.02:s=4117,highpass=f=300,lowpass=f=3500,tremolo=f=0.2:d=0.6[static];
aevalsrc='0.5*gt(random(0)\,0.9994)*(random(1)-0.5)':d=20.2:s=44100,lowpass=f=4000[crackle];
aevalsrc='0.008*sin(2*PI*50*t)+0.004*sin(2*PI*100*t)':d=20.2:s=44100[hum];
[voice][static][crackle][hum]amix=inputs=4:normalize=0:duration=first,afade=t=in:d=0.05,afade=t=out:st=19.9:d=0.3,\
loudnorm=I=-18:TP=-1.5,aresample=44100[out]
GRAPH
ffmpeg -hide_banner -loglevel error -y -i "$TMP/0.wav" -i "$TMP/1.wav" -i "$TMP/2.wav" -i "$TMP/3.wav" -i "$TMP/4.wav" \
  -filter_complex "$(tr -d '\n' < "$TMP/graph")" -map "[out]" -ac 2 -ar 44100 -c:a vorbis -strict -2 -q:a 5 "$OUT"
echo "$OUT"
