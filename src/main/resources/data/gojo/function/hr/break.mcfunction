function gojo:hr/strip
scoreboard players set @s gojo.hrlock 1200
title @s times 0 40 10
title @s subtitle {"text":"영혼째 육체가 비틀렸다 — 60초간 재발동 불가","color":"gray"}
title @s title {"text":"천여주박 붕괴","color":"dark_red","bold":true}
effect give @s minecraft:weakness 10 1
effect give @s minecraft:slowness 6 2
effect give @s minecraft:mining_fatigue 10 1
particle minecraft:soul ~ ~1 ~ 0.4 0.8 0.4 0.05 30 force
particle minecraft:dust{color:[0.15,0.35,0.15],scale:2} ~ ~1 ~ 0.6 1 0.6 0 40 force
playsound minecraft:entity.zombie_villager.cure master @a ~ ~ ~ 1.5 0.5
playsound minecraft:item.shield.break master @a ~ ~ ~ 2 0.6
