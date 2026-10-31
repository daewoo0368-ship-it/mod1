execute as @e[tag=gojo.csm,limit=1] on passengers if entity @s[tag=gojo.mwheel] run rotate @s ~45 0
execute at @e[tag=gojo.csm,limit=1] run playsound minecraft:block.bell.use master @a ~ ~ ~ 3 0.6
execute at @e[tag=gojo.csm,limit=1] run playsound minecraft:block.chain.place master @a ~ ~ ~ 2 0.5
execute at @e[tag=gojo.csm,limit=1] run particle minecraft:dust{color:[1.0,0.85,0.2],scale:1.5} ~ ~4.8 ~ 1 0.2 1 0 30 force
title @a[tag=gojo.cs2v] subtitle {"text":"— 법진이 돈다 —","color":"gold"}
title @a[tag=gojo.cs2v] title ""
