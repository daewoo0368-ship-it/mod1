tag @a[distance=..48] add gojo.fxshake
tag @a[distance=..40] add gojo.fxflash_o
tag @e[tag=gojo.hit] remove gojo.hit
particle minecraft:explosion_emitter ~ ~ ~ 2 1 2 0 3 force
particle minecraft:flame ~ ~ ~ 1 1 1 0.5 300 force
particle minecraft:lava ~ ~ ~ 3 1 3 0 60 force
particle minecraft:large_smoke ~ ~2 ~ 3 2 3 0.05 80 force
particle minecraft:flash{color:[1.0,0.6,0.1,1.0]} ~ ~ ~ 0 0 0 0 1 force
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 4 0.5
playsound minecraft:item.firecharge.use master @a ~ ~ ~ 3 0.5
playsound minecraft:entity.blaze.death master @a ~ ~ ~ 2 0.5
execute as @e[type=!#gojo:ignore,distance=..8] unless score @s gojo.id = #owner gojo.id if data entity @s Health run function gojo:fuga/burn
scoreboard players set #near gojo.tmp 0
execute as @a[distance=..10] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
execute if score #grief gojo.cfg matches 1 if score #near gojo.tmp matches 0 run summon minecraft:fireball ~ ~0.5 ~ {ExplosionPower:5b,Motion:[0.0,-3.0,0.0],acceleration_power:1.0d}
summon marker ~ ~ ~ {Tags:["gojo.flames","gojo.fnew"]}
execute as @n[type=marker,tag=gojo.fnew] run function gojo:fuga/flames_init
kill @s
