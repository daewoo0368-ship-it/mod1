tag @s remove gojo.mnew
tag @s add gojo.dom
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
execute store result score @s gojo.pos run data get entity @s Pos[0]
execute store result score #my gojo.tmp run data get entity @s Pos[1]
execute store result score #mz gojo.tmp run data get entity @s Pos[2]
scoreboard players operation @s gojo.ct = #mz gojo.tmp
scoreboard players operation @s gojo.chant = #my gojo.tmp
