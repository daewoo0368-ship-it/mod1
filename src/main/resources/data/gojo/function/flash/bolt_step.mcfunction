particle minecraft:dust{color:[0.0,0.0,0.0],scale:1.3} ~ ~ ~ 0 0 0 0 2 force
particle minecraft:dust{color:[1.0,0.0,0.0],scale:0.9} ~ ~ ~ 0.05 0.05 0.05 0 1 force
execute store result score #r gojo.tmp run data get entity @s Rotation[0]
execute store result score #j gojo.tmp run random value -45..45
scoreboard players operation #r gojo.tmp += #j gojo.tmp
execute store result entity @s Rotation[0] float 1 run scoreboard players get #r gojo.tmp
execute store result score #r gojo.tmp run data get entity @s Rotation[1]
execute store result score #j gojo.tmp run random value -35..35
scoreboard players operation #r gojo.tmp += #j gojo.tmp
execute store result entity @s Rotation[1] float 1 run scoreboard players get #r gojo.tmp
execute at @s run tp @s ^ ^ ^0.35
scoreboard players remove #step gojo.tmp 1
execute if score #step gojo.tmp matches 1.. at @s run function gojo:flash/bolt_step
