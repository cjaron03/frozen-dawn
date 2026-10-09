#!/usr/bin/env python3
"""Independent thermal baseline using Python's erfc and a frozen pre-patch phase curve."""
import argparse
import csv
import math
from pathlib import Path

BOUNDS = (0, .05, .12, .22, .34, .46, .6, 1)
OFFSETS = (0, 0, -10, -25, -45, -70, -120, -273)
PRESETS = {"default": (1, 1, 8000), "cinematic": (2/3, 1.5, 8000), "brutal": (4/3, .5, 12000)}


def surface(progress, scale=1):
    for i in range(1, len(BOUNDS)):
        if progress <= BOUNDS[i]:
            base = OFFSETS[i-1] + (progress-BOUNDS[i-1])/(BOUNDS[i]-BOUNDS[i-1])*(OFFSETS[i]-OFFSETS[i-1])
            break
    else:
        base = OFFSETS[-1]
    rebound = 0
    for boundary in BOUNDS[1:-1]:
        distance = abs(progress-boundary)
        if distance < .03:
            rebound = 3*(1-distance/.03)
            break
    return base*scale+rebound


def ground(y, progress, scale, geothermal, diffusivity, steps=20000):
    z = min(128, max(0, 64-y))
    if not z:
        return surface(progress, scale)
    result = .30*z*geothermal
    previous = 0
    count = round(progress*steps)
    for k in range(1, count+1):
        now = surface(k/steps, scale)
        lag = progress-(k-.5)/steps
        result += (now-previous)*math.erfc(z/(2*math.sqrt(diffusivity*lag)))
        previous = now
    return result


def old_depth(y):
    return {64: 0, 32: 10, 0: 25, -32: 55, -64: 80}[y]


def write_reference(destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("w", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("y", "progress", "surfaceScale", "geothermalStrength", "diffusivity", "referenceTemperature"))
        for name in ("default", "brutal", "cinematic"):
            scale, geothermal, a = PRESETS[name]
            for progress in (.46, .60, .85, 1):
                for y in (64, 32, 0, -32, -64):
                    writer.writerow((y, progress, scale, geothermal, a, ground(y, progress, scale, geothermal, a)))


def write_comparison(destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("w", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("preset", "y", "progress", "before", "after", "difference"))
        for name, (scale, geothermal, a) in PRESETS.items():
            for progress in (.46, .60, .85, 1):
                for y in (64, 32, 0, -32, -64):
                    before = surface(progress, scale)+old_depth(y)*geothermal
                    after = ground(y, progress, scale, geothermal, a)
                    writer.writerow((name,y,progress,before,after,after-before))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path)
    parser.add_argument("--comparison", type=Path)
    args = parser.parse_args()
    if not args.reference and not args.comparison:
        parser.error("Choose --reference and/or --comparison")
    if args.reference:
        write_reference(args.reference)
    if args.comparison:
        write_comparison(args.comparison)
