scoreboard players operation @s gojo.id = #cast gojo.id
tp @s ~ ~ ~ ~ ~
execute store result score #v gojo.tmp run random value 0..7
scoreboard players set #s gojo.tmp 0
execute at @s run function gojo:ws/step
kill @s
