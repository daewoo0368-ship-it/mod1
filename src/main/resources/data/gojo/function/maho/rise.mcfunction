scoreboard players remove @s gojo.ct 1
tp @s ~ ~0.2 ~
particle minecraft:squid_ink ~ ~4.2 ~ 1.3 0.05 1.3 0.03 25 force
particle minecraft:large_smoke ~ ~4.4 ~ 1.2 0.2 1.2 0.01 6 force
execute if score @s gojo.ct matches 10 run playsound minecraft:block.bell.resonate master @a ~ ~4 ~ 2 0.5
execute if score @s gojo.ct matches 0 run data merge entity @s {NoAI:0b,Invulnerable:0b,NoGravity:0b}
execute if score @s gojo.ct matches 0 at @s run playsound minecraft:entity.ravager.roar master @a ~ ~ ~ 2 0.6
execute if score @s gojo.ct matches 0 run function gojo:maho/anim/roar
execute if score @s gojo.ct matches 0 run scoreboard players set @s gojo.atk -12
