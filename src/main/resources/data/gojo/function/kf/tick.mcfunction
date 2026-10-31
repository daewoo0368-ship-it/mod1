scoreboard players add @s gojo.life 1
scoreboard players operation #owner gojo.id = @s gojo.id
particle minecraft:cherry_leaves ~ ~2 ~ 9 2 9 0 20 force
particle minecraft:spore_blossom_air ~ ~1 ~ 9 1 9 0 10 force
particle minecraft:dust{color:[0.95,0.55,0.75],scale:1} ~ ~1 ~ 9 1 9 0 12 force
scoreboard players operation #m gojo.tmp = @s gojo.life
scoreboard players operation #m gojo.tmp %= #10 gojo.cfg
execute if score #m gojo.tmp matches 0 if score @s gojo.life matches ..59 as @e[type=!#gojo:ignore,distance=..10] unless score @s gojo.id = #owner gojo.id if data entity @s Health at @s run function gojo:kf/sap
execute if score @s gojo.life matches 60.. run function gojo:kf/bloom
