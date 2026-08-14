#!/usr/bin/env python3
"""Deterministically build the Aggregate's structural/vocal candidate mixes."""

from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
RAW = Path(__file__).resolve().parent / "raw"
OUT = ROOT / "src/main/resources/assets/frozendawn/sounds/entity/aggregate"


def render(name, duration, clips):
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
    for clip in clips:
        command += ["-i", str(RAW / clip[0])]
    chains = []
    labels = []
    for index, (_, start, length, filters, volume, delay) in enumerate(clips):
        label = f"a{index}"
        chain = (f"[{index}:a]atrim=start={start}:duration={length},"
                 f"asetpts=PTS-STARTPTS,{filters},volume={volume}")
        if delay:
            chain += f",adelay={delay}|{delay}"
        chains.append(chain + f"[{label}]")
        labels.append(f"[{label}]")
    fade_out = max(0.1, duration - 0.5)
    chains.append("".join(labels)
                  + f"amix=inputs={len(labels)}:duration=longest:normalize=0,"
                    "highpass=f=28,lowpass=f=14500,alimiter=limit=0.88,"
                  + f"afade=t=in:st=0:d=0.06,afade=t=out:st={fade_out}:d=0.5[mix]")
    output = OUT / f"{name}.ogg"
    with tempfile.TemporaryDirectory(prefix="aggregate-audio-") as temp_dir:
        intermediate = Path(temp_dir) / f"{name}.wav"
        command += ["-filter_complex", ";".join(chains), "-map", "[mix]",
                    "-t", str(duration), "-ac", "1", "-ar", "44100",
                    "-c:a", "pcm_s16le", str(intermediate)]
        subprocess.run(command, check=True)
        subprocess.run([
            "oggenc", "--quiet", "--quality", "5", "--output", str(output),
            str(intermediate),
        ], check=True)


def candidates():
    OUT.mkdir(parents=True, exist_ok=True)
    for variant in range(3):
        offset = variant * 0.32
        render(f"awaken_{variant + 1}", 5.2, [
            ("bass_rumble.mp3", 4.0 + variant, 5.2, "lowpass=f=170", 0.82, 0),
            ("debris.mp3", offset, 4.4, "atempo=0.86", 0.76, 450),
            ("body_drag.mp3", offset, 4.3, "lowpass=f=4800,atempo=0.82", 0.64, 120),
            ("animal_groan.mp3", offset, 3.3,
             "asetrate=44100*0.63,aresample=44100,areverse,lowpass=f=2400", 0.32, 900),
        ])
        render(f"ambient_{variant + 1}", 4.3, [
            ("concrete_drag.mp3", 2.0 + variant * 1.3, 4.1,
             "asetrate=44100*0.78,aresample=44100,lowpass=f=5200", 0.52, 0),
            ("phantom_groan.mp3", offset, 3.1,
             "asetrate=44100*0.69,aresample=44100,areverse,lowpass=f=1900", 0.22, 520),
            ("bone_breaks.mp3", 0.7 + offset, 1.2, "lowpass=f=7600", 0.30, 2400),
        ])
        render(f"slam_{variant + 1}", 2.8, [
            ("heavy_stomp.mp3", offset, 2.8, "asetrate=44100*0.84,aresample=44100", 0.94, 0),
            ("bone_breaks.mp3", 0.35 + offset, 1.4, "highpass=f=120", 0.72, 220),
            ("debris.mp3", 0.4 + offset, 2.1, "lowpass=f=7200", 0.62, 310),
            ("bass_rumble.mp3", 8.0 + variant, 2.8, "lowpass=f=140", 0.66, 0),
        ])
        render(f"reallocation_{variant + 1}", 4.1, [
            ("concrete_drag.mp3", 4.0 + variant, 4.0, "atempo=0.78,lowpass=f=6800", 0.72, 0),
            ("bone_breaks.mp3", offset, 2.2, "atempo=0.82", 0.62, 620),
            ("animal_groan.mp3", offset, 3.0,
             "asetrate=44100*0.58,aresample=44100,lowpass=f=2300", 0.26, 350),
            ("phantom_groan.mp3", offset, 3.0,
             "asetrate=44100*0.73,aresample=44100,areverse,lowpass=f=1700", 0.22, 930),
        ])
        render(f"death_{variant + 1}", 6.4, [
            ("debris.mp3", offset, 4.5, "atempo=0.82,lowpass=f=9000", 0.92, 700),
            ("body_drag.mp3", offset, 4.4, "asetrate=44100*0.72,aresample=44100", 0.75, 0),
            ("bass_rumble.mp3", 12.0 + variant, 6.2, "lowpass=f=160", 0.82, 0),
            ("animal_groan.mp3", offset, 3.6,
             "asetrate=44100*0.55,aresample=44100,areverse,lowpass=f=2100", 0.34, 800),
            ("phantom_groan.mp3", offset, 3.5,
             "asetrate=44100*0.68,aresample=44100,lowpass=f=1800", 0.28, 1750),
        ])


if __name__ == "__main__":
    candidates()
    print(f"Wrote Aggregate candidate mixes to {OUT.relative_to(ROOT)}")
