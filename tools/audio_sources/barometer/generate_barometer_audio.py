#!/usr/bin/env python3
"""Render the owner-selected original ORSA Panel buzzer for barometer cyclone warnings.
Requires Python, ffmpeg and oggenc. No sampled recordings.
"""
from pathlib import Path
import array,hashlib,json,math,subprocess,wave
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
OUT=ROOT/'src/main/resources/assets/frozendawn/sounds/block/barometer'
BUILD=ROOT/'build/cyclone-evidence/barometer-alarm/generated'
SR=44100
TAU=2*math.pi

def burst(at,duration,f0,f1=None,rough=0,drive=1,style='buzzer'):
 return dict(at=at,duration=duration,startHz=f0,endHz=f0 if f1 is None else f1,rough=rough,drive=drive,style=style)
B=burst
CUES={
 'cyclone_warning':(1.6,.20,[B(.10,.42,290,rough=22,drive=1.8),B(.80,.42,430,rough=22,drive=1.8)]),
 'cyclone_catastrophic':(3.35,.24,[B(.10+i*.43,.31,290 if i%2==0 else 430,rough=28,drive=2.2) for i in range(7)])}

def synth(events,duration,target=.20):
 data=array.array('f',[0])*round(duration*SR)
 for e in events:
  phase=0;low=0;count=round(e['duration']*SR);offset=round(e['at']*SR)
  for i in range(count):
   t=i/SR;u=i/max(1,count-1)
   if e['style']=='siren':
    shape=.5-.5*math.cos(math.pi*u)
   else:shape=u
   freq=e['startHz']+(e['endHz']-e['startHz'])*shape
   freq+=1.5*math.sin(TAU*4.8*t)
   phase+=TAU*freq/SR
   if e['style']=='redline':
    raw=math.sin(phase)+.30*math.sin(3*phase)+.14*math.sin(5*phase)
   elif e['style']=='siren':
    raw=math.sin(phase)+.22*math.sin(2*phase)+.12*math.sin(3*phase)
   else:
    raw=math.sin(phase)+.30*math.sin(2*phase)+.16*math.sin(3*phase)
   # Original amplitude roughness, no data-like chirps, attention-tone pair, or header.
   raw*=.78+.22*math.sin(TAU*e['rough']*t)
   raw=math.tanh(e['drive']*raw)/math.tanh(e['drive'])
   low+=.32*(raw-low)
   envelope=min(1,t/.012,(e['duration']-t)/.035)
   data[offset+i]+=low*max(0,envelope)
   # Tiny enclosure reflections give it an instrument-speaker body.
   for delay,gain in [(0.026,.12),(.052,.045)]:
    at=offset+i+round(delay*SR)
    if at<len(data):data[at]+=low*max(0,envelope)*gain
 peak=max(abs(x) for x in data);active=sum(abs(x)>.005 for x in data)
 rms=math.sqrt(sum(x*x for x in data)/max(active,1))
 scale=min(target/max(rms,1e-9),.65/max(peak,1e-9))
 return array.array('f',(x*scale for x in data))

def main():
 OUT.mkdir(parents=True,exist_ok=True);BUILD.mkdir(parents=True,exist_ok=True)
 manifest={}
 for index,(name,(duration,target,events)) in enumerate(CUES.items()):
  data=synth(events,duration,target);wav=BUILD/(name+'-44100.wav');resampled=BUILD/(name+'.wav');ogg=OUT/(name+'.ogg')
  with wave.open(str(wav),'wb') as w:
   w.setparams((1,2,SR,0,'NONE','not compressed'));w.writeframes(array.array('h',(round(x*32767) for x in data)).tobytes())
  subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-map_metadata','-1','-ac','1','-ar','24000',str(resampled)],check=True)
  subprocess.run(['oggenc','-Q','-q','5','--serial',str(2400+index),'-o',str(ogg),str(resampled)],check=True)
  decoded=array.array('f',subprocess.check_output(['ffmpeg','-v','error','-i',str(ogg),'-ac','1','-ar','24000','-f','f32le','-']))
  peak=max(abs(x) for x in decoded);assert peak<.8 and abs(decoded[0])<.002 and abs(decoded[-1])<.002
  assert abs(len(decoded)/24000-duration)<.01
  manifest[name]={'output':str(ogg.relative_to(ROOT)),'sha256':hashlib.sha256(ogg.read_bytes()).hexdigest(),'duration':duration,'channels':1,'sampleRate':24000,'decodedPeak':peak,'activeRmsTarget':target,'recipe':events,'provenance':'Original Frozen Dawn procedural Panel buzzer, owner selected October19 2026. No sampled recordings.'}
 (HERE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(manifest,indent=2))
if __name__=='__main__':main()
