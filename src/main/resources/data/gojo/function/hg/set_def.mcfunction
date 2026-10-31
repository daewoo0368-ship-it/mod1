tag @s add gojo.defend
scoreboard players operation @s gojo.dfn = #owner gojo.id
execute if entity @s[type=player] run title @s actionbar {"text":"당신은 피고인으로 지정되었다","color":"gold"}
execute at @s run particle minecraft:dust{color:[1.0,0.85,0.2],scale:1.2} ~ ~1 ~ 0.4 0.8 0.4 0 30 force
