scoreboard players add #s gojo.tmp 1
particle minecraft:dust{color:[1.0,0.08,0.08],scale:2.2} ~ ~ ~ 0.15 0.15 0.15 0 5 force
execute if entity @s[tag=gojo.strong] run particle minecraft:dust{color:[1.0,0.08,0.08],scale:3.5} ~ ~ ~ 0.4 0.4 0.4 0 6 force
execute if entity @s[tag=gojo.strong] run particle minecraft:flame ~ ~ ~ 0.3 0.3 0.3 0.02 2 force
particle minecraft:end_rod ~ ~ ~ 0.05 0.05 0.05 0 1 force
execute unless block ~ ~ ~ #gojo:passable run return run function gojo:red/boom
execute as @e[type=!#gojo:ignore,distance=..1.5] unless score @s gojo.id = #owner gojo.id run tag @s add gojo.hit
execute if entity @e[tag=gojo.hit,limit=1] run return run function gojo:red/boom
tp @s ^ ^ ^0.5
execute if score #s gojo.tmp matches ..3 at @s run function gojo:red/step
