# `/fdbot` dev command

`/fdbot` is a dev-only survival command. It performs the physical step through the same server methods a player would, and prints one chat line.

It does not change heaters, the EVA suit, doors, recipes, Maeve, MACS, or Architects. It only invokes existing game logic.

## Gating

The command is registered only when the game is not running in production (`!FMLEnvironment.production`). That is true for `./gradlew runClient`, the dev server, and the GameTest server. A shipped jar still contains the class, but the registration hook returns before the node is added, so a normal player has no `/fdbot`.

There is no config flag. A flag that defaulted to off would also hide the command from `runClient`.

The node requires permission level 2. On an integrated server the host already has that. A LAN guest does not, unless they are opped.

Ids and tags use the vanilla `resource_or_tag_key` argument (`minecraft:oak_log`, `#minecraft:logs`). That type is already in the command-argument registry, so the server can send the tree when a player is opped. Open to LAN with cheats does that for the host. A custom Brigadier argument type is not in the registry, and building that packet throws before any `/fdbot` command runs. `StringArgumentType.word()` is not a substitute: it stops at `:` and `#`, and a greedy string would consume the count or coordinates that follow the id.

Smelting is not implemented. Fuel a furnace with `use`, or skip that step.

## Do not use it for measurements

Never use `/fdbot` around Architects, Hearths, or during Maeve/MACS measurements.

Every use is one game-log line:

```
[FDBOT] player=<name> uuid=<uuid> args=<arguments> ok=<true|false> result=<same text as chat>
```

Maeve's beliefs, commitments, and pawn logic are unchanged, and they do not read a bot-assisted flag. Changing that path would change the measurement. The `[FDBOT]` prefix is the marker: if a log contains it, the session was bot-assisted and the report must say so.

`FdBotAudit.isBotAssisted(UUID)` is an in-memory flag set for the rest of the server run after that player's first `/fdbot`. It is not saved. `/fdbot status` prints `botAssisted=true` once the flag is set, including on that same status command.

## What the actions actually do

Breaks are instant. `gather` calls `ServerPlayerGameMode.destroyBlock`, which applies tool durability once per block, respects harvest checks, and drops loot through the normal player path. It does not wait out the block's break time. Drops are collected with `ItemEntity.playerTouch` after the vanilla pickup delay is cleared, because a command cannot wait those ten ticks. If that refuses the item, it is added to the inventory directly.

`goto` teleports. It does not walk or pathfind. Feet are placed at the block coordinates, centered on x/z.

`front` and `here` are feet-level cells from the player's horizontal facing, not the crosshair. `front` is one block ahead. `here` is the cell the player is standing in, so placement there usually fails. `place` and `use` have to be inside vanilla block reach (about five blocks). Teleport first if the target is farther.

`place` sneaks and right-clicks the top of the block under the target, through `useItemOn`. If the item is in the hotbar, that slot is selected. Otherwise the stack is swapped into the selected hotbar slot.

`use` right-clicks that block with whatever is in the main hand. An empty hand toggles a door or opens a container. A held item may be inserted or placed instead.

`craft` uses the server recipe manager and the player's own inventory (the 36 storage slots, not armor or the offhand). `count` is the number of result items. A recipe that yields more than one (a log makes four planks) stops on the craft that meets or passes the request. 2x2 recipes need no table. 3x3 recipes need a placed crafting table within 6 blocks, measured in a straight line through loaded chunks. A crafting table item in the inventory does not count. Shaped and shapeless recipes are assembled, including Frozen Dawn recipes that extend the shaped recipe. Other custom grids, such as the caloric-lined EVA upgrade, are refused with that reason. A missing blueprint fails the same way the recipe's own `matches` check fails.

`status` temperature is `TemperatureManager`'s world sample at the player's feet. It is not the HUD value after armor, frostbite, or hearthrot. `vacuum` is `SuitIntegrityHandler.isVacuumExposure`. `suitO2` is the suit reserve in ticks. `tankO2` is the sum of `O2` components on tank items in the inventory.

Searches only see loaded chunks. `gather` defaults to a radius of 24 and will not search past 48. `goto nearest` and `face nearest` search 48. Counts are capped at 64. Ids without a namespace are `minecraft:`. A leading `#` is a block tag (`#minecraft:logs`). `craft` does not accept tags.

## Examples

```
/fdbot status
/fdbot gather #minecraft:logs 8
/fdbot gather minecraft:oak_log 4 16
/fdbot craft minecraft:oak_planks 4
/fdbot place minecraft:crafting_table front
/fdbot craft minecraft:chest 1
/fdbot place minecraft:oak_door front
/fdbot use front
/fdbot goto 100 64 -20
/fdbot goto nearest minecraft:crafting_table
/fdbot face nearest #minecraft:logs
/fdbot use 100 65 -20
```

Chat is one line, prefixed with `FDBOT`. A failure is the red system message and still one line, for example `FDBOT could not craft minecraft:chest: need a crafting table within 6 blocks` or `FDBOT gathered 0 minecraft:stone with empty hand; skipped 1 that need a better tool`.
