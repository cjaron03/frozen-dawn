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


def mix(*layers):
    """Sums (start seconds, samples) layers into one buffer."""
    n = max(int(RATE * start) + len(buf) for start, buf in layers); out = [0.0] * n
    for start, buf in layers:
        o = int(RATE * start)
        for i, v in enumerate(buf): out[o + i] += v
    return out


def noise(seed, seconds, cutoff):
    """Low-passed white noise: a one-pole filter at roughly cutoff Hz."""
    rnd = random.Random(seed); a = 1 - math.exp(-2 * math.pi * cutoff / RATE); y = 0.0; out = []
    for _ in range(int(RATE * seconds)):
        y += a * ((rnd.random() * 2 - 1) - y); out.append(y)
    return out


def clank(freq, seconds=.7, decay=6.0):
    """A heavy steel impact: inharmonic ringing partials over a short low thump."""
    out = []
    for i in range(int(RATE * seconds)):
        t = i / RATE
        ring = sum(a * math.sin(2 * math.pi * freq * k * t) for k, a in ((1, 1), (2.76, .5), (5.4, .25), (8.93, .12)))
        out.append(math.exp(-t * decay) * .45 * ring + math.exp(-t * 30) * math.sin(2 * math.pi * 48 * t))
    return out


def pump(f0, f1, seconds, fade_in, fade_out):
    """Coolant pumps changing speed: a whine gliding f0 to f1 Hz over a 55 Hz motor bed and a breathing water rush."""
    whine = tone(lambda t: f0 + (f1 - f0) * t / seconds, seconds, ((1, .5), (2, .25), (3, .1)), attack=fade_in, release=fade_out)
    bed = tone(lambda t: 55 * (f0 + (f1 - f0) * t / seconds) / max(f0, f1), seconds, ((1, 1), (2, .4)), attack=fade_in, release=fade_out)
    rush = noise(17, seconds, 500)
    return [w + .6 * b + .5 * n * min(1, i / RATE / fade_in, (seconds - i / RATE) / fade_out) for i, (w, b, n) in enumerate(zip(whine, bed, rush))]


def startup():
    """About 4.5 s: a breaker clunk, the coolant pumps spinning up from 40 to 220 Hz, and three rising chimes as the
    core goes critical, before it hands over to the running hum."""
    chimes = []
    for k, f in enumerate((523.25, 659.25, 783.99)): chimes.append((2.4 + .28 * k, tone(lambda t, f=f: f, .5, ((1, 1), (2, .2)), release=.4)))
    return mix((0, clank(90, .5, 9)), (.15, pump(40, 220, 4.2, .6, .9)), *[(s, [.35 * v for v in b]) for s, b in chimes])


def shutdown():
    """About 4.5 s: two falling chimes as the rods settle, then the pumps winding down from 220 to 30 Hz into silence
    and a last soft relay click."""
    chimes = [(0, tone(lambda t: 659.25, .5, ((1, 1), (2, .2)), release=.4)), (.3, tone(lambda t: 440, .7, ((1, 1), (2, .2)), release=.6))]
    return mix(*[(s, [.35 * v for v in b]) for s, b in chimes], (.5, pump(220, 30, 3.8, .2, 2.4)), (4.3, [.4 * v for v in clank(140, .25, 18)]))


def bell(freq, seconds, decay):
    """A soft struck bell: a few slightly inharmonic partials."""
    return [math.exp(-i / RATE * decay) * sum(a * math.sin(2 * math.pi * freq * k * i / RATE) for k, a in ((1, 1), (2.01, .45), (3.02, .2), (4.18, .1)))
            for i in range(int(RATE * seconds))]


def running():
    """About 2.5 s: the core at power. Two low bell strokes, G4 then C5, ringing out over a soft 110 Hz swell: rounder
    and slower than the startup's quick chimes."""
    swell = [.18 * math.sin(2 * math.pi * 110 * i / RATE) * math.sin(math.pi * i / (RATE * 2.4)) for i in range(int(RATE * 2.4))]
    return mix((0, bell(392, 2.2, 2.4)), (.45, bell(523.25, 2.0, 2.0)), (0, swell))


def scram():
    """About 3 s: a harsh two-tone trip horn, then the rods slamming home, four quick steel clanks, and the
    hydraulic accumulators venting in a long hiss."""
    horn = []
    for f in (950, 700, 950):
        horn += tone(lambda t, f=f: f, .14, ((1, 1), (2, .5), (3, .4), (4, .3), (5, .2)), attack=.003, release=.01)
    clanks = [(.5 + .07 * k, [(1 - .15 * k) * v for v in clank(70 + 9 * k)]) for k in range(4)]
    hiss = [v * math.exp(-i / RATE * 1.4) * min(1, i / RATE / .05) for i, v in enumerate(noise(31, 2.4, 3000))]
    return mix((0, [.6 * v for v in horn]), *clanks, (.62, [1.2 * v for v in hiss]))


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
    for name, make in (("hum", hum), ("overheat_warning", overheat_warning), ("scram", scram), ("meltdown_alert", meltdown_alert),
                       ("startup", startup), ("shutdown", shutdown), ("running", running)):
        save(name, make())
        print(OUT / f"{name}.ogg")
    # Geiger clicks and the dense loop are cut from a real CC0 recording by extract_geiger.py.
    save("dosimeter_chirp", dosimeter_chirp()); print(OUT / "dosimeter_chirp.ogg")
    save("dosimeter_alarm", dosimeter_alarm()); print(OUT / "dosimeter_alarm.ogg")
