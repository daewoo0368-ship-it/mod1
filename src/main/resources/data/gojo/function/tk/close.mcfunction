tag @s add gojo.closing
scoreboard players set @s gojo.life 0
scoreboard players operation #owner gojo.id = @s gojo.id
execute as @a if score @s gojo.id = #owner gojo.id unless score @s gojo.tcd matches 101.. run scoreboard players set @s gojo.tcd 100
execute as @e[type=block_display,tag=gojo.twall] if score @s gojo.id = #owner gojo.id run data merge entity @s {start_interpolation:0,interpolation_duration:10,transformation:{scale:[0f,0f,0f]}}
execute as @e[tag=gojo.ttrap,distance=..80] run tag @s remove gojo.ttrap
title @a[distance=..40] actionbar {"text":"영역 해제","color":"gray"}
playsound minecraft:block.azalea_leaves.break master @a ~ ~ ~ 2 0.5
