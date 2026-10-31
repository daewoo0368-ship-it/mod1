tag @a[distance=..40] add gojo.fxshake
tag @a[distance=..40] add gojo.fxflash_p
execute as @e[type=marker,tag=gojo.orb,distance=..4] if score @s gojo.id = #owner gojo.id run kill @s
scoreboard players set #strong gojo.tmp 0
execute if entity @s[tag=gojo.strong] run scoreboard players set #strong gojo.tmp 1
execute if score #strong gojo.tmp matches 1 run title @a[distance=..48] title {"text":"허식 「무라사키」","color":"dark_purple","bold":true}
execute if score #strong gojo.tmp matches 1 run title @a[distance=..48] subtitle ""
particle minecraft:dust{color:[0.6,0.1,1.0],scale:4} ~ ~ ~ 1 1 1 0 80 force
particle minecraft:sonic_boom ~ ~ ~ 0 0 0 0 1 force
particle minecraft:end_rod ~ ~ ~ 0 0 0 0.4 40 force
playsound minecraft:entity.warden.sonic_boom master @a ~ ~ ~ 3 0.5
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 2 0.5
playsound minecraft:block.beacon.activate master @a ~ ~ ~ 2 0.5
execute positioned ^ ^ ^1 summon marker run function gojo:purple/init_beam
scoreboard players set #strong gojo.tmp 0
kill @s
