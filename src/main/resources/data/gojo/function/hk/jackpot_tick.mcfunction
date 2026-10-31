scoreboard players remove @s gojo.jpt 1
execute if score @s gojo.jpt matches ..0 run return run function gojo:hk/jackpot_end
effect give @s minecraft:regeneration 2 4 true
effect give @s minecraft:resistance 2 1 true
effect give @s minecraft:strength 2 1 true
effect give @s minecraft:speed 2 1 true
execute store result score @s gojo.hp run data get entity @s Health
execute if score @s gojo.hp matches ..8 run effect give @s minecraft:instant_health 1 3 true
execute if score @s gojo.hp matches ..8 run particle minecraft:totem_of_undying ~ ~1 ~ 0.5 1 0.5 0.3 30 force
scoreboard players set @s gojo.cd 0
scoreboard players set @s gojo.dcd 0
scoreboard players set @s gojo.mcd 0
scoreboard players set @s gojo.jcd 0
scoreboard players set @s gojo.gcd 0
scoreboard players set @s gojo.ucd 0
scoreboard players set @s gojo.pcd 0
scoreboard players set @s gojo.wcd 0
scoreboard players set @s gojo.kcd 0
scoreboard players set @s gojo.tcd 0
scoreboard players set @s gojo.hcd 0
execute if predicate gojo:chance10 run particle minecraft:happy_villager ~ ~1 ~ 0.4 0.8 0.4 0 3
scoreboard players operation #sec gojo.tmp = @s gojo.jpt
scoreboard players operation #sec gojo.tmp /= #20 gojo.cfg
title @s actionbar [{"text":"대박 — 무한 주력 ","color":"gold","bold":true},{"score":{"name":"#sec","objective":"gojo.tmp"},"color":"white"},{"text":"초","color":"gold"}]
