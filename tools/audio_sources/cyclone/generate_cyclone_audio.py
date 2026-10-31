#!/usr/bin/env python3
"""Reproduce recorded CC0 cyclone beds. Requires Python 3, ffmpeg, ffprobe and oggenc."""
import array
import hashlib
import json
import math
import subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
OUT=ROOT/'src/main/resources/assets/frozendawn/sounds/ambient/cyclone'
BUILD=ROOT/'build/cyclone-evidence/audio-2/generated'
SR=24000
DURATION=64
SEAM=4
RECIPES={
 'snow1':('snow',35,60,2000,.85,.090), 'snow2':('snow',165,60,1900,.85,.090),
 'roar1':('snow',45,35,1050,.72,.095), 'roar2':('snow',245,35,1150,.72,.095),
 'room1':('room',50,45,1900,1,.045), 'room2':('room',220,45,1700,1,.045),
 'buffet1':('buffet',18,40,650,1,.050), 'buffet2':('buffet',80,40,800,1,.050),
}
GUSTS={'snow1':[(11,8),(41,10)],'snow2':[(19,11),(49,7)]}
def run(*args):return subprocess.check_output(list(args))
def source_audio(path,start,low,high,speed,steady=False):
 rate=int(json.loads(run('ffprobe','-v','error','-show_entries','stream=sample_rate','-of','json',str(path)))['streams'][0]['sample_rate'])
 filters=f'asetrate={round(rate*speed)},aresample={SR},highpass=f={low},lowpass=f={high},highpass=f=25'
 if steady:filters+=',acompressor=threshold=0.025:ratio=4:attack=100:release=1500:knee=4:makeup=1'
 raw=run('ffmpeg','-v','error','-i',str(path),'-ss',str(start),'-t',str(DURATION+SEAM),'-ac','1','-af',filters,'-ar',str(SR),'-f','f32le','-')
 data=array.array('f',raw)
 assert len(data)>=(DURATION+SEAM)*SR,(path,len(data))
 return data[:(DURATION+SEAM)*SR]
def periodic(data):
 seam=SEAM*SR;end=DURATION*SR
 out=array.array('f',data[seam:end])
 for i in range(seam):
  angle=(i+.5)/seam*math.pi/2
  out.append(data[end+i]*math.cos(angle)+data[i]*math.sin(angle))
 assert len(out)==DURATION*SR
 return out

def main():
 OUT.mkdir(parents=True,exist_ok=True);BUILD.mkdir(parents=True,exist_ok=True)
 ledger={s['key']:s for s in json.loads((HERE/'source-ledger.json').read_text())}
 for rec in ledger.values():
  p=HERE/rec['originalLocalPath'];assert hashlib.sha256(p.read_bytes()).hexdigest()==rec['sha256']
 manifest={}
 for name,(key,start,low,high,speed,target) in RECIPES.items():
  data=periodic(source_audio(HERE/ledger[key]['originalLocalPath'],start,low,high,speed,steady=name.startswith('roar')))
  rms=math.sqrt(sum(x*x for x in data)/len(data));peak=max(abs(x) for x in data)
  scale=min(target/rms,.62/max(peak,1e-9));data=array.array('f',(x*scale for x in data))
  if name in GUSTS:
   # Long smooth gusts between quiet intervals; the separate roar bed never drops out.
   for i in range(len(data)):
    seconds=i/SR
    envelope=max((math.sin(math.pi*(seconds-start)/duration)**2
                  for start,duration in GUSTS[name] if start<=seconds<start+duration),default=0)
    data[i]*=envelope
  pcm=BUILD/(name+'.f32');pcm.write_bytes(data.tobytes());out=OUT/(name+'.ogg')
  wav=BUILD/(name+'.wav')
  subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(SR),'-ac','1','-i',str(pcm),'-c:a','pcm_s16le','-map_metadata','-1',str(wav)],check=True)
  subprocess.run(['oggenc','-Q','-q','5','--serial',str(1000+list(RECIPES).index(name)),'-o',str(out),str(wav)],check=True)
  # Inspect the shipped decoder output, not just the pre-encode samples.
  decoded=array.array('f',run('ffmpeg','-v','error','-i',str(out),'-ac','1','-ar',str(SR),'-f','f32le','-'))
  dec_rms=math.sqrt(sum(x*x for x in decoded)/len(decoded));dec_peak=max(abs(x) for x in decoded)
  boundary=abs(decoded[-1]-decoded[0]);assert dec_peak<.85 and boundary<.12,(name,dec_peak,boundary)
  info=json.loads(run('ffprobe','-v','error','-show_entries','format=duration:stream=channels,sample_rate','-of','json',str(out)))
  assert abs(float(info['format']['duration'])-DURATION)<.01
  assert info['streams'][0]['channels']==1 and int(info['streams'][0]['sample_rate'])==SR
  manifest[name]={'sourceKey':key,'role':'occasional outdoor gusts' if name in GUSTS else 'steady outdoor roar' if name.startswith('roar') else 'sheltered wind',
   'gustWindowsSeconds':GUSTS.get(name,[]),'steadyCompressor':name.startswith('roar'),'startSecondsAfterSpeedChange':start,'highpassHz':low,'lowpassHz':high,'speedAndPitch':speed,
   'durationSeconds':DURATION,'sampleRate':SR,'channels':1,'internalLoopSeamSeconds':SEAM,'runtimeCrossfadeSeconds':6,
   'rms':dec_rms,'peak':dec_peak,'decodedLoopBoundaryStep':boundary,'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),
   'output':str(out.relative_to(ROOT))}
 (HERE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
 print(json.dumps(manifest,indent=2))
if __name__=='__main__':main()
