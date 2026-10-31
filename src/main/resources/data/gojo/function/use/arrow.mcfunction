advancement revoke @s only gojo:use_arrow
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없다 — 천여주박","color":"gray"}
execute if score @s gojo.fuga matches 1.. run return 0
execute if score @s gojo.cd matches 1.. run return run title @s actionbar [{"text":"쿨타임 ","color":"gray"},{"score":{"name":"@s","objective":"gojo.cd"},"color":"white"},{"text":"틱","color":"gray"}]
scoreboard players set #cost gojo.tmp 250
execute unless function gojo:ce/spend run return 0
scoreboard players set @s gojo.fuga 1
scoreboard players set @s gojo.cd 100
title @a[distance=..32] times 0 30 10
title @a[distance=..32] subtitle {"text":"■","color":"dark_red"}
title @a[distance=..32] title {"text":"「푸가(開)」","color":"gold","bold":true}
playsound minecraft:item.firecharge.use master @a ~ ~ ~ 1.5 0.5
playsound minecraft:block.fire.ambient master @a ~ ~ ~ 2 0.5
