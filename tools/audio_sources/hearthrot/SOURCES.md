# Hearthrot audio sources

The contamination line is generated locally with the same Samantha-based female
ORSA suit-voice workflow used by existing generated interface lines. It contains
no recorded third-party performance.

The breathing rasp is transformed from Frozen Dawn's existing EVA breathing.
The crystalline layers and heart-loss cue use
`tools/audio_sources/undone_architect/ice_crack_2205.ogg`; its author, URL,
license, and shipping rights are documented in that source folder's `SOURCES.md`.

The human respiratory performances are Creative Commons Zero recordings:

- `strong_double_cough_cc0.wav`: "Strong Double Cough" by qubodup,
  https://freesound.org/people/qubodup/sounds/743360/ (CC0 1.0). The three
  shipped coughs are restrained timing/EQ variants of this source.
- `wheezing_cc0.wav`: "Wheezing" by thedapperdan,
  https://freesound.org/people/thedapperdan/sounds/205012/ (CC0 1.0). The
  shipped wheeze is trimmed, equalized, compressed, and level-matched.
- `zombie_choking_cc0.wav`: "Zombie Choking.wav" by mrh4hn,
  https://freesound.org/people/mrh4hn/sounds/426637/ (CC0 1.0). The shipped
  breath-catch recovery is trimmed, equalized, compressed, and level-matched.

All Hearthrot assets are reproducible with `tools/generate_hearthrot_sounds.sh`.
The Samantha line is exported through `tools/generate_samantha_tts.m` because
some macOS versions produce header-only audio when `say -o` is used directly.
