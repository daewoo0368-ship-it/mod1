# 자폭 무라사키: 십자선을 따라다니는 영창 창 + 혁 → 창이 그 자리에서 보라색으로 폭발
scoreboard players set #cost gojo.tmp 150
execute unless function gojo:ce/spend run return 0
scoreboard players set @s gojo.cd 100
title @s actionbar {"text":"허식 「자폭 무라사키」","color":"dark_purple","bold":true}
title @a[distance=..48] title {"text":"창 × 혁 ─ 자폭 무라사키","color":"light_purple","bold":true}
title @a[distance=..48] subtitle ""
scoreboard players operation #cast gojo.id = @s gojo.id
execute as @e[type=marker,tag=gojo.blue,tag=gojo.strong] if score @s gojo.id = #cast gojo.id at @s run function gojo:blue/fuse
