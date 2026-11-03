#!/usr/bin/env python3
"""Sequence CC0 mask breathing, strained inhales, a cough and recovery gasps."""
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
    "male_gasp_cc0.mp3": "5d6b4cf6c9eafebd4ec7d33159629e4895ff0b7ba6ee27f2df9a8fb75223ed87",
    "strong_double_cough_cc0.mp3": "1dba44a22290fd52c52b19b99619a0a72372fbc71ffeaf244157926c46870c88",
}
NORMAL_SHA256 = "d3f1dfe47be702ad567bb197adac85fad695bd587553349bba64a6a15ade0adb"
RATE = 48000
DURATION = 42
# Source, start, duration. Takes alternate; only one contributes to each sample.
SEGMENTS = (
    ("mask", 5.0, 4.1), ("strain", 17.4, 3.3), ("mask", 9.1, 4.2),
    ("cough", 0.02, 1.0), ("pause", 0, 0.14), ("gasp", 0.03, 0.6),
    ("strain", 23.3, 3.4), ("mask", 14.0, 4.5), ("strain", 8.0, 3.5),
    ("mask", 18.8, 4.6), ("gasp", 0.03, 0.6), ("strain", 27.0, 3.3),
    ("mask", 23.3, 4.8), ("strain", 30.0, 3.96),
)


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
                  "highpass=f=180,lowpass=f=3000")
    strain = decode(SOURCES / "scared_heavy_breathing_cc0.mp3",
                    "highpass=f=180,lowpass=f=3000,equalizer=f=1100:t=q:w=1.1:g=2")
    recordings = {
        "mask": mask,
        "strain": strain,
        "cough": decode(SOURCES / "strong_double_cough_cc0.mp3",
                        "highpass=f=180,lowpass=f=3000"),
        "gasp": decode(SOURCES / "male_gasp_cc0.mp3",
                       "highpass=f=180,lowpass=f=3000,equalizer=f=1100:t=q:w=1.1:g=2"),
    }
    # Match the mask's tone/level; keep the cough restrained and the inhale clear.
    gains = {name: rms(mask) / rms(samples) for name, samples in recordings.items()}
    gains["cough"] *= 0.80
    gains["gasp"] *= 1.10
    processed = array("f")
    for source, start, duration in SEGMENTS:
        count = round(duration * RATE)
        if source == "pause":
            processed.extend(array("f", [0.0]) * count)
            continue
        recording = recordings[source]
        source_gain = gains[source]
        first = round(start * RATE)
        segment = recording[first:first + count]
        if len(segment) != count:
            raise SystemExit("A selected breathing segment exceeds its recording.")
        fade_seconds = 0.015 if source == "cough" else 0.025 if source == "gasp" else 0.08
        for i, sample in enumerate(segment):
            value = sample * source_gain
            # Common gentle compression keeps sharper inhales audible without
            # forcing every other breath quieter to accommodate their peaks.
            if abs(value) > 0.08:
                value = math.copysign(0.08 + (abs(value) - 0.08) / 3, value)
            value = 0.85 * value + 0.15 * math.tanh(value * 2) / 2
            # Separate short fades, not a crossfade: adjacent takes never overlap.
            fade = min(1.0, i / (fade_seconds * RATE), (count - 1 - i) / (fade_seconds * RATE))
            processed.append(value * fade)
    if len(processed) != DURATION * RATE:
        raise SystemExit("Selected segments do not total the intended loop length.")

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
