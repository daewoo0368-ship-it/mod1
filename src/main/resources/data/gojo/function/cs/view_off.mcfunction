execute if entity @s[tag=gojo.gm_s] run gamemode survival @s
execute if entity @s[tag=gojo.gm_c] run gamemode creative @s
execute if entity @s[tag=gojo.gm_a] run gamemode adventure @s
execute if entity @s[tag=gojo.gm_sp] run gamemode spectator @s
tag @s remove gojo.gm_s
tag @s remove gojo.gm_c
tag @s remove gojo.gm_a
tag @s remove gojo.gm_sp
scoreboard players operation #me gojo.id = @s gojo.id
execute as @e[type=marker,tag=gojo.csret] if score @s gojo.id = #me gojo.id run tag @s add gojo.csmine
execute unless entity @s[tag=gojo.csg] unless entity @s[tag=gojo.cstp] run tp @s @e[type=marker,tag=gojo.csmine,limit=1]
execute if entity @s[tag=gojo.csg] run tp @s @e[type=mannequin,tag=gojo.csga,limit=1]
execute if entity @s[tag=gojo.cstp] run tp @s @e[tag=gojo.cst,limit=1]
kill @e[type=marker,tag=gojo.csmine]
tag @s remove gojo.csv
