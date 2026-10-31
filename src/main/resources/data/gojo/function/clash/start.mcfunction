scoreboard players operation #b gojo.tmp = @s gojo.id
tag @s add gojo.clashing
tag @e[type=marker,tag=gojo.cself] add gojo.clashing
summon marker ~ ~ ~ {Tags:["gojo.clash","gojo.cnew"]}
execute as @n[type=marker,tag=gojo.cnew] run function gojo:clash/init
execute as @e[type=marker,tag=gojo.domain,tag=gojo.clashing] at @s as @e[tag=gojo.trapped,distance=..80] run function gojo:domain/release
title @a[distance=..64] times 5 40 10
title @a[distance=..64] subtitle {"text":"필중 효과가 상쇄된다 — 도구를 꾹 눌러 밀어붙여라","color":"gray"}
title @a[distance=..64] title {"text":"영역 싸움","color":"light_purple","bold":true}
playsound minecraft:block.end_portal.spawn master @a ~ ~ ~ 3 0.6
playsound minecraft:entity.warden.sonic_boom master @a ~ ~ ~ 3 0.5
playsound minecraft:block.glass.break master @a ~ ~ ~ 3 0.5
