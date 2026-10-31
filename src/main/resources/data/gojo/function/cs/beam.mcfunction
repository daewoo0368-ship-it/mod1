particle minecraft:dust{color:[0.6,0.1,1.0],scale:4} ~ ~ ~ 1.4 1.4 1.4 0 60 force
particle minecraft:witch ~ ~ ~ 1.6 1.6 1.6 0 15 force
particle minecraft:end_rod ~ ~ ~ 1 1 1 0.05 5 force
execute if score #grief gojo.cfg matches 1 unless entity @e[type=mannequin,tag=gojo.csga,distance=..7] unless entity @e[tag=gojo.cst,distance=..3] run function gojo:purple/erase
execute if entity @e[tag=gojo.cst,distance=..2.5] run return run function gojo:cs/impact
execute facing entity @e[tag=gojo.cst,limit=1] eyes run tp @s ^ ^ ^0.6 ~ ~
