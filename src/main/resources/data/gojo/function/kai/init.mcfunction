tag @s add gojo.kai
scoreboard players operation @s gojo.id = #cast gojo.id
scoreboard players set @s gojo.life 0
execute store result score @s gojo.ct run random value 0..7
tp @s ~ ~ ~ ~ ~
