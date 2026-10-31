tag @a[distance=..40] add gojo.fxflash_w
tag @a[distance=..40] add gojo.fxshake_s
title @a[distance=..40] times 5 40 10
title @a[distance=..40] subtitle {"text":"영역전개","color":"gray","italic":true}
title @a[distance=..40] title {"text":"무량공처","color":"white","bold":true}
playsound minecraft:block.end_portal.spawn master @a ~ ~ ~ 2 0.8
playsound minecraft:block.beacon.activate master @a ~ ~ ~ 2 0.5
playsound minecraft:ambient.cave master @a ~ ~ ~ 2 0.5
particle minecraft:flash{color:[1.0,1.0,1.0,1.0]} ~ ~1 ~ 0 0 0 0 1 force
summon marker ~ ~ ~ {Tags:["gojo.domain","gojo.dnew"]}
execute as @n[type=marker,tag=gojo.dnew] run function gojo:domain/init
