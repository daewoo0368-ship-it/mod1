tag @a[distance=..24] add gojo.fxflash_k
execute unless entity @e[type=marker,tag=gojo.pr,tag=!gojo.prc,distance=..0.5] run return 0
execute as @e[type=marker,tag=gojo.pr,tag=!gojo.prc,distance=..0.5] run tag @s add gojo.prc
tp @e[type=marker,tag=gojo.prc,distance=..0.5,limit=1] @s
scoreboard players set @s gojo.prt 0
tag @s add gojo.sealed
tag @s remove gojo.inf
scoreboard players set @s gojo.chant 0
scoreboard players set @s gojo.fuga 0
scoreboard players operation @s gojo.prown = #owner gojo.id
attribute @s minecraft:jump_strength modifier add gojo:pr -1 add_multiplied_total
attribute @s minecraft:gravity modifier add gojo:pr -1 add_multiplied_total
execute if entity @s[type=!player] unless data entity @s {NoAI:1b} run function gojo:prison/freeze
execute as @e[tag=gojo.prd] if score @s gojo.id = #owner gojo.id run kill @s
scoreboard players operation #cast gojo.id = #owner gojo.id
execute at @s run function gojo:prison/build_big
execute as @e[tag=gojo.prdnew] run function gojo:prison/disp_init
execute at @s run particle minecraft:dust{color:[0.45,0.0,0.05],scale:2} ~ ~1 ~ 0.8 1.2 0.8 0 80 force
execute at @s run playsound minecraft:block.iron_door.close master @a ~ ~ ~ 2 0.4
execute at @s run playsound minecraft:block.chain.place master @a ~ ~ ~ 2 0.5
execute at @s run playsound minecraft:entity.elder_guardian.curse master @a ~ ~ ~ 1.5 0.5
title @a[distance=..32] times 5 50 15
title @a[distance=..32] subtitle {"text":"옥문 폐문","color":"gray","italic":true}
title @a[distance=..32] title {"text":"봉인","color":"dark_red","bold":true}
