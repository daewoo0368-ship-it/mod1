effect give @s minecraft:resistance 1 4 true
effect give @s minecraft:fire_resistance 1 0 true
particle minecraft:dust{color:[0.75,0.9,1.0],scale:0.5} ~ ~1 ~ 1.2 1.2 1.2 0 3
tag @s add gojo.infself
scoreboard players operation #infown gojo.id = @s gojo.id
execute as @e[type=#gojo:projectiles,distance=..5,tag=!gojo.halted] unless function gojo:inf/is_own unless data entity @s item.components."minecraft:custom_data".gojo_ten run function gojo:inf/halt
execute as @e[type=#gojo:projectiles,tag=gojo.halted,distance=..5.5] run data merge entity @s {Motion:[0.0,0.0,0.0]}
execute as @e[type=!#gojo:ignore,type=!#gojo:projectiles,distance=0.01..2.8] unless score @s gojo.id = #infown gojo.id unless items entity @s weapon.mainhand *[minecraft:custom_data~{gojo_ten:1b}] if data entity @s Health facing entity @s feet positioned ^ ^ ^2.9 if block ~ ~ ~ #gojo:passable if block ~ ~1 ~ #gojo:passable run function gojo:inf/push
tag @s remove gojo.infself
