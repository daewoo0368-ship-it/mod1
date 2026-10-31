advancement revoke @s only gojo:use_stick
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없다 — 천여주박","color":"gray"}
execute if score @s gojo.wcd matches 1.. run return 0
scoreboard players set #cost gojo.tmp 20
execute unless function gojo:ce/spend run return 0
scoreboard players set @s gojo.wcd 25
scoreboard players operation #cast gojo.id = @s gojo.id
title @s actionbar {"text":"「목구(木球)」","color":"dark_green","bold":true}
playsound minecraft:block.wood.break master @a ~ ~ ~ 1.5 0.6
playsound minecraft:entity.wind_charge.throw master @a ~ ~ ~ 1.5 0.6
execute anchored eyes positioned ^-0.6 ^-0.3 ^1.2 rotated ~-9 ~-3 summon block_display run function gojo:wb/init
execute anchored eyes positioned ^0.6 ^-0.3 ^1.2 rotated ~9 ~-3 summon block_display run function gojo:wb/init
execute anchored eyes positioned ^-0.3 ^0.2 ^1.2 rotated ~-4 ~-6 summon block_display run function gojo:wb/init
execute anchored eyes positioned ^0.3 ^0.2 ^1.2 rotated ~4 ~-6 summon block_display run function gojo:wb/init
execute anchored eyes positioned ^ ^-0.1 ^1.4 rotated ~ ~-2 summon block_display run function gojo:wb/init
execute anchored eyes positioned ^ ^0.4 ^1.2 rotated ~ ~-8 summon block_display run function gojo:wb/init
