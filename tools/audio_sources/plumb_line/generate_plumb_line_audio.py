#!/usr/bin/env python3
"""Original deterministic ORSA GPR-4 Plumb Line sounds; synthesized, no source recordings.

pulse     high-voltage transmitter discharge, sub-bass ground thump, gravel rumble and ground echoes
scan      ten seconds of transformer hum, cooling fan and receiver sampling ticks with faint echo pings
complete  descending receiver blips, relay release and fan spin-down
abort     capacitor whine collapsing as power sags, relay chatter and a contactor clunk

Usage from the repository root (writes .ogg for the game and .wav previews under build/):
  python3 tools/audio_sources/plumb_line/generate_plumb_line_audio.py [name ...]
"""
from array import array
import math
from pathlib import Path
import random
import subprocess
import sys
import tempfile
import wave

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = ROOT / "src/main/resources/assets/frozendawn/sounds/block/plumb_line"
PREVIEW = ROOT / "build/plumb-line-audio-preview"
RATE = 48000
TAU = math.tau


class OnePole:
    """One-pole low-pass; subtract from the input for a high-pass."""
    def __init__(self, cutoff):
        self.a = 1 - math.exp(-TAU * cutoff / RATE)
        self.y = 0.0

    def __call__(self, x):
        self.y += self.a * (x - self.y)
        return self.y


class BandPass:
    """RBJ constant-peak band-pass biquad."""
    def __init__(self, centre, q):
        w = TAU * centre / RATE
        alpha = math.sin(w) / (2 * q)
        a0 = 1 + alpha
        self.b0, self.b2 = alpha / a0, -alpha / a0
        self.a1, self.a2 = -2 * math.cos(w) / a0, (1 - alpha) / a0
        self.x1 = self.x2 = self.y1 = self.y2 = 0.0

    def __call__(self, x):
        y = self.b0 * x + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2
        self.x2, self.x1, self.y2, self.y1 = self.x1, x, self.y1, y
        return y


def env(t, attack, decay):
    return (1 - math.exp(-t / max(attack, 1e-6))) * math.exp(-t * decay)


def echoes(samples, taps):
    out = list(samples)
    for delay, gain, cutoff in taps:
        lp = OnePole(cutoff)
        d = round(delay * RATE)
        for i in range(len(out)):
            if i >= d:
                out[i] += gain * lp(samples[i - d])
    return out


def pulse():
    rng = random.Random(171017)
    n = round(RATE * 2.6)
    crack_bp, rumble_lp, rumble_slow, grit_bp = BandPass(4200, 1.4), OnePole(170), OnePole(12), BandPass(900, .8)
    phase = 0.0
    out = []
    for i in range(n):
        t = i / RATE
        noise = rng.uniform(-1, 1)
        # Transmitter discharge: an arcing crack a few milliseconds long, then the ground answers.
        crack = 1.4 * crack_bp(noise) * math.exp(-t * 520) + (.9 if i < 24 else 0) * (1 - i / 24)
        # The thump: a sine whose pitch falls from 74 Hz to 36 Hz, softly saturated so it reads on small speakers.
        freq = 36 + 38 * math.exp(-t * 11)
        phase += TAU * freq / RATE
        body = math.tanh(1.8 * (math.sin(phase) + .22 * math.sin(2 * phase))) * env(t, .004, 3.1)
        # Gravel and soil: low brown noise with a slow irregular grit, as the wave passes through the ground.
        brown = rumble_lp(noise)
        grit = .55 + .45 * math.tanh(6 * rumble_slow(rng.uniform(-1, 1)))
        rumble = 3.2 * brown * grit * env(t - .012, .03, 2.1) if t > .012 else 0
        debris = .25 * grit_bp(noise) * env(t - .05, .02, 7) if t > .05 else 0
        out.append(.55 * crack + 1.0 * body + rumble + debris)
    out = echoes(out, ((.118, .28, 260), (.241, .17, 200), (.392, .09, 150), (.61, .05, 120)))
    return out, .92


def scan():
    rng = random.Random(171018)
    n = round(RATE * 10.2)
    fan_bp, fan_lp, tick_bp, ping_bp = BandPass(620, .7), OnePole(1800), BandPass(1650, 6), BandPass(2650, 14)
    out = []
    ticks = [round(RATE * (.18 + k * .25)) for k in range(40)]
    pings = sorted(round(RATE * rng.uniform(.6, 9.6)) for _ in range(9))
    for i in range(n):
        t = i / RATE
        noise = rng.uniform(-1, 1)
        wobble = 1 + .04 * math.sin(TAU * .7 * t)
        hum = math.tanh(2.2 * (.5 * math.sin(TAU * 60 * t) + .35 * math.sin(TAU * 120 * t) + .18 * math.sin(TAU * 180 * t))) * .28 * wobble
        # Cooling fan: banded noise with a faint blade-pass flutter.
        fan = .9 * fan_lp(fan_bp(noise)) * (1 + .15 * math.sin(TAU * 41 * t))
        tick = sum(math.exp(-(i - k) / (RATE * .004)) for k in ticks if 0 <= i - k < RATE * .03)
        ping = sum(math.exp(-(i - k) / (RATE * .045)) for k in pings if 0 <= i - k < RATE * .25)
        sample = hum + fan + 2.2 * tick_bp(noise * tick + .6 * tick) + 1.1 * ping_bp(ping * math.sin(TAU * 2650 * t) * 2)
        fade = min(1, t / .3, (n / RATE - t) / .45)
        out.append(sample * fade)
    return out, .5


def complete():
    rng = random.Random(171019)
    n = round(RATE * 1.4)
    click_bp, fan_bp = BandPass(2400, 3), BandPass(620, .7)
    out = []
    blips = ((.02, 1320), (.15, 990), (.28, 740))
    for i in range(n):
        t = i / RATE
        noise = rng.uniform(-1, 1)
        sample = 0.0
        for start, freq in blips:
            u = t - start
            if 0 <= u < .12:
                sample += .55 * math.sin(TAU * freq * u) * env(u, .004, 30)
        u = t - .5  # relay release: a dry click with a short body knock
        if u >= 0:
            sample += 1.2 * click_bp(noise) * math.exp(-u * 400) + .5 * math.sin(TAU * 165 * u) * env(u, .001, 40)
        if t > .45:  # fan spinning down
            fan_bp_dyn = 1 - min(1, (t - .45) / .9)
            sample += .35 * fan_bp(noise) * fan_bp_dyn * fan_bp_dyn
        out.append(sample)
    return out, .55


def abort():
    rng = random.Random(171020)
    n = round(RATE * 1.7)
    click_bp, knock_lp = BandPass(2100, 2.5), OnePole(240)
    phase = 0.0
    out = []
    chatter = (.18, .23, .31, .36, .47)
    for i in range(n):
        t = i / RATE
        noise = rng.uniform(-1, 1)
        freq = 300 + 1900 * math.exp(-t * 3.2)  # capacitor whine collapsing as the supply sags
        phase += TAU * freq / RATE
        whine = .32 * math.sin(phase) * (1 + .3 * math.sin(TAU * 17 * t)) * math.exp(-t * 1.6)
        hum = .25 * math.sin(TAU * 60 * t) * math.exp(-t * 2.5)
        relay = sum(1.0 * math.exp(-(t - c) * 500) for c in chatter if 0 <= t - c < .02) * click_bp(noise)
        cut = 1 if t < .9 else math.exp(-(t - .9) * 45)  # the contactor takes the supply with it
        whine *= cut
        hum *= cut
        u = t - .9  # contactor dropping out
        clunk = (1.1 * math.sin(TAU * 88 * u) * env(u, .002, 18) + 1.2 * knock_lp(noise) * math.exp(-u * 60)) if u >= 0 else 0
        out.append(whine + hum + 1.4 * relay + clunk)
    return out, .7


SOUNDS = {"pulse": pulse, "scan": scan, "complete": complete, "abort": abort}
SERIAL = {"pulse": 171017, "scan": 171018, "complete": 171019, "abort": 171020}


def pcm(samples, peak):
    top = max(abs(x) for x in samples) or 1
    data = array("h")
    for i, x in enumerate(samples):
        fade = min(1, i / 48, (len(samples) - 1 - i) / 960)
        data.append(max(-32767, min(32767, round(x * peak / top * fade * 32767))))
    if sys.byteorder != "little":
        data.byteswap()
    return data.tobytes()


def main(names):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    for name in names or SOUNDS:
        samples, peak = SOUNDS[name]()
        preview = PREVIEW / f"{name}.wav"
        with wave.open(str(preview), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(pcm(samples, peak))
        destination = OUTPUT / f"{name}.ogg"
        subprocess.run(["oggenc", "-Q", "-q", "4", "--serial", str(SERIAL[name]), "-o", str(destination), str(preview)], check=True)
        print(destination.relative_to(ROOT), preview.relative_to(ROOT))


if __name__ == "__main__":
    main(sys.argv[1:])
