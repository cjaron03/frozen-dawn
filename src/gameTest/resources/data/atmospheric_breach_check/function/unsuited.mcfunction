execute unless score #built abcheck matches 1 run return 0
item replace entity @s armor.head with minecraft:air
item replace entity @s armor.chest with minecraft:air
item replace entity @s armor.legs with minecraft:air
item replace entity @s armor.feet with minecraft:air
gamemode survival @s
tellraw @s {"text": "Unsuited test: breach starts the existing ten-second suffocation buildup. Cold may also hurt. Use the safety button to end exposure.", "color": "aqua"}
