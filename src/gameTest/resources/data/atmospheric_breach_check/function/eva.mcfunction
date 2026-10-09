execute unless score #built abcheck matches 1 run return 0
item replace entity @s armor.head with frozendawn:eva_helmet
item replace entity @s armor.chest with frozendawn:eva_chestplate
item replace entity @s armor.legs with frozendawn:eva_leggings
item replace entity @s armor.feet with frozendawn:eva_boots
item replace entity @s hotbar.8 with frozendawn:o2_tank_mk3
gamemode survival @s
tellraw @s {"text": "Full EVA and a fresh tank for this disposable check. Drop an item, then open the breach. Watch O2 switch from ambient intake to tank use.", "color": "aqua"}
