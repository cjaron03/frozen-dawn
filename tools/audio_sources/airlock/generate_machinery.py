#!/usr/bin/env python3
"""Original deterministic ORSA hardware synthesis. No sampled recordings or voices."""
import hashlib,json,math,random,struct,subprocess,wave
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'src/main/resources/assets/frozendawn/sounds/block/airlock'
PREVIEW=ROOT/'build/thermal-evidence/airlock-service/audio-previews'
SR=24000

def noise(length,seed,cutoff):
    rng=random.Random(seed);v=0;alpha=1-math.exp(-2*math.pi*cutoff/SR);out=[]
    for _ in range(int(length*SR)):
        v+=alpha*(rng.uniform(-1,1)-v);out.append(v)
    return out

def mix(out,start,data,gain=1):
    offset=round(start*SR)
    for i,v in enumerate(data):
        if 0<=offset+i<len(out):out[offset+i]+=v*gain

def clack(length=.3,seed=7,heavy=1):
    n=noise(length,seed,4800)
    return [math.exp(-t/(.075*heavy))*(.33*n[i]+sum(a*math.sin(2*math.pi*f*t)*math.exp(-t/d) for f,a,d in [(132,.36,.065*heavy),(286,.18,.1),(721,.12,.035)])) for i in range(len(n)) for t in [i/SR]]

def hiss(length,seed,gain=.4):
    n=noise(length,seed,2700)
    return [gain*n[i]*min(1,t/.035)*min(1,(length-t)/.1) for i in range(len(n)) for t in [i/SR]]

def motor(length,recover=False):
    n=noise(length,49 if recover else 41,1000)
    return [(min(1,t/.025)*min(1,(length-t)/.055))*(.10*math.sin(2*math.pi*83*t)+.06*math.sin(2*math.pi*166*t)+.04*math.sin(2*math.pi*249*t)+.09*n[i])*(.8+.2*math.cos(2*math.pi*11*t)) for i in range(len(n)) for t in [i/SR]]

def sound(name,length):
    a=[0.]*round(length*SR)
    if name=='door_open':
        mix(a,0,clack(.22,1,.7));mix(a,.10,hiss(.35,2,.42));mix(a,.19,motor(.53));mix(a,.63,clack(.23,3,.45),.35)
    elif name=='door_close':
        mix(a,0,motor(.36),.6);mix(a,.29,clack(.4,4,1.5),1.25);mix(a,.44,clack(.22,5,.65),.52);mix(a,.52,hiss(.19,6,.20))
    elif name.startswith('pump_'):
        mix(a,0,motor(.94,name=='pump_recover'));mix(a,0,hiss(.94,8 if name=='pump_recover' else 9,.38 if name=='pump_fill' else .22))
    elif name in ('ready','evacuated'):
        mix(a,0,motor(.25),.5);mix(a,.13,clack(.2,10,.55),.3)
        if name=='evacuated':mix(a,.17,hiss(.42,11,.75))
        for onset,freq in [(.38,660),(.53,880)]:
            mix(a,onset,[.055*math.sin(2*math.pi*freq*i/SR)*math.sin(math.pi*i/(.13*SR))**2 for i in range(round(.13*SR))])
    elif name=='refuse':mix(a,0,clack(.20,12,.6),.7);mix(a,.075,hiss(.22,13,.3))
    elif name=='interrupted':mix(a,0,motor(.18),.6);mix(a,.14,clack(.28,14,.8),.55)
    elif name=='valve':mix(a,0,clack(.25,15,.8),.8);mix(a,.12,hiss(.16,16,.28))
    return a

def wav(path,data):
    assert max(map(abs,data),default=0)<.99,'Clipping'
    with wave.open(str(path),'wb') as w:
        w.setparams((1,2,SR,0,'NONE','not compressed'));w.writeframes(b''.join(struct.pack('<h',round(v*32767)) for v in data))

def encode(src,dst):
    if dst.suffix=='.ogg':
        subprocess.run(['oggenc','--quiet','-q','5','-o',str(dst),str(src)],check=True)
    else:
        subprocess.run(['ffmpeg','-v','error','-y','-i',str(src),'-c:a','libmp3lame','-q:a','5',str(dst)],check=True)

def main():
    OUT.mkdir(parents=True,exist_ok=True);PREVIEW.mkdir(parents=True,exist_ok=True)
    lengths={'door_open':.93,'door_close':.92,'pump_fill':.94,'pump_recover':.94,'ready':.79,'evacuated':.79,'refuse':.39,'interrupted':.5,'valve':.42}
    sounds={name:sound(name,length) for name,length in lengths.items()};manifest={}
    for name,data in sounds.items():
        src=PREVIEW/(name+'.wav');dst=OUT/(name+'.ogg');wav(src,data);encode(src,dst)
        manifest[name]={'sha256':hashlib.sha256(dst.read_bytes()).hexdigest(),'duration':len(data)/SR,'peak':max(map(abs,data)),'sample_rate':SR,'channels':1}
    for title,parts in {'01-heavy-hatch':[(0,'door_open'),(1.8,'door_close')], '02-refill-cycle':[(i,'pump_fill') for i in range(4)]+[(4,'ready')], '03-recovery-cycle':[(i,'pump_recover') for i in range(4)]+[(4,'evacuated'),(5.4,'refuse')]}.items():
        a=[0.]*round((parts[-1][0]+len(sounds[parts[-1][1]])/SR+.25)*SR)
        for t,name in parts:mix(a,t,sounds[name])
        src=PREVIEW/(title+'.wav');wav(src,a);encode(src,PREVIEW/(title+'.mp3'))
    (Path(__file__).parent/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({'previews':str(PREVIEW),'assets':manifest},indent=2))
if __name__=='__main__':main()
