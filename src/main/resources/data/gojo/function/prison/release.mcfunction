scoreboard players operation #owner gojo.id = @s gojo.id
execute as @e[tag=gojo.sealed] if score @s gojo.prown = #owner gojo.id run function gojo:prison/free
execute as @e[tag=gojo.prd] if score @s gojo.id = #owner gojo.id run kill @s
particle minecraft:dust{color:[0.45,0.0,0.05],scale:2} ~ ~1 ~ 1 1.2 1 0 60 force
particle minecraft:large_smoke ~ ~1 ~ 0.8 1 0.8 0.03 30 force
playsound minecraft:block.iron_door.open master @a ~ ~ ~ 2 0.6
playsound minecraft:block.chain.break master @a ~ ~ ~ 2 0.6
title @a[distance=..32] actionbar {"text":"옥문 개방 — 봉인 해제","color":"gray"}
kill @s
