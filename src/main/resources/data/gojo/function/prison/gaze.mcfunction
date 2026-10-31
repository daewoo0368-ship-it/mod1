tag @s add gojo.prin
scoreboard players add @s gojo.prt 1
execute at @s run particle minecraft:dust{color:[0.45,0.0,0.05],scale:0.8} ~ ~1 ~ 0.3 0.6 0.3 0 3
execute if entity @s[type=player] run title @s actionbar [{"text":"옥문강이 너를 보고 있다... ","color":"dark_red"},{"score":{"name":"@s","objective":"gojo.prt"},"color":"white"},{"text":"/40","color":"gray"}]
execute if score @s gojo.prt matches 40.. run function gojo:prison/seal
