# Tough As Nails breath reference study

Decision: improve Frozen Dawn's existing cold-breath presentation on Minecraft 1.21.1 / NeoForge / Java 21, using native particle infrastructure. No additional temperature mod is required.

Inspected on 2026-10-20: official `Glitchfiend/ToughAsNails` 1.21.1 revision `c52ea955a0cbe5dcbb7569d4ed6ed09d8c386781`. Its [LICENSE](https://github.com/Glitchfiend/ToughAsNails/blob/c52ea955a0cbe5dcbb7569d4ed6ed09d8c386781/LICENSE), [README](https://github.com/Glitchfiend/ToughAsNails/blob/c52ea955a0cbe5dcbb7569d4ed6ed09d8c386781/README.md), build files and [version settings](https://github.com/Glitchfiend/ToughAsNails/blob/c52ea955a0cbe5dcbb7569d4ed6ed09d8c386781/gradle.properties) were checked. The license states “All rights reserved.” The version settings identify Minecraft 1.21.1, NeoForge 21.1.23 and GlitchCore 2.1.0.0.

The inspected [temperature client hooks](https://github.com/Glitchfiend/ToughAsNails/blob/c52ea955a0cbe5dcbb7569d4ed6ed09d8c386781/common/src/main/java/toughasnails/temperature/TemperatureHooksClient.java), [level-render handler](https://github.com/Glitchfiend/ToughAsNails/blob/c52ea955a0cbe5dcbb7569d4ed6ed09d8c386781/common/src/main/java/toughasnails/client/handler/LevelRenderHandler.java) and particle tree did not identify a dedicated breath effect. This is a bounded inspection, not a claim about every TAN version or modpack. The older 1.12 branch tree was also checked at `c3222de9f7958d8b22258a8d9129fcfeb1107249`; its listing alone is not implementation evidence. The owner subsequently said the remembered reference may be mistaken.

| Candidate | Intended use | Verified rights / compatibility | Safe takeaway | Decision |
| --- | --- | --- | --- | --- |
| Tough As Nails 1.21.1 | Study the remembered breath appearance | Source is visible; inspected license provides no reuse grant. MC/NeoForge version matches the target family. | No specific breath implementation was established; no code or assets adopted. | Study only; no dependency or reuse. |
| Existing Frozen Dawn `ColdEffects` and native particle pipeline | Original improvement | Existing project code and Minecraft runtime APIs/sprites already used by the mod. | Keep temperature gating; author a translucent exhale emitter/provider and smooth fade. | Implement locally. |

Implementation uses `ColdBreathPolicy`, `ColdBreathParticle`, existing `ColdEffects` and the project's standard particle registration. The particle JSON references unchanged Minecraft `generic_7`/`generic_6`/`generic_5` sprites at runtime. No TAN images, models, sounds, textures or code are shipped. Existing Frozen Dawn light-wind audio is reused without importing an external asset. No additional asset provenance entry is needed for an imported binary because none was imported.

Private inspection receipts and the official license/source snapshots remain ignored under `build/phase1-evidence/reference-toughasnails/`. Those reference snapshots are not distributed. Visual similarity to any remembered mod is unverified until the owner reviews Frozen Dawn's own result.
