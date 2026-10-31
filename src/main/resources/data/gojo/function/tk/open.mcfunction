tag @a[distance=..40] add gojo.fxshake_s
title @a[distance=..48] times 5 50 15
title @a[distance=..48] subtitle {"text":"영역전개","color":"gray","italic":true}
title @a[distance=..48] title {"text":"타이코카이","color":"dark_green","bold":true}
playsound minecraft:block.azalea.place master @a ~ ~ ~ 3 0.5
playsound minecraft:block.roots.break master @a ~ ~ ~ 3 0.5
playsound minecraft:block.big_dripleaf.tilt_down master @a ~ ~ ~ 2 0.5
particle minecraft:cherry_leaves ~ ~2 ~ 5 3 5 0.1 200 force
summon marker ~ ~ ~ {Tags:["gojo.tk","gojo.tknew"]}
execute as @n[type=marker,tag=gojo.tknew] run function gojo:tk/init
