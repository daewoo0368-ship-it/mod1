execute if entity @s[tag=gojo.gm_s] run gamemode survival @s
execute if entity @s[tag=gojo.gm_c] run gamemode creative @s
execute if entity @s[tag=gojo.gm_a] run gamemode adventure @s
execute if entity @s[tag=gojo.gm_sp] run gamemode spectator @s
tag @s remove gojo.gm_s
tag @s remove gojo.gm_c
tag @s remove gojo.gm_a
tag @s remove gojo.gm_sp
scoreboard players operation #me gojo.id = @s gojo.id
execute as @e[type=marker,tag=gojo.cs2ret] if score @s gojo.id = #me gojo.id run tag @s add gojo.csmine
tp @s @e[type=marker,tag=gojo.csmine,limit=1]
kill @e[type=marker,tag=gojo.csmine]
tag @s remove gojo.cs2v
