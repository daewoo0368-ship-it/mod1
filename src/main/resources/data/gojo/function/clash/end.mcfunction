scoreboard players operation #a gojo.tmp = @s gojo.ca
scoreboard players operation #b gojo.tmp = @s gojo.cb
execute as @e[type=marker,tag=gojo.dom] if score @s gojo.id = #a gojo.tmp run tag @s remove gojo.clashing
execute as @e[type=marker,tag=gojo.dom] if score @s gojo.id = #b gojo.tmp run tag @s remove gojo.clashing
bossbar set gojo:clash players
kill @s
