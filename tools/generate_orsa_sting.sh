#!/usr/bin/env bash
# The ORSA boot sting: tools/GenerateOrsaSting.java sings it dry, FFmpeg adds the room and encodes it.
# The replay's copy is the sting broken: about 30% lower and slower, wobbling and crushed, catching on a stretch of
# "SA" and dropping out twice (owner preview, 2026-11-09).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/ui/orsa_boot_sting.ogg"
REPLAY="$ROOT/src/main/resources/assets/frozendawn/sounds/ui/orsa_boot_sting_replay.ogg"
DRY="$(mktemp -t orsa_sting).wav"
trap 'rm -f "$DRY"' EXIT
java "$ROOT/tools/GenerateOrsaSting.java" "$DRY"
ffmpeg -hide_banner -loglevel error -y -i "$DRY" \
  -af "aecho=0.8:0.55:47|89|151|233:0.28|0.2|0.14|0.09,highpass=f=60,loudnorm=I=-20:TP=-3:LRA=11" \
  -ar 44100 -ac 2 -c:a vorbis -strict experimental -q:a 5 "$OUT"
ffmpeg -hide_banner -loglevel error -y -i "$OUT" -filter_complex "\
[0:a]asetrate=44100*0.71,aresample=44100,vibrato=f=3.1:d=0.35,acrusher=bits=7:mix=0.45:mode=log,lowpass=f=3200,asplit=4[a][b][c][d];\
[a]atrim=0:1.55,asetpts=PTS-STARTPTS[p1];\
[b]atrim=1.43:1.55,asetpts=PTS-STARTPTS,volume=0.8[r1];\
[c]atrim=1.43:1.55,asetpts=PTS-STARTPTS,volume=0.6[r2];\
[d]atrim=1.55,asetpts=PTS-STARTPTS[p2];\
[p1][r1][r2][p2]concat=n=4:v=0:a=1,volume='if(between(t,0.62,0.78)+between(t,2.9,3.08),0.08,1)':eval=frame,\
aecho=0.8:0.6:90:0.25,loudnorm=I=-18:TP=-1.5[out]" \
  -map "[out]" -ar 44100 -ac 2 -c:a vorbis -strict experimental -q:a 5 "$REPLAY"
echo "$OUT"
echo "$REPLAY"
