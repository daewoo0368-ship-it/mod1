tag @s add gojo.closing
scoreboard players set @s gojo.life 0
scoreboard players operation #owner gojo.id = @s gojo.id
execute as @a if score @s gojo.id = #owner gojo.id unless score @s gojo.jcd matches 101.. run scoreboard players set @s gojo.jcd 100
execute as @e[type=block_display,tag=gojo.jwall] if score @s gojo.id = #owner gojo.id run data merge entity @s {start_interpolation:0,interpolation_duration:10,transformation:{scale:[0f,0f,0f]}}
execute as @e[tag=gojo.jtrap,distance=..80] run tag @s remove gojo.jtrap
title @a[distance=..40] actionbar {"text":"영역 해제","color":"gray"}
playsound minecraft:block.sculk_shrieker.shriek master @a ~ ~ ~ 1.5 1.5
