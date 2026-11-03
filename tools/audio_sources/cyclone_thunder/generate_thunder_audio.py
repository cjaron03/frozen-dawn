#!/usr/bin/env python3
"""Render the owner-selected CC0 blend plus two independent strikes. No synthetic thunder.
Requires Python 3, ffmpeg/ffprobe and oggenc. Retained inputs are public MP3 previews, not WAV masters.
"""
import array, hashlib, json, math, subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
OUT=ROOT/'src/main/resources/assets/frozendawn/sounds/ambient/cyclone/thunder'
BUILD=ROOT/'build/cyclone-evidence/thunder-variety/generated'
SR=24000
PAD=.03
# Starts align each recording's first strong crack with a short lead-in. Later starts use only their tails.
RECIPES={
 'close1':dict(duration=4.8,dryStart=1.977,closeStart=.631,dry=.80,close=.45,speed=1.0,high=6500,fadeIn=.004,fadeOut=.85,targetRms=.105),
 'close2':dict(duration=5.6,dryStart=1.977,closeStart=.625,dry=.42,close=.86,speed=.96,high=6000,fadeIn=.005,fadeOut=1.0,targetRms=.105),
 'close3':dict(duration=4.6,dryStart=1.982,closeStart=.640,dry=.68,close=.65,speed=1.025,high=6200,fadeIn=.004,fadeOut=.90,targetRms=.105),
 'mid1':dict(duration=6.0,dryStart=1.975,closeStart=.625,dry=.68,close=.55,speed=.97,high=2600,fadeIn=.065,fadeOut=1.2,targetRms=.09),
 'mid2':dict(duration=5.8,dryStart=1.970,closeStart=.635,dry=.50,close=.80,speed=1.01,high=2200,fadeIn=.080,fadeOut=1.1,targetRms=.09),
 'distant1':dict(duration=6.0,dryStart=2.150,closeStart=.850,dry=.70,close=.55,speed=.97,high=850,fadeIn=.28,fadeOut=1.6,targetRms=.065),
 'distant2':dict(duration=6.0,dryStart=2.300,closeStart=1.050,dry=.55,close=.75,speed=1.01,high=1050,fadeIn=.32,fadeOut=1.6,targetRms=.065),
 'close4':dict(duration=5.4,layers=[dict(key='clap',start=.697,weight=1)],speed=1.0,high=6200,fadeIn=.004,fadeOut=1.0,targetRms=.105),
 'close5':dict(duration=5.8,layers=[dict(key='bank',start=.567,weight=1)],speed=1.0,high=6500,fadeIn=.004,fadeOut=1.1,targetRms=.105),
 'mid3':dict(duration=6.0,layers=[dict(key='clap',start=.690,weight=1)],speed=.98,high=2400,fadeIn=.08,fadeOut=1.2,targetRms=.09),
 'mid4':dict(duration=6.0,layers=[dict(key='bank',start=.560,weight=1)],speed=1.0,high=2800,fadeIn=.06,fadeOut=1.2,targetRms=.09),
 'distant3':dict(duration=6.0,layers=[dict(key='clap',start=1.100,weight=1)],speed=.97,high=1000,fadeIn=.30,fadeOut=1.6,targetRms=.065),
 'distant4':dict(duration=6.0,layers=[dict(key='bank',start=.900,weight=1)],speed=1.0,high=800,fadeIn=.30,fadeOut=1.6,targetRms=.065),
}
def run(*args):return subprocess.check_output(args)
def read_segment(path,start,duration,speed):
 raw=run('ffmpeg','-v','error','-i',str(path),'-ss',str(start),'-ac','1',
  '-af',f'aresample={SR},asetrate={round(SR*speed)},aresample={SR},highpass=f=35',
  '-t',str(duration),'-ar',str(SR),'-f','f32le','-')
 a=array.array('f',raw);size=round(duration*SR)
 assert len(a)>=size,(path,len(a),size)
 a=a[:size];peak=max(abs(x) for x in a);assert peak>0
 return array.array('f',(x/peak for x in a))
def main():
 OUT.mkdir(parents=True,exist_ok=True);BUILD.mkdir(parents=True,exist_ok=True)
 ledger={s['key']:s for s in json.loads((HERE/'source-ledger.json').read_text())}
 assert set(ledger)=={'dry','close','clap','bank'}
 for s in ledger.values():assert hashlib.sha256((HERE/s['originalLocalPath']).read_bytes()).hexdigest()==s['sha256']
 manifest={}
 for index,(name,r) in enumerate(RECIPES.items()):
  if 'layers' in r:
   layers=r['layers'];segments=[read_segment(HERE/ledger[layer['key']]['originalLocalPath'],layer['start'],r['duration'],r['speed']) for layer in layers]
   mixed=array.array('f',(sum(segment[i]*layer['weight'] for segment,layer in zip(segments,layers)) for i in range(round(r['duration']*SR))))
   keys=[layer['key'] for layer in layers]
  else:
   a=read_segment(HERE/ledger['dry']['originalLocalPath'],r['dryStart'],r['duration'],r['speed'])
   b=read_segment(HERE/ledger['close']['originalLocalPath'],r['closeStart'],r['duration'],r['speed'])
   mixed=array.array('f',(x*r['dry']+y*r['close'] for x,y in zip(a,b)));keys=['dry','close']
  raw=BUILD/(name+'-mix.f32');raw.write_bytes(mixed.tobytes())
  filtered=array.array('f',run('ffmpeg','-v','error','-f','f32le','-ar',str(SR),'-ac','1','-i',str(raw),
   '-af',f"lowpass=f={r['high']},acompressor=threshold=0.12:ratio=2:attack=5:release=450:knee=2.5:makeup=1",'-f','f32le','-'))
  for i in range(len(filtered)):
   t=i/SR;weight=min(1,t/r['fadeIn'],(r['duration']-t)/r['fadeOut'])
   filtered[i]*=max(0,math.sin(max(0,weight)*math.pi/2))
  rms=math.sqrt(sum(x*x for x in filtered)/len(filtered));peak=max(abs(x) for x in filtered)
  scale=min(r['targetRms']/rms,.72/peak)
  filtered=array.array('f',[0])*round(PAD*SR)+array.array('f',(x*scale for x in filtered))+array.array('f',[0])*round(PAD*SR)
  pcm=BUILD/(name+'.f32');pcm.write_bytes(filtered.tobytes())
  wav=BUILD/(name+'.wav');out=OUT/(name+'.ogg')
  subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(SR),'-ac','1','-i',str(pcm),'-c:a','pcm_s16le','-map_metadata','-1',str(wav)],check=True)
  subprocess.run(['oggenc','-Q','-q','5','--serial',str(3100+index),'-o',str(out),str(wav)],check=True)
  decoded=array.array('f',run('ffmpeg','-v','error','-i',str(out),'-ac','1','-ar',str(SR),'-f','f32le','-'))
  decPeak=max(abs(x) for x in decoded);decRms=math.sqrt(sum(x*x for x in decoded)/len(decoded))
  assert decPeak<.85 and abs(decoded[0])<.003 and abs(decoded[-1])<.003,(name,decPeak,decoded[0],decoded[-1])
  assert abs(len(decoded)/SR-(r['duration']+2*PAD))<.01
  info=json.loads(run('ffprobe','-v','error','-show_entries','stream=sample_rate,channels:format=duration','-of','json',str(out)))
  assert info['streams'][0]['channels']==1 and int(info['streams'][0]['sample_rate'])==SR
  manifest[name]={'recipe':r,'output':str(out.relative_to(ROOT)),'sourceKeys':keys,'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),
   'durationSeconds':len(decoded)/SR,'sampleRate':SR,'channels':1,'decodedPeak':decPeak,'decodedRms':decRms,'firstSample':decoded[0],'lastSample':decoded[-1],
   'edgePaddingSeconds':PAD,'worstColdFilteredDurationSeconds':(r['duration']+2*PAD)/(.94*.75),'processing':('Aligned normalized source blends' if len(keys)>1 else 'Independent normalized recorded strike')+', highpass35Hz, distance lowpass, gentle compression, sine edge fades, level-match and Vorbis encode'}
  assert manifest[name]['worstColdFilteredDurationSeconds']<10
 (HERE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(manifest,indent=2))
if __name__=='__main__':main()
