#!/usr/bin/env python3
"""Prepare the selected CC0 forest wind; requires ffmpeg and oggenc, adds no synthesized layers."""
from pathlib import Path
import array, hashlib, json, math, subprocess, wave

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = ROOT / 'src/main/resources/assets/frozendawn/sounds/ambient'
EVIDENCE = ROOT / 'build/phase1-evidence/budjillett-wind/rendered'
RATE = 24000
DURATION = 57
STARTS = (90, 180)
FILTER = 'highpass=f=70,lowpass=f=8000,acompressor=threshold=0.02:ratio=2:attack=20:release=350:makeup=1'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def decode(path, start=None):
    cmd = ['ffmpeg', '-v', 'error']
    if start is not None:
        cmd += ['-ss', str(start)]
    cmd += ['-i', str(path)]
    if start is not None:
        cmd += ['-t', str(DURATION), '-af', FILTER]
    cmd += ['-ac', '1', '-ar', str(RATE), '-f', 'f32le', '-']
    values = array.array('f')
    values.frombytes(subprocess.check_output(cmd))
    return values


def stats(values):
    return dict(peak=max(abs(x) for x in values), rms=math.sqrt(sum(x*x for x in values)/len(values)),
                samples=len(values), durationSeconds=len(values)/RATE)


def main():
    ledger_path = HERE / 'source-ledger.json'
    ledger = json.loads(ledger_path.read_text())
    source = HERE / ledger['originalLocalPath']
    assert sha(source) == ledger['sha256'], 'Source recording differs from preserved download'
    outputs = [OUT / f'wind_phase_one_{i}.ogg' for i in (1, 2)]
    ledger.update(modifications='generate_wind.py: two 57-second trims at 90/180 s; 70 Hz high-pass, 8 kHz low-pass, gentle 2:1 peak compression; RMS matching capped at 0.80 peak; five-second edge fades; mono 24 kHz Vorbis quality 5. No synthesis, pitch shift or time stretch.',
                  localRuntimeOutputPaths=[str(p.relative_to(ROOT)) for p in outputs])
    ledger_path.write_text(json.dumps(ledger, indent=2)+'\n')
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    result = dict(sourceSHA256=sha(source), filter=FILTER, sampleRate=RATE, fadeSeconds=5, variants=[])
    for i, (start, output) in enumerate(zip(STARTS, outputs), 1):
        values = decode(source, start)
        assert len(values) == DURATION * RATE
        before = stats(values)
        gain = min(.12 / before['rms'], .80 / before['peak'])
        fade = 5 * RATE
        for j in range(len(values)):
            values[j] *= gain * min(1, j/fade, (len(values)-1-j)/fade)
        after = stats(values)
        assert after['peak'] <= .801
        wav = EVIDENCE / f'wind_phase_one_{i}.wav'
        with wave.open(str(wav), 'wb') as handle:
            handle.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
            handle.writeframes(array.array('h', (round(x*32767) for x in values)).tobytes())
        subprocess.run(['oggenc', '-Q', '-q', '5', '--serial', str(1094850+i), '-o', str(output), str(wav)], check=True)
        decoded = stats(decode(output))
        assert decoded['peak'] < .95, 'Vorbis encode introduced clipping/headroom violation'
        assert abs(decoded['durationSeconds'] - DURATION) < .02
        result['variants'].append(dict(path=str(output.relative_to(ROOT)), startSeconds=start,
                                      durationSeconds=DURATION, linearGain=gain, before=before,
                                      beforeEncoding=after, decoded=decoded, sha256=sha(output)))
    result['encoders'] = dict(ffmpeg=subprocess.check_output(['ffmpeg', '-version'], text=True).splitlines()[0],
                              oggenc=subprocess.check_output(['oggenc', '--version'], text=True).strip())
    (HERE / 'manifest.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
