#!/usr/bin/env python3
"""Room warning cues: original strained chime and CC0 klaxon derivative."""
import hashlib, json, math, struct, subprocess, tempfile, wave
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
SOURCE = Path(__file__).resolve().parent
OUT = ROOT / 'src/main/resources/assets/frozendawn/sounds/block/oxygen_gauge'
PREVIEW = ROOT / 'build/thermal-evidence/room-o2/warning-audio'
SR = 44100

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
    source=SOURCE/'klaxon-584001-hq.mp3'
    with tempfile.TemporaryDirectory() as temp:
        decoded=Path(temp)/'critical.wav'
        subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(source),'-ac','1','-ar',str(SR),'-af','loudnorm=I=-18:TP=-3:LRA=5,afade=t=in:st=0:d=0.03,afade=t=out:st=3.86:d=0.08','-c:a','pcm_s16le',str(decoded)],check=True)
        subprocess.run(['oggenc','--quiet','-q','5','-o',str(OUT/'critical.ogg'),str(decoded)],check=True)
    paths=[source,OUT/'strained.ogg',OUT/'critical.ogg']
    manifest={str(p.relative_to(ROOT)):{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size} for p in paths}
    (SOURCE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    for name in ('strained','critical'):
        subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(OUT/(name+'.ogg')),'-c:a','libmp3lame','-q:a','2',str(PREVIEW/(name+'.mp3'))],check=True)
    print(json.dumps(manifest,indent=2))
if __name__=='__main__': main()
