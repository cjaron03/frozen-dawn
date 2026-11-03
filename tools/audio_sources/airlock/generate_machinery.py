#!/usr/bin/env python3
"""Render owner-selected #4 worn-mechanism recordings. No synthesized layers.

Requires Python 3, ffmpeg and oggenc. Source originals and licenses are retained
beside this script; output is mono 24 kHz Vorbis. --only updates selected cues.
"""
from pathlib import Path
import argparse, array, hashlib, json, math, subprocess, wave
ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
SRC=HERE/'recordings'
OUT=ROOT/'src/main/resources/assets/frozendawn/sounds/block/airlock'
PREVIEW=ROOT/'build/power-network-evidence/airlock-recorded-sounds/rendered'
SR=44100
cache={}
def read(name,start=0,dur=None,low=50,high=8000):
    key=(name,start,dur,low,high)
    if key not in cache:
        cmd=['ffmpeg','-v','error','-i',str(SRC/name),'-ss',str(start)]
        if dur is not None:cmd+=['-t',str(dur)]
        cmd+=['-ac','1','-ar',str(SR),'-af',f'highpass=f={low},lowpass=f={high}','-f','f32le','-']
        raw=subprocess.check_output(cmd);a=array.array('f');a.frombytes(raw);out=list(a)
        # Trim only edge silence, then smooth the cuts. No pitch/speed shifts.
        threshold=max(abs(x) for x in out)*.006
        active=[i for i,x in enumerate(out) if abs(x)>threshold]
        if active:out=out[max(0,active[0]-220):min(len(out),active[-1]+440)]
        n=len(out)
        for i in range(n):out[i]*=min(1,i/180,(n-1-i)/440)
        cache[key]=out
    return cache[key]
def part(file,start=0,dur=None,at=0,gain=1,low=50,high=8000,loop=None):
    return dict(file=file,start=start,duration=dur,at=at,gain=gain,low=low,high=high,loop=loop)
def mix(parts,duration,target=.09,peak=.72):
    out=[0.0]*round(duration*SR)
    for p in parts:
        a=read(p['file'],p['start'],p['duration'],p['low'],p['high']);r=math.sqrt(sum(x*x for x in a)/len(a)) or 1
        # Normalize source layers so a distant recording does not disappear in a close-mic mix.
        gain=p['gain']*.1/r;offset=round(p['at']*SR)
        if p['loop']:
            total=round(p['loop']*SR);step=max(1,len(a)-round(.04*SR));starts=range(0,total,step)
        else:starts=[0];total=len(a)
        for s in starts:
            for i,x in enumerate(a):
                j=offset+s+i
                if s+i>=total or j>=len(out):break
                weight=1
                if p['loop']:weight=min(1,i/(.04*SR),(len(a)-1-i)/(.04*SR),(total-s-i-1)/(.035*SR))
                out[j]+=x*gain*weight
    rms=math.sqrt(sum(x*x for x in out)/len(out)) or 1;mx=max(abs(x) for x in out) or 1;scale=min(target/rms,peak/mx)
    # Fade the final cut too, especially the one-second pump segments.
    return [x*scale*min(1, i/(.004*SR), (len(out)-1-i)/(.012*SR))
            for i,x in enumerate(out)]
def save(p,a):
    pcm=array.array('h',(round(x*32767) for x in a));assert max(abs(x) for x in a)<.9
    with wave.open(str(p),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(SR);w.writeframes(pcm.tobytes())

P=part;F='foley100/'
# Selected concept 4: same recorded latch/rattle/stop and bell palette.
# The two pump mixes fit the existing once-per-20-ticks event cadence.
CUES={
 'door_open':(1.50,.09,[P('squeakyclick1.wav',gain=.7),P('mechanical2.wav',at=.25,gain=.8),P('rattle1.wav',at=.62,gain=.55)]),
 'door_close':(1.50,.09,[P('squeakyclick2.wav',gain=.5),P('rattle1.wav',at=.3,gain=.55),P('clank1.wav',at=.77,gain=.8)]),
 'pump_recover':(.94,.065,[P('air_long.mp3',.4,.94,gain=.85,high=6500),P('squeakyclick1.wav',gain=.2)]),
 'pump_fill':(.94,.065,[P(F+'machine_03.ogg',gain=.65,loop=.94,high=5000),P('rattle1.wav',at=.1,gain=.2)]),
 'ready':(.79,.055,[P(F+'bell_01.ogg',0,.65,gain=.6,high=5000)]),
 'evacuated':(.79,.055,[P(F+'bell_02.ogg',gain=.6,high=3800),P('air_short.mp3',.1,.32,at=.12,gain=.12,high=5000)]),
 'refuse':(.39,.065,[P('clank1.wav',0,.35,gain=.55,high=4000)]),
 'interrupted':(.50,.065,[P('rattle1.wav',0,.22,gain=.35),P('mechanical2.wav',0,.28,at=.18,gain=.55,high=4500)]),
 'valve':(.42,.065,[P('squeakyclick1.wav',0,.3,gain=.6),P('air_short.mp3',.12,.2,at=.12,gain=.14,high=5000)]),
}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only',nargs='+',choices=CUES)
    selected=parser.parse_args().only
    ledger={r['key']:r for r in json.loads((HERE/'source-ledger.json').read_text())}
    for name,rec in ledger.items():
        if sha(SRC/name)!=rec['sha256']:
            raise ValueError('Source recording hash mismatch: '+name)
    OUT.mkdir(parents=True,exist_ok=True);PREVIEW.mkdir(parents=True,exist_ok=True)
    manifest_path=HERE/'manifest.json'
    manifest=json.loads(manifest_path.read_text()) if selected else {}
    for name,(duration,target,parts) in CUES.items():
        if selected and name not in selected:continue
        data=mix(parts,duration,target)
        high=PREVIEW/(name+'-44100.wav');wav=PREVIEW/(name+'.wav');ogg=OUT/(name+'.ogg')
        save(high,data)
        subprocess.run(['ffmpeg','-v','error','-y','-i',str(high),'-ar','24000','-ac','1',str(wav)],check=True)
        subprocess.run(['oggenc','--quiet','-q','5','-c','LICENSE=CC0 1.0 Universal','-o',str(ogg),str(wav)],check=True)
        with wave.open(str(wav),'rb') as w:
            n=w.getnframes();pcm=array.array('h');pcm.frombytes(w.readframes(n))
        manifest[name]={'sha256':sha(ogg),'duration':n/24000,'peak':max(abs(x) for x in pcm)/32768,
                        'sample_rate':24000,'channels':1,'provenance':'CC0 recordings; selected concept 4 worn mechanism',
                        'source_recordings':sorted({p['file'] for p in parts}),'parts':parts}
    manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
    timelines={
      '01-doors':[(0,'door_open'),(2,'door_close'),(4,'refuse'),(4.8,'valve')],
      '02-refill-cycle':[(i,'pump_fill') for i in range(4)]+[(4,'ready')],
      '03-recovery-cycle':[(i,'pump_recover') for i in range(4)]+[(4,'evacuated')],
    }
    for title,events in timelines.items():
        if any(not (PREVIEW/(name+'.wav')).exists() for _,name in events):continue
        with wave.open(str(PREVIEW/(events[-1][1]+'.wav')),'rb') as w:tail=w.getnframes()/24000
        out=[0.] * round((events[-1][0]+tail+.25)*24000)
        for at,name in events:
            with wave.open(str(PREVIEW/(name+'.wav')),'rb') as w:
                pcm=array.array('h');pcm.frombytes(w.readframes(w.getnframes()))
            for i,x in enumerate(pcm):out[round(at*24000)+i]+=x/32768
        wav=PREVIEW/(title+'.wav')
        with wave.open(str(wav),'wb') as w:
            w.setparams((1,2,24000,0,'NONE','not compressed'))
            w.writeframes(array.array('h',(round(x*32767) for x in out)).tobytes())
        subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libmp3lame','-q:a','3',str(PREVIEW/(title+'.mp3'))],check=True)
    print(json.dumps({'updated':selected or list(CUES),'previews':str(PREVIEW)},indent=2))

if __name__=='__main__':main()
