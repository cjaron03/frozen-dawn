# Aggregate audio sources

All source recordings below are published under Creative Commons 0 on Freesound.
The checked-in `raw/` files are the public high-quality previews from those pages.
`process_audio.py` deterministically converts them to mono OGG and controls sub-bass
headroom for Minecraft's vacuum-aware playback path.

| File | Freesound source | Creator | Use |
| --- | --- | --- | --- |
| `bone_breaks.mp3` | https://freesound.org/s/574752/ | ericnorcross81 | fracture and shedding transients |
| `body_drag.mp3` | https://freesound.org/s/473526/ | Kneeling | dragged ossuary movement |
| `concrete_drag.mp3` | https://freesound.org/s/545544/ | rsellick | mass and architectural drag |
| `debris.mp3` | https://freesound.org/s/703247/ | xkeril | collapse and impact tails |
| `bass_rumble.mp3` | https://freesound.org/s/166122/ | deleted_user_2104797 | bounded low-frequency bed |
| `heavy_stomp.mp3` | https://freesound.org/s/812538/ | Yoyamen1212 | Slam and foot mass |
| `animal_groan.mp3` | https://freesound.org/s/571386/ | Lewis.B.M | chopped/formant-split broken vocal layer |
| `phantom_groan.mp3` | https://freesound.org/s/473525/ | Kneeling | reversed broken vocal layer |
| `dragon_dying_breath.mp3` | https://freesound.org/s/276577/ | MickBoere | sustained final-breath candidate |
| `dinosaur_death.mp3` | https://freesound.org/s/185366/ | anwul | short immediate death-scream candidate |
| `agony_roar.mp3` | https://freesound.org/s/711482/ | Mastersoundboy2005 | long natural agony-roar candidate |

No source is shipped unmodified as an in-game event. The two vocal recordings are
mixed with structural material and never form words. `generate_roars.py` uses those
same verified CC0 recordings for the Aggregate's louder physical roar, convergence
charge, and body-expulsion rupture events. `death_candidate_scream` is the selected
death event: the short dinosaur death performance remains prominent, followed by a
restrained dying-breath tail, bone fractures, and falling debris.
