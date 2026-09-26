#!/usr/bin/env python3
"""Original deterministic bow release and wordless soul breath; no source recordings."""
from array import array
import math
from pathlib import Path
import random
import subprocess
import sys
import tempfile
import wave

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = ROOT / "src/main/resources/assets/frozendawn/sounds/entity/architect"
RATE = 48000
DURATION = .62


def synthesize(variant):
    rng = random.Random(260926 + variant)
    # Karplus-Strong noise excitation makes the release a plucked string, not a laser tone.
    frequency = (247, 261, 233)[variant - 1]
    string = [rng.uniform(-1, 1) for _ in range(round(RATE / frequency))]
    samples = []
    low_noise = slow_noise = 0.0
    for i in range(round(RATE * DURATION)):
        t = i / RATE
        index = i % len(string)
        pluck = string[index]
        string[index] = .992 * .5 * (pluck + string[(index + 1) % len(string)])
        noise = rng.uniform(-1, 1)
        low_noise += .17 * (noise - low_noise)
        slow_noise += .026 * (noise - slow_noise)
        breath = low_noise - slow_noise
        snap = .25 * noise * math.exp(-t * 210)
        twang = .62 * pluck * math.exp(-t * 14)
        # A quiet filtered breath with irregular resonant texture; there is no spoken voice.
        envelope = (1 - math.exp(-t * 80)) * math.exp(-t * 8)
        texture = .65 + .2 * math.sin(math.tau * 520 * t) + .15 * math.sin(math.tau * 870 * t)
        soul = .8 * breath * envelope * texture
        sample = snap + twang + soul
        # Short, low-level echoes give the release a hollow tail without a persistent hum.
        for delay, gain in ((.037, .12), (.071, .06)):
            at = i - round(delay * RATE)
            if at >= 0:
                sample += samples[at] * gain
        samples.append(sample)
    peak = max(abs(x) for x in samples)
    pcm = array("h")
    for i, sample in enumerate(samples):
        fade = min(1, i / 48, (len(samples) - 1 - i) / 960)
        pcm.append(round(sample * .65 / peak * fade * 32767))
    if sys.byteorder != "little":
        pcm.byteswap()
    return pcm.tobytes()


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="architect-bow-") as temporary:
        for variant in (1, 2, 3):
            wav = Path(temporary) / f"shoot_{variant}.wav"
            with wave.open(str(wav), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(RATE)
                output.writeframes(synthesize(variant))
            destination = OUTPUT / f"shoot_{variant}.ogg"
            subprocess.run(["oggenc", "-Q", "-q", "4", "--serial", str(260926 + variant),
                            "-o", str(destination), str(wav)], check=True)
            print(destination.relative_to(ROOT))


if __name__ == "__main__":
    main()
