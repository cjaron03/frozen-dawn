# Selected hurt voice C

Owner chose +10percent pitch and hollow texture C on2026-10-08. Audition: build/blind-architect-evidence/hurt-texture-previews-20261008/selected-C/selection.json.

| Source | Use | Boundary |
| --- | --- | --- |
| Existing Minecraft1.21.1 Warden/Phantom hurt voices | Familiar voice, runtime processing | Referenced from the installed game, never extracted into the release jar |
| Original BlindHurtTexture implementation | Pitch/resampling and hollow EQ | Mod-authored Java; no new dependency |

Four alternatives reference minecraft:mob/warden/hurt_1, hurt_2 and minecraft:mob/phantom/hurt1, hurt2. Native gains0.6 and0.375; baked runtime pitch1.65 and0.66. Actual spatial position, volume, random entity pitch and subtitles remain in the normal sound pipeline. NeoForge21.1.219 SoundInstance.getStream/PlaySoundEvent hooks checked against local patched sources. Whole short PCM performances are processed on the sound I/O executor, with per-voice state and4MB input bound; outputs are ephemeral48kHz mono and freed on stream close. Hurt dispatch is Blind-only, normal/Master events unchanged. Approved click assets unchanged.

Original resampler and standard biquad equations follow the [Audio EQ Cookbook](https://webaudio.github.io/Audio-EQ-Cookbook/audio-eq-cookbook.html), checked2026-10-08. C: high-pass85Hz/Q0.7071;420Hz/Q1/-3dB;1450Hz/Q1.8/+3dB;low-pass5400Hz/Q0.7071; common audition gain1.048824921671377. No third-party implementation copied.

Runtime processing reads resources from the installed game; no redistribution license is asserted for extracted vanilla derivatives. Private audition source hashes are retained in the audition manifests. Resource-pack replacement voices can differ from the audition. Live playback and LAN remain acceptance checks.
