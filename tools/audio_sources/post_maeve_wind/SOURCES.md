# Post-Maeve wind audio sources

The post-Maeve wind variants are synthesized deterministically by
`tools/generate_post_maeve_wind.sh`. They use FFmpeg pink-noise, white-noise,
and sine sources with high-pass filtering, slow amplitude motion, light phase
movement, and controlled dynamics. No recording or third-party sample is used.

The strong and light variants intentionally remove most low-frequency content
from the pre-erasure wind vocabulary. Runtime volume reduction remains
controlled independently by Frozen Dawn's post-Maeve ambience settings.

Frozen Dawn retains rights to the original synthesis design, arrangement,
processing, mixing, and implementation. FFmpeg is a development tool and is not
bundled in the mod jar.

## Runtime outputs

- `src/main/resources/assets/frozendawn/sounds/ambient/wind_post_maeve_light.ogg`
- `src/main/resources/assets/frozendawn/sounds/ambient/wind_post_maeve_strong.ogg`
