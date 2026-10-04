#!/usr/bin/env python3
"""Original deterministic fan, regulator and disposable-pack shutdown synthesis.

No recordings or generated speech are used. FFmpeg provides native Vorbis encoding.
"""
from array import array
import math
from pathlib import Path
import random
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
RATE = 48000


def encode(relative: str, samples: array) -> None:
    peak = max(abs(x) for x in samples)
    if peak > 0.35:
        samples = array('f', (x * 0.35 / peak for x in samples))
    if sys.byteorder != 'little': samples.byteswap()
    output = ROOT / 'src/main/resources/assets/frozendawn/sounds' / relative
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(RATE),
                    '-ac', '1', '-i', 'pipe:0', '-map_metadata', '-1',
                    '-af', 'pan=stereo|c0=c0|c1=c0', '-c:a', 'vorbis', '-strict',
                    'experimental', '-q:a', '4', str(output)], input=samples.tobytes(), check=True)
    print(output.relative_to(ROOT))


def tick(t: float, onset: float, length: float, noise: float) -> float:
    age = t - onset
    if age < 0 or age >= length: return 0.0
    env = math.sin(math.pi * age / length) ** 2 * math.exp(-age * 28)
    return env * (0.65 * math.sin(2 * math.pi * 760 * age) + 0.35 * noise)


def main() -> None:
    rng = random.Random(610104)
    fan = array('f')
    for i in range(RATE * 8):
        t = i / RATE
        noise = rng.uniform(-1, 1)
        # Whole-cycle harmonics and tapered noise keep the native-loop seam quiet.
        edge = min(1, t / 0.04, (8 - t - 1 / RATE) / 0.04)
        rotor = (math.sin(2 * math.pi * 94 * t)
                 + 0.25 * math.sin(2 * math.pi * 188 * t)
                 + 0.12 * math.sin(2 * math.pi * 376 * t))
        bearing = math.sin(2 * math.pi * 23 * t) ** 12
        fan.append(edge * (0.06 * rotor * (0.9 + 0.1 * math.cos(2 * math.pi * 2 * t))
                          + 0.017 * bearing * noise
                          + 0.022 * (tick(t, 1.2, 0.09, noise) + tick(t, 4.8, 0.09, noise))))
    encode('ambient/eva_emergency_fan.ogg', fan)
    for relative, duration, shutdown in [('ui/suit/emergency_regulator.ogg', 0.65, False),
                                         ('ui/suit/emergency_shutdown.ogg', 1.25, True)]:
        samples = array('f')
        filtered = 0.0
        phase = 0.0
        for i in range(round(RATE * duration)):
            t = i / RATE
            noise = rng.uniform(-1, 1)
            filtered = filtered * 0.75 + noise * 0.25
            click = 0.16 * tick(t, 0.02, 0.08, noise) + 0.12 * tick(t, 0.17, 0.10, noise)
            if shutdown:
                frequency = 140 - 100 * min(1, t / 0.8)
                phase += 2 * math.pi * frequency / RATE
                coast = 0.09 * math.sin(phase) * max(0, 1 - t / 0.9) ** 2
                purge = 0.09 * filtered * max(0, math.sin(math.pi * min(1, t / 0.8))) ** 2
                value = click + coast + purge
            else:
                envelope = math.sin(math.pi * min(1, max(0, (t - 0.1) / 0.48))) ** 2
                value = click + 0.11 * filtered * envelope
            edge = min(1, t / 0.01, (duration - t - 1 / RATE) / 0.03)
            samples.append(value * max(0, edge))
        encode(relative, samples)


if __name__ == '__main__': main()
