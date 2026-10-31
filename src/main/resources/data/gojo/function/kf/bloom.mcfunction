particle minecraft:cherry_leaves ~ ~2 ~ 8 3 8 0.2 300 force
particle minecraft:dust{color:[1.0,0.7,0.85],scale:2.5} ~ ~1.5 ~ 9 2 9 0 150 force
particle minecraft:flash{color:[1.0,0.85,0.9,1.0]} ~ ~1 ~ 0 0 0 0 1 force
playsound minecraft:block.amethyst_cluster.break master @a ~ ~ ~ 3 0.5
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 2 1.2
execute as @e[type=!#gojo:ignore,distance=..10] unless score @s gojo.id = #owner gojo.id if data entity @s Health run damage @s 12 minecraft:magic
execute as @a if score @s gojo.id = #owner gojo.id run effect give @s minecraft:instant_health 1 1 true
execute as @e[type=block_display,tag=gojo.kfd] if score @s gojo.id = #owner gojo.id run kill @s
kill @s
