advancement revoke @s only gojo:use_devtoken
advancement revoke @s only gojo:use_devtoken2
execute if score @s gojo.hold matches 1.. run return run scoreboard players set @s gojo.hold 3
scoreboard players set @s gojo.hold 3
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"이미 장면이 재생 중이다","color":"gray"}
execute if score @s gojo.cscd matches 1.. run return run title @s actionbar [{"text":"쿨타임 ","color":"gray"},{"score":{"name":"@s","objective":"gojo.cscd"},"color":"white"},{"text":"틱","color":"gray"}]
scoreboard players set @s gojo.cscd 600
tag @s add gojo.csg
execute unless entity @s[tag=gojo.cstest] run tag @a[distance=0.01..64,sort=nearest,limit=1] add gojo.cstp
tag @s remove gojo.cstest
execute rotated ~ 0 summon marker run function gojo:cs/init
