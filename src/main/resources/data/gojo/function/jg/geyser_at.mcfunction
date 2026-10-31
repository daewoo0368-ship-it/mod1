execute store result score #r gojo.tmp run random value -10..10
execute store result score #r2 gojo.tmp run random value -10..10
execute store result entity @s Pos[0] double 1 run data get entity @s Pos[0]
scoreboard players operation #gx gojo.tmp = #r gojo.tmp
execute store result score #px gojo.tmp run data get entity @s Pos[0]
execute store result score #pz gojo.tmp run data get entity @s Pos[2]
scoreboard players operation #px gojo.tmp += #r gojo.tmp
scoreboard players operation #pz gojo.tmp += #r2 gojo.tmp
execute store result entity @s Pos[0] double 1 run scoreboard players get #px gojo.tmp
execute store result entity @s Pos[2] double 1 run scoreboard players get #pz gojo.tmp
execute at @s run particle minecraft:lava ~ ~ ~ 0.4 3 0.4 0 40 force
execute at @s run particle minecraft:flame ~ ~3 ~ 0.4 3 0.4 0.05 80 force
execute at @s run particle minecraft:dust{color:[1.0,0.3,0.0],scale:2.5} ~ ~3 ~ 0.5 3 0.5 0 40 force
execute at @s run playsound minecraft:block.lava.extinguish master @a ~ ~ ~ 2 0.5
execute at @s as @e[type=!#gojo:ignore,distance=..2.5] unless score @s gojo.id = #owner gojo.id if data entity @s Health run damage @s 12 minecraft:lava
kill @s
