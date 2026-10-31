tag @a[distance=..40] add gojo.fxshake
tag @a[distance=..30] add gojo.fxflash_r
particle minecraft:dust{color:[1.0,0.08,0.08],scale:4} ~ ~ ~ 4 4 4 0 250 force
particle minecraft:explosion_emitter ~ ~ ~ 2 2 2 0 4 force
particle minecraft:flash{color:[1.0,0.3,0.3,1.0]} ~ ~ ~ 0 0 0 0 1 force
execute as @e[type=!#gojo:ignore,distance=..10] unless score @s gojo.id = #owner gojo.id run damage @s 25 minecraft:magic
scoreboard players set #near gojo.tmp 0
execute as @a[distance=..9] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
execute if score #grief gojo.cfg matches 1 if score #near gojo.tmp matches 0 run summon minecraft:creeper ~ ~ ~ {Fuse:0s,ignited:1b,powered:1b,ExplosionRadius:4b,NoAI:1b,Silent:1b,Invulnerable:1b}
summon minecraft:wind_charge ~ ~0.1 ~ {Motion:[0.0,-2.0,0.0]}
summon minecraft:wind_charge ~1 ~0.1 ~ {Motion:[0.0,-2.0,0.0]}
summon minecraft:wind_charge ~-1 ~0.1 ~ {Motion:[0.0,-2.0,0.0]}
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 4 0.5
playsound minecraft:entity.lightning_bolt.thunder master @a ~ ~ ~ 2 1.5
