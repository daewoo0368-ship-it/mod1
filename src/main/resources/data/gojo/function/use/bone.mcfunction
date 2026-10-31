advancement revoke @s only gojo:use_bone
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없다 — 천여주박","color":"gray"}
execute if score @s gojo.hold matches 1.. run return run scoreboard players set @s gojo.hold 3
scoreboard players set @s gojo.hold 3
scoreboard players operation #cast gojo.id = @s gojo.id
scoreboard players set #has gojo.tmp 0
execute as @e[type=iron_golem,tag=gojo.maho] if score @s gojo.id = #cast gojo.id run scoreboard players set #has gojo.tmp 1
execute if score #has gojo.tmp matches 1 run return run execute as @e[type=iron_golem,tag=gojo.maho] if score @s gojo.id = #cast gojo.id at @s run function gojo:maho/dismiss
scoreboard players set #cost gojo.tmp 300
execute unless function gojo:ce/spend run return 0
function gojo:maho/summon
