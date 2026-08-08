#!/usr/bin/env python3
"""Derive transparent, directionally lit Moon assets from Minecraft's atlas."""

from pathlib import Path
import sys

from PIL import Image


SOURCE_TILE_SIZE = 32
SCALE = 8
TILE_SIZE = SOURCE_TILE_SIZE * SCALE
TRANSPARENT = (0, 0, 0, 0)


def lunar_mask(tile: Image.Image) -> Image.Image:
    """Select the actual full-Moon pixels, excluding its opaque black tile."""
    luminance = tile.convert("L")
    return luminance.point(lambda value: 255 if value >= 24 else 0)


def piece_masks(material: Image.Image) -> list[Image.Image]:
    pieces = [Image.new("L", material.size, 0) for _ in range(3)]
    for y in range(TILE_SIZE):
        native_y = y / SCALE
        for x in range(TILE_SIZE):
            if material.getpixel((x, y)) == 0:
                continue
            native_x = x / SCALE
            jitter = 0.08 * ((y // 6) % 3 - 1)
            upper_edge = 17.55 + 0.30 * (native_y - 12.0) + jitter
            lower_edge = 18.55 - 0.28 * (native_y - 16.0) - jitter
            if native_y < 16.0 and native_x > upper_edge:
                pieces[0].putpixel((x, y), 255)
            elif native_y >= 16.0 and native_x > lower_edge:
                pieces[1].putpixel((x, y), 255)
            elif native_y > 18.65 + jitter and native_x < 14.5:
                pieces[2].putpixel((x, y), 255)
    return pieces


def transparent_full_moon(tile: Image.Image) -> tuple[Image.Image, Image.Image]:
    material = lunar_mask(tile)
    result = Image.new("RGBA", tile.size, TRANSPARENT)
    result.paste(tile, (0, 0), material)
    return result, material


def damage_tile(tile: Image.Image, stage: int) -> Image.Image:
    damaged, material = transparent_full_moon(tile)
    pieces = piece_masks(material)
    dark_cells = {
        1: {(18, 14): 0.52, (15, 17): 0.66, (17, 18): 0.72},
        2: {(17, 13): 0.58, (14, 18): 0.56},
        3: {(13, 15): 0.48, (16, 16): 0.62, (18, 18): 0.42},
        4: {(14, 13): 0.42, (15, 19): 0.36, (17, 15): 0.48},
    }
    active_dark_cells: dict[tuple[int, int], float] = {}
    for damage_stage in range(1, stage + 1):
        active_dark_cells.update(dark_cells[damage_stage])

    for y in range(TILE_SIZE):
        native_y = y / SCALE
        for x in range(TILE_SIZE):
            if material.getpixel((x, y)) == 0:
                continue
            native_x = x / SCALE
            factor = active_dark_cells.get((int(native_x), int(native_y)))
            if factor is not None:
                red, green, blue, alpha = damaged.getpixel((x, y))
                damaged.putpixel((x, y), (
                    round(red * factor),
                    round(green * factor),
                    round(blue * factor),
                    alpha,
                ))

            chipped = (
                stage >= 1
                and native_y < 12.35
                and 15.3 < native_x < 16.15
            ) or (
                stage >= 3
                and native_x < 12.55
                and 14.3 < native_y < 16.4
            ) or (
                stage >= 3
                and native_y > 19.25
                and 15.0 < native_x < 16.2
            ) or (
                stage >= 4
                and native_x < 12.8
                and 17.2 < native_y < 18.6
            )
            if chipped:
                damaged.putpixel((x, y), TRANSPARENT)

    # Fragments are rendered independently in-game, so remove them from the
    # primary silhouette instead of baking black seams or duplicate pieces.
    for index in range(min(3, max(0, stage - 1))):
        damaged.paste(TRANSPARENT, (0, 0, TILE_SIZE, TILE_SIZE), pieces[index])
    return damaged


def build_atlas(vanilla: Image.Image, stage: int) -> Image.Image:
    scaled = vanilla.resize(
        (vanilla.width * SCALE, vanilla.height * SCALE),
        Image.Resampling.NEAREST,
    )
    atlas = Image.new("RGBA", scaled.size, TRANSPARENT)
    full_moon_box = (0, 0, TILE_SIZE, TILE_SIZE)
    source_tile = scaled.crop(full_moon_box)
    moon = (transparent_full_moon(source_tile)[0]
            if stage == 0 else damage_tile(source_tile, stage))
    atlas.paste(moon, (0, 0), moon)
    return atlas


def build_fragment(vanilla: Image.Image, index: int) -> Image.Image:
    scaled = vanilla.resize(
        (vanilla.width * SCALE, vanilla.height * SCALE),
        Image.Resampling.NEAREST,
    )
    tile = scaled.crop((0, 0, TILE_SIZE, TILE_SIZE))
    moon, material = transparent_full_moon(tile)
    mask = piece_masks(material)[index]
    fragment = Image.new("RGBA", tile.size, TRANSPARENT)
    fragment.paste(moon, (0, 0), mask)
    return fragment


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(
            "usage: prepare_post_maeve_moon_textures.py "
            "<vanilla moon_phases.png> <output directory>"
        )

    source = Path(sys.argv[1])
    output = Path(sys.argv[2])
    output.mkdir(parents=True, exist_ok=True)
    vanilla = Image.open(source).convert("RGBA")
    if vanilla.size != (128, 64):
        raise ValueError(f"expected 128x64 vanilla Moon atlas, got {vanilla.size}")

    names = {
        0: "post_maeve_moon_clean.png",
        1: "post_maeve_moon_stressed.png",
        2: "post_maeve_moon_calving.png",
        3: "post_maeve_moon_ragged.png",
        4: "post_maeve_moon_ringing.png",
    }
    for stage, name in names.items():
        build_atlas(vanilla, stage).save(output / name, optimize=True)
    for index in range(3):
        build_fragment(vanilla, index).save(
            output / f"post_maeve_moon_fragment_{index + 1}.png",
            optimize=True,
        )


if __name__ == "__main__":
    main()
