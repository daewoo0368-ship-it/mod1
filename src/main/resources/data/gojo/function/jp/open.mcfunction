tag @a[distance=..40] add gojo.fxshake_s
title @a[distance=..48] times 5 50 15
title @a[distance=..48] subtitle {"text":"영역전개","color":"gray","italic":true}
title @a[distance=..48] title {"text":"자폐원돈과","color":"dark_aqua","bold":true}
playsound minecraft:block.sculk_shrieker.shriek master @a ~ ~ ~ 2 0.6
playsound minecraft:entity.warden.emerge master @a ~ ~ ~ 2 0.8
playsound minecraft:block.sculk_catalyst.bloom master @a ~ ~ ~ 2 0.5
particle minecraft:sculk_soul ~ ~1 ~ 3 2 3 0.05 80 force
summon marker ~ ~ ~ {Tags:["gojo.jp","gojo.jnew"]}
execute as @n[type=marker,tag=gojo.jnew] run function gojo:jp/init
