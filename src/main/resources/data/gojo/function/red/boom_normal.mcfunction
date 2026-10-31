tag @a[distance=..25] add gojo.fxshake_s
particle minecraft:dust{color:[1.0,0.08,0.08],scale:3.5} ~ ~ ~ 2 2 2 0 100 force
particle minecraft:explosion_emitter ~ ~ ~ 0 0 0 0 1 force
execute as @e[type=!#gojo:ignore,distance=..5] unless score @s gojo.id = #owner gojo.id run damage @s 10 minecraft:magic
scoreboard players set #near gojo.tmp 0
execute as @a[distance=..6] if score @s gojo.id = #owner gojo.id run scoreboard players set #near gojo.tmp 1
execute if score #grief gojo.cfg matches 1 if score #near gojo.tmp matches 0 run summon minecraft:creeper ~ ~ ~ {Fuse:0s,ignited:1b,ExplosionRadius:4b,NoAI:1b,Silent:1b,Invulnerable:1b}
execute unless score #grief gojo.cfg matches 1 run summon minecraft:wind_charge
execute if score #grief gojo.cfg matches 1 if score #near gojo.tmp matches 1 run summon minecraft:wind_charge ~ ~0.1 ~ {Motion:[0.0,-2.0,0.0]}
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 2 0.7
