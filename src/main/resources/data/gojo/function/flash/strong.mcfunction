tag @a[distance=..24] add gojo.fxshake_s
tag @a[distance=..16] add gojo.fxflash_k
damage @s 35 minecraft:magic by @p[tag=gojo.atk]
title @a[distance=..32] title {"text":"흑섬","color":"black","bold":true}
title @a[distance=..32] subtitle {"text":"— 黑閃 —","color":"dark_red"}
particle minecraft:dust{color:[0.0,0.0,0.0],scale:4} ~ ~1 ~ 0.8 1 0.8 0 120 force
particle minecraft:dust{color:[0.9,0.0,0.0],scale:3} ~ ~1 ~ 1.2 1.4 1.2 0 90 force
particle minecraft:dust_color_transition{from_color:[1.0,0.0,0.0],to_color:[0.0,0.0,0.0],scale:2.5} ~ ~1 ~ 2 2 2 0 80 force
particle minecraft:sonic_boom ~ ~1 ~ 0 0 0 0 1 force
particle minecraft:crit ~ ~1 ~ 0.5 0.5 0.5 1.2 60 force
particle minecraft:explosion ~ ~1 ~ 1 1 1 0 4 force
scoreboard players set #bolts gojo.tmp 16
scoreboard players set #blen gojo.tmp 15
execute positioned ~ ~1 ~ run function gojo:flash/bolts
execute positioned ~ ~0.1 ~ run function gojo:flash/ring
playsound minecraft:entity.lightning_bolt.thunder master @a ~ ~ ~ 2 1.8
playsound minecraft:entity.lightning_bolt.impact master @a ~ ~ ~ 2 0.8
playsound minecraft:block.anvil.land master @a ~ ~ ~ 2 0.5
playsound minecraft:entity.warden.sonic_boom master @a ~ ~ ~ 1 2
scoreboard players set #kb gojo.tmp 16
execute rotated as @p[tag=gojo.atk] rotated ~ -15 run function gojo:flash/kb
effect give @p[tag=gojo.atk] minecraft:strength 10 1 true
effect give @p[tag=gojo.atk] minecraft:speed 10 0 true
tellraw @p[tag=gojo.atk] {"text":"「존(Zone)」 돌입 — 10초간 강화","color":"dark_red","italic":true}
