advancement revoke @s only gojo:use_redstone
execute if score @s gojo.hold matches 1.. run return run scoreboard players set @s gojo.hold 3
scoreboard players set @s gojo.hold 3
execute if entity @s[tag=gojo.hr] run return run title @s actionbar {"text":"주력이 없는 몸이다 — 천여주박","color":"gray"}
execute if score @s gojo.cemax matches 5000.. run return run title @s actionbar {"text":"주력이 더 이상 늘어나지 않는다 (최대 5000)","color":"gray"}
item modify entity @s weapon.mainhand gojo:consume_one
scoreboard players add @s gojo.cemax 100
scoreboard players add @s gojo.ce 100
title @s actionbar [{"text":"주력 최대치 증가 → ","color":"aqua"},{"score":{"name":"@s","objective":"gojo.cemax"},"color":"white","bold":true}]
playsound minecraft:block.respawn_anchor.charge master @s ~ ~ ~ 1 1.4
playsound minecraft:entity.player.levelup master @s ~ ~ ~ 0.6 1.6
particle minecraft:dust{color:[0.3,0.75,1.0],scale:1.2} ~ ~1 ~ 0.4 0.7 0.4 0 25
particle minecraft:dust{color:[0.9,0.1,0.1],scale:0.8} ~ ~1 ~ 0.3 0.6 0.3 0 10
