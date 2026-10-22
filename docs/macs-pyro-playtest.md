# MACS Pyro Check

Owner playtest world for the Pyro Architect ([macs-pyro.md](macs-pyro.md), §9.4a). The Pyro is a natural Architect asked through the real gate (`fd maeve pyro roll`); its travel, watch, looks and uploads are not scripted. No lab-bridge functions are approved for this world: agents read `fd maeve dump` and the `[MACS Pyro]` log lines only.

## Prepare

The preparer builds a fresh save from a closed world's `level.dat` schema. It never changes the template and refuses to overwrite:

```
python3 -B tools/prepare_macs_pyro_playtest.py --template "<closed world>/level.dat"
./gradlew runClientLab --console=plain -PfdLabWorld='MACS Pyro Check'
```

The world is Normal difficulty (locked) with advancement popups and chat awards filtered. On first join a BEGIN link appears after about 2 seconds; it reappears on every rejoin until clicked. BEGIN only works once.

## Setup

Stand on open ground and click BEGIN. Setup:

- wakes an empty Maeve and sets mid phase 6;
- clears a flat snow field 48 blocks around you;
- builds a stone cabin with a lit thermal heater against its east wall and a closed oak door in that wall;
- gives an EVA suit, sword, spyglass, food, oxygen tanks and Resistance IV;
- seeds one shelter sample at the cabin, so Maeve suspects you live there and knows of no heat.

Phase 6 is vacuum: open flames go out the moment they are placed. The heater and magma are the heat sources here.

## Steps

Before diagnostics, describe what you saw. Each CALL clears the cooldown and rolls one Architect 60 blocks east. It comes from the east with orange eyes, stops about 40 blocks out, and stares with a hand to its chin. It looks after 10 seconds, then every 5, and leaves after one minute. Click Dump after it leaves.

| Step | Owner does | Expect |
| --- | --- | --- |
| Thin wall | Click CALL. Watch from inside or beside the cabin. | It never comes closer than its post. The dump shows a HEAT_SOURCE belief by the east wall; the log shows `WALL_GLOW`. |
| Open door | Click Fresh Maeve, then Open door, then CALL. | `LEAKED` at the doorway. |
| Sealed | Click Fresh Maeve, then Sealed (wool walls and roof, glass window), then CALL. | Nothing seen (`COLD` in the log). After it leaves, Mud? reports Covered in Mud earned. No toast appears in this world. |
| Decoy | Keep Sealed, click Fresh Maeve, then Decoy (magma 14 blocks north), then CALL. | `DIRECT` at the magma; Mud? stays not earned. No decoy removes it. |
| Kill early | Fresh Maeve, Thin wall, CALL, then kill it within 10 seconds of it stopping. | It fights back only after being struck. Killed before its first look, nothing is uploaded. |
| Kill late | Fresh Maeve, Thin wall, CALL, then kill it after its first look. | The HEAT_SOURCE belief stays in the dump. |

Fresh Maeve erases this save's Maeve and wakes her empty again, so the heat she learned no longer blocks the next Pyro. If a roll says it was not designated, the `PYRO:` line gives the reason.

## Watch for

- Whether it ever walks toward the cabin or the heater.
- Whether the orange eyes and stare read clearly through the spyglass.
- When departing it is removed once no player is within 32 blocks. Watching from the cabin, about 40 blocks away, it may vanish in the open. Say whether that looks wrong.
