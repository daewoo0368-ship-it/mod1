scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
execute if score @s gojo.life matches 120.. run return run function gojo:meteor/impact
particle minecraft:flame ~ ~ ~ 3 3 3 0.1 60 force
particle minecraft:large_smoke ^ ^ ^-4 3 3 3 0.05 30 force
particle minecraft:lava ~ ~ ~ 3 3 3 0 8 force
particle minecraft:dust{color:[1.0,0.35,0.0],scale:3} ^ ^ ^-5 2.5 2.5 2.5 0 25 force
execute if predicate gojo:chance10 run playsound minecraft:block.fire.ambient master @a ~ ~ ~ 4 0.4
execute positioned ^ ^ ^5 unless block ~ ~ ~ #gojo:passable run return run function gojo:meteor/impact
execute positioned ^ ^ ^3 as @e[type=!#gojo:ignore,distance=..5] unless score @s gojo.id = #owner gojo.id if data entity @s Health run tag @n[type=marker,tag=gojo.met] add gojo.mhit
execute if entity @s[tag=gojo.mhit] run return run function gojo:meteor/impact
execute as @e[type=block_display,tag=gojo.mpt] if score @s gojo.id = #owner gojo.id at @s run tp @s ^ ^ ^0.9
tp @s ^ ^ ^0.9
