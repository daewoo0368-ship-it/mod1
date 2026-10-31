execute if entity @s[type=player] run return run function gojo:six/see_player
particle minecraft:dust{color:[0.45,0.8,1.0],scale:0.7} ~ ~1 ~ 0.25 0.5 0.25 0 2 force @a[tag=gojo.sixv]
execute if entity @s[type=#gojo:hostile] run particle minecraft:dust{color:[1.0,0.3,0.3],scale:0.7} ~ ~1.2 ~ 0.2 0.4 0.2 0 1 force @a[tag=gojo.sixv]
