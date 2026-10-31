scoreboard players add #s gojo.tmp 1
particle minecraft:flame ~ ~ ~ 0.08 0.08 0.08 0.01 4 force
particle minecraft:dust{color:[1.0,0.45,0.0],scale:1.6} ~ ~ ~ 0.05 0.05 0.05 0 2 force
particle minecraft:lava ~ ~ ~ 0 0 0 0 1 force
execute unless block ~ ~ ~ #gojo:passable run return run function gojo:fuga/boom
execute as @e[type=!#gojo:ignore,distance=..1.5] unless score @s gojo.id = #owner gojo.id if data entity @s Health run tag @s add gojo.hit
execute if entity @e[tag=gojo.hit,limit=1] run return run function gojo:fuga/boom
tp @s ^ ^ ^0.6
execute if score #s gojo.tmp matches ..2 at @s run function gojo:fuga/step
