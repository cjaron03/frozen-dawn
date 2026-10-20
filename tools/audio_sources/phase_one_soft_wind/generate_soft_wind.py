#!/usr/bin/env python3
"""Reproduce the selected CC0 soft wind from the retained official preview."""
from pathlib import Path
import hashlib,json,re,subprocess
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
OUTPUT=ROOT/'src/main/resources/assets/frozendawn/sounds/ambient/wind_early_forest.ogg'
EVIDENCE=ROOT/'build/phase1-evidence/soft-wind-integration'

def measure(path,mono=False):
    filt=('atrim=start=3:duration=30,asetpts=PTS-STARTPTS,aformat=channel_layouts=mono,' if mono else '')+'loudnorm=I=-18:TP=-2:LRA=9:print_format=json'
    result=subprocess.run(['ffmpeg','-hide_banner','-i',str(path),'-af',filt,'-f','null','-'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,text=True)
    return json.loads(re.search(r'\{\s*"input_i".*?\}',result.stderr,re.S).group())

def main():
    ledger=json.loads((HERE/'source-ledger.json').read_text());source=HERE/ledger['originalLocalPath']
    assert hashlib.sha256(source.read_bytes()).hexdigest()==ledger['sha256']
    stats=measure(source,True);EVIDENCE.mkdir(parents=True,exist_ok=True)
    normalized=EVIDENCE/'soft-wind-normalized.wav'
    filt=('atrim=start=3:duration=30,asetpts=PTS-STARTPTS,aformat=channel_layouts=mono,loudnorm=I=-18:TP=-2:LRA=9:measured_I='+stats['input_i']+':measured_TP='+stats['input_tp']+':measured_LRA='+stats['input_lra']+':measured_thresh='+stats['input_thresh']+':offset='+stats['target_offset']+':linear=true,afade=t=in:d=3,afade=t=out:st=27:d=3')
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(source),'-af',filt,'-ac','1','-ar','24000','-c:a','pcm_s16le',str(normalized)],check=True)
    subprocess.run(['oggenc','-Q','-q','5','--serial','459977','-o',str(OUTPUT),str(normalized)],check=True)
    result=measure(OUTPUT)
    assert float(result['input_tp']) < -1,result
    info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration:stream=sample_rate,channels','-of','json',str(OUTPUT)],text=True))
    assert abs(float(info['format']['duration'])-30)<.05 and info['streams'][0]['channels']==1,info
    manifest={'sourceSHA256':ledger['sha256'],'runtimePath':str(OUTPUT.relative_to(ROOT)),'sha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),'normalizationFilter':filt,'originalMonoLevels':stats,'decodedLevels':result,'media':info,'ffmpeg':subprocess.check_output(['ffmpeg','-version'],text=True).splitlines()[0],'oggenc':subprocess.check_output(['oggenc','--version'],text=True).strip()}
    (HERE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(manifest,indent=2))

if __name__=='__main__':main()
