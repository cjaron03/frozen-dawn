#!/usr/bin/env python3
"""Build minimally processed CC0 death-roar candidates for listening tests."""

from process_audio import OUT, render


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    render("death_candidate_breath", 6.2, [
        ("dragon_dying_breath.mp3", 0.0, 5.86,
         "highpass=f=38,lowpass=f=11800", 1.18, 0),
        ("bone_breaks.mp3", 0.2, 1.9,
         "highpass=f=130,lowpass=f=8200", 0.34, 2450),
        ("debris.mp3", 0.5, 3.1, "lowpass=f=9200", 0.42, 3000),
    ])
    render("death_candidate_scream", 5.4, [
        ("dinosaur_death.mp3", 0.0, 2.27,
         "highpass=f=46,lowpass=f=12000", 1.32, 0),
        ("dragon_dying_breath.mp3", 0.45, 4.8,
         "asetrate=44100*0.94,aresample=44100,lowpass=f=7600", 0.60, 720),
        ("bone_breaks.mp3", 0.0, 2.2, "highpass=f=115", 0.46, 1580),
        ("debris.mp3", 0.3, 3.0, "lowpass=f=9000", 0.46, 2100),
    ])
    render("death_candidate_agony", 6.1, [
        ("agony_roar.mp3", 0.0, 5.7,
         "highpass=f=42,lowpass=f=11800", 1.22, 0),
        ("body_drag.mp3", 0.2, 4.5,
         "asetrate=44100*0.88,aresample=44100,lowpass=f=4300", 0.30, 1150),
        ("bone_breaks.mp3", 0.1, 2.1, "highpass=f=125", 0.38, 2750),
        ("debris.mp3", 0.5, 3.0, "lowpass=f=9000", 0.44, 3000),
    ])
    print(f"Wrote Aggregate death candidates to {OUT}")


if __name__ == "__main__":
    main()
