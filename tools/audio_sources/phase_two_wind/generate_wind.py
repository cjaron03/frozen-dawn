#!/usr/bin/env python3
"""Reproduce two phase-2 CC0 wind excerpts; all edge envelopes are runtime-owned."""
from pathlib import Path
import hashlib,json,re,subprocess
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
EVIDENCE=ROOT/'build/phase2-evidence/audio'

def run(args):
    return subprocess.run(args,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)

def measure(path,base=''):
    filt=(base+',' if base else '')+'loudnorm=I=-18:TP=-2:LRA=9:print_format=json'
    result=run(['ffmpeg','-hide_banner','-i',str(path),'-af',filt,'-f','null','-'])
    return json.loads(re.search(r'\{\s*"input_i".*?\}',result.stderr,re.S).group())

def main():
    ledger=json.loads((HERE/'source-ledger.json').read_text());source=HERE/ledger['originalLocalPath']
    assert hashlib.sha256(source.read_bytes()).hexdigest()==ledger['sha256']
    EVIDENCE.mkdir(parents=True,exist_ok=True);outputs=[]
    for number,start in enumerate([30,210],1):
        base=f'atrim=start={start}:duration=54,asetpts=PTS-STARTPTS,aformat=channel_layouts=mono,highpass=f=80,lowpass=f=6000'
        stats=measure(source,base)
        filt=(base+',loudnorm=I=-18:TP=-2:LRA=9:measured_I='+stats['input_i']+':measured_TP='+stats['input_tp']+':measured_LRA='+stats['input_lra']+':measured_thresh='+stats['input_thresh']+':offset='+stats['target_offset']+':linear=true')
        wav=EVIDENCE/f'wind-phase-two-{number}.wav';output=ROOT/f'src/main/resources/assets/frozendawn/sounds/ambient/wind_phase_two_{number}.ogg'
        run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(source),'-af',filt,'-ac','1','-ar','24000','-c:a','pcm_s16le',str(wav)])
        run(['oggenc','-Q','-q','5','--serial',str(1175040+number),'-o',str(output),str(wav)])
        decoded=measure(output)
        media=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration:stream=channels,sample_rate','-of','json',str(output)],text=True))
        assert abs(float(media['format']['duration'])-54)<.01 and media['streams'][0]['channels']==1
        assert float(decoded['input_tp']) < -1 and -20 < float(decoded['input_i']) < -16,decoded
        outputs.append({'path':str(output.relative_to(ROOT)),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'sourceStartSeconds':start,'durationSeconds':54,'filter':filt,'measuredSource':stats,'measuredOutput':decoded,'media':media})
    result={'sourceSHA256':ledger['sha256'],'ffmpeg':subprocess.check_output(['ffmpeg','-version'],text=True).splitlines()[0],'oggenc':subprocess.check_output(['oggenc','--version'],text=True).strip(),'edgeFades':'Runtime equal-power 100-tick overlap, no baked fades','outputs':outputs}
    (HERE/'manifest.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))

if __name__=='__main__':main()
