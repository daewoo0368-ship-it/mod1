advancement revoke @s only gojo:use_leather
execute if entity @e[type=marker,tag=gojo.csany] run return run title @s actionbar {"text":"장면 재생 중에는 술식을 쓸 수 없다","color":"gray"}
execute if entity @s[tag=gojo.sealed] run return run title @s actionbar {"text":"옥문강에 봉인되어 있다","color":"dark_red"}
execute if score @s gojo.hold matches 1.. run return run scoreboard players set @s gojo.hold 3
scoreboard players set @s gojo.hold 3
execute if entity @s[tag=gojo.hr] run return run function gojo:hr/off
execute if score @s gojo.hrlock matches 1.. run return run title @s actionbar [{"text":"육체가 아직 회복되지 않았다 ","color":"dark_gray"},{"score":{"name":"@s","objective":"gojo.hrlock"},"color":"white"},{"text":"틱","color":"dark_gray"}]
function gojo:hr/on
