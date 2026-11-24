# The descent's sounds

The era instruments and chamber zero's icicle (CH-45, CH-46), from the owner's approved prototype,
"The Descent Melody", delivered as `descent-melodies.zip` on 2026-11-23.

- The clips are synthesized from the respawn ring's tones. The generator is `thoughts/melody/synth.py` in the
  owner's project files; it is not in this repository.
- The icicle clip's wind comes from two CC0 recordings: "Winter Snowstorm Ambience" by HECKFRICKER
  (freesound 754256) and "strong wind on coastal path" by bruno.auzet (freesound 706471). The drips are synthesized.

Each clip keeps its own tempo, room and uneven slowing. Conversion, the same for all ten: one uniform -6.5 dB trim
so no clip peaks above -6 dB (the balance between clips is unchanged), mono 44.1 kHz Vorbis, `oggenc -q 5`:

    ffmpeg -i <clip>.mp3 -map_metadata -1 -af volume=-6.5dB -ac 1 -ar 44100 -f wav - | oggenc -q 5 -s 4401 -o <out>.ogg -

| Prototype file | Shipped file | Event |
| --- | --- | --- |
| `1816_musicbox.mp3` | `block/descent/music_box.ogg` | `block.descent.music_box` |
| `1_maunder_churchbell.mp3` | `block/descent/maunder.ogg` | `block.descent.maunder` |
| `2_famine_monasterybell.mp3` | `block/descent/famine.ogg` | `block.descent.famine` |
| `3_536_lyre.mp3` | `block/descent/late_antique.ogg` | `block.descent.late_antique` |
| `4_bronzeage_lur.mp3` | `block/descent/bronze_age.ogg` | `block.descent.bronze_age` |
| `5_4k2_bulllyre.mp3` | `block/descent/four_kiloyear.ogg` | `block.descent.four_kiloyear` |
| `6_8k2_drum_whistle.mp3` | `block/descent/eight_kiloyear.ogg` | `block.descent.eight_kiloyear` |
| `7_youngerdryas_stonewhistle.mp3` | `block/descent/younger_dryas.ogg` | `block.descent.younger_dryas` |
| `8_iceage_boneflute.mp3` | `block/descent/glacial_maximum.ogg` | `block.descent.glacial_maximum` |
| `0_chamberzero_icicle.mp3` | `ambient/descent/icicle.ogg` | `ambient.descent.icicle` |

Every event plays at volume 0.6 and is heard to 8 blocks, not streamed.
