#!/usr/bin/env python3
"""Cut the ORSA Geiger Counter sounds from a real CC0 recording.
Source: Freesound 555934 "Geiger Counter Hotspot (Sweeping)" by cookies+policy, CC0 1.0 (public domain),
https://freesound.org/people/cookies+policy/sounds/555934/ . Download its HQ preview, then run:
python3 tools/audio_sources/hearthstone/extract_geiger.py --source 555934_10713160-hq.mp3
Writes five single clicks from the sparse tail and a seamless dense loop from the hotspot peak."""
import argparse, math, struct, subprocess, tempfile, wave
from pathlib import Path

OUT = Path(__file__).resolve().parents[3] / "src/main/resources/assets/frozendawn/sounds/blocks/reactor"
SOURCE_SHA256 = "3486cf93eeddb0a954f30eead174163825ebfd177f7c4e7d7dc2024f4918da06"
RATE = 44100


def load(path):
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "src.wav"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(path), "-ac", "1", "-ar", str(RATE), str(wav)], check=True)
        with wave.open(str(wav)) as w:
            n = w.getnframes(); return [v / 32768 for v in struct.unpack("<%dh" % n, w.readframes(n))]


def save(name, buf, peak_to=0.9):
    peak = max(abs(v) for v in buf) or 1
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / f"{name}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
            w.writeframes(b"".join(struct.pack("<h", int(v / peak * peak_to * 32767)) for v in buf))
        subprocess.run(["oggenc", "-Q", "-q", "6", "-s", "1", str(wav), "-o", str(OUT / f"{name}.ogg")], check=True)
    print(OUT / f"{name}.ogg")


def onsets(x, threshold):
    found, last = [], -10**9
    for i, v in enumerate(x):
        if abs(v) > threshold and i - last > int(0.012 * RATE): found.append(i); last = i
    return found


def main():
    p = argparse.ArgumentParser(); p.add_argument("--source", type=Path, required=True); a = p.parse_args()
    import hashlib
    digest = hashlib.sha256(a.source.read_bytes()).hexdigest()
    if digest != SOURCE_SHA256: raise SystemExit(f"Unexpected source {digest}")
    x = load(a.source); peak = max(abs(v) for v in x)
    hits = onsets(x, 0.25 * peak)
    # Isolated clicks (60 ms of quiet either side) from the sparse tail, strongest first, varied in level.
    isolated = [o for k, o in enumerate(hits) if o > 12 * RATE
                and (k == 0 or o - hits[k - 1] > 0.06 * RATE) and (k + 1 == len(hits) or hits[k + 1] - o > 0.06 * RATE)]
    isolated.sort(key=lambda o: -max(abs(v) for v in x[o:o + 200]))
    for n, o in enumerate(isolated[:5], 1):
        start, length = o - int(0.002 * RATE), int(0.030 * RATE)
        clip = x[start:start + length]
        fade = int(0.008 * RATE)
        clip = [v * (1 if i < length - fade else (length - i) / fade) for i, v in enumerate(clip)]
        save(f"geiger_{n}", clip)
    # Seamless dense loop from the hotspot (about 45 clicks per second): crossfade the tail into the head.
    start, length, xf = int(9.0 * RATE), int(2.0 * RATE), int(0.15 * RATE)
    seg = x[start:start + length + xf]
    loop = seg[:length]
    for i in range(xf):
        t = i / xf
        loop[i] = seg[length + i] * (1 - t) + seg[i] * t
    save("geiger_dense", loop, 0.8)


if __name__ == "__main__":
    main()
