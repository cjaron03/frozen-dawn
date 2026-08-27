#!/usr/bin/env python3
"""Build the Resonant texture and recorded structural sound palette."""

from __future__ import annotations

import random
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/audio_sources/resonant"
SOUND_OUT = ROOT / "src/main/resources/assets/frozendawn/sounds/entity/resonant"
TEXTURE_OUT = ROOT / "src/main/resources/assets/frozendawn/textures/entity/resonant.png"


def render_sound(name: str, duration: float,
                 clips: list[tuple[str, float, float, float]]) -> None:
    """Mix time-sliced field recordings while preserving their physical transients."""
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
    for source_name, start, _volume, _pitch in clips:
        command.extend(["-ss", str(start), "-t", str(duration + 0.5),
                        "-i", str(SOURCE / source_name)])

    chains: list[str] = []
    labels: list[str] = []
    for index, (_source_name, _start, volume, pitch) in enumerate(clips):
        label = f"a{index}"
        labels.append(f"[{label}]")
        chains.append(
            f"[{index}:a]aformat=sample_rates=44100:channel_layouts=mono,"
            f"asetrate={44100 * pitch:.2f},aresample=44100,"
            f"atrim=0:{duration},apad=whole_dur={duration},"
            f"volume={volume}[{label}]"
        )
    chains.append(
        "".join(labels)
        + f"amix=inputs={len(labels)}:normalize=0:dropout_transition=0,"
          f"highpass=f=35,lowpass=f=7200,"
          f"afade=t=in:st=0:d=0.025,afade=t=out:st={max(0.05, duration - 0.13)}:d=0.13,"
          "loudnorm=I=-16:TP=-1.0:LRA=7,"
          "alimiter=limit=0.92:level=false[out]"
    )
    SOUND_OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as wave_file:
        command.extend(["-filter_complex", ";".join(chains),
                        "-map", "[out]", "-t", str(duration), wave_file.name])
        subprocess.run(command, check=True)
        subprocess.run(["oggenc", "-Q", "-q", "6", "-o",
                        str(SOUND_OUT / f"{name}.ogg"), wave_file.name], check=True)


def add_noise(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int],
              seed: int, base: tuple[int, int, int], alpha: int,
              spread: int = 22) -> None:
    rng = random.Random(seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            variation = rng.randint(-spread, spread)
            draw.point((x, y), fill=(
                max(0, min(255, base[0] + variation)),
                max(0, min(255, base[1] + variation)),
                max(0, min(255, base[2] + variation)),
                alpha,
            ))


def texture() -> None:
    image = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Every occupied UV island receives cold, dirty material. The different
    # values keep the long silhouette readable without cyan or emissive light.
    islands = [
        ((0, 0, 29, 17), 11, (126, 133, 132), 220),
        ((30, 0, 61, 18), 12, (161, 167, 164), 224),
        ((64, 0, 88, 18), 13, (55, 59, 59), 230),
        ((0, 24, 15, 49), 21, (92, 99, 98), 220),
        ((16, 24, 33, 34), 22, (151, 157, 154), 226),
        ((34, 24, 47, 40), 23, (25, 29, 29), 238),
        ((48, 24, 58, 42), 24, (186, 191, 185), 230),
        ((0, 50, 35, 61), 31, (180, 185, 180), 228),
        ((0, 64, 47, 96), 41, (102, 109, 108), 218),
        ((50, 64, 63, 89), 42, (91, 98, 97), 206),
        ((64, 64, 75, 91), 43, (78, 85, 84), 154),
        ((76, 64, 89, 89), 44, (96, 103, 102), 206),
        ((90, 64, 103, 91), 45, (73, 80, 79), 154),
    ]
    for box, seed, base, alpha in islands:
        add_noise(draw, box, seed, base, alpha)

    # No face while listening: both visible head faces are hollowed rather than
    # given eyes. The brow remains pale enough to resolve through a wall.
    draw.rectangle((7, 7, 13, 14), fill=(18, 21, 21, 238))
    draw.rectangle((21, 7, 27, 14), fill=(24, 27, 27, 234))
    draw.rectangle((37, 8, 54, 10), fill=(196, 201, 194, 232))
    # Cavity stays almost black, interrupted by a few physical frost inclusions.
    draw.rectangle((35, 25, 42, 38), fill=(14, 18, 18, 242))
    draw.rectangle((36, 27, 37, 29), fill=(139, 147, 143, 210))
    draw.rectangle((40, 34, 42, 36), fill=(110, 119, 116, 202))
    # The forming jaw is stretched, empty, and visibly a different structure.
    draw.rectangle((66, 4, 83, 15), fill=(44, 48, 48, 232))
    draw.rectangle((69, 7, 80, 13), fill=(7, 10, 10, 245))

    # Lower limbs lose opacity toward the floor, giving them a vaporous end
    # without making the whole creature emissive or camera-facing.
    pixels = image.load()
    for x0, x1 in ((64, 75), (90, 103)):
        for y in range(64, 92):
            fade = int(158 - ((y - 64) / 27.0) * 105)
            for x in range(x0, x1 + 1):
                r, g, b, a = pixels[x, y]
                if a:
                    pixels[x, y] = (r, g, b, min(a, fade))

    TEXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(TEXTURE_OUT)


SOUNDS: dict[str, tuple[float, list[tuple[str, float, float, float]]]] = {
    "knock": (1.15, [
        ("wall_knock_cc0.mp3", 0.0, 1.55, 0.92),
        ("metal_groan_cc0.mp3", 0.3, 0.24, 0.70),
    ]),
    "phase": (2.7, [
        ("concrete_drag_cc0.mp3", 5.8, 1.35, 0.78),
        ("metal_groan_cc0.mp3", 2.9, 0.42, 0.64),
    ]),
    "pulse_windup": (1.75, [
        ("metal_groan_cc0.mp3", 4.5, 1.28, 0.82),
        ("ice_fracture_cc0.mp3", 11.0, 0.35, 0.72),
    ]),
    "pulse": (1.35, [
        ("metal_groan_cc0.mp3", 7.0, 1.15, 0.62),
        ("wall_knock_cc0.mp3", 0.15, 0.72, 0.78),
        ("ice_fracture_cc0.mp3", 19.0, 0.55, 0.67),
    ]),
    "breach": (1.55, [
        ("wall_knock_cc0.mp3", 0.0, 1.35, 0.78),
        ("ice_fracture_cc0.mp3", 4.0, 1.25, 0.82),
        ("concrete_drag_cc0.mp3", 16.0, 0.75, 0.72),
    ]),
    "grab": (1.2, [
        ("concrete_drag_cc0.mp3", 21.0, 1.4, 0.88),
        ("ice_fracture_cc0.mp3", 24.5, 0.62, 0.92),
    ]),
    "release": (0.95, [
        ("ice_fracture_cc0.mp3", 8.2, 1.4, 1.08),
        ("metal_groan_cc0.mp3", 1.0, 0.32, 0.82),
    ]),
    "hurt_1": (0.75, [
        ("ice_fracture_cc0.mp3", 2.0, 1.5, 1.02),
    ]),
    "hurt_2": (0.82, [
        ("ice_fracture_cc0.mp3", 15.5, 1.45, 0.91),
        ("concrete_drag_cc0.mp3", 10.0, 0.30, 0.83),
    ]),
    "death": (2.8, [
        ("ice_fracture_cc0.mp3", 26.0, 1.3, 0.78),
        ("metal_groan_cc0.mp3", 6.0, 0.72, 0.62),
    ]),
    "death_collapse": (2.25, [
        ("building_collapse_cc0.mp3", 0.0, 1.35, 0.78),
        ("stone_debris_cc0.mp3", 0.35, 1.2, 0.84),
        ("concrete_drag_cc0.mp3", 2.0, 0.55, 0.65),
        ("ice_fracture_cc0.mp3", 6.0, 0.48, 0.72),
    ]),
}


for sound_name, (sound_duration, sound_clips) in SOUNDS.items():
    render_sound(sound_name, sound_duration, sound_clips)
texture()
print(SOUND_OUT)
print(TEXTURE_OUT)
