advancement revoke @s only gojo:use_wart
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없다 — 천여주박","color":"gray"}
execute if score @s gojo.hold matches 1.. run return run scoreboard players set @s gojo.hold 3
scoreboard players set @s gojo.hold 3
execute if score @s gojo.ucd matches 1.. run return run title @s actionbar [{"text":"쿨타임 ","color":"gray"},{"score":{"name":"@s","objective":"gojo.ucd"},"color":"white"},{"text":"틱","color":"gray"}]
scoreboard players set #cost gojo.tmp 350
execute unless function gojo:ce/spend run return 0
scoreboard players set @s gojo.ucd 200
scoreboard players operation #cast gojo.id = @s gojo.id
title @a[distance=..64] times 0 40 10
title @a[distance=..64] subtitle {"text":"죠고","color":"gold","italic":true}
title @a[distance=..64] title {"text":"극번 「운(隕)」","color":"red","bold":true}
playsound minecraft:entity.blaze.ambient master @a ~ ~ ~ 2 0.5
playsound minecraft:item.firecharge.use master @a ~ ~ ~ 2 0.5
particle minecraft:flame ~ ~1 ~ 0.6 1 0.6 0.05 40 force
particle minecraft:lava ~ ~1 ~ 0.6 1 0.6 0 10 force
summon marker ~ ~ ~ {Tags:["gojo.ray"]}
execute anchored eyes positioned ^ ^ ^1 as @n[type=marker,tag=gojo.ray] run function gojo:meteor/ray_init
