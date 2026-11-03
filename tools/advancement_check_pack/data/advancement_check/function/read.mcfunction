scoreboard players set #personnel advcheck 0
scoreboard players set #brimstone advcheck 0
scoreboard players set #airlock advcheck 0
scoreboard players set #bell advcheck 0
scoreboard players set #rimewood advcheck 0
execute if entity @s[advancements={frozendawn:personnel_file=true}] run scoreboard players set #personnel advcheck 1
execute if entity @s[advancements={frozendawn:fire_and_brimstone=true}] run scoreboard players set #brimstone advcheck 1
execute if entity @s[advancements={frozendawn:im_sorry_im_afraid_i_cant_do_that=true}] run scoreboard players set #airlock advcheck 1
execute if entity @s[advancements={frozendawn:for_whom_the_bell_tolls=true}] run scoreboard players set #bell advcheck 1
execute if entity @s[advancements={frozendawn:life_finds_a_way=true}] run scoreboard players set #rimewood advcheck 1
