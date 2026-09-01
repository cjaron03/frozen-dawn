# Undone audio sources

All runtime Undone audio is synthesized deterministically by
`tools/generate_undone_sounds.sh`. The generator uses FFmpeg sine, evaluated
oscillator, white-noise, pink-noise, and brown-noise sources. It contains no
recorded performance, generated voice model, or third-party sample.

The palette covers three ambient movements, pressure breathing, an almost-word
formant effect, attack and hurt impacts, jaw movement, grasp casting/holding/
breaking, and a synthetic terminal wail. The failed-word effect is deliberately
speech-adjacent but does not encode or synthesize spoken language.

Frozen Dawn retains rights to the original synthesis design, arrangement,
processing, mixing, and implementation. FFmpeg is a development tool and is not
bundled in the mod jar.

## Runtime outputs

- `src/main/resources/assets/frozendawn/sounds/entity/undone/ambient_1.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/ambient_2.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/ambient_3.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/attack.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/breath.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/death.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/failed_word.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/grab.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/grasp_break.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/grasp_cast.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/grasp_hold.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/hurt.ogg`
- `src/main/resources/assets/frozendawn/sounds/entity/undone/jaw.ogg`
