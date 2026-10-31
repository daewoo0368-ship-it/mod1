tag @a[distance=..40] add gojo.fxflash_o
tag @a[distance=..40] add gojo.fxshake_s
title @a[distance=..48] times 5 50 15
title @a[distance=..48] subtitle {"text":"영역전개","color":"gray","italic":true}
title @a[distance=..48] title {"text":"개관철위산","color":"gold","bold":true}
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 3 0.4
playsound minecraft:block.lava.extinguish master @a ~ ~ ~ 3 0.5
playsound minecraft:entity.blaze.ambient master @a ~ ~ ~ 2 0.4
particle minecraft:lava ~ ~1 ~ 4 2 4 0 80 force
particle minecraft:flame ~ ~1 ~ 4 2 4 0.1 120 force
summon marker ~ ~ ~ {Tags:["gojo.jg","gojo.gnew"]}
execute as @n[type=marker,tag=gojo.gnew] run function gojo:jg/init
