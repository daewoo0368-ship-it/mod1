advancement revoke @s only gojo:use_nugget
execute if items entity @s weapon.mainhand minecraft:gold_nugget[minecraft:custom_name="개발자 토큰3"] run return 0
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없다 — 천여주박","color":"gray"}
scoreboard players set @s gojo.push 3
scoreboard players operation #cast gojo.id = @s gojo.id
scoreboard players set #clsh gojo.tmp 0
execute as @e[type=marker,tag=gojo.dom,tag=gojo.clashing] if score @s gojo.id = #cast gojo.id run scoreboard players set #clsh gojo.tmp 1
execute if score #clsh gojo.tmp matches 1 run return run scoreboard players set @s gojo.hold 3
execute if score @s gojo.hold matches 1.. run return run scoreboard players set @s gojo.hold 3
scoreboard players set @s gojo.hold 3
scoreboard players set #has gojo.tmp 0
execute as @e[type=marker,tag=gojo.hk,tag=!gojo.closing] if score @s gojo.id = #cast gojo.id run scoreboard players set #has gojo.tmp 1
execute if score #has gojo.tmp matches 1 run return run execute as @e[type=marker,tag=gojo.hk,tag=!gojo.closing] if score @s gojo.id = #cast gojo.id at @s run function gojo:hk/close
execute if score @s gojo.hkcd matches 1.. run return run title @s actionbar [{"text":"영역 쿨타임 ","color":"gray"},{"score":{"name":"@s","objective":"gojo.hkcd"},"color":"white"},{"text":"틱","color":"gray"}]
scoreboard players set #cost gojo.tmp 300
execute unless function gojo:ce/spend run return 0
function gojo:hk/open
