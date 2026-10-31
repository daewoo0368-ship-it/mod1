tag @s add gojo.cs2v
execute if predicate gojo:gm_survival run tag @s add gojo.gm_s
execute if predicate gojo:gm_creative run tag @s add gojo.gm_c
execute if predicate gojo:gm_adventure run tag @s add gojo.gm_a
execute if predicate gojo:gm_spectator run tag @s add gojo.gm_sp
summon marker ~ ~ ~ {Tags:["gojo.cs2ret","gojo.csretnew"]}
scoreboard players operation @e[type=marker,tag=gojo.csretnew] gojo.id = @s gojo.id
execute rotated as @s as @e[type=marker,tag=gojo.csretnew] run tp @s ~ ~ ~ ~ ~
tag @e[type=marker,tag=gojo.csretnew] remove gojo.csretnew
tag @s remove gojo.inf
gamemode spectator @s
