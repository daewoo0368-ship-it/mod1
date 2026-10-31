attribute @s minecraft:scale modifier remove gojo:idle
execute store result score #r gojo.tmp run random value 0..3
execute if score #r gojo.tmp matches 0 run attribute @s minecraft:scale modifier add gojo:idle -0.45 add_multiplied_total
execute if score #r gojo.tmp matches 1 run attribute @s minecraft:scale modifier add gojo:idle -0.25 add_multiplied_total
execute if score #r gojo.tmp matches 2 run attribute @s minecraft:scale modifier add gojo:idle 0.35 add_multiplied_total
execute if score #r gojo.tmp matches 3 run attribute @s minecraft:scale modifier add gojo:idle 0.7 add_multiplied_total
tag @s add gojo.deformed
scoreboard players set @s gojo.dt 600
execute if entity @s[type=player] run title @s actionbar {"text":"영혼의 형태가 비틀린다...","color":"dark_aqua"}
