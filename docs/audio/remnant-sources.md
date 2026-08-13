# Remnant audio sources

The shipping Remnant voice and structure cues are processed from CC0 Freesound
recordings. Exact authors, source URLs, licenses, and the reproducible generation
script live in [`tools/audio_sources/remnant/SOURCES.md`](../../tools/audio_sources/remnant/SOURCES.md).

`false_radio.ogg` and `false_heater.ogg` remain Frozen Dawn-owned environmental
cues. They create the false refuge; they are not the Remnant's voice.

The false-radio dialogue is original Frozen Dawn writing synthesized with the
same Samantha voice used by the ORSA field radio. The reproducible
`tools/generate_remnant_radio_voices.sh` pipeline preserves that single speaker
and adds only damaged-radio filtering, dropout, and a quiet delayed reflection.
