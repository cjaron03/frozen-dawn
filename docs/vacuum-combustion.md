# Vacuum combustion checkpoint

Owning checkout: `/Users/jaroncabral/.codex/worktrees/rimewood/minecraft-mod`, branch `feat/rimewood`. Baseline: `b5a04337c18fee7668c677c9d8ce12f3ac0a2803` (ORSA Ration Warmer). Vacuum combustion is committed separately before Rimewood implementation.

## Behavior

The existing Overworld vacuum boundary (apocalypse progress >= 0.85) controls ordinary combustion. Existing shared sealed-room and oxygen-support rules permit combustion; warmth alone does not. Soul flames remain supernatural exceptions and do not supply oxygen. Redstone components and lava retain their existing behavior.

Ordinary fire disappears. Torches and lanterns become unlit blocks; candles and campfires turn off. Furnaces, smokers, blast furnaces and MiteAway stop combustion and preserve unspent fuel. Already consumed furnace burn time is lost, and partial cooking progress cools normally. Extinguished torches/lanterns drop normal items and can be relit with flint and steel in breathable air; they do not relight automatically. This does not alter burning entities or introduce a new oxygen simulation.

Torch models reference the exact vanilla wooden shaft texture and a black coal-texture tip, with unchanged native dimensions. Standing and wall torches emit no light or particles. The lantern retains its native model and texture with zero emitted light. No generated charred-shaft artwork is included.

New block writes normalize immediately. Loaded old chunks are scanned incrementally (4096 cells / 64 sections per tick); indexed lights are checked 32 at a time. No infrastructure or light chunks are loaded by this feature. Shared air samples are cached for ten game ticks, so breach response can take up to half a second plus index cycling.

## Verification

Java 21 `./gradlew build gameTestGate --console=plain` passed: 665 unit tests, zero errors/failures; 268 native GameTests, including all five vacuum cases and all 263 required gate cases. The native cases verify real block writes/state preservation/zero light, earlier-phase and soul exceptions, unspent furnace and repellent fuel, sealed-room/breach behavior, and existing lights at a phase transition through the production index loop.

The first run exposed one existing late-vacuum MACS fixture placing ordinary fire. Its now-removed flame made that fixture invalid; it now asserts and uses supported soul fire for the same hazard-avoidance contract. Failed and passing attempts are retained in `build/vacuum-combustion-evidence/`, with final native report `aeb33bd2-4e39-4134-bd58-ad86543e16e3`.

Release jar SHA-256: `e42a7f7aa862d1b83699ebb19e099931b81e9e65d2043d05d975974b737836f4`. The existing smoke profile's Frozen Dawn jar was replaced with that exact artifact; dependency jars were preserved. Saved-world reload and multiplayer remain separate acceptance checks.

## Owner visual check

Fresh save: **Vacuum Flame Check**, prepared with `tools/prepare_vacuum_combustion_playtest.py`. It copies seed metadata only, preserving the source save hash and all other saved worlds. Its setup runs once; subsequent opens do not reset the scene. Fire spread and mob spawning are disabled to isolate extinction. The apocalypse clock is paused independently of ordinary simulation.

Launch with Java 21: `./gradlew runClientLab --console=plain '-PfdLabWorld=Vacuum Flame Check'`. Use the agent terminal and retain its log. The owner performs the visual pass:

1. Inspect the normal torch in mid Phase 6.
2. Open chat and click **Advance this test world to VACUUM**. Outdoor ordinary flames go out; the wooden torch shaft remains unchanged with a black tip. Soul sources and sealed-room lights remain lit.
3. Click **Open the sealed room**. Its ordinary sources go out.
4. Click **Restore roof**, allow light/air checks to settle, then relight the torch or lantern with flint and steel.

The client was launched through the owning Gradle checkout. Log: `/private/tmp/rimewood-vacuum-client.log`; live session `cc6f26d6-5f0d-44bb-92b5-99e0eedd2c5a` reported **Vacuum Flame Check**, player Dev at (0.5, 65, 8.5), paused at game tick 14. Setup reported READY and the diagnostic bridge confirmed `vfcheck #built=1`; no datapack/model loading errors appeared. Gradle terminal session: 77858.

Visual acceptance is pending the owner's report. Do not replay setup, erase this scene, or claim headless tests prove the visual result. Diagnostic bridge snapshot/status is permitted; the owner uses the visible controls for scene changes.

## Research sources

- NASA, vacuum lacks oxygen needed for ordinary combustion: https://technology.nasa.gov/nasa-technology-fights-wildfires
- NeoForge fire extension contract: https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/common/extensions/IBlockExtension.java
- Installed Minecraft 1.21.1 / NeoForge 21.1.219 source and resource jars provide the exact native fire/furnace hooks and torch model layout. Native texture references are used rather than redistributed vanilla bitmaps.
