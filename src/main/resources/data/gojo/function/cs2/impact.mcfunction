tag @a[tag=gojo.cs2v] add gojo.fxshake
tag @a[tag=gojo.cs2v] add gojo.fxflash_o
particle minecraft:explosion_emitter ~ ~ ~ 3 2 3 0 8 force
particle minecraft:flame ~ ~ ~ 2 2 2 0.6 600 force
particle minecraft:lava ~ ~ ~ 5 2 5 0 100 force
particle minecraft:large_smoke ~ ~4 ~ 5 4 5 0.08 200 force
particle minecraft:flash{color:[1.0,0.6,0.1,1.0]} ~ ~ ~ 0 0 0 0 1 force
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 8 0.3
playsound minecraft:item.firecharge.use master @a ~ ~ ~ 6 0.4
playsound minecraft:entity.blaze.death master @a ~ ~ ~ 4 0.4
playsound minecraft:entity.lightning_bolt.thunder master @a ~ ~ ~ 4 0.5
execute as @e[tag=gojo.csm,limit=1] on passengers run kill @s
execute as @e[tag=gojo.csm,limit=1] at @s run tp @s ~ -400 ~
kill @e[tag=gojo.csm]
execute if score #grief gojo.cfg matches 1 run summon minecraft:fireball ~ ~0.5 ~ {ExplosionPower:6b,Motion:[0.0,-3.0,0.0],acceleration_power:1.0d}
kill @s
