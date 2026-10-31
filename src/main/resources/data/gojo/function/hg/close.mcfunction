scoreboard players operation #owner gojo.id = @s gojo.id
execute as @e[tag=gojo.defend] if score @s gojo.dfn = #owner gojo.id run function gojo:hg/release_def
execute as @e[tag=gojo.hgd] if score @s gojo.id = #owner gojo.id run kill @s
execute as @a if score @s gojo.id = #owner gojo.id unless score @s gojo.hgcd matches 301.. run scoreboard players set @s gojo.hgcd 300
title @a[distance=..32] actionbar {"text":"폐정 — 영역 해제","color":"gray"}
playsound minecraft:block.wooden_door.open master @a ~ ~ ~ 2 0.6
kill @s
