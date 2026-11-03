#!/usr/bin/env python3
"""Prepare a human throat-click audition, keeping the earlier dry-click bank."""
from datetime import datetime, timezone
import json
import math
import subprocess

from generate_clicks import HERE, ROOT, RATE, SOURCE_INFO, decode, sha, write_wav

THROAT = "688417_7724198-hq.mp3"
INPUTS = {
    **SOURCE_INFO,
    THROAT: {
        "creator": "Fupicat",
        "page": "https://freesound.org/people/Fupicat/sounds/688417/",
        "download": "https://cdn.freesound.org/previews/688/688417_7724198-hq.mp3",
        "original_filename": "688417__fupicat__weird-throat-clicking.ogg",
    },
}
MOUTH = [
    ("370869_3531538-hq.mp3", .115, .285),
    ("370869_3531538-hq.mp3", .605, .780),
    ("370869_3531538-hq.mp3", .985, 1.170),
    ("573148_12947664-hq.mp3", .000, .155),
]
# Each short burst fits inside the design's fastest 0.5-second pulse interval.
# The second transients decorate one pulse; they are not extra sensing returns.
PULSES = [
    ("throat_click_1", 2.10, 2.46, 1.10, [(0, .015, .95), (3, .142, .35)]),
    ("throat_click_2", 3.70, 4.07, 1.14, [(1, .008, 1.00), (2, .172, .40)]),
    ("throat_click_3", 5.58, 5.94, 1.04, [(3, .012, .92), (0, .115, .34)]),
    ("throat_click_4", 8.02, 8.41, 1.16, [(2, .015, 1.00), (3, .190, .30)]),
    ("throat_click_5", 14.02, 14.39, 1.09, [(0, .009, .95), (1, .120, .32)]),
    ("throat_click_6", 17.56, 17.93, 1.12, [(3, .010, 1.00), (2, .157, .38)]),
]
VOCALS = [
    ("throat_idle_1", 9.46, 10.29, 1.06, .36,
     [(0, .080, .68), (3, .315, .26)], .055),
    ("throat_search_1", 13.76, 14.68, 1.16, .55,
     [(1, .030, .83), (3, .172, .42), (0, .442, .30)], .085),
    ("throat_lock_1", 17.35, 18.48, 1.23, .66,
     [(3, .020, 1.00), (2, .137, .51), (1, .335, .38)], .085),
]


def cut(source, start, end, speed, highpass, lowpass, peak):
    crop = source[round(start * RATE):round(end * RATE)]
    count = math.floor((len(crop) - 1) / speed)
    low = smooth = 0.0
    hp = 1 - math.exp(-math.tau * highpass / RATE)
    lp = 1 - math.exp(-math.tau * lowpass / RATE)
    result = []
    for i in range(count):
        at = i * speed
        lo = int(at)
        value = crop[lo] * (1 - at + lo) + crop[lo + 1] * (at - lo)
        low += hp * (value - low)
        smooth += lp * (value - low - smooth)
        fade = max(0, min(1, i / (.006 * RATE), (count - 1 - i) / (.025 * RATE)))
        result.append(smooth * fade)
    scale = peak / max(abs(x) for x in result)
    return [x * scale for x in result]


def overlay(output, layer, offset, gain):
    start = round(offset * RATE)
    for i, value in enumerate(layer):
        if start + i >= len(output):
            break
        output[start + i] += gain * value


def finish(samples):
    # Mild saturation adds body without an oscillator or a mechanical repeating gate.
    samples = [math.tanh(x * 1.35) for x in samples]
    peak = max(abs(x) for x in samples)
    result = []
    for i, value in enumerate(samples):
        fade = max(0, min(1, i / 48, (len(samples) - 1 - i) / 960))
        result.append(value * (.68 / peak) * fade)
    return result


def make_burst(sources, start, end, speed, throat_peak, accents, tail):
    voice = cut(sources[THROAT], start, end, speed, 140, 7600, throat_peak)
    result = voice + [0.0] * round(tail * RATE)
    # A quiet lower copy of the same performance broadens the throat resonance.
    low_voice = cut(sources[THROAT], start, end, speed * .82, 190, 2400, throat_peak)
    overlay(result, low_voice, .007, .14)
    for index, offset, gain in accents:
        source, mouth_start, mouth_end = MOUTH[index]
        click = cut(sources[source], mouth_start, mouth_end, 1.08, 340, 10500, .58)
        overlay(result, click, offset, gain)
    # Small reflections stay attached to the voice; large cave reverb belongs to the world.
    dry = list(result)
    overlay(result, dry, .021, .045)
    overlay(result, dry, .038, .025)
    return finish(result)


def main():
    output = HERE / "processed"
    preview = ROOT / "build/blind-architect-audio"
    output.mkdir(exist_ok=True)
    preview.mkdir(parents=True, exist_ok=True)
    sources = {name: decode(HERE / "raw" / name) for name in INPUTS}
    manifest = {
        "status": "Prepared throat-performance bank; see selection.json for owner acceptance; no runtime registration",
        "license": "CC0-1.0 source layers; original processing",
        "reference": "https://www.naughtydog.com/blog/the_last_of_us_part_i_hbo_tv_show_clickers",
        "reference_role": "Sound-design research only; no reference audio used",
        "tools": {name: subprocess.check_output(cmd, text=True).splitlines()[0] for name, cmd in {
            "ffmpeg": ["ffmpeg", "-version"], "oggenc": ["oggenc", "--version"],
        }.items()},
        "inputs": [], "outputs": [],
        "processing": "Natural throat performance, resampling, first-order high/low pass, "
                      "quiet lower throat layer, irregular mouth accents, 21/38 ms reflections, "
                      "tanh saturation, fades and peak normalization. No generated voice.",
    }
    for name, info in INPUTS.items():
        path = HERE / "raw" / name
        manifest["inputs"].append({
            **info, "path": str(path.relative_to(ROOT)), "sha256": sha(path),
            "license_url": "https://creativecommons.org/publicdomain/zero/1.0/",
            "retrieved_utc": datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat(),
            "encoding": "Official shared high-quality MP3 preview, not original download",
            "metadata": json.loads(subprocess.check_output([
                "ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path),
            ])),
        })
    bank = {}
    specs = [(name, start, end, speed, .46, accents, .065, "echo_pulse")
             for name, start, end, speed, accents in PULSES]
    specs += [(*spec, "vocal_accent") for spec in VOCALS]
    for index, (name, start, end, speed, voice_gain, accents, tail, role) in enumerate(specs, 1):
        samples = make_burst(sources, start, end, speed, voice_gain, accents, tail)
        wav = preview / f"{name}.wav"
        ogg = output / f"{name}.ogg"
        write_wav(wav, samples)
        subprocess.run(["oggenc", "-Q", "-q", "4", "--serial", str(26100200 + index),
                        "-o", str(ogg), str(wav)], check=True)
        decoded = decode(ogg)
        peak = max(abs(x) for x in decoded)
        assert peak < .99, f"Clipping encoded audio: {name}"
        assert role != "echo_pulse" or len(decoded) / RATE < .5
        bank[name] = list(decoded)
        manifest["outputs"].append({
            "name": name, "role": role, "path": str(ogg.relative_to(ROOT)), "sha256": sha(ogg),
            "throat_source": THROAT, "throat_crop_seconds": [start, end], "speed_ratio": speed,
            "throat_peak_before_mix": voice_gain, "mouth_accents": [
                {"source": MOUTH[i][0], "crop_seconds": list(MOUTH[i][1:]),
                 "offset_seconds": at, "gain": gain, "speed_ratio": 1.08}
                for i, at, gain in accents],
            "tail_seconds": tail, "duration_seconds": len(decoded) / RATE,
            "decoded_peak": peak, "decoded_rms": math.sqrt(sum(x*x for x in decoded) / len(decoded)),
            "sample_rate": RATE, "channels": 1,
        })
    # First: earlier dry click twice. Then: all six new bursts, then idle/search/lock accents.
    reel = [0.0] * round(.3 * RATE)
    old = list(decode(output / "click_1.ogg"))
    for _ in range(2):
        reel += old + [0.0] * round(.55 * RATE)
    reel += [0.0] * RATE
    for name in bank:
        reel += bank[name] + [0.0] * round(.8 * RATE)
    reel_path = preview / "throat-comparison.wav"
    write_wav(reel_path, reel)
    manifest["audition"] = {
        "path": str(reel_path.relative_to(ROOT)), "sha256": sha(reel_path),
        "order": ["click_1", "click_1", *bank],
        "note": "Earlier dry click twice, six revised pulses, idle/search/lock vocal accents",
    }
    (HERE / "throat-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    for entry in manifest["outputs"]:
        print(f"{entry['name']}: {entry['duration_seconds']:.3f}s, decoded peak {entry['decoded_peak']:.3f}")
    print(f"Comparison: {reel_path}")


if __name__ == "__main__":
    main()
