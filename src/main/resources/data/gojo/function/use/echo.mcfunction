advancement revoke @s only gojo:use_echo
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if score @s gojo.hold matches 1.. run return run scoreboard players set @s gojo.hold 3
scoreboard players set @s gojo.hold 3
scoreboard players operation #cast gojo.id = @s gojo.id
scoreboard players set #has gojo.tmp 0
execute as @e[type=marker,tag=gojo.pr] if score @s gojo.id = #cast gojo.id run scoreboard players set #has gojo.tmp 1
execute if score #has gojo.tmp matches 1 run return run execute as @e[type=marker,tag=gojo.pr] if score @s gojo.id = #cast gojo.id at @s run function gojo:prison/release
execute if score @s gojo.pcd matches 1.. run return run title @s actionbar [{"text":"쿨타임 ","color":"gray"},{"score":{"name":"@s","objective":"gojo.pcd"},"color":"white"},{"text":"틱","color":"gray"}]
scoreboard players set @s gojo.pcd 200
title @a[distance=..32] times 0 40 10
title @a[distance=..32] subtitle {"text":"특급 주물","color":"gray","italic":true}
title @a[distance=..32] title {"text":"옥문강(獄門疆)","color":"dark_red","bold":true}
playsound minecraft:block.iron_door.open master @a ~ ~ ~ 1.5 0.5
playsound minecraft:entity.elder_guardian.curse master @a ~ ~ ~ 1 0.6
execute rotated ~ 0 positioned ^ ^ ^3 summon marker run function gojo:prison/init
