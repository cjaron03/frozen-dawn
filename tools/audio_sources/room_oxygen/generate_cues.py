#!/usr/bin/env python3
"""Room warning cues: original strained chime and two-pulse critical beep."""
import hashlib, json, math, struct, subprocess, tempfile, wave
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
SOURCE = Path(__file__).resolve().parent
OUT = ROOT / 'src/main/resources/assets/frozendawn/sounds/block/oxygen_gauge'
PREVIEW = ROOT / 'build/thermal-evidence/room-o2/warning-audio'
SR = 44100

def generate_critical():
    """One selected panel-double burst; runtime owns the repeat interval."""
    OUT.mkdir(parents=True, exist_ok=True)
    samples = [0.0] * round(.55 * SR)
    for onset in (0.0, .29):
        start = round(onset * SR)
        for i in range(round(.17 * SR)):
            t = i / SR
            envelope = max(0.0, min(1.0, t/.008) * min(1.0, (.17-t)/.025))
            tone = math.sin(2*math.pi*880*t) + .14*math.sin(2*math.pi*1760*t)
            samples[start+i] += .30*envelope*tone
    peak = max(abs(value) for value in samples)
    with tempfile.TemporaryDirectory(prefix='fd-panel-beep-') as temp:
        raw = Path(temp)/'critical.wav'
        with wave.open(str(raw), 'wb') as w:
            w.setparams((1,2,SR,0,'NONE','not compressed'))
            w.writeframes(b''.join(struct.pack('<h', round(value*.32/peak*32767)) for value in samples))
        subprocess.run(['oggenc','--quiet','-q','5','-o',str(OUT/'critical.ogg'),str(raw)],check=True)

def update_manifest():
    # Retain historical source credit/hash, but no downloaded audio is needed by the generator.
    paths = [SOURCE/'klaxon-584001-hq.mp3', OUT/'strained.ogg', OUT/'critical.ogg']
    manifest = {str(p.relative_to(ROOT)):{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size} for p in paths if p.exists()}
    (SOURCE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    return manifest

def main():
    OUT.mkdir(parents=True, exist_ok=True); PREVIEW.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temp:
        wav_path=Path(temp)/'strained.wav'
        samples=[0.0]*round(.7*SR)
        for onset, frequency in [(0,660),(.25,520)]:
            for i in range(round(.24*SR)):
                t=i/SR; envelope=min(1,t/.01)*min(1,(.24-t)/.04)*math.exp(-4*t)
                samples[round(onset*SR)+i]+=.22*envelope*(math.sin(2*math.pi*frequency*t)+.12*math.sin(4*math.pi*frequency*t))
        with wave.open(str(wav_path),'wb') as w:
            w.setparams((1,2,SR,0,'NONE','not compressed'))
            w.writeframes(b''.join(struct.pack('<h',round(v*32767)) for v in samples))
        subprocess.run(['oggenc','--quiet','-q','5','-o',str(OUT/'strained.ogg'),str(wav_path)],check=True)
    generate_critical()
    manifest = update_manifest()
    (SOURCE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    for name in ('strained','critical'):
        subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(OUT/(name+'.ogg')),'-c:a','libmp3lame','-q:a','2',str(PREVIEW/(name+'.mp3'))],check=True)
    print(json.dumps(manifest,indent=2))
if __name__=='__main__': main()
