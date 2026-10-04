#!/usr/bin/env python3
"""Mix the verified CC0 mask and strained performances for emergency EVA."""
from array import array
import hashlib
import math
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
SOURCES = Path(__file__).resolve().parent
NORMAL = ROOT / "src/main/resources/assets/frozendawn/sounds/ambient/eva_breathing.ogg"
OUTPUT = NORMAL.with_name("eva_emergency_breathing.ogg")
SOURCE_HASHES = {
    "gas_mask_breath_cc0.mp3": "f8c6ca18865381e58ff613d23a6e1f87028628ec757b388922a289aea6a8dbb8",
    "scared_heavy_breathing_cc0.mp3": "3a6ddf5c6a97f63e633c53f4c7dfeef1348682879c2e3071fdf076a614a15151",
}
NORMAL_SHA256 = "d3f1dfe47be702ad567bb197adac85fad695bd587553349bba64a6a15ade0adb"
RATE = 48000
DURATION = 15  # Matches EvaSuitAmbience's 300-tick clips and two-second overlap.
# Output start, source start, duration: align effort with the mask's breath cycles.
STRAINED_BREATHS = ((1.5, 10.4, 2.0), (6.0, 17.4, 2.3), (10.1, 23.8, 2.4))


def decode(source: Path, filters: str) -> array:
    samples = array("f", subprocess.check_output([
        "ffmpeg", "-v", "error", "-i", str(source), "-af", filters,
        "-f", "f32le", "-ac", "1", "-ar", str(RATE), "pipe:1",
    ]))
    if sys.byteorder != "little":
        samples.byteswap()
    return samples


def rms(samples: array) -> float:
    return math.sqrt(sum(x * x for x in samples) / len(samples))


def main() -> None:
    for name, expected in SOURCE_HASHES.items():
        if hashlib.sha256((SOURCES / name).read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Source changed: {name}; review its provenance before processing.")
    if hashlib.sha256(NORMAL.read_bytes()).hexdigest() != NORMAL_SHA256:
        raise SystemExit("Normal EVA source changed; review the loudness reference.")
    mask = decode(SOURCES / "gas_mask_breath_cc0.mp3",
                  "atrim=start=4.6:end=19.6,asetpts=PTS-STARTPTS,highpass=f=140,lowpass=f=3400")
    strain = decode(SOURCES / "scared_heavy_breathing_cc0.mp3",
                    "highpass=f=180,lowpass=f=3000,equalizer=f=1100:t=q:w=1.1:g=2")
    if len(mask) != DURATION * RATE:
        raise SystemExit("Mask window length does not match the runtime clip.")
    strain_gain = rms(mask) / rms(strain)
    processed = array("f")
    for i, sample in enumerate(mask):
        t = i / RATE
        value = sample
        for output_start, source_start, duration in STRAINED_BREATHS:
            local = t - output_start
            if 0 <= local < duration:
                weight = math.sin(math.pi * local / duration) ** 2
                strained = strain[int((source_start + local) * RATE)] * strain_gain
                # Duck the mask within each inhalation to blend one performance.
                value = sample * (1 - 0.65 * weight) + strained * 0.90 * weight
        value = 0.85 * value + 0.15 * math.tanh(value * 2) / 2
        # Complementary two-second ramps match the client's overlapping clips.
        fade = min(1.0, t / 2, (DURATION - t) / 2)
        processed.append(value * fade)

    target_rms = rms(decode(NORMAL, "anull"))
    gain = min(target_rms / rms(processed), 0.70 / max(abs(x) for x in processed))
    for i in range(len(processed)):
        processed[i] *= gain
    if sys.byteorder != "little":
        processed.byteswap()
    subprocess.run([
        "ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1",
        "-i", "pipe:0", "-map_metadata", "-1", "-af", "pan=stereo|c0=c0|c1=c0",
        "-c:a", "vorbis", "-strict", "experimental", "-q:a", "4", str(OUTPUT),
    ], input=processed.tobytes(), check=True)
    print(f"Created {OUTPUT.relative_to(ROOT)} ({DURATION}s, gain {gain:.3f})")


if __name__ == "__main__":
    main()
