tag @s add gojo.fused
scoreboard players operation #owner gojo.id = @s gojo.id
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 4 0.5
playsound minecraft:entity.warden.sonic_boom master @a ~ ~ ~ 4 0.7
playsound minecraft:block.beacon.power_select master @a ~ ~ ~ 4 0.5
particle minecraft:explosion_emitter ~ ~ ~ 0 0 0 0 1 force
particle minecraft:dust{color:[0.6,0.1,1.0],scale:4} ~ ~ ~ 4 4 4 0 120 force
particle minecraft:end_rod ~ ~ ~ 3 3 3 0.3 80 force
particle minecraft:sonic_boom ~ ~ ~ 0 0 0 0 1 force
execute as @e[type=!#gojo:ignore,distance=..14] unless score @s gojo.id = #owner gojo.id run damage @s 60 minecraft:magic
execute if score #grief gojo.cfg matches 1 run function gojo:purple/erase_strong
kill @s
