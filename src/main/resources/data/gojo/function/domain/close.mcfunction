tag @s add gojo.closing
scoreboard players set @s gojo.life 0
scoreboard players operation #owner gojo.id = @s gojo.id
execute as @a[distance=..64] if score @s gojo.id = #owner gojo.id run scoreboard players set @s gojo.dcd 100
execute as @e[type=block_display,tag=gojo.wall] if score @s gojo.id = #owner gojo.id run data merge entity @s {start_interpolation:0,interpolation_duration:10,transformation:{translation:[0f,0f,0f],scale:[0f,0f,0f]}}
execute as @e[tag=gojo.trapped,distance=..80] run function gojo:domain/release
title @a[distance=..40] actionbar {"text":"영역 해제","color":"gray"}
playsound minecraft:block.beacon.deactivate master @a ~ ~ ~ 2 0.5
playsound minecraft:block.glass.break master @a ~ ~ ~ 2 0.5
