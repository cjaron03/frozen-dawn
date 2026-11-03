execute unless score #built abcheck matches 0 run return 0
scoreboard players set #built abcheck -1
gamemode creative @s
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doFireTick false
gamerule keepInventory true
weather clear
time set noon
fd world preset default
fd world set phase 6 mid
fd world pause
fill -15 64 -15 15 64 15 minecraft:bedrock
fill -15 65 -15 15 90 15 minecraft:air
fill -4 65 -4 4 70 4 minecraft:glass
fill -3 65 -3 3 69 3 minecraft:air
setblock 0 65 -2 frozendawn:geothermal_core
setblock -2 65 0 minecraft:torch
setblock -3 67 0 minecraft:wall_torch[facing=east]
setblock 2 65 0 minecraft:campfire[lit=true]
setblock 2 65 2 minecraft:candle[lit=true]
setblock -2 65 2 minecraft:lantern
setblock 0 65 2 minecraft:furnace
item replace block 0 65 2 container.0 with minecraft:raw_iron 64
item replace block 0 65 2 container.1 with minecraft:coal 64
setblock -2 65 -2 minecraft:soul_torch
setworldspawn 0 65 0
spawnpoint @s 0 65 0
tp @s 0.5 65 0.5 -90 10
give @s minecraft:flint_and_steel
give @s minecraft:glass 16
give @s minecraft:cobblestone 16
give @s minecraft:cooked_beef 32
give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]
fd world set phase 6 late
fd world pause
scoreboard players set #built abcheck 1
function atmospheric_breach_check:controls
tellraw @s {"text": "READY: fresh sealed glass room with oxygen core. Read the new Atmospheric Breaches entry in the ORSA Field Manual.", "color": "aqua"}
