scoreboard players add #s gojo.tmp 1
execute if entity @s[tag=!gojo.strong] run particle minecraft:dust{color:[0.6,0.1,1.0],scale:4} ~ ~ ~ 1.2 1.2 1.2 0 25 force
execute if entity @s[tag=!gojo.strong] run particle minecraft:witch ~ ~ ~ 1.5 1.5 1.5 0 8 force
execute if entity @s[tag=gojo.strong] run particle minecraft:dust{color:[0.6,0.1,1.0],scale:4} ~ ~ ~ 2.5 2.5 2.5 0 70 force
execute if entity @s[tag=gojo.strong] run particle minecraft:witch ~ ~ ~ 3 3 3 0 20 force
execute if entity @s[tag=gojo.strong] run particle minecraft:dragon_breath ~ ~ ~ 2 2 2 0.02 10 force
particle minecraft:end_rod ~ ~ ~ 1 1 1 0.05 3 force
execute if entity @s[tag=!gojo.strong] as @e[type=!#gojo:ignore,distance=..3.5] unless score @s gojo.id = #owner gojo.id run damage @s 60 minecraft:magic
execute if entity @s[tag=gojo.strong] as @e[type=!#gojo:ignore,distance=..6.5] unless score @s gojo.id = #owner gojo.id run damage @s 200 minecraft:magic
tp @s ^ ^ ^0.5
execute if score #s gojo.tmp matches ..2 at @s run function gojo:purple/step
