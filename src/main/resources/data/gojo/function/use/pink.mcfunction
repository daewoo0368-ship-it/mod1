advancement revoke @s only gojo:use_pink
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없다 — 천여주박","color":"gray"}
execute if score @s gojo.kcd matches 1.. run return run title @s actionbar [{"text":"쿨타임 ","color":"gray"},{"score":{"name":"@s","objective":"gojo.kcd"},"color":"white"},{"text":"틱","color":"gray"}]
scoreboard players set #cost gojo.tmp 150
execute unless function gojo:ce/spend run return 0
scoreboard players set @s gojo.kcd 200
scoreboard players operation #cast gojo.id = @s gojo.id
title @a[distance=..32] times 0 40 10
title @a[distance=..32] subtitle {"text":"꽃이 피어나 투지를 앗아간다","color":"gray","italic":true}
title @a[distance=..32] title {"text":"「공화(供花)」","color":"light_purple","bold":true}
playsound minecraft:block.azalea_leaves.place master @a ~ ~ ~ 2 0.6
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 2 0.5
summon marker ~ ~ ~ {Tags:["gojo.kf","gojo.kfnew"]}
execute as @n[type=marker,tag=gojo.kfnew] run function gojo:kf/init
