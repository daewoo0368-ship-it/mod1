particle minecraft:dust{color:[0.1,0.35,1.0],scale:4} ~ ~ ~ 0.8 0.8 0.8 0 30 force
particle minecraft:reverse_portal ~ ~ ~ 4 4 4 0.1 60 force
particle minecraft:end_rod ~ ~ ~ 0.4 0.4 0.4 0.03 6 force
particle minecraft:sculk_soul ~ ~ ~ 1 1 1 0.02 3 force
execute as @e[type=!#gojo:ignore,distance=0.8..16] unless score @s gojo.id = #owner gojo.id at @s facing entity @n[type=marker,tag=gojo.blue] feet run tp @s ^ ^ ^0.9
execute as @e[type=!#gojo:ignore,distance=..4] unless score @s gojo.id = #owner gojo.id run damage @s 10 minecraft:magic
scoreboard players set #near gojo.tmp 0
execute as @a[distance=..7] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
execute if score #grief gojo.cfg matches 1 if score #near gojo.tmp matches 0 run fill ~-4 ~-4 ~-4 ~4 ~4 ~4 air replace #gojo:breakable
