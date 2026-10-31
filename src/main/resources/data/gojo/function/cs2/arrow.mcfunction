particle minecraft:flame ~ ~ ~ 0.15 0.15 0.15 0.02 12 force
particle minecraft:dust{color:[1.0,0.45,0.0],scale:2} ~ ~ ~ 0.1 0.1 0.1 0 4 force
particle minecraft:lava ~ ~ ~ 0 0 0 0 1 force
execute if entity @e[tag=gojo.csm,distance=..3] run return run function gojo:cs2/impact
execute facing entity @e[tag=gojo.csm,limit=1] eyes run tp @s ^ ^ ^0.9 ~ ~
