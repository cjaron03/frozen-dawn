scoreboard objectives add abcheck dummy
execute unless score #built abcheck matches 1 run scoreboard players set #built abcheck 0
