execute if entity @s[tag=gojo.jackpot] run return 1
execute if entity @s[tag=gojo.six] run scoreboard players operation #cost gojo.tmp /= #4 gojo.cfg
execute if score @s gojo.ce >= #cost gojo.tmp run return run function gojo:ce/pay
title @s actionbar [{"text":"주력이 부족하다  ","color":"dark_aqua","bold":true},{"score":{"name":"@s","objective":"gojo.ce"},"color":"white"},{"text":" / ","color":"gray"},{"score":{"name":"#cost","objective":"gojo.tmp"},"color":"aqua"}]
playsound minecraft:block.note_block.bass master @s ~ ~ ~ 1 0.5
return 0
