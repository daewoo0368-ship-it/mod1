particle minecraft:dust{color:[1.0,0.85,0.2],scale:2} ~ ~1 ~ 0.5 1 0.5 0 50 force
particle minecraft:sweep_attack ~ ~1 ~ 0.3 0.5 0.3 0 5 force
playsound minecraft:block.bell.use master @a ~ ~ ~ 2 0.4
playsound minecraft:entity.player.attack.crit master @a ~ ~ ~ 2 0.5
title @a[distance=..24] actionbar {"text":"집행","color":"gold","bold":true}
damage @s 100000 minecraft:generic_kill
