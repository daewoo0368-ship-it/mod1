tag @a[distance=..48] add gojo.fxflash_r
tag @a[distance=..48] add gojo.fxshake_s
title @a[distance=..64] times 5 50 15
title @a[distance=..64] subtitle {"text":"영역전개","color":"gray","italic":true}
title @a[distance=..64] title {"text":"복마어주자","color":"dark_red","bold":true}
playsound minecraft:entity.wither.spawn master @a ~ ~ ~ 3 0.5
playsound minecraft:block.bell.resonate master @a ~ ~ ~ 3 0.5
playsound minecraft:entity.warden.emerge master @a ~ ~ ~ 2 0.6
particle minecraft:flash{color:[0.8,0.0,0.0,1.0]} ~ ~1 ~ 0 0 0 0 1 force
summon marker ~ ~ ~ {Tags:["gojo.ms","gojo.mnew"]}
execute as @n[type=marker,tag=gojo.mnew] run function gojo:ms/init
execute rotated ~ 0 positioned ^ ^ ^-8 align xyz positioned ~0.5 ~ ~0.5 run function gojo:ms/shrine
execute as @e[type=block_display,tag=gojo.snew] run function gojo:ms/shrine_tag
