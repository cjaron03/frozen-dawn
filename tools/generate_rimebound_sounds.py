#!/usr/bin/env python3
"""Synthesize Rimebound-only ice, ground, and fire telegraphs and encode OGGs."""

from __future__ import annotations

import math
import random
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/frozendawn/sounds/entity/rimebound"
RATE = 44_100


def render(name: str, seconds: float, seed: int, low: float, high: float,
           noise: float, cracks: int, sweep: float = 0.0, tremor: float = 0.0) -> None:
    rng = random.Random(seed)
    frames = int(seconds * RATE)
    impulses = [(rng.randrange(frames), rng.uniform(0.2, 1.0),
                 rng.uniform(30.0, 140.0)) for _ in range(cracks)]
    values: list[int] = []
    filtered = 0.0
    for i in range(frames):
        t = i / RATE
        envelope = min(1.0, t / 0.035) * min(1.0, (seconds - t) / 0.12)
        frequency = low + (high - low) * (t / seconds) + sweep * math.sin(t * 1.7)
        drone = math.sin(2 * math.pi * frequency * t)
        drone += 0.45 * math.sin(2 * math.pi * frequency * 1.73 * t + 0.7)
        raw_noise = rng.uniform(-1.0, 1.0)
        filtered += 0.045 * (raw_noise - filtered)
        signal = 0.18 * drone + noise * filtered
        if tremor:
            signal *= 0.72 + 0.28 * math.sin(2 * math.pi * tremor * t) ** 2
        for at, strength, decay in impulses:
            age = i - at
            if 0 <= age < RATE // 8:
                signal += strength * math.exp(-age / (RATE / decay)) * (
                    math.sin(age * 0.31) + rng.uniform(-0.35, 0.35))
        signal = max(-0.96, min(0.96, signal * envelope * 0.78))
        values.append(int(signal * 32767))

    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as temp:
        with wave.open(temp.name, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(RATE)
            wav.writeframes(b"".join(struct.pack("<h", value) for value in values))
        subprocess.run([
            "oggenc", "-Q", "-q", "5", "-o",
            str(OUT / f"{name}.ogg"), temp.name,
        ], check=True)


PROFILES = [
    ("ambient_1", 2.8, 1, 37, 31, .50, 3, 4, 2.7),
    ("ambient_2", 3.1, 2, 44, 27, .46, 5, 7, 1.9),
    ("hurt_1", .65, 3, 98, 43, .38, 8, 0, 0),
    ("hurt_2", .75, 4, 73, 36, .44, 10, 0, 0),
    ("death", 2.1, 5, 91, 24, .58, 31, -9, 0),
    ("contraction", 1.15, 6, 52, 29, .55, 12, 2, 0),
    ("burrow", 2.4, 7, 34, 25, .72, 17, 6, 3.4),
    ("eruption", 1.35, 8, 46, 116, .62, 27, 12, 0),
    ("lance_windup", 1.25, 9, 70, 290, .19, 5, 33, 4.8),
    ("lance", .62, 10, 250, 105, .42, 11, -18, 0),
    ("lance_embed", .46, 11, 112, 58, .34, 8, 0, 0),
    ("shell_crack", .52, 12, 127, 51, .48, 13, 0, 0),
    ("shell_shatter", 1.05, 13, 115, 37, .55, 28, 0, 0),
    ("armor", 1.55, 14, 41, 79, .46, 15, 8, 2.3),
    ("freeze_windup", 1.35, 15, 55, 188, .28, 7, 21, 5.5),
    ("freeze", 1.1, 16, 84, 39, .64, 24, -4, 0),
    ("leap", .72, 17, 48, 132, .42, 8, 20, 0),
    ("fire_scream", 1.65, 18, 181, 477, .31, 18, 75, 7.5),
]

for profile in PROFILES:
    render(*profile)

print(OUT)
