#!/usr/bin/env python3
"""Prepare CC0-derived Blind Architect audition assets; no runtime registration."""
from array import array
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import subprocess
import sys
import wave

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
RATE = 48000
SOURCE_INFO = {
    "370869_3531538-hq.mp3": {
        "creator": "Xemptful",
        "page": "https://freesound.org/people/Xemptful/sounds/370869/",
        "download": "https://cdn.freesound.org/previews/370/370869_3531538-hq.mp3",
        "original_filename": "370869__xemptful__mouth-clicks.wav",
    },
    "573148_12947664-hq.mp3": {
        "creator": "fleurescence",
        "page": "https://freesound.org/people/fleurescence/sounds/573148/",
        "download": "https://cdn.freesound.org/previews/573/573148_12947664-hq.mp3",
        "original_filename": "573148__fleurescence__tongue-click.wav",
    },
}
# Crops include each complete transient plus its immediate recorded decay.
VARIANTS = [
    ("click_1", "370869_3531538-hq.mp3", .115, .285, 1.00, .045),
    ("click_2", "370869_3531538-hq.mp3", .605, .780, .96, .045),
    ("click_3", "370869_3531538-hq.mp3", .985, 1.170, 1.03, .040),
    ("click_4", "573148_12947664-hq.mp3", .000, .155, .94, .055),
    ("click_5", "370869_3531538-hq.mp3", .115, .285, .91, .080),
    ("click_6", "573148_12947664-hq.mp3", .000, .155, 1.02, .045),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def decode(path):
    pcm = array("f", subprocess.check_output([
        "ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(RATE),
        "-f", "f32le", "-",
    ]))
    if sys.byteorder != "little":
        pcm.byteswap()
    if not pcm or not all(math.isfinite(x) for x in pcm):
        raise ValueError(f"Invalid decoded source: {path}")
    return pcm


def write_wav(path, samples):
    if any(not math.isfinite(x) or abs(x) > .99 for x in samples):
        raise ValueError(f"Non-finite or clipping PCM: {path}")
    pcm = array("h", (round(x * 32767) for x in samples))
    if sys.byteorder != "little":
        pcm.byteswap()
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(pcm.tobytes())


def make_click(source, start, end, speed, resonance):
    crop = source[round(start * RATE):round(end * RATE)]
    stretched = []
    for i in range(math.floor((len(crop) - 1) / speed)):
        at = i * speed
        lo = int(at)
        stretched.append(crop[lo] * (1 - (at - lo)) + crop[lo + 1] * (at - lo))
    # Remove DC/low rumble and soften extreme upper frequencies with first-order filters.
    low = smooth = 0.0
    high_alpha = 1 - math.exp(-math.tau * 110 / RATE)
    low_alpha = 1 - math.exp(-math.tau * 9000 / RATE)
    clean = []
    for value in stretched:
        low += high_alpha * (value - low)
        smooth += low_alpha * (value - low - smooth)
        clean.append(smooth)
    peak = max(abs(x) for x in clean)
    if peak < .001:
        raise ValueError("Selected crop contains no usable click")
    attack_at = max(range(len(clean)), key=lambda i: abs(clean[i]))
    # Retain the recorded shape; add only a faint decaying, wordless hollow resonance.
    result = []
    duration = len(clean) / RATE + .14
    for i in range(round(duration * RATE)):
        value = clean[i] if i < len(clean) else 0.0
        for delay, gain in ((.019, .070), (.033, .035)):
            at = i - round(delay * RATE)
            if 0 <= at < len(clean):
                value += clean[at] * gain
        t = (i - attack_at) / RATE
        if t >= 0:
            value += resonance * peak * math.exp(-t * 38) * (
                math.sin(math.tau * 410 * t) + .28 * math.sin(math.tau * 1060 * t)
            )
        # Sub-millisecond attack fade and 15 ms end fade avoid trim discontinuities.
        fade = min(1.0, i / 24, (round(duration * RATE) - 1 - i) / 720)
        result.append(value * max(0.0, fade))
    scale = .68 / max(abs(x) for x in result)
    return [x * scale for x in result]


def main():
    output = HERE / "processed"
    output.mkdir(exist_ok=True)
    preview = ROOT / "build/blind-architect-audio"
    preview.mkdir(parents=True, exist_ok=True)
    manifest = {
        "status": "Prepared audition assets; not registered in runtime; human listening pending",
        "license": "CC0-1.0",
        "license_url": "https://creativecommons.org/publicdomain/zero/1.0/",
        "tools": {name: subprocess.check_output(cmd, text=True).splitlines()[0] for name, cmd in {
            "ffmpeg": ["ffmpeg", "-version"], "oggenc": ["oggenc", "--version"],
        }.items()},
        "inputs": [], "outputs": [], "audition_order": [],
    }
    sources = {}
    for name, info in SOURCE_INFO.items():
        path = HERE / "raw" / name
        metadata = json.loads(subprocess.check_output([
            "ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path),
        ]))
        manifest["inputs"].append({
            **info, "path": str(path.relative_to(ROOT)), "sha256": sha(path),
            "retrieved_utc": datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat(),
            "encoding": "Official public high-quality MP3 preview, not original WAV",
            "metadata": metadata,
        })
        sources[name] = decode(path)
    clicks = []
    for index, (name, source, start, end, speed, resonance) in enumerate(VARIANTS, 1):
        samples = make_click(sources[source], start, end, speed, resonance)
        wav = preview / f"{name}.wav"
        ogg = output / f"{name}.ogg"
        write_wav(wav, samples)
        subprocess.run(["oggenc", "-Q", "-q", "4", "--serial", str(26100100 + index),
                        "-o", str(ogg), str(wav)], check=True)
        decoded = decode(ogg)
        peak = max(abs(x) for x in decoded)
        if peak >= .99:
            raise ValueError(f"Encoded audio clips: {ogg}")
        manifest["outputs"].append({
            "name": name, "source": source, "crop_seconds": [start, end],
            "speed_ratio": speed, "resonance_gain": resonance,
            "path": str(ogg.relative_to(ROOT)), "sha256": sha(ogg),
            "duration_seconds": len(decoded) / RATE, "decoded_peak": peak,
            "decoded_rms": math.sqrt(sum(x*x for x in decoded) / len(decoded)),
            "channels": 1, "sample_rate": RATE,
        })
        manifest["audition_order"].append(name)
        clicks.append(list(decoded))
    # Each variant appears twice. Reels decode the actual Ogg files, not pre-encode PCM.
    reel = [0.0] * round(.4 * RATE)
    for click in clicks:
        reel += click + [0.0] * round(.35 * RATE) + click + [0.0] * round(.9 * RATE)
    write_wav(preview / "click-audition.wav", reel)
    # Timing illustration only; production sensing/AI does not exist yet.
    cadence = [0.0] * round(.4 * RATE)
    for intervals in ([2.8, 2.8], [1.7, 1.7], [1.05, 1.05, 1.05], [.6, .6, .6]):
        for i, interval in enumerate(intervals):
            click = clicks[i % len(clicks)]
            cadence += click + [0.0] * max(0, round(interval * RATE) - len(click))
        cadence += [0.0] * RATE
    write_wav(preview / "cadence-example.wav", cadence)
    (HERE / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    for entry in manifest["outputs"]:
        print(f"{entry['name']}: {entry['duration_seconds']:.3f}s, mono 48 kHz, "
              f"peak {entry['decoded_peak']:.3f}")
    print(f"Audition: {preview / 'click-audition.wav'}")
    print(f"Timing illustration: {preview / 'cadence-example.wav'}")


if __name__ == "__main__":
    main()
