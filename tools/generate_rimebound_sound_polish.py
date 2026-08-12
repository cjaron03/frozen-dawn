#!/usr/bin/env python3
"""Generate distinct Rimebound pressure, strike, and encasement cues."""

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


def encode(name: str, samples: list[float]) -> None:
    peak = max(0.001, max(abs(value) for value in samples))
    scale = 0.91 / peak
    pcm = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, value * scale)) * 32767))
                   for value in samples)
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as temp:
        with wave.open(temp.name, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(RATE)
            wav.writeframes(pcm)
        subprocess.run(["oggenc", "-Q", "-q", "6", "-o",
                        str(OUT / f"{name}.ogg"), temp.name], check=True)


def envelope(t: float, duration: float, attack: float, release: float) -> float:
    return min(1.0, t / attack) * min(1.0, (duration - t) / release)


def pressure_call(name: str, duration: float, seed: int, base: float,
                  rise: float, cracks: int) -> None:
    rng = random.Random(seed)
    crack_times = sorted(rng.uniform(0.18, duration - 0.18) for _ in range(cracks))
    phase = 0.0
    rough = 0.0
    samples: list[float] = []
    for index in range(int(duration * RATE)):
        t = index / RATE
        bend = base + rise * (t / duration) ** 1.7
        bend += 7.0 * math.sin(t * 2.1) + 3.5 * math.sin(t * 7.7)
        phase += 2.0 * math.pi * bend / RATE
        carrier = math.sin(phase + 1.7 * math.sin(phase * 0.31))
        carrier += 0.34 * math.sin(phase * 2.03 + math.sin(t * 4.0))
        rough += 0.018 * (rng.uniform(-1.0, 1.0) - rough)
        value = 0.34 * carrier + 0.38 * rough
        for crack in crack_times:
            age = t - crack
            if 0.0 <= age <= 0.11:
                value += math.exp(-age * 43.0) * (
                    0.72 * math.sin(2.0 * math.pi * (940.0 - age * 4200.0) * age)
                    + 0.25 * rng.uniform(-1.0, 1.0))
        value *= envelope(t, duration, 0.08, 0.24)
        value *= 0.82 + 0.18 * math.sin(2.0 * math.pi * 1.35 * t) ** 2
        samples.append(value)
    encode(name, samples)


def wedge_strike(name: str, seed: int, pitch: float) -> None:
    rng = random.Random(seed)
    duration = 0.72
    samples: list[float] = []
    for index in range(int(duration * RATE)):
        t = index / RATE
        hit = math.exp(-t * 24.0) * (
            0.9 * math.sin(2.0 * math.pi * pitch * t)
            + 0.5 * math.sin(2.0 * math.pi * pitch * 2.81 * t))
        scrape_t = max(0.0, t - 0.06)
        scrape = math.exp(-scrape_t * 7.0) * rng.uniform(-1.0, 1.0)
        scrape *= 0.42 + 0.58 * math.sin(2.0 * math.pi * 53.0 * scrape_t) ** 2
        tail = 0.24 * math.sin(2.0 * math.pi * (118.0 - 70.0 * t) * t)
        tail *= math.exp(-t * 3.8)
        samples.append((hit + 0.48 * scrape + tail)
                       * envelope(t, duration, 0.006, 0.11))
    encode(name, samples)


def crystal_growth(name: str, duration: float, seed: int, inward: bool) -> None:
    rng = random.Random(seed)
    grains = [(rng.uniform(0.0, duration), rng.uniform(520.0, 2200.0),
               rng.uniform(0.15, 0.48)) for _ in range(46)]
    samples: list[float] = []
    for index in range(int(duration * RATE)):
        t = index / RATE
        progress = t / duration
        density = progress if inward else 1.0 - progress
        base_frequency = (96.0 + 210.0 * progress) if inward else (220.0 - 120.0 * progress)
        value = 0.22 * math.sin(2.0 * math.pi * base_frequency * t
                                + 2.1 * math.sin(t * 3.4))
        for start, frequency, strength in grains:
            age = t - start
            if 0.0 <= age <= 0.045:
                value += strength * math.exp(-age * 82.0) * math.sin(
                    2.0 * math.pi * frequency * age)
        noise = rng.uniform(-1.0, 1.0)
        value += noise * (0.07 + 0.19 * density)
        value *= envelope(t, duration, 0.018, 0.12)
        samples.append(value)
    encode(name, samples)


pressure_call("ambient_3", 3.7, 81, 58.0, 74.0, 4)
pressure_call("ambient_4", 4.25, 82, 33.0, -9.0, 7)
pressure_call("hurt_3", 0.88, 83, 146.0, -78.0, 8)
pressure_call("death_2", 2.65, 84, 124.0, -102.0, 24)
pressure_call("burrow_2", 2.5, 85, 29.0, 18.0, 19)
pressure_call("eruption_2", 1.4, 86, 42.0, 168.0, 23)
pressure_call("freeze_windup_2", 1.5, 87, 68.0, 235.0, 9)
wedge_strike("attack_1", 91, 188.0)
wedge_strike("attack_2", 92, 143.0)
crystal_growth("encase", 0.82, 101, True)
crystal_growth("solidify", 1.25, 102, True)
crystal_growth("break_free", 0.95, 103, False)

print(OUT)
