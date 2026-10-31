advancement revoke @s only gojo:use_book
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.sealed] run return 0
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없다 — 천여주박","color":"gray"}
scoreboard players set @s gojo.push 3
scoreboard players operation #cast gojo.id = @s gojo.id
scoreboard players set #clsh gojo.tmp 0
execute as @e[type=marker,tag=gojo.dom,tag=gojo.clashing] if score @s gojo.id = #cast gojo.id run scoreboard players set #clsh gojo.tmp 1
execute if score #clsh gojo.tmp matches 1 run return run scoreboard players set @s gojo.hold 3
execute if score @s gojo.hold matches 1.. run return run scoreboard players set @s gojo.hold 3
scoreboard players set @s gojo.hold 3
scoreboard players operation #cast gojo.id = @s gojo.id
scoreboard players set #has gojo.tmp 0
execute as @e[type=marker,tag=gojo.hg] if score @s gojo.id = #cast gojo.id run scoreboard players set #has gojo.tmp 1
execute if score #has gojo.tmp matches 1 run return run title @s actionbar {"text":"재판이 진행 중이다","color":"gray"}
execute if items entity @s container.* *[minecraft:custom_data~{gojo_exec:1b}] run return run title @s actionbar {"text":"사형 집행인의 검을 지닌 동안은 주복사사를 다시 펼칠 수 없다","color":"gold"}
execute if items entity @s weapon.offhand *[minecraft:custom_data~{gojo_exec:1b}] run return run title @s actionbar {"text":"사형 집행인의 검을 지닌 동안은 주복사사를 다시 펼칠 수 없다","color":"gold"}
execute if score @s gojo.hgcd matches 1.. run return run title @s actionbar [{"text":"영역 쿨타임 ","color":"gray"},{"score":{"name":"@s","objective":"gojo.hgcd"},"color":"white"},{"text":"틱","color":"gray"}]
scoreboard players set #cost gojo.tmp 400
execute unless function gojo:ce/spend run return 0
title @a[distance=..48] times 5 50 15
title @a[distance=..48] subtitle {"text":"영역전개","color":"gray","italic":true}
title @a[distance=..48] title {"text":"주복사사(誅伏賜死)","color":"gold","bold":true}
playsound minecraft:block.bell.use master @a ~ ~ ~ 2 0.5
playsound minecraft:block.wooden_door.close master @a ~ ~ ~ 2 0.5
playsound minecraft:block.anvil.land master @a ~ ~ ~ 1 0.5
execute rotated ~ 0 summon marker run function gojo:hg/init
