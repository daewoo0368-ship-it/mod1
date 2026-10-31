advancement revoke @s only gojo:use_purple
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없다 — 천여주박","color":"gray"}
execute if score @s gojo.chant matches 1.. run return 0
execute if score @s gojo.cd matches 1.. run return run title @s actionbar [{"text":"쿨타임 ","color":"gray"},{"score":{"name":"@s","objective":"gojo.cd"},"color":"white"},{"text":"틱","color":"gray"}]
scoreboard players set #cost gojo.tmp 250
execute if predicate gojo:sneaking run scoreboard players set #cost gojo.tmp 500
execute unless function gojo:ce/spend run return 0
execute if predicate gojo:sneaking run return run function gojo:chant/start {kind:3}
scoreboard players set @s gojo.cd 100
scoreboard players set #strong gojo.tmp 0
function gojo:cast/purple
