scoreboard players operation #owner gojo.id = @s gojo.id
execute as @a[distance=..64] if score @s gojo.id = #owner gojo.id run scoreboard players set @s gojo.mcd 100
execute as @e[type=block_display,tag=gojo.shrine] if score @s gojo.id = #owner gojo.id at @s run function gojo:ms/shrine_kill
title @a[distance=..64] actionbar {"text":"영역 해제","color":"gray"}
playsound minecraft:block.bell.use master @a ~ ~ ~ 2 0.5
kill @s
