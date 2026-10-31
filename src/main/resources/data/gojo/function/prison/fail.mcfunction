execute as @e[tag=gojo.prd] if score @s gojo.id = #owner gojo.id run kill @s
particle minecraft:large_smoke ~ ~0.8 ~ 0.4 0.4 0.4 0.02 20 force
playsound minecraft:block.iron_door.close master @a ~ ~ ~ 1.5 0.8
title @a[distance=..16] actionbar {"text":"옥문강이 닫혔다 — 봉인 실패","color":"gray"}
kill @s
