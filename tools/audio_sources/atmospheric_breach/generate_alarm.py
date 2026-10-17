#!/usr/bin/env python3
"""Original Frozen Dawn three-pulse critical alarm; no sampled recordings."""
from pathlib import Path
import math
import struct
import subprocess
import tempfile
import wave

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = ROOT / "src/main/resources/assets/frozendawn/sounds/ui/suit/atmospheric_breach_alarm.ogg"
RATE = 44100
DURATION = 0.92

with tempfile.TemporaryDirectory(prefix="fd-breach-alarm-") as folder:
    raw = Path(folder) / "alarm.wav"
    samples = bytearray()
    for index in range(round(RATE * DURATION)):
        t = index / RATE
        phase = t % 0.32
        if t < 0.84 and phase < 0.20:
            envelope = min(1.0, phase / 0.006, (0.20 - phase) / 0.016)
            value = envelope * (0.70 * math.sin(2 * math.pi * 880 * t)
                                + 0.15 * math.sin(2 * math.pi * 1760 * t))
        else:
            value = 0.0
        samples.extend(struct.pack("<h", round(value * 32767)))
    with wave.open(str(raw), "wb") as file:
        file.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        file.writeframes(samples)
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(raw),
                    "-ac", "2", "-c:a", "vorbis", "-strict", "-2", "-q:a", "5", str(OUTPUT)], check=True)
print(OUTPUT)
