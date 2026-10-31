tag @s add gojo.blue
execute if score #strong gojo.tmp matches 1 run tag @s add gojo.strong
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
tp @s ~ ~ ~ ~ ~
