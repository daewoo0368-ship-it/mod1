advancement revoke @s only gojo:hit_black
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.confisc] run return run title @s actionbar [{"text":"술식 몰수 중 ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.cft"},"color":"white"},{"text":"틱","color":"gray"}]
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없다 — 천여주박","color":"gray"}
scoreboard players set #cost gojo.tmp 30
execute if predicate gojo:sneaking run scoreboard players set #cost gojo.tmp 80
execute unless function gojo:ce/spend run return 0
tag @s add gojo.atk
scoreboard players set #bf_sneak gojo.tmp 0
execute if predicate gojo:sneaking run scoreboard players set #bf_sneak gojo.tmp 1
execute as @e[type=!#gojo:ignore,distance=..7,nbt={HurtTime:10s}] if function gojo:flash/is_mine at @s run function gojo:flash/hit
tag @s remove gojo.atk
