#!/usr/bin/env python3
"""Build loud vocal and discharge events from the Aggregate's verified CC0 sources."""

from process_audio import OUT, render


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for variant in range(3):
        offset = variant * 0.12
        render(f"roar_{variant + 1}", 4.5, [
            ("animal_groan.mp3", offset, 4.25,
             f"asetrate=44100*{0.66 + variant * 0.04},aresample=44100,"
             "highpass=f=52,lowpass=f=5200", 1.18, 0),
            ("phantom_groan.mp3", offset, 3.7,
             "asetrate=44100*0.76,aresample=44100,lowpass=f=2600", 0.48, 180),
            ("bone_breaks.mp3", 0.2 + offset, 1.5,
             "highpass=f=150,lowpass=f=7600", 0.46, 620),
            ("bass_rumble.mp3", 7.0 + variant, 4.4,
             "lowpass=f=180", 0.72, 0),
        ])

    render("discharge_charge", 4.8, [
        ("concrete_drag.mp3", 1.2, 4.7,
         "asetrate=44100*0.72,aresample=44100,lowpass=f=6400", 0.82, 0),
        ("animal_groan.mp3", 0.0, 4.25,
         "areverse,asetrate=44100*0.70,aresample=44100,lowpass=f=3900", 0.72, 240),
        ("phantom_groan.mp3", 0.1, 4.3,
         "areverse,asetrate=44100*0.82,aresample=44100,lowpass=f=2500", 0.44, 0),
        ("bass_rumble.mp3", 10.0, 4.8, "lowpass=f=190", 0.78, 0),
    ])

    render("discharge_burst", 3.2, [
        ("animal_groan.mp3", 0.1, 3.0,
         "asetrate=44100*0.64,aresample=44100,highpass=f=48,lowpass=f=5200", 1.12, 0),
        ("bone_breaks.mp3", 0.0, 1.8, "highpass=f=110", 0.88, 80),
        ("debris.mp3", 0.5, 2.6, "lowpass=f=8800", 0.84, 120),
        ("heavy_stomp.mp3", 0.0, 2.7,
         "asetrate=44100*0.78,aresample=44100", 0.92, 0),
        ("bass_rumble.mp3", 14.0, 3.1, "lowpass=f=165", 0.82, 0),
    ])

    print(f"Wrote Aggregate roar and discharge mixes to {OUT}")


if __name__ == "__main__":
    main()
