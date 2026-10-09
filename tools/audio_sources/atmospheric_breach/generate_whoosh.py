#!/usr/bin/env python3
"""Frozen Dawn original seeded airflow synthesis; Python standard library + FFmpeg."""
import math, random, struct, subprocess, tempfile, wave
from pathlib import Path
root=Path(__file__).resolve().parents[3]
out=root/'src/main/resources/assets/frozendawn/sounds/ui/suit/atmospheric_breach_whoosh.ogg'
rate=48000;rng=random.Random(72610);samples=[];low=0.0
for i in range(rate*2):
    t=i/rate;noise=rng.uniform(-1,1);low=0.88*low+0.12*noise
    envelope=min(1,t/0.045)*math.exp(-2.5*t)*min(1,(2-t)/0.16)
    samples.append(struct.pack('<h',round(max(-1,min(1,(noise*0.3+low*1.1)*envelope))*19000)))
out.parent.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory() as tmp:
    wav=Path(tmp)/'whoosh.wav'
    with wave.open(str(wav),'wb') as f:
        f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate);f.writeframes(b''.join(samples))
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-af','highpass=f=130,lowpass=f=5200','-ac','2','-c:a','vorbis','-strict','-2','-q:a','5',str(out)],check=True)
print(out)
