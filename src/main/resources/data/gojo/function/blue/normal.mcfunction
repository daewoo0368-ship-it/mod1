particle minecraft:dust{color:[0.1,0.35,1.0],scale:2.5} ~ ~ ~ 0.35 0.35 0.35 0 15 force
particle minecraft:reverse_portal ~ ~ ~ 2 2 2 0.05 25 force
particle minecraft:end_rod ~ ~ ~ 0.2 0.2 0.2 0.02 3 force
execute as @e[type=!#gojo:ignore,distance=0.8..8] unless score @s gojo.id = #owner gojo.id at @s facing entity @n[type=marker,tag=gojo.blue] feet run tp @s ^ ^ ^0.5
execute as @e[type=!#gojo:ignore,distance=..2.5] unless score @s gojo.id = #owner gojo.id run damage @s 5 minecraft:magic
scoreboard players set #near gojo.tmp 0
execute as @a[distance=..5] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
execute if score #grief gojo.cfg matches 1 if score #near gojo.tmp matches 0 run fill ~-2 ~-2 ~-2 ~2 ~2 ~2 air replace #gojo:breakable
