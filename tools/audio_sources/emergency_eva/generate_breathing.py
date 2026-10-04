#!/usr/bin/env python3
"""Create emergency filter breathing from the unchanged owner's EVA recording."""
from array import array
import hashlib
import math
from pathlib import Path
import random
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "src/main/resources/assets/frozendawn/sounds/ambient/eva_breathing.ogg"
OUTPUT = SOURCE.with_name("eva_emergency_breathing.ogg")
SOURCE_SHA256 = "d3f1dfe47be702ad567bb197adac85fad695bd587553349bba64a6a15ade0adb"
RATE = 48000


def decode(filters: str | None = None) -> array:
    cmd = ["ffmpeg", "-v", "error", "-i", str(SOURCE)]
    if filters:
        cmd += ["-af", filters]
    cmd += ["-f", "f32le", "-ac", "1", "-ar", str(RATE), "pipe:1"]
    samples = array("f", subprocess.check_output(cmd))
    if sys.byteorder != "little":
        samples.byteswap()
    return samples


def main() -> None:
    if hashlib.sha256(SOURCE.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise SystemExit("Owner recording changed; review the source before generating a derivative.")
    original = decode()
    filtered = decode("highpass=f=140,lowpass=f=3000,equalizer=f=1600:t=q:w=1.1:g=3.5")
    if len(filtered) != len(original):
        raise SystemExit("Processing changed the recording length.")

    rng = random.Random(0x0A5AE6)
    envelope = noise_low = noise_high = 0.0
    low_coefficient = 1 - math.exp(-2 * math.pi * 2800 / RATE)
    high_coefficient = 1 - math.exp(-2 * math.pi * 700 / RATE)
    attack = 1 - math.exp(-1 / (0.020 * RATE))
    release = 1 - math.exp(-1 / (0.120 * RATE))
    processed = array("f")
    for i, sample in enumerate(filtered):
        magnitude = abs(sample)
        envelope += (magnitude - envelope) * (attack if magnitude > envelope else release)
        noise_low += low_coefficient * (rng.uniform(-1, 1) - noise_low)
        noise_high += high_coefficient * (noise_low - noise_high)
        # Dry filter rasp follows the breath; there is no constant hiss bed.
        rasp = (noise_low - noise_high) * envelope * 0.55
        # Slightly rough valve movement, without increasing breathing speed.
        flutter = 1 - 0.06 * (0.5 + 0.5 * math.sin(2 * math.pi * 12.5 * i / RATE))
        compressed = 0.82 * sample + 0.18 * math.tanh(sample * 2) / 2
        processed.append(compressed * flutter + rasp)

    original_rms = math.sqrt(sum(x * x for x in original) / len(original))
    processed_rms = math.sqrt(sum(x * x for x in processed) / len(processed))
    gain = min(original_rms / processed_rms, 0.85 / max(abs(x) for x in processed))
    for i in range(len(processed)):
        processed[i] *= gain
    if sys.byteorder != "little":
        processed.byteswap()
    subprocess.run([
        "ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1",
        "-i", "pipe:0", "-map_metadata", "-1", "-af", "pan=stereo|c0=c0|c1=c0", "-c:a", "vorbis",
        "-strict", "experimental", "-q:a", "4", str(OUTPUT),
    ], input=processed.tobytes(), check=True)
    print(f"Created {OUTPUT.relative_to(ROOT)} ({len(original) / RATE:.3f}s, gain {gain:.3f})")


if __name__ == "__main__":
    main()
