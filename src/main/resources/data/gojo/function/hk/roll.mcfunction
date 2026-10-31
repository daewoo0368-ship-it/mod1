scoreboard players add @s gojo.adapt 1
execute store result score #r gojo.tmp run random value 1..100
execute store result score @s gojo.r1 run random value 1..7
scoreboard players operation @s gojo.r2 = @s gojo.r1
scoreboard players operation @s gojo.r3 = @s gojo.r1
execute if score #r gojo.tmp matches 21..55 run function gojo:hk/miss_riichi
execute if score #r gojo.tmp matches 56.. run function gojo:hk/miss_plain
