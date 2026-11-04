#!/usr/bin/env bash
# ORSA TR-0 Terminus sounds: ffmpeg lavfi synthesis only, fixed noise seed, mono 44.1 kHz Vorbis (oggenc -q 5: ffmpeg's
# own Vorbis encoder only writes stereo, and Minecraft attenuates only mono sounds by distance). The conventions of
# tools/generate_sever_lash_sounds.sh. Sources are recorded in tools/audio_sources/terminus/SOURCES.md.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/item/terminus"
TMP="${TMPDIR:-/tmp}/frozendawn_terminus.wav"
mkdir -p "$OUT"
trap 'rm -f "$TMP"' EXIT

render() { # name, then ffmpeg inputs and filter args ending in [out]
  local name="$1"; shift
  ffmpeg -hide_banner -loglevel error -y "$@" -map "[out]" -ac 1 -ar 44100 -map_metadata -1 -fflags +bitexact -flags:a +bitexact -c:a pcm_s16le "$TMP"
  oggenc -Q -q 5 -s 55001 -o "$OUT/$name.ogg" "$TMP"
}

# Wind-up, 2 s (owner's pick, after Hyperion's silent deathwand): a slow heartbeat, a 48 Hz thump decaying fast, its
# rate quickening from 1.2 to 6 beats a second, under a thin 5.2 kHz whine barely there and rising a little. Heard only
# within 8 blocks. It stops dead: the discharge makes no sound; the world goes quiet instead (TerminusHush).
render windup \
  -f lavfi -i "aevalsrc='0.035*sin(2*PI*(5200*t+150*t*t))*pow(t/2\,1.5)+0.7*sin(2*PI*48*mod(1.2*t+1.2*t*t\,1)/(1.2+2.4*t))*exp(-22*mod(1.2*t+1.2*t*t\,1)/(1.2+2.4*t))*(0.4+0.6*t/2)':s=44100:d=2" \
  -filter_complex "[0:a]afade=t=out:st=1.99:d=0.01,alimiter=limit=0.8:level=0[out]"

# The vault's grind, 0.55 s, played every half second while a door or the bridge moves: low stone-on-steel, a 34 Hz rumble
# under band-limited noise with a slow shudder. Short on purpose, so the sound stops soon after the door does.
OUT="$ROOT/src/main/resources/assets/frozendawn/sounds/blocks/vault"
mkdir -p "$OUT"
render grind \
  -f lavfi -i "anoisesrc=d=0.55:c=brown:r=44100:a=0.9:s=55003" \
  -f lavfi -i "aevalsrc='0.5*sin(2*PI*34*t)*(0.75+0.25*sin(2*PI*11*t))':d=0.55:s=44100" \
  -filter_complex "[0:a]lowpass=f=520,highpass=f=70,tremolo=f=17:d=0.6[n];[n][1:a]amix=inputs=2:normalize=0,afade=t=in:st=0:d=0.04,afade=t=out:st=0.46:d=0.09,alimiter=limit=0.8:level=0[out]"

# The Watch Floor's bass pulse, 0.35 s, sent at a rate that quickens with the clock: one thump at 42 Hz falling away fast.
render pulse \
  -f lavfi -i "aevalsrc='0.9*sin(2*PI*(42*t-14*t*t))*exp(-11*t)':s=44100:d=0.35" \
  -filter_complex "[0:a]afade=t=out:st=0.3:d=0.05,alimiter=limit=0.85:level=0[out]"

# A fuse, 0.09 s: a dry relay click, pitched up by the game while a hold builds and again when it seats.
render fuse \
  -f lavfi -i "anoisesrc=d=0.09:c=white:r=44100:a=0.7:s=55004" \
  -filter_complex "[0:a]highpass=f=900,lowpass=f=5200,afade=t=out:st=0.01:d=0.08,alimiter=limit=0.8:level=0[out]"

# A wrong fuse, 0.7 s: two falling square-ish buzzes, flat and unfriendly.
render wrong \
  -f lavfi -i "aevalsrc='0.4*sgn(sin(2*PI*(if(lt(t\,0.3)\,220\,165))*t))*if(lt(mod(t\,0.35)\,0.28)\,1\,0)':s=44100:d=0.7" \
  -filter_complex "[0:a]lowpass=f=1800,afade=t=out:st=0.6:d=0.1,alimiter=limit=0.7:level=0[out]"

# The Custodian's shell, 1.2 s: seals letting go, a hiss of cold air over a falling 60 Hz clunk.
render shell \
  -f lavfi -i "anoisesrc=d=1.2:c=pink:r=44100:a=0.7:s=55005" \
  -f lavfi -i "aevalsrc='0.8*sin(2*PI*(60*t-12*t*t))*exp(-5*t)':d=1.2:s=44100" \
  -filter_complex "[0:a]highpass=f=1400,lowpass=f=7000,afade=t=in:st=0:d=0.03,afade=t=out:st=0.3:d=0.9[n];[n][1:a]amix=inputs=2:normalize=0,alimiter=limit=0.8:level=0[out]"

# The purge, 1.6 s: everything at once, a rising white rush cut dead.
render purge \
  -f lavfi -i "anoisesrc=d=1.6:c=white:r=44100:a=0.8:s=55006" \
  -f lavfi -i "aevalsrc='0.5*sin(2*PI*(80*t+260*t*t))':d=1.6:s=44100" \
  -filter_complex "[0:a]highpass=f=400,afade=t=in:st=0:d=1.4[n];[n][1:a]amix=inputs=2:normalize=0,afade=t=out:st=1.57:d=0.03,alimiter=limit=0.85:level=0[out]"

# The evacuation recording, 20 s. Worn tape: hiss with slow flutter, crackle, a two-tone chime, and dropouts in the voice.
# The voice is tools/generate_local_voice_assets.sh's Piper render of its manifest line, passed in VAULT_ANNOUNCEMENT_VOICE;
# run that script to rebuild this file, since without a voice this writes the bed alone.
BED=(-f lavfi -i "anoisesrc=d=20:c=pink:r=44100:a=0.16:s=55007"
     -f lavfi -i "anoisesrc=d=20:c=white:r=44100:a=0.5:s=55008"
     -f lavfi -i "aevalsrc='0.22*(sin(2*PI*880*t)*between(t\,0.2\,0.6)+sin(2*PI*660*t)*between(t\,0.65\,1.15))':d=20:s=44100")
MIX="[0:a]lowpass=f=3200,highpass=f=240,tremolo=f=0.7:d=0.5[h];[1:a]highpass=f=2400,volume=0.05,tremolo=f=9:d=0.95[c];[2:a]lowpass=f=2200[ch]"
if [[ -n "${VAULT_ANNOUNCEMENT_VOICE:-}" ]]; then
  render announcement "${BED[@]}" -i "$VAULT_ANNOUNCEMENT_VOICE" -filter_complex "$MIX;[3:a]aresample=44100,adelay=1500:all=1,highpass=f=300,lowpass=f=3000,acrusher=bits=9:mix=0.25,tremolo=f=11:d=0.35,volume=1.5,volume=0:enable='between(t\,5.1\,5.35)+between(t\,9.4\,9.55)+between(t\,12.7\,13.05)',apad=whole_dur=20[v];[h][c][ch][v]amix=inputs=4:normalize=0:duration=first,afade=t=out:st=19.5:d=0.5,alimiter=limit=0.8:level=0[out]"
else
  render announcement "${BED[@]}" -filter_complex "$MIX;[h][c][ch]amix=inputs=3:normalize=0:duration=first,afade=t=out:st=19.5:d=0.5,alimiter=limit=0.8:level=0[out]"
fi
