tag @s add gojo.purple
execute if score #strong gojo.tmp matches 1 run tag @s add gojo.strong
scoreboard players operation @s gojo.id = #owner gojo.id
scoreboard players set @s gojo.life 0
tp @s ~ ~ ~ ~ ~
