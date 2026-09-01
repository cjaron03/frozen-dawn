# Local Piper voice models

Voice model binaries are intentionally not tracked in this repository. Keep
them in a local directory such as `.local/piper-voices/` and point
`PIPER_DATA_DIR` at that directory.

## Recommended development voice

- Model: `en_US-amy-medium`
- Download through the official Piper CLI:

  ```sh
  python3 -m pip install piper-tts
  python3 -m piper.download_voices en_US-amy-medium \
    --data-dir "$PWD/.local/piper-voices"
  ```

- Upstream model card:
  https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/amy/medium/MODEL_CARD
- The model card points to the Mycroft Mimic 3 voices dataset. That dataset
  is licensed under Creative Commons Attribution-ShareAlike 4.0:
  https://github.com/MycroftAI/mimic3-voices/blob/master/LICENSE

Generated performances must retain this attribution and ShareAlike review in
the release asset notice. The Piper engine itself is GPL-3.0-or-later and is
used only as a local generation tool; it is not bundled in the Frozen Dawn jar.

The current model is a legally documented development/replacement path, not
proof that it reproduces the prior Samantha voice. It preserves the exact
dialogue text and the existing Frozen Dawn processing profiles. Replace the
model only after checking that model's own `MODEL_CARD` and dataset terms.
