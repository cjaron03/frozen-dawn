#!/usr/bin/env python3
"""Original CFU-7 Hearthstone Reactor audio. Run from the repo root:
python3 tools/audio_sources/hearthstone/generate_reactor_audio.py
Pure-Python synthesis to 44.1 kHz mono WAV, then oggenc (quality 5). Deterministic."""
import math, random, struct, subprocess, tempfile, wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parents[3] / "src/main/resources/assets/frozendawn/sounds/blocks/reactor"


def sines(parts, seconds):
    n = int(RATE * seconds); buf = [0.0] * n
    for freq, amp, phase in parts:
        w = 2 * math.pi * freq / RATE
        for i in range(n): buf[i] += amp * math.sin(w * i + phase)
    return buf


def tone(freq_fn, seconds, harmonics=((1, 1), (3, .33), (5, .15), (7, .07)), attack=.01, release=.03):
    n = int(RATE * seconds); buf = []; phase = 0.0
    for i in range(n):
        t = i / RATE; phase += 2 * math.pi * freq_fn(t) / RATE
        buf.append(min(1, t / attack, (seconds - t) / release) * sum(a * math.sin(h * phase) for h, a in harmonics))
    return buf


def silence(seconds): return [0.0] * int(RATE * seconds)


def save(name, buf):
    peak = max(abs(v) for v in buf) or 1
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / f"{name}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
            w.writeframes(b"".join(struct.pack("<h", int(v / peak * .85 * 32767)) for v in buf))
        subprocess.run(["oggenc", "-Q", "-q", "5", "-s", "1", str(wav), "-o", str(OUT / f"{name}.ogg")], check=True)


def hum():
    """4 s seamless loop: 55/55.5 Hz beat (0.5 Hz throb), octave, and a low rumble bed of whole-cycle partials."""
    r = random.Random(7)
    q = lambda f: round(f * 4) / 4
    rumble = [(q(r.uniform(25, 90)), r.uniform(.02, .06), r.uniform(0, 6.28)) for _ in range(40)]
    return sines([(55, 1, 0), (55.5, .8, 0), (110, .45, .4), (165, .15, 1.0)] + rumble, 4.0)


def overheat_warning():
    """Two-tone klaxon, 440/330 Hz odd-harmonic tones, 2 s."""
    buf = []
    for _ in range(2): buf += tone(lambda t: 440, .45) + silence(.05) + tone(lambda t: 330, .45) + silence(.05)
    return buf


def scram():
    """Three falling beeps, then the rods drop: decaying 55/82 Hz thud with a short noise rattle."""
    rnd = random.Random(3); buf = []
    for f in (880, 660, 440): buf += tone(lambda t, f=f: f, .16, ((1, 1), (2, .3))) + silence(.06)
    thud = []
    for i in range(int(RATE * .9)):
        t = i / RATE
        thud.append(math.exp(-t * 7) * (math.sin(2 * math.pi * 55 * t) + .5 * math.sin(2 * math.pi * 82 * t))
                    + math.exp(-t * 25) * .35 * (rnd.random() * 2 - 1))
    return buf + silence(.08) + thud


def meltdown_alert():
    """Rising 600-1100 Hz siren whoop, twice per 1 s."""
    buf = []
    for _ in range(2): buf += tone(lambda t: 600 + 1000 * t, .48, ((1, 1), (2, .4), (3, .25), (4, .12))) + silence(.02)
    return buf


ALARM_HZ = 1100


def dosimeter_tone(seconds, warble=0.0):
    """Dosimeter alarm voice: a 1.1 kHz tone, mostly sine with a soft third harmonic, optional 8 Hz warble.
    Owner found 3 kHz far too shrill; this sits in the range of a smoke-alarm-style buzzer but rounder."""
    n = int(RATE * seconds); out = []
    for i in range(n):
        t = i / RATE
        ph = 2 * math.pi * ALARM_HZ * t - (warble * ALARM_HZ / 8) * math.cos(2 * math.pi * 8 * t)
        out.append(math.sin(ph) + 0.25 * math.sin(3 * ph))
    return out


def dosimeter_chirp():
    """One 70 ms alarm chirp with a click-free envelope."""
    tone = dosimeter_tone(0.07)
    return [v * min(1, i / 60, (len(tone) - i) / 200) for i, v in enumerate(tone)]


def dosimeter_alarm():
    """1 s seamless loop of the continuous alarm (whole cycles of the tone and the 8 Hz warble)."""
    return dosimeter_tone(1.0, warble=0.04)


if __name__ == "__main__":
    for name, make in (("hum", hum), ("overheat_warning", overheat_warning), ("scram", scram), ("meltdown_alert", meltdown_alert)):
        save(name, make())
        print(OUT / f"{name}.ogg")
    # Geiger clicks and the dense loop are cut from a real CC0 recording by extract_geiger.py.
    save("dosimeter_chirp", dosimeter_chirp()); print(OUT / "dosimeter_chirp.ogg")
    save("dosimeter_alarm", dosimeter_alarm()); print(OUT / "dosimeter_alarm.ogg")
