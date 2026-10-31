tag @a[distance=..64] add gojo.fxshake
tag @a[distance=..48] add gojo.fxflash_o
execute as @e[type=block_display,tag=gojo.mpt] if score @s gojo.id = #owner gojo.id run kill @s
particle minecraft:explosion_emitter ~ ~ ~ 6 3 6 0 12 force
particle minecraft:flame ~ ~ ~ 2 1 2 0.8 600 force
particle minecraft:lava ~ ~ ~ 8 3 8 0 120 force
particle minecraft:large_smoke ~ ~4 ~ 8 5 8 0.08 200 force
particle minecraft:flash{color:[1.0,0.5,0.1,1.0]} ~ ~ ~ 0 0 0 0 1 force
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 8 0.3
playsound minecraft:entity.lightning_bolt.thunder master @a ~ ~ ~ 6 0.4
playsound minecraft:entity.warden.sonic_boom master @a ~ ~ ~ 4 0.4
execute as @e[type=!#gojo:ignore,distance=..13] unless score @s gojo.id = #owner gojo.id if data entity @s Health run function gojo:meteor/burn
scoreboard players set #near gojo.tmp 0
execute as @a[distance=..20] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
execute if score #grief gojo.cfg matches 1 if score #near gojo.tmp matches 0 run function gojo:meteor/crater
summon marker ~ ~ ~ {Tags:["gojo.flames","gojo.fnew"]}
summon marker ~5 ~ ~ {Tags:["gojo.flames","gojo.fnew"]}
summon marker ~-5 ~ ~3 {Tags:["gojo.flames","gojo.fnew"]}
summon marker ~ ~ ~-5 {Tags:["gojo.flames","gojo.fnew"]}
execute as @e[type=marker,tag=gojo.fnew] run function gojo:fuga/flames_init
kill @e[type=marker,tag=gojo.ray]
kill @s
